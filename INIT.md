# Smart `init` — Analysis, Requirements & Approaches

## Current state

`init` writes `defaultAstroProcessConfigText` verbatim to `kimage-astro-process.yaml`.  
No file-system inspection is performed; the resulting config is identical regardless of what is in the current directory.

---

## Problems with the status quo

1. **Calibration section is always emitted** even when no bias/dark/flat frames exist, forcing the user to delete or comment it out.
2. **Bayer pattern is always set to `RGGB`** even for monochrome or different-pattern cameras.
3. **Sigmoid midpoint is hardcoded** (`0.01`) regardless of how bright or dark the raw stacked image will be.
4. **Input extension is always `fit`** even when the directory contains `.fits`, `.tiff`, or `.cr2` files.
5. **Quick mode defaults to 3 frames** regardless of how many lights are actually present.

---

## Information available in the working directory

### Directory / file structure

| Path to check | What it tells us |
|---|---|
| `*.fit`, `*.fits`, `*.tiff`, `*.tif`, `*.cr2`, `*.nef`, … (current dir) | Light frames are in the root; detect dominant extension |
| `light/`, `lights/` subdirectory | Light frames are separated; set `inputDirectory` |
| `bias/`, `biases/` | Bias frames present; enable calibration |
| `dark/`, `darks/` | Dark frames present; enable calibration |
| `flat/`, `flats/` | Flat frames present; enable calibration |
| `darkflat/`, `darkflats/` | Darkflat frames present |

### FITS header metadata (from a sample light frame)

| FITS keyword | Config field it informs |
|---|---|
| `NAXIS1` / `NAXIS2` | Frame dimensions (useful for crop/drizzle defaults) |
| `BAYERPAT` / `COLORTYPE` | `format.bayerPattern`; monochrome if absent |
| `EXPTIME` / `EXPOSURE` | Typical exposure time (token for output name) |
| `OBJECT` / `TARGET` | `target.name` / output filename token |
| `INSTRUME` | Camera name (informational) |
| `GAIN` / `ISO` | Could influence noise-reduction strength |

### Statistical sampling of light frames

Reading pixel values from a sample of lights (or even a single frame) enables:

- **Median / mean background level** → sigmoid `midpoint` (see below)
- **Standard deviation** → estimate of noise level; adjust `reduceNoise` strength
- **Histogram shape** → detect clipped highlights (over-exposed) or very dim data

---

## Requirements

### Must-have (R1–R5)

**R1 — Detect input files and extension.**  
Scan the current directory and common subdirectories (`light/`, `lights/`) for image files.  
Pick the extension with the most matching files.  Fail gracefully if nothing is found (emit a comment in the YAML and print a warning).

**R2 — Detect calibration frame directories.**  
The pipeline already adapts automatically when calibration directories are absent — do **not** explicitly disable calibration in the generated config.  
Instead, detect the actual directory name used so the config points to it correctly.  
Recognise common naming variants (case-insensitive, singular/plural):

| Calibration type | Recognised directory names |
|---|---|
| Bias | `bias`, `biases`, `Bias`, `Biases`, `BIAS` |
| Dark | `dark`, `darks`, `Dark`, `Darks`, `DARK` |
| Flat | `flat`, `flats`, `Flat`, `Flats`, `FLAT` |
| Dark flat | `darkflat`, `darkflats`, `dark_flat`, `dark_flats`, `DarkFlat`, `DARKFLAT` |

If a directory is found under a non-default name, emit that name in the config.  
If none of the variants exist, leave the default name in the config with a comment that it will be skipped automatically when absent.

**R3 — Detect Bayer pattern.**  
For FITS files: read `BAYERPAT` header from the first light frame.  
For non-FITS RAW files: default to `RGGB` but add a comment explaining how to change it.  
For TIFF/PNG: assume debayering is already done and omit the debayer step.

**R4 — Compute sigmoid midpoint from image statistics.**  
Sample 1–3 light frames (or the first frame only for speed).  
Compute the median pixel value of the raw data.  
Derive an initial `midpoint` recommendation (see _Approach_ below).

**R5 - discarded.**

### Nice-to-have (R6–R8)

**R6 — Populate `target.name` from FITS `OBJECT` header or directory name.**

**R7 — Suggest `outputName` token pattern based on available FITS keywords.**

**R8 — Print a human-readable summary of what was detected** before writing the YAML, so the user can verify.

---

## Sigmoid midpoint derivation

The sigmoid stretch maps `midpoint → 0.5`, pulling dim nebulosity up into the visible range.  
A reasonable default is derived from the raw stacked (or pre-stack) image statistics:

```
medianBackground = median pixel value of a sample light frame
midpoint = clamp(medianBackground, 0.001, 0.3)
```

| Raw median | Resulting midpoint | Typical case |
|---|---|---|
| < 0.005 | 0.001–0.005 | Very dark sky, narrowband |
| 0.005–0.05 | 0.005–0.05 | Typical dark-site broadband |
| 0.05–0.2 | 0.05–0.2 | Suburban sky or short exposures |
| > 0.2 | 0.2–0.3 (capped) | Bright background / moon-lit |

The subsequent sigmoid iterations (steps 8–12 in the current default) use `midpoint: 0.4` regardless, which is reasonable for post-stretch mid-tones — those can remain fixed defaults.

---

## Possible implementation approaches

### Approach A — Minimal (file-system only, no pixel reading)

Only R1, R2, R5 are addressed.  No image files are opened.

**Steps:**
1. Glob current dir + `light/lights/` for known extensions; count per extension.
2. Check for calibration subdirectories.
3. Emit YAML with detected values; leave sigmoid at a sensible fixed default (e.g., `0.02`).

**Pros:** Fast, no FITS/image dependency at init time, safe.  
**Cons:** Sigmoid midpoint is still a guess.

---

### Approach B — Moderate (file-system + FITS header reading)

R1–R3, R5–R6.  Opens one FITS header (no pixel data).

**Steps:**
1. All of Approach A.
2. Open the first light frame with nom-tam-fits; read `BAYERPAT`, `OBJECT`, `EXPTIME`.
3. Populate the config with detected values.

**Pros:** Minimal overhead; provides Bayer and target name.  
**Cons:** Still no adaptive sigmoid.

---

### Approach C — Full (file-system + header + pixel statistics) ← Recommended

All R1–R8.  Opens 1–3 light frames and reads pixel values.

**Steps:**
1. All of Approach B.
2. Load one or a small sample of light frames at reduced resolution (e.g., every 16th pixel).
3. Compute median background level.
4. Derive sigmoid `midpoint` and optionally `reduceNoise` strength.
5. Emit YAML with all detected values and a brief detection summary printed to stdout.

**Pros:** Config is meaningful out of the box; fewer manual tweaks needed.  
**Cons:** Slightly slower init (seconds for one frame); reading partial pixels requires care with different image types.

**Sampling strategy for speed:**
- FITS/TIFF: read every 16th pixel in both axes (1/256 of all pixels) — sufficient for background statistics.
- Use `ImageReader` to load the frame, then sample via matrix row/col stride.

---

## Open questions

1. Should `init` refuse to run if `kimage-astro-process.yaml` already exists, or silently overwrite it? (Currently: overwrites silently.)  A `--force` flag could be added.
2. Should the detected configuration be shown in `--dry-run` mode before writing?
3. For non-FITS RAW files (CR2, NEF), should `init` delegate to `dcraw`/`libraw` for header reading, or skip header analysis and only do file-system detection?
4. How many light frames should be sampled for statistics?  One is fastest; three gives more robustness against bad frames (e.g., a satellite trail frame that is unusually bright).
