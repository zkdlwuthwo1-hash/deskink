#![cfg_attr(windows, windows_subsystem = "windows")]

#[cfg(windows)]
fn main() {
    if let Err(error) = run() {
        use windows::{
            core::{w, HSTRING},
            Win32::UI::WindowsAndMessaging::*,
        };
        unsafe {
            MessageBoxW(
                None,
                &HSTRING::from(error.to_string()),
                w!("DeskInk"),
                MB_OK | MB_ICONERROR,
            );
        }
    }
}

#[cfg(windows)]
fn run() -> Result<(), Box<dyn std::error::Error>> {
    use std::{os::windows::process::CommandExt, process::Command};
    let args: Vec<_> = std::env::args_os().collect();
    let argument = |name: &str| {
        args.iter()
            .position(|value| value == name)
            .and_then(|at| args.get(at + 1))
    };
    let monitor: usize = argument("--monitor")
        .ok_or("Missing monitor")?
        .to_str()
        .ok_or("Invalid monitor")?
        .parse()?;
    if monitor > 32 {
        return Err("Invalid monitor".into());
    }
    // The common case needs neither PowerShell nor a second host process.
    if deskink_windows::monitor_control::request_monitor(monitor)? {
        return Ok(());
    }
    let project = std::path::PathBuf::from(argument("--project").ok_or("Missing project path")?);
    if !project.is_absolute() || !project.join("scripts/start-deskink.ps1").is_file() {
        return Err("DeskInk project path is unavailable".into());
    }
    let powershell =
        std::path::PathBuf::from(std::env::var_os("SystemRoot").ok_or("Windows path unavailable")?)
            .join("System32/WindowsPowerShell/v1.0/powershell.exe");
    Command::new(powershell)
        .args(["-NoProfile", "-NonInteractive", "-File"])
        .arg(project.join("scripts/start-deskink.ps1"))
        .arg("-Monitor")
        .arg(monitor.to_string())
        .current_dir(project)
        .creation_flags(0x08000000)
        .spawn()?;
    Ok(())
}

#[cfg(not(windows))]
fn main() {}
