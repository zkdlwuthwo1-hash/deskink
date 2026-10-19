//! Small, per-user trust store. No credential belongs in the portable package.
use serde::{Deserialize, Serialize};
use std::{
    collections::BTreeMap,
    fs,
    io::{self, Read, Write},
    path::{Path, PathBuf},
};
use subtle::ConstantTimeEq;

const MAX_DEVICES: usize = 64;
const MAX_BYTES: u64 = 64 * 1024;

#[derive(Clone, Serialize, Deserialize)]
struct Saved {
    version: u8,
    pc_id: [u8; 16],
    tablets: BTreeMap<u64, Vec<[u8; 16]>>,
}

pub(crate) struct TrustedDevices {
    path: PathBuf,
    saved: Saved,
}

impl TrustedDevices {
    pub fn open(path: &Path) -> io::Result<Self> {
        let saved = match fs::File::open(path) {
            Ok(file) => {
                let mut encrypted = Vec::new();
                file.take(MAX_BYTES + 1).read_to_end(&mut encrypted)?;
                if encrypted.len() as u64 > MAX_BYTES {
                    return Err(invalid());
                }
                let mut plain = crypt(&encrypted, false)?;
                let decoded = serde_json::from_slice::<Saved>(&plain);
                plain.fill(0);
                let saved = decoded.map_err(|_| invalid())?;
                if saved.version != 1
                    || saved.pc_id == [0; 16]
                    || saved.tablets.len() > MAX_DEVICES
                    || saved.tablets.iter().any(|(id, tokens)| {
                        *id == 0
                            || tokens.is_empty()
                            || tokens.len() > 2
                            || tokens.iter().any(|token| *token == [0; 16])
                    })
                {
                    return Err(invalid());
                }
                saved
            }
            Err(error) if error.kind() == io::ErrorKind::NotFound => Saved {
                version: 1,
                pc_id: crate::pairing::new_token().map_err(io::Error::other)?,
                tablets: BTreeMap::new(),
            },
            Err(error) => return Err(error),
        };
        Ok(Self {
            path: path.to_owned(),
            saved,
        })
    }

    pub fn accepts(&self, device: u64, token: &[u8; 16]) -> bool {
        self.saved.tablets.get(&device).is_some_and(|tokens| {
            tokens.iter().fold(false, |matched, known| {
                matched | bool::from(known.ct_eq(token))
            })
        })
    }

    pub fn remember(
        &mut self,
        device: u64,
        presented: [u8; 16],
        issued: [u8; 16],
        used_code: bool,
    ) -> io::Result<()> {
        if device == 0 {
            return Err(invalid());
        }
        let mut tokens = vec![issued];
        // Keep the credential the tablet actually proved it has until it uses
        // the replacement. Lost ACKs / failed tablet saves must be recoverable.
        if !used_code && presented != issued && self.accepts(device, &presented) {
            tokens.push(presented);
        }
        if self.saved.tablets.get(&device) == Some(&tokens) {
            return Ok(());
        }
        let mut next = self.saved.clone();
        next.tablets.insert(device, tokens);
        if next.tablets.len() > MAX_DEVICES {
            return Err(io::Error::other("Forget paired tablets before adding more"));
        }
        self.save(next)
    }

    pub fn clear(&mut self) -> io::Result<()> {
        self.save(Saved {
            version: 1,
            pc_id: self.saved.pc_id,
            tablets: BTreeMap::new(),
        })
    }
    pub fn len(&self) -> usize {
        self.saved.tablets.len()
    }
    pub fn pc_id(&self) -> [u8; 16] {
        self.saved.pc_id
    }

    fn save(&mut self, next: Saved) -> io::Result<()> {
        let mut plain = serde_json::to_vec(&next).map_err(io::Error::other)?;
        let encrypted = crypt(&plain, true);
        plain.fill(0);
        let encrypted = encrypted?;
        let parent = self.path.parent().ok_or_else(invalid)?;
        fs::create_dir_all(parent)?;
        let temporary = self.path.with_extension(format!(
            "{}.tmp",
            crate::pairing::token_hex(&crate::pairing::new_token().map_err(io::Error::other)?)
        ));
        let result = (|| {
            let mut file = fs::OpenOptions::new()
                .write(true)
                .create_new(true)
                .open(&temporary)?;
            file.write_all(&encrypted)?;
            file.sync_all()?;
            drop(file);
            fs::rename(&temporary, &self.path)
        })();
        if result.is_err() {
            let _ = fs::remove_file(&temporary);
        }
        result?;
        self.saved = next;
        Ok(())
    }
}

fn invalid() -> io::Error {
    io::Error::new(
        io::ErrorKind::InvalidData,
        "Saved tablet registration is invalid; it was not replaced",
    )
}

#[cfg(windows)]
fn crypt(bytes: &[u8], protect: bool) -> io::Result<Vec<u8>> {
    use windows::Win32::{
        Foundation::{LocalFree, HLOCAL},
        Security::Cryptography::{
            CryptProtectData, CryptUnprotectData, CRYPTPROTECT_UI_FORBIDDEN, CRYPT_INTEGER_BLOB,
        },
    };
    struct Output(CRYPT_INTEGER_BLOB);
    impl Drop for Output {
        fn drop(&mut self) {
            if !self.0.pbData.is_null() {
                unsafe {
                    std::ptr::write_bytes(self.0.pbData, 0, self.0.cbData as usize);
                    let _ = LocalFree(HLOCAL(self.0.pbData.cast()));
                }
            }
        }
    }
    let input = CRYPT_INTEGER_BLOB {
        cbData: u32::try_from(bytes.len()).map_err(|_| invalid())?,
        pbData: bytes.as_ptr().cast_mut(),
    };
    let mut output = Output(CRYPT_INTEGER_BLOB::default());
    unsafe {
        if protect {
            CryptProtectData(
                &input,
                windows::core::w!("DeskInk tablet registration"),
                None,
                None,
                None,
                CRYPTPROTECT_UI_FORBIDDEN,
                &mut output.0,
            )
        } else {
            CryptUnprotectData(
                &input,
                None,
                None,
                None,
                None,
                CRYPTPROTECT_UI_FORBIDDEN,
                &mut output.0,
            )
        }
        .map_err(|_| {
            io::Error::other("Windows could not protect or read tablet registration for this user")
        })?;
        if output.0.pbData.is_null() || output.0.cbData as u64 > MAX_BYTES {
            return Err(invalid());
        }
        Ok(std::slice::from_raw_parts(output.0.pbData, output.0.cbData as usize).to_vec())
    }
}

#[cfg(not(windows))]
fn crypt(_: &[u8], _: bool) -> io::Result<Vec<u8>> {
    Err(io::Error::new(
        io::ErrorKind::Unsupported,
        "DeskInk tablet registration uses Windows user protection",
    ))
}
