//! A per-pixel-alpha, no-activation, mouse-transparent annotation window.
//! It intentionally remains visible to ordinary screen capture (including Zoom).
use deskink_core::{Board, Stroke};
use std::cell::RefCell;
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
    raster: RefCell<InkRaster>,
}

impl Overlay {
    pub fn new(left: i32, top: i32, width: u32, height: u32) -> Result<Self> {
        let raster = RefCell::new(InkRaster::new(width, height)?);
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
            raster,
        })
    }

    pub fn hwnd(&self) -> HWND {
        self.hwnd
    }

    pub fn present(&self, board: &Board) -> Result<()> {
        let mut raster = self.raster.borrow_mut();
        present_bgra(
            self.hwnd,
            self.origin,
            self.width,
            self.height,
            raster.draw(board),
        )
    }
}

// Retain finished ink between pointer/append updates. Equality includes all
// points, so Undo/Clear and reusing a stroke ID cannot leave a stale bitmap.
struct InkRaster {
    settled: tiny_skia::Pixmap,
    frame: tiny_skia::Pixmap,
    completed: Vec<Stroke>,
}

impl InkRaster {
    fn new(width: u32, height: u32) -> Result<Self> {
        Ok(Self {
            settled: tiny_skia::Pixmap::new(width, height)
                .ok_or_else(windows::core::Error::from_win32)?,
            frame: tiny_skia::Pixmap::new(width, height)
                .ok_or_else(windows::core::Error::from_win32)?,
            completed: Vec::new(),
        })
    }

    fn draw(&mut self, board: &Board) -> &[u8] {
        let prefix_len = if board.is_visible() {
            board
                .strokes()
                .iter()
                .take_while(|stroke| stroke.finished)
                .count()
        } else {
            0
        };
        let prefix = &board.strokes()[..prefix_len];
        if !prefix.starts_with(&self.completed) {
            self.settled.fill(tiny_skia::Color::TRANSPARENT);
            self.completed.clear();
        }
        for stroke in &prefix[self.completed.len()..] {
            draw_stroke(&mut self.settled, stroke);
        }
        self.completed
            .extend_from_slice(&prefix[self.completed.len()..]);
        self.frame.data_mut().copy_from_slice(self.settled.data());
        let pixels = &mut self.frame;
        for stroke in board.render_strokes().skip(prefix_len) {
            draw_stroke(pixels, stroke);
        }
        draw_pointer(pixels, board);
        // DIBs use BGRA; the cached settled image stays premultiplied RGBA.
        for pixel in pixels.data_mut().as_chunks_mut::<4>().0 {
            pixel.swap(0, 2);
        }
        pixels.data()
    }
}

fn draw_stroke(pixels: &mut tiny_skia::Pixmap, stroke: &Stroke) {
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

fn draw_pointer(pixels: &mut tiny_skia::Pixmap, board: &Board) {
    if let Some(pointer) = board.pointer() {
        let [r, g, b, _] = pointer.color;
        let (x, y, radius) = (pointer.point.x, pointer.point.y, pointer.radius);
        let mut path = tiny_skia::PathBuilder::new();
        if pointer.laser {
            path.push_circle(x, y, radius);
        } else {
            // Nib tip stays at the actual input position.
            path.move_to(x, y);
            path.line_to(x + 7.0, y - 20.0);
            path.line_to(x + 15.0, y - 12.0);
            path.close();
        }
        if let Some(path) = path.finish() {
            let mut paint = tiny_skia::Paint::default();
            paint.set_color_rgba8(r, g, b, 235);
            pixels.fill_path(
                &path,
                &paint,
                tiny_skia::FillRule::Winding,
                tiny_skia::Transform::identity(),
                None,
            );
            paint.set_color_rgba8(255, 255, 255, 245);
            pixels.stroke_path(
                &path,
                &paint,
                &tiny_skia::Stroke {
                    width: 2.0,
                    ..Default::default()
                },
                tiny_skia::Transform::identity(),
                None,
            );
        }
    }
}

fn present_bgra(hwnd: HWND, origin: POINT, width: u32, height: u32, bytes: &[u8]) -> Result<()> {
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
                biWidth: width as i32,
                biHeight: -(height as i32),
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
            hwnd,
            None,
            Some(&origin),
            Some(&SIZE {
                cx: width as i32,
                cy: height as i32,
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
        let _ = ShowWindow(hwnd, SW_SHOWNOACTIVATE);
        Ok(())
    }
}

impl Drop for Overlay {
    fn drop(&mut self) {
        unsafe {
            let _ = DestroyWindow(self.hwnd);
        }
    }
}
