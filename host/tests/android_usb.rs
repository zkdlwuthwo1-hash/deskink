#![cfg(windows)]
use eternal_host::transport::android_usb::Adb;
use std::{fs, sync::atomic::{AtomicU64, Ordering}, time::Duration};
use tokio::{io::{AsyncReadExt, AsyncWriteExt}, net::TcpListener};

#[test]
fn owned_forward_is_removed_even_after_the_async_runtime_has_stopped() {
    let folder = std::env::temp_dir().join(format!("deskink-adb-runtime-{}", std::process::id()));
    fs::create_dir(&folder).unwrap();
    let server = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
    let port = server.local_addr().unwrap().port();
    let removed = folder.join("removed.txt");
    let script = folder.join("adb.cmd");
    fs::write(&script, format!("@echo off\r\nif \"%1\"==\"-d\" (\r\necho ANDROID-TEST\r\nexit /b 0\r\n)\r\nif \"%4\"==\"--remove\" (\r\necho %5>\"{}\"\r\nexit /b 0\r\n)\r\necho {}\r\n", removed.display(), port)).unwrap();
    let runtime = tokio::runtime::Builder::new_current_thread().enable_all().build().unwrap();
    let tunnel = runtime.block_on(Adb::new(script.clone()).connect()).unwrap();
    drop(runtime);
    drop(tunnel);
    let deadline = std::time::Instant::now() + Duration::from_secs(2);
    while !removed.is_file() && std::time::Instant::now() < deadline { std::thread::sleep(Duration::from_millis(20)); }
    let result = fs::read_to_string(&removed);
    fs::remove_file(script).unwrap();
    if removed.is_file() { fs::remove_file(removed).unwrap(); }
    fs::remove_dir(folder).unwrap();
    assert_eq!(result.unwrap().trim(), format!("tcp:{port}"));
}

/// Only the external ADB executable is substituted; the tunnel uses real TCP.
#[tokio::test]
async fn usb_forward_connects_loopback_and_removes_only_its_owned_port() {
    static ID: AtomicU64 = AtomicU64::new(0);
    let folder = std::env::temp_dir().join(format!("deskink-adb-{}-{}", std::process::id(), ID.fetch_add(1, Ordering::Relaxed)));
    fs::create_dir(&folder).unwrap();
    let server = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let port = server.local_addr().unwrap().port();
    let removed = folder.join("removed.txt");
    let script = folder.join("adb.cmd");
    fs::write(&script, format!("@echo off\r\nif \"%1\"==\"-d\" (\r\necho ANDROID-TEST\r\nexit /b 0\r\n)\r\nif \"%4\"==\"--remove\" (\r\necho %5>\"{}\"\r\nexit /b 0\r\n)\r\nif \"%4\"==\"tcp:0\" (\r\necho {}\r\nexit /b 0\r\n)\r\nexit /b 1\r\n", removed.display(), port)).unwrap();
    let adb = Adb::new(script.clone());
    let mut tunnel = adb.connect().await.unwrap();
    let (mut tablet, _) = server.accept().await.unwrap();
    tunnel.write_all(b"EMLINK\x01\x00").await.unwrap();
    let mut bytes = [0; 8]; tablet.read_exact(&mut bytes).await.unwrap();
    assert_eq!(&bytes, b"EMLINK\x01\x00");
    drop(tunnel);
    tokio::time::timeout(Duration::from_secs(5), async {
        while !removed.is_file() { tokio::time::sleep(Duration::from_millis(20)).await; }
    }).await.unwrap();
    assert_eq!(fs::read_to_string(&removed).unwrap().trim(), format!("tcp:{port}"));
    fs::remove_file(script).unwrap(); fs::remove_file(removed).unwrap(); fs::remove_dir(folder).unwrap();
}
