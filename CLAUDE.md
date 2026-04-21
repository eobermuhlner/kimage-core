# CLAUDE.md

## Git Commits

Write simple, concise commit messages. Do not mention agentic coding tools (Claude Code, OpenCode, etc.) in commit messages or co-author lines.

## Test-Driven Development

Write tests before implementing. For each change:
1. Write a failing test that captures the expected behavior.
2. Implement the minimum code to make it pass.
3. Refactor if needed, keeping tests green.

For bug fixes, write a test that reproduces the bug first, then fix it.

For image-processing changes, use `AbstractImageProcessingTest.assertReferenceImage` — run once to generate the reference, then subsequent runs verify against it. Delete the reference file when intentionally changing output.

## Build & Test

Gradle project, Kotlin 2.0 targeting JVM 17.

```sh
./gradlew build                       # Compile + tests + jar
./gradlew test                        # Run all tests (JUnit Platform)
./gradlew test --tests "ch.obermuhlner.kimage.image.stack.ImageStackTest"
./gradlew test --tests "*.ImageStackTest.stackMedian"
./gradlew run                         # Runs the astro-process CLI (mainClass AstroProcessKt)
./gradlew installDist                 # Produces build/install/kimage-astro-process/bin/kimage-astro-process
```

Tests depending on `AbstractImageProcessingTest` read from `test-input/` and write/compare reference images under `test-results/<classFqn>/<method>/`. If a reference file is missing it is written on first run; subsequent runs compare against it.

## End-User CLI

Application name: `kimage-astro-process` (entry point `ch.obermuhlner.kimage.astro.process.AstroProcessKt`). Commands: `init`, `process`, `config`, `stars`. Reads `kimage-astro-process.yaml` from the current directory; `init` writes `defaultAstroProcessConfigText`. See `README.md` for the full config reference — it is the authoritative documentation for pipeline semantics and parameter ranges.

## Architecture

Two package roots under `ch.obermuhlner.kimage`:

- `core/` — reusable image and matrix infrastructure. No astrophotography specifics.
- `astro/` — astrophotography workflow built on top of `core/`.

### Core abstractions

- `core.image.Image` (interface) — width/height + ordered `channels: List<Channel>`. All pixel operations route through `getPixel(x, y, channel)` / `setPixel`. Convenience conversions between RGB / HSB / YUV / Gray / Luminance live directly on the interface.
- `core.image.MatrixImage` (`AbstractImage` subclass) — the standard implementation. One `Matrix` per channel, indexed by `image[Channel.Red]`. Most pipeline code constructs `MatrixImage(w, h, Channel.Red to m1, Channel.Green to m2, ...)` or `MatrixImage(red, green, blue)`.
- `core.matrix.Matrix` (interface) — `rows`/`cols`, flat `get(index)` / `get(row, col)`, in-place and copying arithmetic operators, `crop()` (returns a `CroppedMatrix` view), `transpose`, `applyEach`. The matmul `operator fun times` uses a blocked algorithm — do not "simplify" it back to the triple loop, that variant is preserved as `timesSlow`.
- `core.matrix.FloatMatrix` vs `DoubleMatrix` — `FloatMatrix` is the default (memory-efficient for large astro frames); `DoubleMatrix` exists for numerical work that needs the extra precision (e.g. `linearalgebra/Invert`, `Determinant`). The `Matrix` interface exposes doubles regardless of storage — conversions happen in `get`/`set`.
- `core.huge.MultiDimensionalFloatArray` / `HugeMultiDimensionalFloatArray` — backs stacking of many large frames without OOMing.

Cross-cutting conventions worth noting when editing:

- Matrices use `(row, col)` indexing; images use `(x, y)`. `image[channel]` returns a matrix whose rows==height, cols==width — be deliberate about the transposition when you bridge the two (see `core.matrix.values.asXY` / `MatrixXY`).
- `Channel` is an enum; `AbstractImage` caches a per-channel index lookup, so channel lookup is O(1).
- I/O is centralized in `core.image.io.ImageReader` / `ImageWriter`. Dispatches on extension: `fit`/`fits` via nom-tam-fits, `json` and `dimg` via internal formats, everything else via `javax.imageio`.

### Astro pipeline

`astro.process.AstroProcess` in `AstroProcess.kt` is the orchestrator. Read the top of that file to understand the shape of the config tree — the YAML is deserialized directly into the nested `ProcessConfig` / `*Config` data classes via SnakeYAML, so field names in YAML must match Kotlin property names.

Pipeline stages, each reading from the previous stage's output directory:

1. **Calibrate** (`astro.align.CalibrationImage.processCalibrationImages`) — bias/dark/darkflat/flat master frames, then apply to lights.
2. **Align** (`astro.align.AlignStars`) — `findStars` → triangle feature matching → `calculateTransformationMatrix` → `applyTransformationToImage`.
3. **Stack** (`core.image.stack.Stack`) — `StackAlgorithm` enum picks median / average / sigma-clip / winsorize / smart-max etc.
4. **Enhance** — ordered list of `EnhanceStepConfig`s; each step has exactly one non-null sub-config (`crop`, `sigmoid`, `reduceNoise`, …) which `EnhanceStepConfig.type` resolves to an `EnhanceStepType`. Steps flagged `addToHighDynamicRange` are captured for the later `highDynamicRange` combine step.
5. **Annotate** (`astro.annotate.*`) — optional WCS-aware decoration using `DeepSkyObjects` (backed by `src/main/resources/NGC.csv`) and `AnnotateZoom`.
6. **Output** — one or more image extensions + token-based filename templating (`outputName: "{targetName}_{stackedCount}x{exposureTime}"`).

The pipeline is resumable: each stage skips work whose output files already exist. Deleting an intermediate directory (`astro-process/aligned`, etc.) forces re-run of that stage onward. A `dirty` flag propagates forward when an earlier stage regenerated outputs, invalidating caches downstream.

`astro.SimpleAstro` is a scratchpad `main` with hardcoded paths used for local experimentation — not part of the shipped CLI.

## Working in this codebase

- Prefer `MatrixImage` construction via the `(red, green, blue)` or `(width, height, channels, matrixFunc)` constructors over manually looping to fill pixels.
- `core.image.values.values()` / `core.matrix.values.values()` give a `DoubleArray`-like view for statistics (`median`, `stddev`, `sigmaClip`) in `core.math`.
- When adding a new enhance step: add a `*Config` data class, add a nullable field to `EnhanceStepConfig`, extend `EnhanceStepType`, extend the `type` getter, and wire the execution branch in `AstroProcess.processAstro`.
- Tests live under two roots: `src/test/kotlin/ch/obermuhlner/kimage/core/` mirrors core unit tests, `src/test/kotlin/ch/obermuhlner/kimage/image/` and `.../astro/` cover image-processing behavior via the reference-image harness.
