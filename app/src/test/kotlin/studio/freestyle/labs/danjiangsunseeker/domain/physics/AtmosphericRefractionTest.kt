package studio.freestyle.labs.danjiangsunseeker.domain.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtmosphericRefractionTest {

    @Test
    fun `refractionFromTrue at zero altitude is approximately 0_57 degrees`() {
        val refraction = AtmosphericRefraction.refractionFromTrue(0.0)
        // Bennett formula at h=0: 1/tan(7.31/4.4) arcmin ≈ 34.2 arcmin ≈ 0.57°
        assertEquals(0.57, refraction, 0.05)
    }

    @Test
    fun `refractionFromTrue at 90 degrees is very small`() {
        val refraction = AtmosphericRefraction.refractionFromTrue(90.0)
        assertTrue("Refraction at zenith should be < 0.02°", refraction < 0.02)
    }

    @Test
    fun `refractionFromTrue below minus 1 degree returns zero`() {
        assertEquals(0.0, AtmosphericRefraction.refractionFromTrue(-2.0), 0.0)
        assertEquals(0.0, AtmosphericRefraction.refractionFromTrue(-90.0), 0.0)
    }

    @Test
    fun `refractionFromApparent below minus 1 degree returns zero`() {
        assertEquals(0.0, AtmosphericRefraction.refractionFromApparent(-1.5), 0.0)
    }

    @Test
    fun `apparentFromTrue is greater than true altitude near horizon`() {
        val trueAlt = 1.0
        val apparentAlt = AtmosphericRefraction.apparentFromTrue(trueAlt)
        assertTrue("Apparent altitude should be higher than true altitude", apparentAlt > trueAlt)
    }

    @Test
    fun `trueFromApparent is inverse of apparentFromTrue within tolerance`() {
        val trueAlt = 5.0
        val apparent = AtmosphericRefraction.apparentFromTrue(trueAlt)
        val recovered = AtmosphericRefraction.trueFromApparent(apparent)
        assertEquals(trueAlt, recovered, 0.001)
    }

    @Test
    fun `refraction decreases as altitude increases`() {
        val r0 = AtmosphericRefraction.refractionFromTrue(0.0)
        val r10 = AtmosphericRefraction.refractionFromTrue(10.0)
        val r45 = AtmosphericRefraction.refractionFromTrue(45.0)
        assertTrue(r0 > r10)
        assertTrue(r10 > r45)
    }
}
