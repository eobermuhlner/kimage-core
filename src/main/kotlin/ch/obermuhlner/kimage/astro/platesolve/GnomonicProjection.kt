package ch.obermuhlner.kimage.astro.platesolve

import kotlin.math.*

/**
 * Gnomonic (tangent plane) projection.
 * Maps spherical coordinates (RA, Dec) to a flat 2D plane.
 */
object GnomonicProjection {

    /**
     * Projects RA/Dec (degrees) to X/Y coordinates on a tangent plane (in radians).
     *
     * @param ra RA of the point to project (degrees)
     * @param dec Dec of the point to project (degrees)
     * @param raCenter RA of the projection center (degrees)
     * @param decCenter Dec of the projection center (degrees)
     * @return Pair of (x, y) coordinates in radians from the center
     */
    fun project(ra: Double, dec: Double, raCenter: Double, decCenter: Double): Pair<Double, Double> {
        val raRad = Math.toRadians(ra)
        val decRad = Math.toRadians(dec)
        val raCenterRad = Math.toRadians(raCenter)
        val decCenterRad = Math.toRadians(decCenter)

        val cosDec = cos(decRad)
        val sinDec = sin(decRad)
        val cosDecCenter = cos(decCenterRad)
        val sinDecCenter = sin(decCenterRad)
        val cosDeltaRa = cos(raRad - raCenterRad)
        val sinDeltaRa = sin(raRad - raCenterRad)

        val denom = sinDec * sinDecCenter + cosDec * cosDecCenter * cosDeltaRa

        val x = cosDec * sin(raRad - raCenterRad) / denom
        val y = (cosDecCenter * sinDec - sinDecCenter * cosDec * cosDeltaRa) / denom

        return Pair(x, y)
    }

    /**
     * Inverse projection: maps X/Y coordinates on a tangent plane (in radians) back to RA/Dec (degrees).
     */
    fun unproject(x: Double, y: Double, raCenter: Double, decCenter: Double): Pair<Double, Double> {
        val raCenterRad = Math.toRadians(raCenter)
        val decCenterRad = Math.toRadians(decCenter)

        val cosDecCenter = cos(decCenterRad)
        val sinDecCenter = sin(decCenterRad)
        val rho = sqrt(x * x + y * y)
        val c = atan(rho)

        if (rho == 0.0) {
            return Pair(raCenter, decCenter)
        }

        val cosC = cos(c)
        val sinC = sin(c)

        val dec = asin(cosC * sinDecCenter + (y * sinC * cosDecCenter / rho))
        val ra = raCenterRad + atan2(x * sinC, rho * cosDecCenter * cosC - y * sinDecCenter * sinC)

        return Pair(normalizeDegrees(Math.toDegrees(ra)), Math.toDegrees(dec))
    }


    private fun normalizeDegrees(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }
}
