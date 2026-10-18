//! Optional Android USB bridge. ADB selects USB only; Wi-Fi/emulator devices are
//! never chosen by discovery. Each tunnel owns one ephemeral loopback forward.
use std::{io, path::PathBuf, pin::Pin, task::{Context, Poll}, time::Duration};
use tokio::{io::{AsyncRead, AsyncWrite, ReadBuf}, net::TcpStream, process::Command};
use super::usbmuxd::BoxedTunnel;

#[derive(Clone)]
pub struct Adb { executable: PathBuf }

impl Adb {
    pub fn new(executable: PathBuf) -> Self { Self { executable } }

    pub fn discover() -> Option<Self> {
        let mut paths = Vec::new();
        if let Some(path) = std::env::var_os("DESKINK_ADB") { paths.push(PathBuf::from(path)); }
        if let Some(local) = std::env::var_os("LOCALAPPDATA") {
            paths.push(PathBuf::from(local).join("DeskInk/android/platform-tools/adb.exe"));
        }
        if let Ok(exe) = std::env::current_exe() {
            if let Some(root) = exe.parent().and_then(|p| p.parent()) {
                paths.push(root.join("runtime/android/platform-tools/adb.exe"));
            }
        }
        paths.into_iter().find(|p| p.is_absolute() && p.is_file()).map(Self::new)
    }

    async fn run(&self, args: &[&str]) -> io::Result<String> {
        let mut command = Command::new(&self.executable);
        command.args(args).kill_on_drop(true);
        #[cfg(windows)]
        command.creation_flags(0x08000000);
        let result = tokio::time::timeout(Duration::from_secs(5), command.output()).await
            .map_err(|_| io::Error::new(io::ErrorKind::TimedOut, "Android USB command timeout"))??;
        if !result.status.success() || result.stdout.len() > 4096 {
            return Err(io::Error::new(io::ErrorKind::NotConnected, "Android USB unavailable; connect one tablet and allow USB debugging"));
        }
        String::from_utf8(result.stdout).map(|s| s.trim().to_owned())
            .map_err(|_| io::Error::new(io::ErrorKind::InvalidData, "ADB response encoding"))
    }

    pub async fn connect(&self) -> io::Result<BoxedTunnel> {
        // -d is ADB's USB-only selector. Pin subsequent commands to that serial
        // so unplug/replug cannot remove a different device's forwarding rule.
        let serial = self.run(&["-d", "get-serialno"]).await?;
        if serial.is_empty() || serial.len() > 128 || serial == "unknown"
            || !serial.as_bytes()[0].is_ascii_alphanumeric()
            || !serial.bytes().all(|b| b.is_ascii_alphanumeric() || b"._-".contains(&b)) {
            return Err(io::Error::new(io::ErrorKind::InvalidData, "Android USB serial unavailable"));
        }
        let port = self.run(&["-s", &serial, "forward", "tcp:0", "tcp:9877"]).await?;
        let port: u16 = port.parse().ok().filter(|p| *p != 0)
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidData, "ADB forward port"))?;
        let forward = Forward { adb: self.clone(), serial, port };
        let socket = tokio::time::timeout(Duration::from_secs(2), TcpStream::connect((std::net::Ipv4Addr::LOCALHOST, port))).await
            .map_err(|_| io::Error::new(io::ErrorKind::TimedOut, "Android app connect timeout"))??;
        socket.set_nodelay(true)?;
        let _ = socket2::SockRef::from(&socket).set_send_buffer_size(32 * 1024);
        Ok(Box::new(Tunnel { socket, _forward: forward }))
    }
}

struct Forward { adb: Adb, serial: String, port: u16 }
impl Drop for Forward {
    fn drop(&mut self) {
        // Drop also runs while Tokio is shutting down. Complete this bounded
        // disconnect-only cleanup without scheduling work on that runtime.
        let mut command = std::process::Command::new(&self.adb.executable);
        command.args(["-s", &self.serial, "forward", "--remove", &format!("tcp:{}", self.port)])
            .stdin(std::process::Stdio::null()).stdout(std::process::Stdio::null()).stderr(std::process::Stdio::null());
        #[cfg(windows)] {
            use std::os::windows::process::CommandExt;
            command.creation_flags(0x08000000);
        }
        if let Ok(mut child) = command.spawn() {
            let deadline = std::time::Instant::now() + Duration::from_secs(2);
            loop {
                match child.try_wait() {
                    Ok(Some(_)) => break,
                    Ok(None) if std::time::Instant::now() < deadline => std::thread::sleep(Duration::from_millis(10)),
                    _ => { let _ = child.kill(); let _ = child.wait(); break; }
                }
            }
        }
    }
}
struct Tunnel { socket: TcpStream, _forward: Forward }
impl AsyncRead for Tunnel {
    fn poll_read(mut self: Pin<&mut Self>, cx: &mut Context<'_>, buf: &mut ReadBuf<'_>) -> Poll<io::Result<()>> {
        Pin::new(&mut self.socket).poll_read(cx, buf)
    }
}
impl AsyncWrite for Tunnel {
    fn poll_write(mut self: Pin<&mut Self>, cx: &mut Context<'_>, bytes: &[u8]) -> Poll<io::Result<usize>> { Pin::new(&mut self.socket).poll_write(cx, bytes) }
    fn poll_flush(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> { Pin::new(&mut self.socket).poll_flush(cx) }
    fn poll_shutdown(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<io::Result<()>> { Pin::new(&mut self.socket).poll_shutdown(cx) }
}
