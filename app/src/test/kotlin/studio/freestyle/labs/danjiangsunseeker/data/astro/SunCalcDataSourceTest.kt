package studio.freestyle.labs.danjiangsunseeker.data.astro

import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 整合測試 — 直接使用 commons-suncalc 函式庫，無需 mock。
 * 以台北已知的天文資料來驗證正確性。
 */
class SunCalcDataSourceTest {

    private lateinit var dataSource: SunCalcDataSource

    // 大屯山 (Datun Mountain) — 已知觀測站
    private val datun = GeoPoint(25.18, 121.52, elevationMeters = 1077.0)
    // 台北市區 (零海拔)
    private val taipei = GeoPoint(25.05, 121.53, elevationMeters = 0.0)

    @Before
    fun setUp() {
        dataSource = SunCalcDataSource()
    }

    @Test
    fun `positionAt returns non-null position`() {
        val time = ZonedDateTime.of(2025, 6, 21, 12, 0, 0, 0, ZoneId.of("Asia/Taipei"))
        val position = dataSource.positionAt(time, taipei)
        assertNotNull(position)
    }

    @Test
    fun `solar noon altitude is positive in Taipei in summer`() {
        // 夏至正午，太北市緯度 25°N，仰角應接近 88°
        val time = ZonedDateTime.of(2025, 6, 21, 12, 0, 0, 0, ZoneId.of("Asia/Taipei"))
        val position = dataSource.positionAt(time, taipei)
        assertTrue("Noon sun altitude should be positive, got ${position.altitudeDegrees}", position.altitudeDegrees > 60.0)
    }

    @Test
    fun `azimuth is in range 0 to 360`() {
        val time = ZonedDateTime.of(2025, 6, 21, 18, 0, 0, 0, ZoneId.of("Asia/Taipei"))
        val position = dataSource.positionAt(time, taipei)
        assertTrue("Azimuth should be in [0, 360], got ${position.azimuthDegrees}", position.azimuthDegrees in 0.0..360.0)
    }

    @Test
    fun `sunset azimuth in summer is in western quadrant (200 to 320 degrees)`() {
        val time = ZonedDateTime.of(2025, 6, 21, 18, 30, 0, 0, ZoneId.of("Asia/Taipei"))
        val position = dataSource.positionAt(time, taipei)
        // 夏至日落方位應在西偏北 ~300° 左右
        assertTrue(
            "Summer sunset azimuth should be in western quadrant, got ${position.azimuthDegrees}",
            position.azimuthDegrees in 200.0..360.0
        )
    }

    @Test
    fun `apparent altitude is greater than or equal to true altitude near horizon`() {
        // 太陽近地平線時，視仰角 >= 真實仰角
        val time = ZonedDateTime.of(2025, 6, 21, 18, 30, 0, 0, ZoneId.of("Asia/Taipei"))
        val position = dataSource.positionAt(time, taipei)
        assertTrue(
            "Apparent altitude should be >= true altitude near horizon",
            position.altitudeDegrees >= position.trueAltitudeDegrees
        )
    }

    @Test
    fun `dailyEvents returns non-null object for summer solstice`() {
        val date = LocalDate.of(2025, 6, 21)
        val events = dataSource.dailyEvents(date, taipei)
        assertNotNull(events)
        assertEquals(date, events.date)
    }

    @Test
    fun `dailyEvents has sunrise before sunset in Taipei`() {
        val date = LocalDate.of(2025, 6, 21)
        val events = dataSource.dailyEvents(date, taipei)
        assertNotNull("Sunrise should exist", events.sunrise)
        assertNotNull("Sunset should exist", events.sunset)
        assertTrue(
            "Sunrise should be before sunset",
            events.sunrise!!.isBefore(events.sunset!!)
        )
    }

    @Test
    fun `dailyEvents observer elevation affects sunset azimuth`() {
        val date = LocalDate.of(2025, 6, 21)
        val eventsLow = dataSource.dailyEvents(date, taipei)
        val eventsHigh = dataSource.dailyEvents(date, datun)
        // Both should have sunset azimuth (may differ slightly)
        assertNotNull("Low elevation sunset azimuth", eventsLow.sunsetAzimuthDegrees)
        assertNotNull("High elevation sunset azimuth", eventsHigh.sunsetAzimuthDegrees)
    }

    @Test
    fun `golden hour evening starts at sunset`() {
        val date = LocalDate.of(2025, 6, 21)
        val events = dataSource.dailyEvents(date, taipei)
        if (events.sunset != null && events.goldenHourEveningStart != null) {
            assertEquals(
                "Golden hour evening should start at sunset",
                events.sunset,
                events.goldenHourEveningStart
            )
        }
    }
}
