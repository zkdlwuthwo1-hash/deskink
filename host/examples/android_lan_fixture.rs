//! Disposable debug fixture for Android/Windows LAN interoperability.
//! Exercises the shipped transport and authenticated annotation receiver without
//! capture, GPU, connected USB devices, user settings, or persistent pairing.
//! Usage: android_lan_fixture.exe <new-ready.json>; requires
//! ETERNAL_USB_DIRECT=127.0.0.1:9. Stop via the ready JSON's stopPath, or after 45 s.

use std::path::{Path, PathBuf};
use std::sync::atomic::Ordering;
use std::sync::{mpsc as std_mpsc, Arc};
use std::time::{Duration, Instant};

use eternal_host::annotation::{AnnotationState, AnnotationWorkspace};
use eternal_host::capture::OutputInfo;
use eternal_host::control::SharedControl;
use eternal_host::encoder::NALUnit;
use eternal_host::transport::start_sender;
use serde_json::{json, Value};
use tokio::sync::mpsc;

type FixtureResult<T> = Result<T, Box<dyn std::error::Error + Send + Sync>>;

fn publish_new(path: &Path, value: Value) -> FixtureResult<()> {
    if path.exists() {
        return Err(format!("fixture output already exists: {}", path.display()).into());
    }
    let pending = path.with_extension("pending");
    std::fs::write(&pending, serde_json::to_vec(&value)?)?;
    std::fs::rename(pending, path)?;
    Ok(())
}

#[tokio::main(flavor = "multi_thread", worker_threads = 2)]
async fn main() -> FixtureResult<()> {
    if std::env::var("ETERNAL_USB_DIRECT").as_deref() != Ok("127.0.0.1:9") {
        return Err(
            "ETERNAL_USB_DIRECT=127.0.0.1:9 is required; real USB devices must not be contacted"
                .into(),
        );
    }
    let ready = PathBuf::from(
        std::env::args_os()
            .nth(1)
            .ok_or("expected new ready JSON path")?,
    );
    let ready = std::path::absolute(ready)?;
    let result = ready.with_extension("result.json");
    let stop = ready.with_extension("stop");
    for output in [&ready, &result, &stop] {
        if output.exists() {
            return Err(format!("use a fresh fixture path: {}", output.display()).into());
        }
    }
    let frame_path = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../proto/testdata/deskink_workspace_1280x720.h264")
        .canonicalize()?;
    let frame = std::fs::read(&frame_path)?;
    eternal_host::clock::init();

    let reservation = std::net::UdpSocket::bind("0.0.0.0:0")?;
    let port = reservation.local_addr()?.port();
    let board = Arc::new(AnnotationState::new(
        OutputInfo {
            adapter_index: 0,
            output_index: 0,
            device_name: "interop-fixture-display".into(),
            width: 1280,
            height: 720,
            left: 0,
            top: 0,
            is_primary: true,
            adapter_name: "debug fixture; no GPU".into(),
        },
        0,
    ));
    // A completed sentinel makes successful remote Clear observable at the real
    // document boundary; an empty board alone would not prove command delivery.
    {
        let mut ink = board.ink.lock();
        for (index, command) in [
            json!({"type":"begin","id":1,"brush":{"color":[255,80,40,255],"width":4},
                "point":{"x":100,"y":200,"pressure":0.4}}),
            json!({"type":"end","id":1}),
        ]
        .into_iter()
        .enumerate()
        {
            ink.receive(&serde_json::to_vec(&json!({
                "version":1,"session":0,"epoch":0,"seq":index + 1,"command":command
            }))?)
            .map_err(|error| format!("invalid seed ink: {error:?}"))?;
        }
        assert_eq!(ink.board().strokes().len(), 1);
        ink.reconnect(0, 0);
    }
    let mut shared = SharedControl::new(port, 4_000_000);
    shared.annotation = Some(Arc::new(AnnotationWorkspace::new(board.clone())));
    shared.lan_enabled.store(true, Ordering::SeqCst);
    shared.running.store(true, Ordering::SeqCst);
    shared.pairing.lock().require_usb_code = true;
    let code = shared.pairing.lock().code();
    let (nal, nal_rx) = mpsc::channel(2);
    let (_audio, audio_rx) = mpsc::channel(2);
    let (supervisor, _supervisor_rx) = std_mpsc::channel();
    let control = shared.clone();
    drop(reservation);
    let mut sender = tokio::spawn(async move {
        start_sender(nal_rx, audio_rx, port, control, supervisor)
            .await
            .map_err(|error| error.to_string())
    });
    // Give bind failures a chance to surface before publishing the endpoint.
    tokio::time::sleep(Duration::from_millis(100)).await;
    if sender.is_finished() {
        return Err(format!("transport stopped before readiness: {:?}", sender.await).into());
    }
    publish_new(
        &ready,
        json!({"address":"127.0.0.1","port":port,"code":code,
            "framePath":frame_path,"resultPath":result,"stopPath":stop}),
    )?;
    println!("Disposable LAN fixture ready (synthetic pairing; no capture/devices).");

    let deadline = Instant::now() + Duration::from_secs(45);
    let mut sent = 0_u64;
    let mut passed = false;
    while Instant::now() < deadline && !stop.exists() {
        if sender.is_finished() {
            return Err(format!("transport stopped unexpectedly: {:?}", sender.await).into());
        }
        if shared.client_connected() {
            sent += 1;
            nal.send(NALUnit {
                data: frame.clone(),
                sequence: sent,
                timestamp: Instant::now(),
                encode_duration_us: 0,
                is_keyframe: true,
            })
            .await?;
            if !passed {
                let ink = board.ink.lock();
                if ink.accepted_sequence() == 1
                    && board.revision.load(Ordering::Acquire) > 0
                    && ink.board().strokes().is_empty()
                {
                    publish_new(&result, json!({"clear_received":true,"authenticated":true}))?;
                    passed = true;
                    println!("Authenticated remote ink cleared the sentinel stroke.");
                }
            }
        }
        tokio::time::sleep(Duration::from_millis(100)).await;
    }
    shared.stop();
    if tokio::time::timeout(Duration::from_secs(2), &mut sender)
        .await
        .is_err()
    {
        sender.abort();
    }
    if !passed {
        return Err("fixture ended without authenticated remote Clear".into());
    }
    Ok(())
}
