//! Portable DeskInk updates. Network and package work run off the ink, capture,
//! transport, and GUI threads. Only an explicit install click requests exit.
use eframe::egui;
use serde::{Deserialize, Serialize};
use std::{
    path::{Path, PathBuf},
    sync::mpsc,
    time::Duration,
};

const MANIFEST_URL: &str =
    "https://raw.githubusercontent.com/zkdlwuthwo1-hash/deskink/main/updates/windows.json";
const ASSET_PREFIX: &str = "https://github.com/zkdlwuthwo1-hash/deskink/releases/download/";

#[derive(Clone, Deserialize, Serialize)]
pub struct UpdateOffer {
    pub version: String,
    pub source_commit: String,
    pub url: String,
    pub sha256: String,
    pub size: u64,
}

#[derive(Deserialize)]
struct BuildIdentity {
    version: String,
    source_commit: String,
}

impl UpdateOffer {
    /// Public descriptor seam: a documentation-only replacement never prompts
    /// for restart, even when its ZIP digest changed. Downgrades are rejected.
    pub fn for_installation(build_json: &str, update_json: &str) -> Result<Option<Self>, String> {
        let current: BuildIdentity = serde_json::from_str(build_json).map_err(|e| e.to_string())?;
        let update: Self = serde_json::from_str(update_json).map_err(|e| e.to_string())?;
        let version = |value: &str| -> Result<semver::Version, String> {
            let version = semver::Version::parse(value.strip_prefix('v').unwrap_or(value))
                .map_err(|e| e.to_string())?;
            if !version.pre.is_empty() || !version.build.is_empty() {
                return Err("Unsupported update version".into());
            }
            Ok(version)
        };
        let hex = |value: &str, length| {
            value.len() == length && value.bytes().all(|b| b.is_ascii_hexdigit())
        };
        let release = update
            .url
            .strip_prefix(ASSET_PREFIX)
            .and_then(|path| path.strip_suffix("/DeskInk-Windows-x64.zip"));
        if !hex(&current.source_commit, 40)
            || !hex(&update.source_commit, 40)
            || !hex(&update.sha256, 64)
            || update.size == 0
            || update.size > 268_435_456
            || !release.is_some_and(|tag| {
                !tag.is_empty()
                    && tag
                        .bytes()
                        .all(|b| b.is_ascii_alphanumeric() || b"._-".contains(&b))
            })
        {
            return Err("Invalid DeskInk update descriptor".into());
        }
        let next = version(&update.version)?;
        let installed = version(&current.version)?;
        Ok((next > installed
            || (next == installed
                && !update
                    .source_commit
                    .eq_ignore_ascii_case(&current.source_commit)))
        .then_some(update))
    }
}

enum State {
    Unmanaged,
    Checking,
    Current,
    Available(UpdateOffer),
    Preparing,
    Ready(PathBuf),
    Installing,
    Failed(String),
}

enum ResultMessage {
    Checked(Result<Option<UpdateOffer>, String>),
    Prepared(Result<PathBuf, String>),
    Installer(Result<InstallReady, String>),
}

struct InstallReady {
    child: std::process::Child,
    receipt: PathBuf,
}

pub struct UpdatePanel {
    root: Option<PathBuf>,
    state: State,
    receiver: mpsc::Receiver<ResultMessage>,
    sender: mpsc::Sender<ResultMessage>,
}

impl UpdatePanel {
    pub fn disabled() -> Self {
        let (sender, receiver) = mpsc::channel();
        Self {
            root: None,
            state: State::Unmanaged,
            receiver,
            sender,
        }
    }

    /// Invoke once when the real controller is created, after monitor-forwarding
    /// exits have returned. Development builds do not check or install updates.
    pub fn start(ctx: &egui::Context) -> Self {
        let mut panel = Self::disabled();
        let root = std::env::current_exe().ok().and_then(|exe| {
            let bin = exe.parent()?;
            if bin.file_name()?.to_str()? != "bin" {
                return None;
            }
            Some(bin.parent()?.to_path_buf())
        });
        let Some(root) = root.filter(|root| {
            root.join("build-info.json").is_file()
                && root.join("scripts/update-portable.ps1").is_file()
        }) else {
            return panel;
        };
        let Ok(build) = std::fs::read_to_string(root.join("build-info.json")) else {
            return panel;
        };
        panel.root = Some(root);
        panel.state = State::Checking;
        let sender = panel.sender.clone();
        let repaint = ctx.clone();
        if let Err(error) = std::thread::Builder::new()
            .name("deskink-update-check".into())
            .spawn(move || {
                let result = fetch_offer(&build);
                let _ = sender.send(ResultMessage::Checked(result));
                repaint.request_repaint();
            })
        {
            panel.state = State::Failed(error.to_string());
        }
        panel
    }

    pub fn show(&mut self, ui: &mut egui::Ui, monitor: usize) -> bool {
        for result in self.receiver.try_iter() {
            self.state = match result {
                ResultMessage::Checked(Ok(Some(offer))) => State::Available(offer),
                ResultMessage::Checked(Ok(None)) => State::Current,
                ResultMessage::Checked(Err(error))
                | ResultMessage::Prepared(Err(error))
                | ResultMessage::Installer(Err(error)) => State::Failed(error),
                ResultMessage::Prepared(Ok(job)) => State::Ready(job),
                ResultMessage::Installer(Ok(mut installer)) => {
                    // If the controller was minimized for a long time, the
                    // waiting helper may already have timed out. Do not close
                    // the presentation after its installer has exited.
                    match installer.child.try_wait() {
                        Ok(None) if installer.receipt.is_file() => return true,
                        _ => State::Failed(
                            "Installer stopped before restart. DeskInk stays open.".into(),
                        ),
                    }
                }
            };
        }
        let mut prepare = None;
        let mut install = None;
        ui.group(|ui| {
            ui.label("App updates");
            match &self.state {
                State::Unmanaged => {
                    ui.small("Updates are available in the portable Windows package.");
                }
                State::Checking => {
                    ui.small("Checking for updates in the background...");
                }
                State::Current => {
                    ui.small("DeskInk is up to date.");
                }
                State::Available(offer) => {
                    ui.label(format!("DeskInk {} is available", offer.version));
                    if ui.button("Download update").clicked() {
                        prepare = Some(offer.clone());
                    }
                    ui.small("You can keep writing while it downloads. Install when ready.");
                }
                State::Preparing => {
                    ui.small("Downloading and verifying update. DeskInk stays open.");
                }
                State::Ready(job) => {
                    ui.label("Update verified and ready.");
                    ui.small(
                        "Installing closes DeskInk and clears the current ink on every screen.",
                    );
                    if ui.button("Install and restart (clears ink)").clicked() {
                        install = Some(job.clone());
                    }
                }
                State::Installing => {
                    ui.small("Preparing installer. DeskInk will restart when it is ready.");
                }
                State::Failed(error) => {
                    ui.small("Update unavailable. DeskInk keeps working.");
                    ui.small(error);
                }
            }
        });
        if let Some(offer) = prepare {
            if let Some(root) = self.root.clone() {
                let sender = self.sender.clone();
                let repaint = ui.ctx().clone();
                self.state = State::Preparing;
                if let Err(error) = std::thread::Builder::new()
                    .name("deskink-update-download".into())
                    .spawn(move || {
                        let result = prepare_update(&root, &offer);
                        let _ = sender.send(ResultMessage::Prepared(result));
                        repaint.request_repaint();
                    })
                {
                    self.state = State::Failed(error.to_string());
                }
            }
        }
        if let Some(job) = install {
            let sender = self.sender.clone();
            let repaint = ui.ctx().clone();
            self.state = State::Installing;
            if let Err(error) = std::thread::Builder::new()
                .name("deskink-update-install".into())
                .spawn(move || {
                    let result = launch_installer(&job, monitor);
                    let _ = sender.send(ResultMessage::Installer(result));
                    repaint.request_repaint();
                })
            {
                self.state = State::Failed(error.to_string());
            }
        }
        false
    }
}

fn fetch_offer(build: &str) -> Result<Option<UpdateOffer>, String> {
    let agent = ureq::Agent::config_builder()
        .timeout_global(Some(Duration::from_secs(5)))
        .max_redirects(0)
        .build()
        .new_agent();
    let text = agent
        .get(MANIFEST_URL)
        .header("User-Agent", "DeskInk-Updater/1")
        .call()
        .and_then(|mut response| {
            response
                .body_mut()
                .with_config()
                .limit(16 * 1024)
                .read_to_string()
        })
        .map_err(|error| format!("Could not check releases: {error}"))?;
    UpdateOffer::for_installation(build, &text)
}

#[cfg(windows)]
fn powershell(script: &Path) -> Result<std::process::Command, String> {
    use std::os::windows::process::CommandExt;
    let shell = PathBuf::from(std::env::var_os("SystemRoot").ok_or("Windows folder unavailable")?)
        .join("System32/WindowsPowerShell/v1.0/powershell.exe");
    let mut command = std::process::Command::new(shell);
    command
        .args([
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
        ])
        .arg(script)
        .creation_flags(0x08000000);
    Ok(command)
}

#[cfg(windows)]
fn prepare_update(root: &Path, offer: &UpdateOffer) -> Result<PathBuf, String> {
    let folder =
        PathBuf::from(std::env::var_os("LOCALAPPDATA").ok_or("Local app data unavailable")?)
            .join("DeskInk/updates");
    std::fs::create_dir_all(&folder).map_err(|error| error.to_string())?;
    let request = folder.join(format!("request-{}.json", std::process::id()));
    std::fs::write(
        &request,
        serde_json::to_vec(offer).map_err(|error| error.to_string())?,
    )
    .map_err(|error| error.to_string())?;
    let result = powershell(&root.join("scripts/update-portable.ps1"))?
        .arg("-Mode")
        .arg("Stage")
        .arg("-PackageRoot")
        .arg(root)
        .arg("-DescriptorPath")
        .arg(&request)
        .output()
        .map_err(|error| error.to_string());
    let _ = std::fs::remove_file(request);
    let output = result?;
    if !output.status.success() {
        return Err(String::from_utf8_lossy(&output.stderr)
            .trim()
            .chars()
            .take(600)
            .collect());
    }
    #[derive(Deserialize)]
    struct Prepared {
        job_path: PathBuf,
    }
    let prepared: Prepared = serde_json::from_slice(&output.stdout)
        .map_err(|error| format!("Invalid installer response: {error}"))?;
    Ok(prepared.job_path)
}

#[cfg(windows)]
fn launch_installer(job: &Path, monitor: usize) -> Result<InstallReady, String> {
    let workspace = job.parent().ok_or("Invalid update job")?;
    let script = workspace.join("apply.ps1");
    let ready = workspace.join("ready");
    if ready.exists() {
        std::fs::remove_file(&ready).map_err(|error| error.to_string())?;
    }
    let errors = workspace.join("installer-error.txt");
    let stderr = std::fs::File::create(&errors).map_err(|error| error.to_string())?;
    let mut child = powershell(&script)?
        .current_dir(workspace)
        .stdout(std::process::Stdio::null())
        .stderr(stderr)
        .arg("-Mode")
        .arg("Apply")
        .arg("-JobPath")
        .arg(job)
        .arg("-WaitProcessId")
        .arg(std::process::id().to_string())
        .arg("-SignalReady")
        .arg("-Monitor")
        .arg(monitor.to_string())
        .spawn()
        .map_err(|error| error.to_string())?;
    let deadline = std::time::Instant::now() + Duration::from_secs(15);
    loop {
        if child
            .try_wait()
            .map_err(|error| error.to_string())?
            .is_some()
        {
            let detail = std::fs::read_to_string(&errors).unwrap_or_default();
            return Err(format!(
                "Installer could not prepare: {}",
                detail.trim().chars().take(600).collect::<String>()
            ));
        }
        if ready.is_file() {
            return Ok(InstallReady {
                child,
                receipt: ready,
            });
        }
        if std::time::Instant::now() >= deadline {
            // Only this owned, pre-install helper is stopped. It cannot replace
            // files while the current DeskInk PID is still running.
            let _ = child.kill();
            let _ = child.wait();
            return Err("Installer preparation timed out. DeskInk stays open.".into());
        }
        std::thread::sleep(Duration::from_millis(40));
    }
}

#[cfg(not(windows))]
fn prepare_update(_root: &Path, _offer: &UpdateOffer) -> Result<PathBuf, String> {
    Err("Windows only".into())
}
#[cfg(not(windows))]
fn launch_installer(_job: &Path, _monitor: usize) -> Result<InstallReady, String> {
    Err("Windows only".into())
}
