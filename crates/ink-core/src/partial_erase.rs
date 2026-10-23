use crate::{Point, Stroke};

// Intersect each centerline segment with the swept circular eraser (a capsule).
// Keep the complementary intervals as disconnected contours of the same stroke.
// No new IDs or sampled erase dots are sent over the network.
pub(crate) fn cut(
    stroke: &Stroke,
    from: Point,
    to: Point,
    radius: f32,
) -> Option<(Vec<Point>, Vec<usize>)> {
    let radius = f64::from(radius + stroke.brush.width / 2.);
    let mut points = Vec::new();
    let mut breaks = Vec::new();
    let mut changed = false;
    let mut start = 0;
    for end in stroke
        .breaks
        .iter()
        .copied()
        .chain(std::iter::once(stroke.points.len()))
    {
        let contour = &stroke.points[start..end];
        if contour.len() == 1 {
            let p = contour[0];
            if interval(p, p, from, to, radius).is_some() {
                changed = true;
            } else {
                if !points.is_empty() {
                    breaks.push(points.len());
                }
                points.push(p);
            }
        }
        let mut join = false;
        for pair in contour.windows(2) {
            let a = pair[0];
            let b = pair[1];
            let mut visible = vec![(0., 1.)];
            if let Some((lo, hi)) = interval(a, b, from, to, radius) {
                if hi - lo > 1e-9 {
                    changed = true;
                    visible.clear();
                    if lo > 1e-9 {
                        visible.push((0., lo));
                    }
                    if hi < 1. - 1e-9 {
                        visible.push((hi, 1.));
                    }
                }
            }
            if visible.is_empty() {
                join = false;
            }
            for (lo, hi) in visible {
                let p = interpolate(a, b, lo);
                let q = interpolate(a, b, hi);
                if !join || lo > 0. {
                    if !points.is_empty() {
                        breaks.push(points.len());
                    }
                    points.push(p);
                }
                if points.last() != Some(&q) {
                    points.push(q);
                }
                join = hi == 1.;
            }
        }
        start = end;
    }
    changed.then_some((points, breaks))
}

fn interpolate(a: Point, b: Point, t: f64) -> Point {
    if t == 0. {
        return a;
    }
    if t == 1. {
        return b;
    }
    Point {
        x: (f64::from(a.x) + (f64::from(b.x) - f64::from(a.x)) * t) as f32,
        y: (f64::from(a.y) + (f64::from(b.y) - f64::from(a.y)) * t) as f32,
        pressure: (f64::from(a.pressure) + (f64::from(b.pressure) - f64::from(a.pressure)) * t)
            as f32,
    }
}

fn interval(a: Point, b: Point, from: Point, to: Point, r: f64) -> Option<(f64, f64)> {
    let ax = f64::from(a.x);
    let ay = f64::from(a.y);
    let dx = f64::from(b.x) - ax;
    let dy = f64::from(b.y) - ay;
    let fx = f64::from(from.x);
    let fy = f64::from(from.y);
    let tx = f64::from(to.x);
    let ty = f64::from(to.y);
    if ax.min(ax + dx) > fx.max(tx) + r
        || ax.max(ax + dx) < fx.min(tx) - r
        || ay.min(ay + dy) > fy.max(ty) + r
        || ay.max(ay + dy) < fy.min(ty) - r
    {
        return None;
    }
    let mut ranges = Vec::with_capacity(3);
    let length2 = dx * dx + dy * dy;
    for (cx, cy) in [(fx, fy), (tx, ty)] {
        let x = ax - cx;
        let y = ay - cy;
        if length2 < 1e-18 {
            if x * x + y * y <= r * r {
                ranges.push((0., 1.));
            }
        } else {
            let dot = x * dx + y * dy;
            let discriminant = dot * dot - length2 * (x * x + y * y - r * r);
            if discriminant >= 0. {
                let root = discriminant.sqrt();
                let lo = ((-dot - root) / length2).max(0.);
                let hi = ((-dot + root) / length2).min(1.);
                if lo <= hi {
                    ranges.push((lo, hi));
                }
            }
        }
    }
    let length = (tx - fx).hypot(ty - fy);
    if length > 1e-9 {
        let ux = (tx - fx) / length;
        let uy = (ty - fy) / length;
        let mut lo: f64 = 0.;
        let mut hi: f64 = 1.;
        for (p, d, min, max) in [
            (
                (ax - fx) * ux + (ay - fy) * uy,
                dx * ux + dy * uy,
                0.,
                length,
            ),
            (-(ax - fx) * uy + (ay - fy) * ux, -dx * uy + dy * ux, -r, r),
        ] {
            if d.abs() < 1e-12 {
                if p < min || p > max {
                    hi = -1.;
                }
            } else {
                let t1 = (min - p) / d;
                let t2 = (max - p) / d;
                lo = lo.max(t1.min(t2));
                hi = hi.min(t1.max(t2));
            }
        }
        if lo <= hi {
            ranges.push((lo, hi));
        }
    }
    if ranges.is_empty() {
        None
    } else {
        Some((
            ranges.iter().map(|r| r.0).fold(1., f64::min),
            ranges.iter().map(|r| r.1).fold(0., f64::max),
        ))
    }
}
