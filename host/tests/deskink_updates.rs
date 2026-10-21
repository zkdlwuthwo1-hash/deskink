use eternal_host::deskink_updates::UpdateOffer;
use serde_json::{json, Value};

fn current() -> String {
    json!({"version":"v0.5.0", "source_commit":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
        .to_string()
}

fn published() -> Value {
    json!({
        "version":"v0.5.1",
        "source_commit":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        "url":"https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.5.1/DeskInk-Windows-x64.zip",
        "sha256":"1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
        "size":123456
    })
}

#[test]
fn a_new_release_is_offered_and_docs_only_changes_do_not_restart() {
    let mut manifest = published();
    let update = UpdateOffer::for_installation(&current(), &manifest.to_string())
        .unwrap()
        .unwrap();
    assert_eq!(update.version, "v0.5.1");
    manifest["version"] = json!("v0.5.0");
    manifest["source_commit"] = json!("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    assert!(
        UpdateOffer::for_installation(&current(), &manifest.to_string())
            .unwrap()
            .is_none()
    );
    manifest["source_commit"] = json!("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    assert!(
        UpdateOffer::for_installation(&current(), &manifest.to_string())
            .unwrap()
            .is_some()
    );
    manifest["version"] = json!("v0.4.9");
    assert!(
        UpdateOffer::for_installation(&current(), &manifest.to_string())
            .unwrap()
            .is_none()
    );
}

#[test]
fn only_bounded_stable_same_repository_assets_can_be_offered() {
    for (field, value) in [
        ("url", json!("https://github.com/other/repo/releases/download/v0.5.1/DeskInk-Windows-x64.zip")),
        ("url", json!("https://github.com/zkdlwuthwo1-hash/deskink/releases/download/../../evil/DeskInk-Windows-x64.zip")),
        ("url", json!("http://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.5.1/DeskInk-Windows-x64.zip")),
        ("sha256", json!("bad")), ("source_commit", json!("bad")),
        ("version", json!("v0.6.0-rc.1")), ("version", json!("vv0.5.1")),
        ("size", json!(0)), ("size", json!(268435457)),
    ] {
        let mut manifest = published();
        manifest[field] = value;
        assert!(UpdateOffer::for_installation(&current(), &manifest.to_string()).is_err(), "{field}: {}", manifest[field]);
    }
}
