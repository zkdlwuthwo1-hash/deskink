//! Local desktop commands for launchers and Stream Deck. No network listener,
//! global shortcut, or remote mouse/keyboard injection is needed.
use std::sync::Once;
use windows::{
    core::{w, Result},
    Win32::{
        Foundation::{HWND, LPARAM, LRESULT, WPARAM},
        System::LibraryLoader::GetModuleHandleW,
        UI::WindowsAndMessaging::*,
    },
};

const SELECT_MONITOR: u32 = WM_APP + 41;
type Handler = Box<dyn Fn(usize)>;

pub fn is_running() -> bool {
    unsafe { FindWindowExW(HWND_MESSAGE, None, w!("DeskInk.MonitorControl.v1"), None).is_ok() }
}

/// Returns false when no updated DeskInk instance is listening on this desktop.
pub fn request_monitor(index: usize) -> Result<bool> {
    if index > 32 {
        return Ok(false);
    }
    let hwnd = unsafe { FindWindowExW(HWND_MESSAGE, None, w!("DeskInk.MonitorControl.v1"), None) };
    let Ok(hwnd) = hwnd else {
        return Ok(false);
    };
    unsafe {
        PostMessageW(hwnd, SELECT_MONITOR, WPARAM(index), LPARAM(0))?;
    }
    Ok(true)
}

/// Owned and dispatched on the GUI thread; the callback wakes egui even when
/// its visible controller window is minimized.
pub struct MonitorControl {
    hwnd: HWND,
    _handler: Box<Handler>,
}

impl MonitorControl {
    pub fn new(on_select: impl Fn(usize) + 'static) -> Result<Self> {
        static REGISTER: Once = Once::new();
        let instance = unsafe { GetModuleHandleW(None)? };
        REGISTER.call_once(|| unsafe {
            RegisterClassW(&WNDCLASSW {
                lpfnWndProc: Some(window_proc),
                hInstance: instance.into(),
                lpszClassName: w!("DeskInk.MonitorControl.v1"),
                ..Default::default()
            });
        });
        let hwnd = unsafe {
            CreateWindowExW(
                WINDOW_EX_STYLE::default(),
                w!("DeskInk.MonitorControl.v1"),
                w!("DeskInk monitor commands"),
                WINDOW_STYLE::default(),
                0,
                0,
                0,
                0,
                HWND_MESSAGE,
                None,
                instance,
                None,
            )?
        };
        let handler: Box<Handler> = Box::new(Box::new(on_select));
        unsafe {
            SetWindowLongPtrW(hwnd, GWLP_USERDATA, (&*handler as *const Handler) as isize);
        }
        Ok(Self {
            hwnd,
            _handler: handler,
        })
    }
}

impl Drop for MonitorControl {
    fn drop(&mut self) {
        unsafe {
            SetWindowLongPtrW(self.hwnd, GWLP_USERDATA, 0);
            let _ = DestroyWindow(self.hwnd);
        }
    }
}

unsafe extern "system" fn window_proc(
    hwnd: HWND,
    message: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    if message == SELECT_MONITOR && wparam.0 <= 32 {
        let handler = GetWindowLongPtrW(hwnd, GWLP_USERDATA) as *const Handler;
        if let Some(handler) = handler.as_ref() {
            handler(wparam.0);
        }
        return LRESULT(1);
    }
    DefWindowProcW(hwnd, message, wparam, lparam)
}
