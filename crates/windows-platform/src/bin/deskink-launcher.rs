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
        std::process::exit(1);
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
    let monitor: usize = match argument("--monitor") {
        Some(value) => value.to_str().ok_or("Invalid monitor")?.parse()?,
        None => 0,
    };
    if monitor > 32 {
        return Err("Invalid monitor".into());
    }
    // The common case needs neither PowerShell nor a second host process.
    let executable = std::env::current_exe()?;
    let android = args.iter().any(|value| value == "--android")
        || executable.file_stem().is_some_and(|name| name.eq_ignore_ascii_case("DeskInk-Android"));
    let shortcuts = args.iter().any(|value| value == "--create-shortcuts")
        || executable
            .file_stem()
            .is_some_and(|name| name.eq_ignore_ascii_case("DeskInk-Shortcuts"));
    if !shortcuts && !android && deskink_windows::monitor_control::request_monitor(monitor)? {
        return Ok(());
    }
    let (root, script) = if let Some(project) = argument("--project") {
        (
            std::path::PathBuf::from(project),
            "scripts/start-deskink.ps1",
        )
    } else {
        let root = executable
            .parent()
            .ok_or("DeskInk folder is unavailable")?
            .to_path_buf();
        (
            root,
            if shortcuts {
                "scripts/create-shortcuts.ps1"
            } else if android {
                "scripts/start-android.ps1"
            } else {
                "scripts/start-portable.ps1"
            },
        )
    };
    if !root.is_absolute() || !root.join(script).is_file() {
        return Err("Extract the entire DeskInk ZIP into a folder before running DeskInk.exe. The scripts folder is missing.".into());
    }
    let powershell =
        std::path::PathBuf::from(std::env::var_os("SystemRoot").ok_or("Windows path unavailable")?)
            .join("System32/WindowsPowerShell/v1.0/powershell.exe");
    let mut command = Command::new(powershell);
    command
        .args([
            "-NoProfile",
            "-NonInteractive",
            "-Sta",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
        ])
        .arg(root.join(script));
    if !shortcuts {
        command.arg("-Monitor").arg(monitor.to_string());
    }
    command
        .current_dir(root)
        .creation_flags(0x08000000)
        .spawn()?;
    Ok(())
}

#[cfg(not(windows))]
fn main() {}
