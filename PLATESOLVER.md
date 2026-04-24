# Plate Solver Design & Robustness

This document outlines the strategy for improving the native Kotlin plate solver. The goal is to maintain **scale-invariance** (allowing blind solving without prior knowledge of focal length) while eliminating false positives and "micro-matches."

## Current Challenges
The current triangle-based matching algorithm is susceptible to false positives in dense star fields because:
1.  **Low Geometric Specificity**: A triangle only has 2 degrees of freedom (two internal angles), making it statistically easy to find accidental matches in a large catalog.
2.  **Star Clustering**: Bright stars are often concentrated in small areas (e.g., clusters), leading the algorithm to match tiny sub-regions at incorrect scales.
3.  **No Scale Priors**: Without sanity bounds, the solver can accept solutions that imply physically impossible pixel scales (e.g., 0.001 arcsec/pixel).

## Robustness Strategies

### 1. Geometric Specificity (Quads)
Move from matching 3-star triplets (triangles) to 4-star patterns (quads).
- **Benefit**: Quads provide a significantly more unique "fingerprint."
- **Implementation**: Define a coordinate system using the two furthest stars in a set of four, then record the relative positions of the other two.

### 2. Uniform Star Distribution (Grid-Based Pruning)
Ensure that selected stars span the entire image/search area.
- **Benefit**: Prevents "zooming in" on dense clusters and ensures the affine fit is stable across the FOV.
- **Implementation**: Divide the area into a grid (e.g., 4x4) and select the brightest $N$ stars from each cell.

### 3. Relative Magnitude Constraints
Use the fact that relative brightness between stars is generally preserved across sensors/filters.
- **Benefit**: Discards geometrically similar matches where the star brightness order is completely different.
- **Implementation**: Implement a "Rank Consistency" check during matching.

### 4. Physical Scale Sanity Bounds
Apply wide but realistic bounds on the resulting pixel scale.
- **Benefit**: Instantly rejects "micro-matches" that imply impossible resolutions.
- **Implementation**: Filter RANSAC candidates based on the scale component of the transformation matrix.

### 5. Probabilistic Verification
Rigorous verification of a "candidate" solution before acceptance.
- **Benefit**: A true solution will have a massive consensus of inliers across the entire image.
- **Implementation**: Once a fit is found, project all detected stars and calculate a "Log-Likelihood" score based on position and brightness.

---

## Task List

- [ ] **Infrastructure Improvements**
    - [x] Abstract `PlateSolver` interface
    - [x] VizieR API integration for automatic catalog downloading
    - [x] Local disk caching for star catalogs (`~/.kimage-astro-process/star-catalog`)
    - [x] Gnomonic projection utility (Degrees <-> Tangent Plane Radians)

- [ ] **Robustness Features**
    - [x] Implement **Grid-Based Star Selection** (Uniform distribution)
    - [x] Implement **Scale-Range Filtering** (Reject physically impossible scales)
    - [x] Implement **Relative Magnitude Ranking** (Rank-order consistency check)
    - [x] Move matching logic from **Triangles to Quads**
    - [x] Implement **Consensus-Based Verification** (Inlier count threshold)

- [ ] **Performance & Optimization**
    - [x] Parallelize RANSAC iterations
    - [x] Implement KD-Tree for fast catalog star lookups
    - [x] Optimize triangle/quad feature sorting and searching

---

## Technical Implementation Details

### 1. WCS to Affine Mapping
The transformation matrix $T$ found by `AlignStars` relates image pixels $[x, y]$ to tangent plane radians $[r_x, r_y]$.
To convert to WCS keywords (where CD matrix is in degrees/pixel):
- $x_{deg} = (x_{img} - CRPIX1) \cdot CD1\_1 + (y_{img} - CRPIX2) \cdot CD1\_2$
- $CD1\_1 = T_{0,0} \cdot (180/\pi)$
- $CD1\_2 = T_{0,1} \cdot (180/\pi)$
- $CD2\_1 = T_{1,0} \cdot (180/\pi)$
- $CD2\_2 = T_{1,1} \cdot (180/\pi)$

### 2. Numerical Stability
Because tangent plane radians are very small ($\approx 10^{-5}$ per pixel), the affine fit is numerically unstable.
- **Solution**: Scale catalog coordinates by a factor (e.g., $10,000$) before matching.
- **Result**: De-scale the resulting matrix before generating WCS output.

### 3. VizieR Catalog Access
- **Universal Columns**: Always request `_RA` and `_DE`. These are VizieR aliases for J2000 decimal degrees regardless of the underlying catalog's specific column names (e.g., `RAJ2000` vs `RA_ICRS`).
- **Separators**: The `-asu-tsv` format often uses a mix of tabs and spaces. Use a regex whitespace delimiter `Regex("\\s+")` for robust parsing.
- **Gaia DR3**: `I/355/gaiadr3`
- **Tycho-2**: `I/239/tyc_main`

### 4. Project-Specific Conventions
- **Star Object**: Use the `brightness` property for magnitude/value.
- **Coordinate Logic**: `AlignStars` uses the convention $x' = ax + by + c$.
