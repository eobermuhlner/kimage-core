# Code Review - Issues to Fix

## HIGH Severity (Fix First)

### 1. Division by Zero in Image.kt
- **File:** `src/main/kotlin/ch/obermuhlner/kimage/core/image/Image.kt` line 97
- **Issue:** `(cmax - cmin) / cmax` crashes when `cmax == 0.0`
- **Fix:** Add guard: `if (cmax == 0.0) return 0.0`

### 2. Division by Zero in MathFunctions.kt
- **File:** `src/main/kotlin/ch/obermuhlner/kimage/core/math/MathFunctions.kt` line 48
- **Issue:** `(a - x0) / (x1 - x0)` crashes when `x0 == x1`
- **Fix:** Add `require(x0 != x1)` or return safe default

### 3. Null Safety in JsonImageParser.kt
- **File:** `src/main/kotlin/ch/obermuhlner/kimage/core/image/io/json/JsonImageParser.kt` line 46
- **Issue:** `data[channel.name]!!` crashes if channel missing from JSON data
- **Fix:** Use `data[channel.name] ?: DoubleMatrix(...)` with fallback

### 4. Histogram Underflow
- **File:** `src/main/kotlin/ch/obermuhlner/kimage/core/math/Histogram.kt` line 68
- **Issue:** `bins[index]--` on `Histogram.remove()` can underflow to negative values
- **Fix:** Add check: `if (bins[index] > 0) bins[index]--`

---

## MEDIUM Severity

### 5. Off-by-One in Median Filter
- **File:** `src/main/kotlin/ch/obermuhlner/kimage/core/image/filter/MedianFilter.kt` lines 89-90
- **Issue:** Returns `values[0]` when `n == 0` without bounds check
- **Fix:** Add early return: `if (n == 0) return Double.NaN`

### 6. Silent Divergence in Debayer
- **File:** `src/main/kotlin/ch/obermuhlner/kimage/core/image/bayer/Debayer.kt` lines 196-221
- **Issue:** `Monochrome` mode assigns grayscale to all channels, ignoring color parameters
- **Fix:** Apply `r * red`, `g * green`, `b * blue` coefficients

### 7. Resource Leak Risk - Temp Files
- **File:** `src/main/kotlin/ch/obermuhlner/kimage/core/huge/HugeMultiDimensionalFloatArray.kt` lines 27-34
- **Issue:** No `Closeable` implementation; temp files may persist on exception
- **Fix:** Implement `Closeable` and use `use()` blocks

### 8. Integer Overflow in FloatMatrix
- **File:** `src/main/kotlin/ch/obermuhlner/kimage/core/matrix/FloatMatrix.kt` line 43
- **Issue:** `row * cols + col` overflows with large matrices (`rows * cols > Int.MAX_VALUE`)
- **Fix:** Use `row.toLong() * cols + col` or validate bounds

---

## LOW Severity

### 9. Dead Code in Debayer
- **File:** `src/main/kotlin/ch/obermuhlner/kimage/core/image/bayer/Debayer.kt` lines 467-470
- **Issue:** Large blocks of commented code create maintenance burden
- **Fix:** Delete commented code or create explanatory comment

### 10. Missing Validation in Matrix Creation
- **File:** `src/main/kotlin/ch/obermuhlner/kimage/core/matrix/FloatMatrix.kt` lines 28-33
- **Issue:** No validation that `rows > 0` and `cols > 0` before allocation
- **Fix:** Add `require(rows > 0 && cols > 0)`

---

**Recommended Priority Order:** Fix issues 1-4 first (immediate crash risk), then 5-8 (data corruption/memory risk), then 9-10 (code quality).