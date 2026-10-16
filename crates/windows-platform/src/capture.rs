//! Session-local exclusion: never set WDA_EXCLUDEFROMCAPTURE, which also hides
//! annotations from screen-sharing applications.
use std::ffi::c_void;
use windows::{
    core::{factory, IInspectable, IInspectable_Vtbl, Interface, Result, HRESULT},
    Foundation::Collections::IIterable,
    Graphics::{
        Capture::{Direct3D11CaptureFramePool, GraphicsCaptureItem, GraphicsCaptureSession},
        DirectX::{Direct3D11::IDirect3DDevice, DirectXPixelFormat},
    },
    Win32::{
        Foundation::{HMODULE, HWND, POINT},
        Graphics::{
            Direct3D::D3D_DRIVER_TYPE_HARDWARE,
            Direct3D11::{
                D3D11CreateDevice, ID3D11Device, ID3D11Texture2D, D3D11_CPU_ACCESS_READ,
                D3D11_CREATE_DEVICE_BGRA_SUPPORT, D3D11_MAPPED_SUBRESOURCE, D3D11_MAP_READ,
                D3D11_SDK_VERSION, D3D11_TEXTURE2D_DESC, D3D11_USAGE_STAGING,
            },
            Dxgi::IDXGIDevice,
            Gdi::{MonitorFromPoint, HMONITOR, MONITOR_DEFAULTTOPRIMARY},
        },
        System::WinRT::{
            Direct3D11::{CreateDirect3D11DeviceFromDXGIDevice, IDirect3DDxgiInterfaceAccess},
            Graphics::Capture::IGraphicsCaptureItemInterop,
        },
    },
    UI::WindowId,
};

// ABI from Microsoft/windows-rs d3c0d1e528f02edec11c5c2902eb2aa8565219c1.
// The published windows crate does not yet project this interface. Keep the
// shim isolated and remove it once the same ABI is available in a release.
#[repr(transparent)]
#[derive(Clone)]
struct DisplaySession(IInspectable);
unsafe impl Interface for DisplaySession {
    type Vtable = DisplaySessionVtable;
    const IID: windows::core::GUID =
        windows::core::GUID::from_u128(0xbb91f61b_218a_587d_8580_2701a74c0525);
}
#[repr(C)]
struct DisplaySessionVtable {
    base: IInspectable_Vtbl,
    set_excluded: unsafe extern "system" fn(*mut c_void, *mut c_void, *mut u64) -> HRESULT,
    get_excluded: unsafe extern "system" fn(*mut c_void, *mut *mut c_void) -> HRESULT,
}

/// Owns the D3D device, frame pool and session together. The caller initializes
/// WinRT on its capture thread before constructing this object.
pub struct MonitorCapture {
    pub device: ID3D11Device,
    pub pool: Direct3D11CaptureFramePool,
    pub session: GraphicsCaptureSession,
    pub item: GraphicsCaptureItem,
    staging: Option<(u32, u32, ID3D11Texture2D)>,
}

pub struct CapturedFrame {
    pub width: u32,
    pub height: u32,
    pub bgra: Vec<u8>,
}

impl MonitorCapture {
    pub fn primary() -> Result<Self> {
        // SAFETY: MonitorFromPoint returns an OS monitor handle; no pointers borrowed.
        Self::for_monitor(unsafe {
            MonitorFromPoint(POINT { x: 0, y: 0 }, MONITOR_DEFAULTTOPRIMARY)
        })
    }

    pub fn for_monitor(monitor: HMONITOR) -> Result<Self> {
        let mut device = None;
        // SAFETY: output storage lives through the call. Returned COM objects own
        // their references, including the device kept alive with the frame pool.
        unsafe {
            D3D11CreateDevice(
                None,
                D3D_DRIVER_TYPE_HARDWARE,
                HMODULE::default(),
                D3D11_CREATE_DEVICE_BGRA_SUPPORT,
                None,
                D3D11_SDK_VERSION,
                Some(&mut device),
                None,
                None,
            )?;
        }
        let device = device
            .ok_or_else(|| windows::core::Error::from_hresult(HRESULT(0x80004005u32 as i32)))?;
        let dxgi: IDXGIDevice = device.cast()?;
        let winrt: IDirect3DDevice =
            unsafe { CreateDirect3D11DeviceFromDXGIDevice(&dxgi)? }.cast()?;
        let interop: IGraphicsCaptureItemInterop = factory::<GraphicsCaptureItem, _>()?;
        let item: GraphicsCaptureItem = unsafe { interop.CreateForMonitor(monitor)? };
        let pool = Direct3D11CaptureFramePool::CreateFreeThreaded(
            &winrt,
            DirectXPixelFormat::B8G8R8A8UIntNormalized,
            2,
            item.Size()?,
        )?;
        let session = pool.CreateCaptureSession(&item)?;
        Ok(Self {
            device,
            pool,
            session,
            item,
            staging: None,
        })
    }

    /// Coalesced FrameArrived notifications are hints, not a one-to-one queue.
    /// Drain at most our two pool buffers and read back only the newest frame.
    /// A stale notification may legitimately find an empty pool.
    pub fn latest_frame(&mut self) -> Result<Option<CapturedFrame>> {
        let mut latest = None;
        for _ in 0..2 {
            match self.pool.TryGetNextFrame() {
                Ok(frame) => {
                    if let Some(previous) = latest.replace(frame) {
                        let _ = previous.Close();
                    }
                }
                // windows 0.58 projects WinRT's successful null return as an
                // empty Error (HRESULT S_OK). Real HRESULT failures propagate.
                Err(error) if error.code() == HRESULT(0) => break,
                Err(error) => {
                    if let Some(previous) = latest {
                        let _ = previous.Close();
                    }
                    return Err(error);
                }
            }
        }
        let Some(frame) = latest else { return Ok(None) };
        let result = self.read_frame(&frame);
        let _ = frame.Close();
        result.map(Some)
    }

    fn read_frame(
        &mut self,
        frame: &windows::Graphics::Capture::Direct3D11CaptureFrame,
    ) -> Result<CapturedFrame> {
        let access: IDirect3DDxgiInterfaceAccess = frame.Surface()?.cast()?;
        // SAFETY: the captured D3D surface exposes ID3D11Texture2D via its typed interop interface.
        let texture: ID3D11Texture2D = unsafe { access.GetInterface()? };
        let mut desc = D3D11_TEXTURE2D_DESC::default();
        unsafe {
            texture.GetDesc(&mut desc);
        }
        let size = frame.ContentSize()?;
        if size.Width <= 0
            || size.Height <= 0
            || size.Width as u32 > desc.Width
            || size.Height as u32 > desc.Height
        {
            return Err(windows::core::Error::from_hresult(HRESULT(
                0x80070057u32 as i32,
            )));
        }
        if self.staging.as_ref().map(|s| (s.0, s.1)) != Some((desc.Width, desc.Height)) {
            let mut copy_desc = desc;
            copy_desc.Usage = D3D11_USAGE_STAGING;
            copy_desc.BindFlags = 0;
            copy_desc.CPUAccessFlags = D3D11_CPU_ACCESS_READ.0 as u32;
            copy_desc.MiscFlags = 0;
            let mut copy = None;
            unsafe {
                self.device
                    .CreateTexture2D(&copy_desc, None, Some(&mut copy))?;
            }
            self.staging = Some((
                desc.Width,
                desc.Height,
                copy.ok_or_else(|| {
                    windows::core::Error::from_hresult(HRESULT(0x80004005u32 as i32))
                })?,
            ));
        }
        let staging = &self.staging.as_ref().unwrap().2;
        let context = unsafe { self.device.GetImmediateContext()? };
        let width = size.Width as u32;
        let height = size.Height as u32;
        let row_bytes = width as usize * 4;
        let mut bgra = vec![0u8; row_bytes * height as usize];
        unsafe {
            context.CopyResource(staging, &texture);
            let mut mapped = D3D11_MAPPED_SUBRESOURCE::default();
            context.Map(staging, 0, D3D11_MAP_READ, 0, Some(&mut mapped))?;
            if mapped.pData.is_null() || (mapped.RowPitch as usize) < row_bytes {
                context.Unmap(staging, 0);
                return Err(windows::core::Error::from_hresult(HRESULT(
                    0x80004005u32 as i32,
                )));
            }
            for y in 0..height as usize {
                std::ptr::copy_nonoverlapping(
                    mapped.pData.cast::<u8>().add(y * mapped.RowPitch as usize),
                    bgra.as_mut_ptr().add(y * row_bytes),
                    row_bytes,
                );
            }
            context.Unmap(staging, 0);
        }
        Ok(CapturedFrame {
            width,
            height,
            bgra,
        })
    }

    /// Returns E_NOINTERFACE on machines without session-local exclusion.
    /// A successful call is capability evidence, not proof of pixel exclusion.
    pub fn exclude_windows(&self, windows: &[HWND]) -> Result<u64> {
        let display: DisplaySession = self.session.cast()?;
        let ids: IIterable<WindowId> = windows
            .iter()
            .map(|hwnd| WindowId {
                Value: hwnd.0 as usize as u64,
            })
            .collect::<Vec<_>>()
            .try_into()?;
        let mut iteration = 0;
        // SAFETY: QI verified the IID; vtable exactly matches the documented ABI.
        // ids is a real IIterable<Windows.UI.WindowId>, kept alive for this call.
        unsafe {
            (display.vtable().set_excluded)(display.as_raw(), ids.as_raw(), &mut iteration).ok()?;
        }
        Ok(iteration)
    }
}

impl Drop for MonitorCapture {
    fn drop(&mut self) {
        let _ = self.session.Close();
        let _ = self.pool.Close();
    }
}
