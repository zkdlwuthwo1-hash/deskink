use deskink_windows::capture::MonitorCapture;
use windows::{
    Graphics::Capture::GraphicsCaptureSession,
    Win32::System::WinRT::{RoInitialize, RoUninitialize, RO_INIT_MULTITHREADED},
};

fn main() -> windows::core::Result<()> {
    unsafe {
        RoInitialize(RO_INIT_MULTITHREADED)?;
    }
    let result = probe();
    unsafe {
        RoUninitialize();
    }
    result
}

fn probe() -> windows::core::Result<()> {
    println!("WGC supported: {}", GraphicsCaptureSession::IsSupported()?);
    let mut capture = MonitorCapture::primary()?;
    assert!(
        capture.latest_frame()?.is_none(),
        "An empty pool is not a capture failure"
    );
    println!("Empty frame pool: handled without a capture error");
    println!(
        "Monitor: {} ({} x {})",
        capture.item.DisplayName()?,
        capture.item.Size()?.Width,
        capture.item.Size()?.Height
    );
    match capture.exclude_windows(&[]) {
        Ok(iteration) => println!("Session-local exclusion: AVAILABLE (iteration {iteration}). Pixel verification pending."),
        Err(error) => {
            println!("Session-local exclusion: UNAVAILABLE ({error})");
            return Err(error);
        }
    }
    Ok(())
}
