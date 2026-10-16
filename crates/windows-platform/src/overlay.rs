//! A per-pixel-alpha, no-activation, mouse-transparent annotation window.
//! It intentionally remains visible to ordinary screen capture (including Zoom).
use deskink_core::Board;
use windows::{
    core::{w, Result},
    Win32::{
        Foundation::{COLORREF, HWND, POINT, SIZE},
        Graphics::Gdi::*,
        UI::WindowsAndMessaging::*,
    },
};

pub struct Overlay {
    hwnd: HWND,
    width: u32,
    height: u32,
    origin: POINT,
}

impl Overlay {
    pub fn new(left: i32, top: i32, width: u32, height: u32) -> Result<Self> {
        // The stock STATIC class needs no custom wndproc and owns no pointer to Rust data.
        let hwnd = unsafe {
            CreateWindowExW(
                WS_EX_LAYERED
                    | WS_EX_TRANSPARENT
                    | WS_EX_NOACTIVATE
                    | WS_EX_TOOLWINDOW
                    | WS_EX_TOPMOST,
                w!("STATIC"),
                w!("DeskInk annotations"),
                WS_POPUP,
                left,
                top,
                width as i32,
                height as i32,
                None,
                None,
                None,
                None,
            )?
        };
        Ok(Self {
            hwnd,
            width,
            height,
            origin: POINT { x: left, y: top },
        })
    }

    pub fn hwnd(&self) -> HWND {
        self.hwnd
    }

    pub fn present(&self, board: &Board) -> Result<()> {
        let mut pixels = tiny_skia::Pixmap::new(self.width, self.height)
            .ok_or_else(windows::core::Error::from_win32)?;
        for stroke in board.strokes() {
            let [r, g, b, a] = stroke.brush.color;
            let mut paint = tiny_skia::Paint::default();
            paint.set_color_rgba8(r, g, b, a);
            paint.anti_alias = true;
            if let Some(first) = stroke.points.first() {
                if stroke.points.len() == 1 {
                    let mut path = tiny_skia::PathBuilder::new();
                    path.push_circle(first.x, first.y, stroke.brush.width / 2.0);
                    if let Some(path) = path.finish() {
                        pixels.fill_path(
                            &path,
                            &paint,
                            tiny_skia::FillRule::Winding,
                            tiny_skia::Transform::identity(),
                            None,
                        );
                    }
                } else {
                    let mut path = tiny_skia::PathBuilder::new();
                    path.move_to(first.x, first.y);
                    for point in &stroke.points[1..] {
                        path.line_to(point.x, point.y);
                    }
                    if let Some(path) = path.finish() {
                        pixels.stroke_path(
                            &path,
                            &paint,
                            &tiny_skia::Stroke {
                                width: stroke.brush.width,
                                line_cap: tiny_skia::LineCap::Round,
                                line_join: tiny_skia::LineJoin::Round,
                                ..Default::default()
                            },
                            tiny_skia::Transform::identity(),
                            None,
                        );
                    }
                }
            }
        }
        // DIBs use BGRA; tiny-skia's buffer is already premultiplied RGBA.
        for pixel in pixels.data_mut().as_chunks_mut::<4>().0 {
            pixel.swap(0, 2);
        }
        self.present_bgra(pixels.data())
    }

    fn present_bgra(&self, bytes: &[u8]) -> Result<()> {
        // All GDI resources are released on both success and failure. The DIB
        // data pointer is owned by bitmap and used only before DeleteObject.
        unsafe {
            let dc = CreateCompatibleDC(None);
            if dc.is_invalid() {
                return Err(windows::core::Error::from_win32());
            }
            let info = BITMAPINFO {
                bmiHeader: BITMAPINFOHEADER {
                    biSize: std::mem::size_of::<BITMAPINFOHEADER>() as u32,
                    biWidth: self.width as i32,
                    biHeight: -(self.height as i32),
                    biPlanes: 1,
                    biBitCount: 32,
                    biCompression: BI_RGB.0,
                    ..Default::default()
                },
                ..Default::default()
            };
            let mut bits = std::ptr::null_mut();
            let bitmap = match CreateDIBSection(dc, &info, DIB_RGB_COLORS, &mut bits, None, 0) {
                Ok(bitmap) => bitmap,
                Err(error) => {
                    let _ = DeleteDC(dc);
                    return Err(error);
                }
            };
            std::ptr::copy_nonoverlapping(bytes.as_ptr(), bits.cast(), bytes.len());
            let previous = SelectObject(dc, bitmap);
            let result = UpdateLayeredWindow(
                self.hwnd,
                None,
                Some(&self.origin),
                Some(&SIZE {
                    cx: self.width as i32,
                    cy: self.height as i32,
                }),
                dc,
                Some(&POINT::default()),
                COLORREF(0),
                Some(&BLENDFUNCTION {
                    BlendOp: AC_SRC_OVER as u8,
                    BlendFlags: 0,
                    SourceConstantAlpha: 255,
                    AlphaFormat: AC_SRC_ALPHA as u8,
                }),
                ULW_ALPHA,
            );
            SelectObject(dc, previous);
            let _ = DeleteObject(bitmap);
            let _ = DeleteDC(dc);
            result?;
            let _ = ShowWindow(self.hwnd, SW_SHOWNOACTIVATE);
            Ok(())
        }
    }
}

impl Drop for Overlay {
    fn drop(&mut self) {
        unsafe {
            let _ = DestroyWindow(self.hwnd);
        }
    }
}
