package ch.obermuhlner.kimage.astro.annotate

import ch.obermuhlner.kimage.core.matrix.DoubleMatrix
import ch.obermuhlner.kimage.core.matrix.linearalgebra.invert
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * See: https://danmoser.github.io/notes/gai_fits-imgs.html
 * See: http://astro.physics.uiowa.edu/~kaaret/2018s_a4850/Lab05_astrometry.html
 * See: https://github.com/esdc-esac-esa-int/JWcs
 */

class WCSConverter(
    private val crpix1: Double,
    private val crpix2: Double,
    private val crval1: Double,
    private val crval2: Double,
    private val cdelt1: Double,
    private val cdelt2: Double,
    private val crota1: Double,
    private val crota2: Double,
    private val cd1_1: Double,
    private val cd1_2: Double,
    private val cd2_1: Double,
    private val cd2_2: Double
) {
    constructor(wcs: Map<String, String>) :
            this(
                wcs["CRPIX1"]!!.toDouble(),
                wcs["CRPIX2"]!!.toDouble(),
                wcs["CRVAL1"]!!.toDouble(),
                wcs["CRVAL2"]!!.toDouble(),
                wcs["CDELT1"]!!.toDouble(),
                wcs["CDELT2"]!!.toDouble(),
                wcs["CROTA1"]!!.toDouble(),
                wcs["CROTA2"]!!.toDouble(),
                wcs["CD1_1"]!!.toDouble(),
                wcs["CD1_2"]!!.toDouble(),
                wcs["CD2_1"]!!.toDouble(),
                wcs["CD2_2"]!!.toDouble())

    fun convertXYToRADec(x: Double, y: Double): Pair<Double, Double> {
        val deltaX = x - crpix1
        val deltaY = y - crpix2
        val ra = crval1 + (cd1_1 * deltaX + cd1_2 * deltaY)
        val dec = crval2 + (cd2_1 * deltaX + cd2_2 * deltaY)
        return Pair(ra, dec)
    }

    fun convertRADecToXY_OLD(ra: Double, dec: Double): Pair<Double, Double> {
        val deltaRa = ra - crval1
        val deltaDec = dec - crval2

        val deltaX = (cd1_1 * deltaRa + cd2_1 * deltaDec) / (cd1_1 * cd2_2 - cd1_2 * cd2_1)
        val deltaY = (cd1_2 * deltaRa + cd2_2 * deltaDec) / (cd1_2 * cd2_1 - cd1_1 * cd2_2)

        val x = crpix1 + deltaX
        val y = crpix2 + deltaY

        return Pair(x, y)
    }

    fun convertRADecToXY(raDegrees: Double, decDegrees: Double): Pair<Double, Double> {
        val ra = Math.toRadians(raDegrees)
        val dec = Math.toRadians(decDegrees)
        val ra_p = Math.toRadians(crval1)
        val dec_p = Math.toRadians(crval2)


        val phi = phiRange(Math.PI + atan2(
            -cos(dec) * sin(ra - ra_p),
            sin(dec) * cos(dec_p) - (cos(dec) * sin(dec_p) * cos(ra - ra_p))
        ))
        val theta = asin(
            sin(dec) * sin(dec_p) + (cos(dec) * cos(dec_p) * cos(ra - ra_p))
        )
        val xy = projectInverse(phi, theta)

        val m1 = DoubleMatrix(2, 1)
        m1[0, 0] = xy.first
        m1[1, 0] = xy.second

        val m2 = DoubleMatrix(2, 2)
        m2[0, 0] = cd1_1
        m2[0, 1] = cd1_2
        m2[1, 0] = cd2_1
        m2[1, 1] = cd2_2
        val m3 = m2.invert()
        val m4 = m1 * m3!!

        val x = crpix1 + m4[0, 0]
        val y = crpix2 + m4[1, 0]

        return Pair(x, y)
    }

    private fun phiRange(phi: Double): Double {
        val TWO_PI = Math.PI * 2
        var phiCorrect: Double = phi % TWO_PI
        if (phiCorrect > Math.PI) {
            phiCorrect -= TWO_PI
        } else if (phiCorrect < -Math.PI) {
            phiCorrect += TWO_PI
        }
        return phiCorrect
    }

    fun projectInverse(phi: Double, theta: Double): Pair<Double, Double> {
        val s = sin(theta)
//        if (NumericalUtility.equal(s, 0)) {
//            throw new PixelBeyondProjectionException(this, toDegrees(phi), toDegrees(theta), false);
//        }
        val r_theta = cos(theta) / s
        val x: Double = computeX(r_theta, phi)
        val y: Double = computeY(r_theta, phi)
        return Pair(Math.toDegrees(x), Math.toDegrees(y))
    }

    private fun computeX(radius: Double, phi: Double): Double {
        return radius * sin(phi)
    }

    private fun computeY(radius: Double, phi: Double): Double {
        return -radius * cos(phi)
    }

    fun convertDegreeToLength(degree: Double): Double {
        return (cd1_1 * degree + cd2_1 * degree) / (cd1_1 * cd2_2 - cd1_2 * cd2_1)
    }

}