# Supplemental crate notices

Some published Rust crates omit license texts present at their repository root.
The versioned directories here supplement those packages for binary distribution.
Each `sources.json` records the exact upstream commit URLs, derived from that
crate's `.cargo_vcs_info.json`. The packager also collects notices shipped in
the local Cargo registry, including font license files.

`fax`, `fax_derive` and `ffmpeg-sys-next` declare a license in Cargo metadata but
omit a standalone license text in both the published package and pinned source.
For these, the declared standard license text is included with package metadata
and an explicit explanation; no missing copyright year has been invented.

These are notices for their respective dependencies, not a license for all
new DeskInk source code.
