use ffmpeg_next::format::Pixel;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum EncoderInput {
    Auto,
    Bgra,
    // AMD BGRA is unsupported in the pinned SDK; see docs/decisions.md.
    #[default]
    Yuv420,
}

impl EncoderInput {
    /// Resolve this capture input mode against a real FFmpeg video codec.
    /// The caller receives a supported CPU pixel layout (or an error), without
    /// needing to inspect FFmpeg's format list or know vendor-specific rules.
    pub fn pixel_format(self, codec: ffmpeg_next::Codec) -> Result<Pixel, &'static str> {
        let video = codec
            .video()
            .map_err(|_| "The codec is not a video codec")?;
        let supported: Vec<_> = video.formats().into_iter().flatten().collect();
        select_input(self, codec.name(), &supported)
    }

    pub fn from_env(value: &str) -> Option<Self> {
        match value.trim().to_ascii_lowercase().as_str() {
            "auto" => Some(Self::Auto),
            "bgra" => Some(Self::Bgra),
            "yuv420" | "yuv420p" => Some(Self::Yuv420),
            _ => None,
        }
    }
}

fn select_input(
    mode: EncoderInput,
    codec_name: &str,
    supported: &[Pixel],
) -> Result<Pixel, &'static str> {
    if mode == EncoderInput::Yuv420 || codec_name == "libx264" {
        // YUV 4:2:0 describes chroma sampling, not a single memory layout.
        // Quick Sync in the shipped SDK accepts NV12, but not planar YUV420P.
        return [Pixel::YUV420P, Pixel::NV12]
            .into_iter()
            .find(|format| supported.is_empty() || supported.contains(format))
            .ok_or("The selected encoder does not accept 8-bit YUV 4:2:0 input");
    }
    for format in [Pixel::BGRA, Pixel::BGRZ] {
        if supported.contains(&format) {
            return Ok(format);
        }
    }
    if mode == EncoderInput::Bgra {
        Err("The selected encoder does not accept BGRA/BGR0 input")
    } else {
        // Auto follows the codec's preferred supported layout.
        supported
            .iter()
            .copied()
            .find(|format| matches!(format, Pixel::YUV420P | Pixel::NV12))
            .or_else(|| supported.is_empty().then_some(Pixel::YUV420P))
            .ok_or("The selected encoder does not accept 8-bit YUV 4:2:0 input")
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    #[cfg(windows)]
    fn intel_input_is_supported_by_the_shipped_codec() {
        ffmpeg_next::init().unwrap();
        let codec = ffmpeg_next::encoder::find_by_name("h264_qsv")
            .expect("the Windows runtime must include Quick Sync");
        let formats: Vec<_> = codec.video().unwrap().formats().unwrap().collect();
        for mode in [EncoderInput::default(), EncoderInput::Auto] {
            let selected = mode.pixel_format(codec).unwrap();
            assert!(
                formats.contains(&selected),
                "Intel cannot accept {selected:?}; the runtime advertises {formats:?}"
            );
        }
    }

    #[test]
    fn advertised_formats_control_native_input_and_software_stays_yuv() {
        let formats = [Pixel::YUV420P, Pixel::BGRZ, Pixel::BGRA];
        assert_eq!(
            select_input(EncoderInput::Auto, "h264_nvenc", &formats),
            Ok(Pixel::BGRA)
        );
        assert_eq!(
            select_input(EncoderInput::Auto, "h264_amf", &[Pixel::BGRZ]),
            Ok(Pixel::BGRZ)
        );
        assert_eq!(
            select_input(EncoderInput::Auto, "h264_qsv", &[Pixel::NV12]),
            Ok(Pixel::NV12)
        );
        assert_eq!(
            select_input(EncoderInput::Auto, "unknown", &[]),
            Ok(Pixel::YUV420P)
        );
        assert!(select_input(EncoderInput::Bgra, "hevc_amf", &[Pixel::NV12]).is_err());
        assert_eq!(
            select_input(EncoderInput::Bgra, "libx264", &formats),
            Ok(Pixel::YUV420P)
        );
        assert_eq!(
            select_input(EncoderInput::default(), "h264_nvenc", &formats),
            Ok(Pixel::YUV420P)
        );
        assert_eq!(EncoderInput::from_env("BGRA"), Some(EncoderInput::Bgra));
        assert_eq!(EncoderInput::from_env("mistyped"), None);
    }
}
