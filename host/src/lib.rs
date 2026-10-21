//! EternalMonitor host library. The binary (`main.rs`) is a thin bootstrap
//! over these modules; end-to-end tests drive the same pipeline headlessly.

pub mod audio;
pub mod annotation;
#[cfg(windows)]
pub mod annotation_surface;
pub mod autostart;
pub mod capture;
pub mod clock;
pub mod control;
pub mod discovery;
pub mod encoder;
pub mod gui;
pub mod input;
pub mod logging;
pub mod lan;
pub mod pairing;
mod trusted_devices;
pub mod pipeline;
pub mod settings;
pub mod stats;
pub mod supervisor;
pub mod transport;
pub mod updates;
pub mod vdd;
pub mod vdd_settings;

#[cfg(windows)]
pub mod gpu;
#[cfg(not(windows))]
#[path = "gpu_other.rs"]
pub mod gpu;
