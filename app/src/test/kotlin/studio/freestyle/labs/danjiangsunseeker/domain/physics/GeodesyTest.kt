package studio.freestyle.labs.danjiangsunseeker.domain.physics

import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeodesyTest {

    // 淡江大橋主塔 (BridgeTower position approximation)
    private val tower = GeoPoint(25.1636, 121.4348, elevationMeters = 0.0)
    // 八里渡船頭
    private val bali = GeoPoint(25.1542, 121.4082, elevationMeters = 0.0)
    // 大屯山
    private val datun = GeoPoint(25.1797, 121.5210, elevationMeters = 1077.0)

    @Test
    fun `inverse of same point returns zero distance`() {
        val result = Geodesy.inverse(tower, tower)
        assertEquals(0.0, result.distanceMeters, 0.01)
    }

    @Test
    fun `haversine distance between tower and bali is approximately 2_5 km`() {
        val dist = Geodesy.haversineDistanceMeters(tower, bali)
        // 八里渡船頭到主塔約 2.5 km
        assertEquals(2500.0, dist, 300.0)
    }

    @Test
    fun `vincenty distance between tower and datun is approximately 8_5 km`() {
        val result = Geodesy.inverse(tower, datun)
        // 淡江大橋到大屯山約 8-9 km
        assertEquals(8500.0, result.distanceMeters, 1000.0)
    }

    @Test
    fun `bearing from tower to datun is roughly eastward (60-90 degrees)`() {
        val result = Geodesy.inverse(tower, datun)
        // 大屯山在主塔的東北偏東方向
        assertTrue(
            "Bearing should be roughly northeast, got ${result.initialBearingDegrees}",
            result.initialBearingDegrees in 30.0..120.0
        )
    }

    @Test
    fun `signedAzimuthDelta returns zero for identical azimuths`() {
        assertEquals(0.0, Geodesy.signedAzimuthDelta(270.0, 270.0), 0.001)
    }

    @Test
    fun `signedAzimuthDelta wraps correctly across 360`() {
        val delta = Geodesy.signedAzimuthDelta(5.0, 355.0)
        assertEquals(10.0, delta, 0.001)
    }

    @Test
    fun `signedAzimuthDelta is in range -180 to 180`() {
        val delta = Geodesy.signedAzimuthDelta(270.0, 90.0)
        assertTrue(delta in -180.0..180.0)
    }

    @Test
    fun `direct then inverse roundtrip recovers original distance`() {
        val distance = 5000.0
        val bearing = 270.0
        val endpoint = Geodesy.direct(tower, bearing, distance)
        val result = Geodesy.inverse(tower, endpoint)
        assertEquals(distance, result.distanceMeters, 1.0)
    }

    @Test
    fun `initialBearingDegrees is in range 0 to 360`() {
        val bearing = Geodesy.initialBearingDegrees(tower, datun)
        assertTrue(bearing in 0.0..360.0)
    }
}
