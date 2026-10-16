use serde::{Deserialize, Serialize};

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
    pub finished: bool,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case", deny_unknown_fields)]
pub enum Command {
    Begin { id: u64, brush: Brush, point: Point },
    Append { id: u64, point: Point },
    End { id: u64 },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum InkError {
    InvalidStroke,
    InvalidMessage,
}

#[derive(Default)]
pub struct Board {
    strokes: Vec<Stroke>,
}

impl Board {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn apply(&mut self, command: Command) -> Result<(), InkError> {
        match command {
            Command::Begin { id, brush, point } => {
                if self.strokes.iter().any(|stroke| stroke.id == id) {
                    return Err(InkError::InvalidStroke);
                }
                self.strokes.push(Stroke {
                    id,
                    brush,
                    points: vec![point],
                    finished: false,
                });
            }
            Command::Append { id, point } => {
                let stroke = self
                    .strokes
                    .iter_mut()
                    .find(|stroke| stroke.id == id)
                    .ok_or(InkError::InvalidStroke)?;
                if stroke.finished {
                    return Err(InkError::InvalidStroke);
                }
                stroke.points.push(point);
            }
            Command::End { id } => {
                let stroke = self
                    .strokes
                    .iter_mut()
                    .find(|stroke| stroke.id == id)
                    .ok_or(InkError::InvalidStroke)?;
                stroke.finished = true;
            }
        }
        Ok(())
    }

    pub fn strokes(&self) -> &[Stroke] {
        &self.strokes
    }
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
        for stroke in &mut self.board.strokes {
            stroke.finished = true;
        }
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
        if let Command::Begin { brush, .. } = &envelope.command {
            if !brush.width.is_finite() || !(0.5..=64.0).contains(&brush.width) {
                return Err(InkError::InvalidMessage);
            }
        }
        let point = match &envelope.command {
            Command::Begin { point, .. } | Command::Append { point, .. } => Some(point),
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
    pub fn accepted_sequence(&self) -> u64 {
        self.accepted
    }
}
