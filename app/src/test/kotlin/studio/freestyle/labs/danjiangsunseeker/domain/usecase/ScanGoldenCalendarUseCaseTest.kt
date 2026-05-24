package studio.freestyle.labs.danjiangsunseeker.domain.usecase

import studio.freestyle.labs.danjiangsunseeker.data.astro.SunCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower
import studio.freestyle.labs.danjiangsunseeker.domain.model.DailySunEvents
import studio.freestyle.labs.danjiangsunseeker.domain.model.DefaultHotspots
import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import studio.freestyle.labs.danjiangsunseeker.domain.physics.Geodesy
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class ScanGoldenCalendarUseCaseTest {

    private val sunCalc: SunCalcDataSource = mockk()
    private lateinit var useCase: ScanGoldenCalendarUseCase

    private val fromDate = LocalDate.of(2025, 1, 1)
    private val tz = ZoneId.of("Asia/Taipei")

    private fun fakeEvents(date: LocalDate, observer: GeoPoint, sunsetAzimuth: Double?) =
        DailySunEvents(
            date = date,
            observer = observer,
            sunrise = date.atTime(6, 0).atZone(tz),
            sunset = date.atTime(18, 0).atZone(tz),
            solarNoon = date.atTime(12, 0).atZone(tz),
            goldenHourMorningStart = null,
            goldenHourMorningEnd = null,
            goldenHourEveningStart = date.atTime(18, 0).atZone(tz),
            goldenHourEveningEnd = date.atTime(18, 30).atZone(tz),
            blueHourEveningStart = null,
            blueHourEveningEnd = null,
            sunsetAzimuthDegrees = sunsetAzimuth,
        )

    @Before
    fun setUp() {
        useCase = ScanGoldenCalendarUseCase(sunCalc)
    }

    @Test
    fun `returns empty list when azimuth is far from tower bearing for all days`() {
        val hotspot = DefaultHotspots.ALL.first()
        repeat(7) { d ->
            val date = fromDate.plusDays(d.toLong())
            every { sunCalc.dailyEvents(date, hotspot.position) } returns
                fakeEvents(date, hotspot.position, 200.0) // far from any tower bearing
        }

        val results = useCase(fromDate, days = 7, hotspots = listOf(hotspot))
        assertTrue("No golden dates expected for azimuth 200°", results.isEmpty())
    }

    @Test
    fun `returns a golden date when azimuth exactly matches tower bearing`() {
        val hotspot = DefaultHotspots.ALL.first()
        val towerBearing = Geodesy.inverse(hotspot.position, BridgeTower.position).initialBearingDegrees

        repeat(3) { d ->
            val date = fromDate.plusDays(d.toLong())
            // Only day 0 is aligned
            val azimuth = if (d == 0) towerBearing else towerBearing + 15.0
            every { sunCalc.dailyEvents(date, hotspot.position) } returns
                fakeEvents(date, hotspot.position, azimuth)
        }

        val results = useCase(fromDate, days = 3, maxOffsetDegrees = 2.0, hotspots = listOf(hotspot))

        assertEquals(1, results.size)
        assertEquals(fromDate, results[0].date)
    }

    @Test
    fun `results are sorted by date ascending`() {
        val hotspot = DefaultHotspots.ALL.first()
        val towerBearing = Geodesy.inverse(hotspot.position, BridgeTower.position).initialBearingDegrees

        repeat(10) { d ->
            val date = fromDate.plusDays(d.toLong())
            val azimuth = if (d % 3 == 0) towerBearing else towerBearing + 20.0
            every { sunCalc.dailyEvents(date, hotspot.position) } returns
                fakeEvents(date, hotspot.position, azimuth)
        }

        val results = useCase(fromDate, days = 10, hotspots = listOf(hotspot))

        for (i in 1 until results.size) {
            assertTrue(
                "Results should be sorted by date",
                !results[i].date.isBefore(results[i - 1].date),
            )
        }
    }

    @Test
    fun `wider threshold yields at least as many results as narrow threshold`() {
        val hotspot = DefaultHotspots.ALL.first()
        val towerBearing = Geodesy.inverse(hotspot.position, BridgeTower.position).initialBearingDegrees

        repeat(10) { d ->
            val date = fromDate.plusDays(d.toLong())
            every { sunCalc.dailyEvents(date, hotspot.position) } returns
                fakeEvents(date, hotspot.position, towerBearing + d.toDouble())
        }

        val narrow = useCase(fromDate, days = 10, maxOffsetDegrees = 1.0, hotspots = listOf(hotspot))
        val wide   = useCase(fromDate, days = 10, maxOffsetDegrees = 5.0, hotspots = listOf(hotspot))

        assertTrue("Wide threshold should return >= results than narrow", wide.size >= narrow.size)
    }

    @Test
    fun `every returned golden date has offset within threshold`() {
        val hotspot = DefaultHotspots.ALL.first()
        val towerBearing = Geodesy.inverse(hotspot.position, BridgeTower.position).initialBearingDegrees
        val threshold = 2.0

        repeat(5) { d ->
            val date = fromDate.plusDays(d.toLong())
            val azimuth = if (d == 2) towerBearing + 1.5 else towerBearing + 20.0
            every { sunCalc.dailyEvents(date, hotspot.position) } returns
                fakeEvents(date, hotspot.position, azimuth)
        }

        val results = useCase(fromDate, days = 5, maxOffsetDegrees = threshold, hotspots = listOf(hotspot))

        results.forEach { golden ->
            assertTrue(
                "Offset ${golden.alignmentOffsetDegrees}° exceeds threshold ${threshold}°",
                abs(golden.alignmentOffsetDegrees) <= threshold,
            )
        }
    }

    @Test
    fun `null sunset azimuth day is skipped and not included in results`() {
        val hotspot = DefaultHotspots.ALL.first()
        val towerBearing = Geodesy.inverse(hotspot.position, BridgeTower.position).initialBearingDegrees

        repeat(3) { d ->
            val date = fromDate.plusDays(d.toLong())
            // All days have null azimuth (polar night scenario)
            every { sunCalc.dailyEvents(date, hotspot.position) } returns
                fakeEvents(date, hotspot.position, null)
        }

        val results = useCase(fromDate, days = 3, maxOffsetDegrees = towerBearing, hotspots = listOf(hotspot))
        assertTrue("Days without sunset azimuth should be skipped", results.isEmpty())
    }
}
