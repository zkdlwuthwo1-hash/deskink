use serde::{Deserialize, Serialize};
mod partial_erase;

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct Point {
    pub x: f32,
    pub y: f32,
    pub pressure: f32,
}

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct Brush {
    pub color: [u8; 4],
    pub width: f32,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Stroke {
    pub id: u64,
    pub brush: Brush,
    pub points: Vec<Point>,
    /// Index of the first point of every disconnected contour after the first.
    pub breaks: Vec<usize>,
    pub finished: bool,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ShapeKind {
    Rectangle,
    Ellipse,
    Line,
}

fn shape_points(kind: ShapeKind, a: Point, b: Point) -> Vec<Point> {
    match kind {
        ShapeKind::Rectangle => vec![a, Point { x: b.x, ..a }, b, Point { y: b.y, ..a }, a],
        ShapeKind::Line => vec![a, b],
        ShapeKind::Ellipse => {
            let (cx, cy, rx, ry) = (
                (a.x + b.x) / 2.,
                (a.y + b.y) / 2.,
                (b.x - a.x) / 2.,
                (b.y - a.y) / 2.,
            );
            let mut points: Vec<Point> = (0..64)
                .map(|i| {
                    let t = i as f32 * std::f32::consts::TAU / 64.;
                    Point {
                        x: cx + rx * t.cos(),
                        y: cy + ry * t.sin(),
                        pressure: a.pressure,
                    }
                })
                .collect();
            points.push(points[0]);
            points
        }
    }
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case", deny_unknown_fields)]
pub enum Command {
    Begin {
        id: u64,
        brush: Brush,
        point: Point,
    },
    Append {
        id: u64,
        point: Point,
    },
    End {
        id: u64,
    },
    Shape {
        id: u64,
        kind: ShapeKind,
        a: Point,
        b: Point,
    },
    EditBegin {
        id: u64,
    },
    EditAdd {
        id: u64,
        ids: Vec<u64>,
    },
    EditTransform {
        id: u64,
        sx: f32,
        sy: f32,
        tx: f32,
        ty: f32,
    },
    EditEnd {
        id: u64,
    },
    EditDelete {
        id: u64,
    },
    EraseBegin {
        id: u64,
        point: Point,
        radius: f32,
    },
    ErasePartialBegin {
        id: u64,
        point: Point,
        radius: f32,
    },
    EraseTo {
        id: u64,
        point: Point,
    },
    EraseEnd {
        id: u64,
    },
    Undo,
    Redo,
    Clear,
    SetVisible {
        visible: bool,
    },
    TransientBegin {
        id: u64,
        brush: Brush,
        point: Point,
        lifetime_ms: u32,
    },
    Pointer {
        point: Option<Point>,
        color: [u8; 4],
        radius: f32,
        laser: bool,
    },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum InkError {
    InvalidStroke,
    InvalidMessage,
}

#[derive(Default)]
pub struct Board {
    strokes: Vec<Stroke>,
    eraser: Option<EraseGesture>,
    undo: Vec<Edit>,
    redo: Vec<Edit>,
    hidden: bool,
    temporary: Vec<TransientStroke>,
    now_ms: u64,
    pointer: Option<Pointer>,
    selection_edit: Option<SelectionEdit>,
}

struct SelectionEdit {
    id: u64,
    original: Vec<(usize, Stroke)>,
    transformed: bool,
    changed: bool,
    deleted: bool,
}

#[derive(Clone)]
pub struct Pointer {
    pub point: Point,
    pub color: [u8; 4],
    pub radius: f32,
    pub laser: bool,
    expires_at: u64,
}

#[derive(Clone)]
struct TransientStroke {
    stroke: Stroke,
    lifetime_ms: u32,
    expires_at: u64,
}

#[derive(Default)]
struct Edit {
    remove: Vec<u64>,
    insert: Vec<(usize, Stroke)>,
}

struct EraseGesture {
    id: u64,
    last: Point,
    radius: f32,
    removed: Vec<(usize, Stroke)>,
    partial_before: Option<Vec<Stroke>>,
}

impl Board {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn apply(&mut self, command: Command) -> Result<(), InkError> {
        if self.selection_edit.is_some()
            && !matches!(
                command,
                Command::EditAdd { .. }
                    | Command::EditTransform { .. }
                    | Command::EditEnd { .. }
                    | Command::EditDelete { .. }
                    | Command::Pointer { .. }
                    | Command::SetVisible { .. }
            )
        {
            return Err(InkError::InvalidStroke);
        }
        match command {
            Command::EditBegin { id } => {
                if self.eraser.is_some() || self.strokes.iter().any(|s| !s.finished) {
                    return Err(InkError::InvalidStroke);
                }
                self.selection_edit = Some(SelectionEdit {
                    id,
                    original: vec![],
                    transformed: false,
                    changed: false,
                    deleted: false,
                });
            }
            Command::EditAdd { id, ids } => {
                let edit = self
                    .selection_edit
                    .as_mut()
                    .filter(|e| e.id == id && !e.transformed)
                    .ok_or(InkError::InvalidStroke)?;
                if ids.is_empty() || ids.len() > 32 || edit.original.len() + ids.len() > 4096 {
                    return Err(InkError::InvalidMessage);
                }
                let mut added = Vec::new();
                for id in ids {
                    if edit
                        .original
                        .iter()
                        .chain(added.iter())
                        .any(|(_, s): &(usize, Stroke)| s.id == id)
                    {
                        return Err(InkError::InvalidStroke);
                    }
                    let index = self
                        .strokes
                        .iter()
                        .position(|s| s.id == id && s.finished)
                        .ok_or(InkError::InvalidStroke)?;
                    added.push((index, self.strokes[index].clone()));
                }
                edit.original.extend(added);
            }
            Command::EditTransform { id, sx, sy, tx, ty } => {
                let edit = self
                    .selection_edit
                    .as_mut()
                    .filter(|e| e.id == id && !e.original.is_empty() && !e.deleted)
                    .ok_or(InkError::InvalidStroke)?;
                if ![sx, sy, tx, ty].iter().all(|v| v.is_finite()) || sx <= 0. || sy <= 0. {
                    return Err(InkError::InvalidMessage);
                }
                for (index, original) in &edit.original {
                    let stroke = &mut self.strokes[*index];
                    stroke.points = original
                        .points
                        .iter()
                        .map(|p| Point {
                            x: p.x * sx + tx,
                            y: p.y * sy + ty,
                            ..*p
                        })
                        .collect();
                }
                edit.transformed = true;
                edit.changed = sx != 1. || sy != 1. || tx != 0. || ty != 0.;
            }
            Command::EditEnd { id } => {
                if self.selection_edit.as_ref().map(|e| e.id) != Some(id) {
                    return Err(InkError::InvalidStroke);
                }
                self.finish_selection_edit();
            }
            Command::EditDelete { id } => {
                let edit = self
                    .selection_edit
                    .as_mut()
                    .filter(|e| e.id == id && !e.original.is_empty() && !e.deleted)
                    .ok_or(InkError::InvalidStroke)?;
                let ids: std::collections::HashSet<_> =
                    edit.original.iter().map(|(_, s)| s.id).collect();
                self.strokes.retain(|s| !ids.contains(&s.id));
                edit.deleted = true;
                edit.transformed = true;
                edit.changed = true;
            }
            Command::Shape { id, kind, a, b } => {
                let stroke = self
                    .strokes
                    .iter_mut()
                    .find(|s| s.id == id && !s.finished)
                    .ok_or(InkError::InvalidStroke)?;
                stroke.points = shape_points(kind, a, b);
                stroke.breaks.clear();
            }
            Command::Begin { id, brush, point } => {
                if self.strokes.iter().any(|stroke| stroke.id == id)
                    || self.temporary.iter().any(|t| t.stroke.id == id)
                {
                    return Err(InkError::InvalidStroke);
                }
                self.strokes.push(Stroke {
                    id,
                    brush,
                    points: vec![point],
                    breaks: vec![],
                    finished: false,
                });
                self.redo.clear();
            }
            Command::Append { id, point } => {
                let stroke = self
                    .strokes
                    .iter_mut()
                    .chain(self.temporary.iter_mut().map(|t| &mut t.stroke))
                    .find(|stroke| stroke.id == id)
                    .ok_or(InkError::InvalidStroke)?;
                if stroke.finished {
                    return Err(InkError::InvalidStroke);
                }
                stroke.points.push(point);
            }
            Command::End { id } => {
                if let Some(temporary) = self.temporary.iter_mut().find(|t| t.stroke.id == id) {
                    if temporary.stroke.finished {
                        return Err(InkError::InvalidStroke);
                    }
                    temporary.stroke.finished = true;
                    temporary.expires_at =
                        self.now_ms.saturating_add(u64::from(temporary.lifetime_ms));
                    return Ok(());
                }
                let stroke = self
                    .strokes
                    .iter_mut()
                    .find(|stroke| stroke.id == id)
                    .ok_or(InkError::InvalidStroke)?;
                if stroke.finished {
                    return Err(InkError::InvalidStroke);
                }
                stroke.finished = true;
                self.remember(Edit {
                    remove: vec![id],
                    insert: vec![],
                });
            }
            Command::EraseBegin { id, point, radius } => {
                if self.eraser.is_some() {
                    return Err(InkError::InvalidStroke);
                }
                let removed = self.erase_between(point, point, radius);
                self.eraser = Some(EraseGesture {
                    id,
                    last: point,
                    radius,
                    removed,
                    partial_before: None,
                });
            }
            Command::ErasePartialBegin { id, point, radius } => {
                if self.eraser.is_some() || self.strokes.iter().any(|s| !s.finished) {
                    return Err(InkError::InvalidStroke);
                }
                let before = self.strokes.clone();
                self.erase_partial_between(point, point, radius);
                self.eraser = Some(EraseGesture {
                    id,
                    last: point,
                    radius,
                    removed: vec![],
                    partial_before: Some(before),
                });
            }
            Command::EraseTo { id, point } => {
                let eraser = self
                    .eraser
                    .as_ref()
                    .filter(|e| e.id == id)
                    .ok_or(InkError::InvalidStroke)?;
                let (last, radius) = (eraser.last, eraser.radius);
                let removed = if eraser.partial_before.is_some() {
                    self.erase_partial_between(last, point, radius);
                    vec![]
                } else {
                    self.erase_between(last, point, radius)
                };
                let eraser = self.eraser.as_mut().unwrap();
                eraser.last = point;
                eraser.removed.extend(removed);
            }
            Command::EraseEnd { id } => {
                if self.eraser.as_ref().map(|e| e.id) != Some(id) {
                    return Err(InkError::InvalidStroke);
                }
                self.finish_erase();
            }
            Command::Undo | Command::Redo => {
                if self.eraser.is_some() || self.strokes.iter().any(|s| !s.finished) {
                    return Err(InkError::InvalidStroke);
                }
                let is_undo = matches!(command, Command::Undo);
                let edit = if is_undo {
                    self.undo.pop()
                } else {
                    self.redo.pop()
                };
                if let Some(edit) = edit {
                    let inverse = self.apply_edit(edit);
                    if is_undo {
                        self.redo.push(inverse);
                    } else {
                        self.undo.push(inverse);
                    }
                }
            }
            Command::Clear => {
                if self.eraser.is_some() || self.strokes.iter().any(|s| !s.finished) {
                    return Err(InkError::InvalidStroke);
                }
                if !self.strokes.is_empty() {
                    let removed = self.strokes.drain(..).enumerate().collect();
                    self.remember(Edit {
                        remove: vec![],
                        insert: removed,
                    });
                }
                self.temporary.clear();
                self.pointer = None;
            }
            Command::SetVisible { visible } => self.hidden = !visible,
            Command::TransientBegin {
                id,
                brush,
                point,
                lifetime_ms,
            } => {
                if self.strokes.iter().any(|s| s.id == id)
                    || self.temporary.iter().any(|t| t.stroke.id == id)
                {
                    return Err(InkError::InvalidStroke);
                }
                self.temporary.push(TransientStroke {
                    stroke: Stroke {
                        id,
                        brush,
                        points: vec![point],
                        breaks: vec![],
                        finished: false,
                    },
                    lifetime_ms,
                    expires_at: self.now_ms.saturating_add(u64::from(lifetime_ms)),
                });
            }
            Command::Pointer {
                point,
                color,
                radius,
                laser,
            } => {
                self.pointer = point.map(|point| Pointer {
                    point,
                    color,
                    radius,
                    laser,
                    expires_at: self.now_ms.saturating_add(750),
                });
            }
        }
        Ok(())
    }

    pub fn strokes(&self) -> &[Stroke] {
        &self.strokes
    }

    /// An immutable display copy, without editing history. The caller can
    /// release the input-session lock before rasterizing or calling the OS.
    pub fn snapshot_for_display(&self) -> Self {
        Self {
            strokes: self.strokes.clone(),
            hidden: self.hidden,
            temporary: self.temporary.clone(),
            now_ms: self.now_ms,
            pointer: self.pointer.clone(),
            ..Self::default()
        }
    }

    pub fn is_visible(&self) -> bool {
        !self.hidden
    }

    pub fn render_strokes(&self) -> impl Iterator<Item = &Stroke> {
        self.strokes
            .iter()
            .chain(self.temporary.iter().map(|t| &t.stroke))
            .filter(|_| !self.hidden)
    }

    pub fn has_temporary_content(&self) -> bool {
        !self.temporary.is_empty() || self.pointer.is_some()
    }

    pub fn pointer(&self) -> Option<&Pointer> {
        self.pointer.as_ref()
    }

    fn advance_time(&mut self, now_ms: u64) -> bool {
        self.now_ms = self.now_ms.max(now_ms);
        let before = self.temporary.len();
        self.temporary
            .retain(|t| !t.stroke.finished || t.expires_at > self.now_ms);
        let pointer_expired = self
            .pointer
            .as_ref()
            .is_some_and(|p| p.expires_at <= self.now_ms);
        if pointer_expired {
            self.pointer = None;
        }
        before != self.temporary.len() || pointer_expired
    }

    fn erase_between(&mut self, from: Point, to: Point, radius: f32) -> Vec<(usize, Stroke)> {
        let mut removed = Vec::new();
        let mut index = 0;
        while index < self.strokes.len() {
            let stroke = &self.strokes[index];
            let limit = (radius + stroke.brush.width / 2.0).powi(2);
            let hit = stroke.points.iter().enumerate().any(|(i, p)| {
                if i == 0 || stroke.breaks.binary_search(&i).is_ok() {
                    point_segment_distance_squared(*p, from, to) <= limit
                } else {
                    segment_distance_squared(from, to, stroke.points[i - 1], *p) <= limit
                }
            });
            if hit {
                removed.push((index, self.strokes.remove(index)));
            } else {
                index += 1;
            }
        }
        removed
    }

    fn erase_partial_between(&mut self, from: Point, to: Point, radius: f32) {
        for stroke in &mut self.strokes {
            if let Some((points, breaks)) = partial_erase::cut(stroke, from, to, radius) {
                stroke.points = points;
                stroke.breaks = breaks;
            }
        }
        self.strokes.retain(|s| !s.points.is_empty());
    }

    fn remember(&mut self, edit: Edit) {
        self.redo.clear();
        self.undo.push(edit);
        if self.undo.len() > 100 {
            self.undo.remove(0);
        }
    }

    fn finish_erase(&mut self) {
        if let Some(eraser) = self.eraser.take() {
            if let Some(before) = eraser.partial_before {
                if before != self.strokes {
                    self.remember(Edit {
                        remove: self.strokes.iter().map(|s| s.id).collect(),
                        insert: before.into_iter().enumerate().collect(),
                    });
                }
            } else if !eraser.removed.is_empty() {
                self.remember(Edit {
                    remove: vec![],
                    insert: eraser.removed.into_iter().rev().collect(),
                });
            }
        }
    }

    fn finish_selection_edit(&mut self) {
        if let Some(edit) = self.selection_edit.take() {
            if edit.changed {
                let mut original = edit.original;
                original.sort_by_key(|(index, _)| *index);
                self.remember(Edit {
                    remove: original.iter().map(|(_, s)| s.id).collect(),
                    insert: original,
                });
            }
        }
    }

    fn apply_edit(&mut self, edit: Edit) -> Edit {
        let mut inverse = Edit::default();
        for id in edit.remove {
            if let Some(index) = self.strokes.iter().position(|s| s.id == id) {
                inverse.insert.push((index, self.strokes.remove(index)));
            }
        }
        inverse.insert.reverse();
        for (index, stroke) in edit.insert {
            inverse.remove.push(stroke.id);
            self.strokes.insert(index.min(self.strokes.len()), stroke);
        }
        inverse
    }
}

fn point_segment_distance_squared(p: Point, a: Point, b: Point) -> f32 {
    let (dx, dy) = (b.x - a.x, b.y - a.y);
    let length = dx * dx + dy * dy;
    let t = if length > 0.0 {
        (((p.x - a.x) * dx + (p.y - a.y) * dy) / length).clamp(0.0, 1.0)
    } else {
        0.0
    };
    (p.x - (a.x + t * dx)).powi(2) + (p.y - (a.y + t * dy)).powi(2)
}

fn segment_distance_squared(a: Point, b: Point, c: Point, d: Point) -> f32 {
    let (rx, ry, sx, sy) = (b.x - a.x, b.y - a.y, d.x - c.x, d.y - c.y);
    let cross = rx * sy - ry * sx;
    if cross.abs() > f32::EPSILON {
        let t = ((c.x - a.x) * sy - (c.y - a.y) * sx) / cross;
        let u = ((c.x - a.x) * ry - (c.y - a.y) * rx) / cross;
        if (0.0..=1.0).contains(&t) && (0.0..=1.0).contains(&u) {
            return 0.0;
        }
    }
    point_segment_distance_squared(a, c, d)
        .min(point_segment_distance_squared(b, c, d))
        .min(point_segment_distance_squared(c, a, b))
        .min(point_segment_distance_squared(d, a, b))
}
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Envelope {
    version: u32,
    session: u32,
    epoch: u32,
    seq: u64,
    command: Command,
}

pub struct InkSession {
    board: Board,
    accepted: u64,
    session: u32,
    epoch: u32,
    width: u32,
    height: u32,
}
impl InkSession {
    pub fn reconnect(&mut self, session: u32, epoch: u32) {
        self.session = session;
        self.epoch = epoch;
        self.accepted = 0;
        let unfinished: Vec<u64> = self
            .board
            .strokes
            .iter()
            .filter(|stroke| !stroke.finished)
            .map(|stroke| stroke.id)
            .collect();
        for id in unfinished {
            // Complete the normal edit so Undo also works after switching
            // away halfway through a stroke and returning to this monitor.
            self.board
                .apply(Command::End { id })
                .expect("existing unfinished stroke");
        }
        self.board.finish_erase();
        self.board.finish_selection_edit();
        self.board.temporary.clear();
        self.board.pointer = None;
    }
    pub fn new(session: u32, epoch: u32, width: u32, height: u32) -> Self {
        Self {
            board: Board::new(),
            accepted: 0,
            session,
            epoch,
            width,
            height,
        }
    }
    pub fn receive(&mut self, bytes: &[u8]) -> Result<(), InkError> {
        if bytes.len() > 1024 {
            return Err(InkError::InvalidMessage);
        }
        let envelope: Envelope =
            serde_json::from_slice(bytes).map_err(|_| InkError::InvalidMessage)?;
        if envelope.version != 1 || envelope.session != self.session || envelope.epoch != self.epoch
        {
            return Err(InkError::InvalidMessage);
        }
        if envelope.seq != 0 && envelope.seq <= self.accepted {
            return Ok(());
        }
        if self.accepted.checked_add(1) != Some(envelope.seq) {
            return Err(InkError::InvalidMessage);
        }
        if let Command::Begin { brush, .. } | Command::TransientBegin { brush, .. } =
            &envelope.command
        {
            if !brush.width.is_finite() || !(0.5..=64.0).contains(&brush.width) {
                return Err(InkError::InvalidMessage);
            }
        }
        if let Command::TransientBegin { lifetime_ms, .. } = &envelope.command {
            if !(100..=10_000).contains(lifetime_ms) {
                return Err(InkError::InvalidMessage);
            }
        }
        if let Command::EraseBegin { radius, .. } | Command::ErasePartialBegin { radius, .. } =
            &envelope.command
        {
            if !radius.is_finite() || !(1.0..=128.0).contains(radius) {
                return Err(InkError::InvalidMessage);
            }
        }
        if let Command::Pointer { radius, .. } = &envelope.command {
            if !radius.is_finite() || !(1.0..=128.0).contains(radius) {
                return Err(InkError::InvalidMessage);
            }
        }
        if let Command::Shape { a, b, .. } = &envelope.command {
            if [a, b].iter().any(|p| !self.valid_point(p)) {
                return Err(InkError::InvalidMessage);
            }
        }
        if let Command::EditTransform { id, sx, sy, tx, ty } = &envelope.command {
            let edit = self
                .board
                .selection_edit
                .as_ref()
                .filter(|e| e.id == *id)
                .ok_or(InkError::InvalidStroke)?;
            if ![sx, sy, tx, ty].iter().all(|v| v.is_finite())
                || !(0.001..=1000.).contains(sx)
                || !(0.001..=1000.).contains(sy)
                || edit.original.iter().flat_map(|(_, s)| &s.points).any(|p| {
                    !self.valid_point(&Point {
                        x: p.x * sx + tx,
                        y: p.y * sy + ty,
                        ..*p
                    })
                })
            {
                return Err(InkError::InvalidMessage);
            }
        }
        let point = match &envelope.command {
            Command::Begin { point, .. }
            | Command::Append { point, .. }
            | Command::TransientBegin { point, .. }
            | Command::EraseBegin { point, .. }
            | Command::ErasePartialBegin { point, .. }
            | Command::EraseTo { point, .. } => Some(point),
            Command::Pointer { point, .. } => point.as_ref(),
            _ => None,
        };
        if point.is_some_and(|p| {
            !p.x.is_finite()
                || !p.y.is_finite()
                || !p.pressure.is_finite()
                || !(0.0..=self.width as f32).contains(&p.x)
                || !(0.0..=self.height as f32).contains(&p.y)
                || !(0.0..=1.0).contains(&p.pressure)
        }) {
            return Err(InkError::InvalidMessage);
        }
        self.board.apply(envelope.command)?;
        self.accepted = envelope.seq;
        Ok(())
    }
    pub fn board(&self) -> &Board {
        &self.board
    }
    fn valid_point(&self, p: &Point) -> bool {
        p.x.is_finite()
            && p.y.is_finite()
            && p.pressure.is_finite()
            && (0.0..=self.width as f32).contains(&p.x)
            && (0.0..=self.height as f32).contains(&p.y)
            && (0.0..=1.0).contains(&p.pressure)
    }
    pub fn accepted_sequence(&self) -> u64 {
        self.accepted
    }
    pub fn advance_time(&mut self, now_ms: u64) -> bool {
        self.board.advance_time(now_ms)
    }
}
