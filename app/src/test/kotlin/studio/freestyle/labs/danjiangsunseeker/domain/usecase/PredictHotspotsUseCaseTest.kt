package studio.freestyle.labs.danjiangsunseeker.domain.usecase

import studio.freestyle.labs.danjiangsunseeker.data.astro.MoonCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.data.astro.SunCalcDataSource
import studio.freestyle.labs.danjiangsunseeker.data.astro.TideDataSource
import studio.freestyle.labs.danjiangsunseeker.domain.model.DailySunEvents
import studio.freestyle.labs.danjiangsunseeker.domain.model.DefaultHotspots
import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import studio.freestyle.labs.danjiangsunseeker.domain.model.Hotspot
import studio.freestyle.labs.danjiangsunseeker.domain.model.SunPosition
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PredictHotspotsUseCaseTest {

    private val sunCalc: SunCalcDataSource = mockk()
    private val moonCalc: MoonCalcDataSource = mockk(relaxed = true)
    private val tideDataSource: TideDataSource = mockk(relaxed = true)
    private val targetSunResolver: TowerTargetSunResolver = mockk()
    private val targetMoonResolver: TowerTargetMoonResolver = mockk(relaxed = true)
    private lateinit var useCase: PredictHotspotsUseCase

    private val testDate = LocalDate.of(2025, 6, 21)
    private val tz = ZoneId.of("Asia/Taipei")

    private fun fakeSunEvents(
        observer: GeoPoint,
        sunsetAzimuth: Double? = 285.0
    ): DailySunEvents {
        val sunset = testDate.atTime(18, 45).atZone(tz)
        return DailySunEvents(
            date = testDate,
            observer = observer,
            sunrise = testDate.atTime(5, 12).atZone(tz),
            sunset = sunset,
            solarNoon = testDate.atTime(12, 0).atZone(tz),
            goldenHourMorningStart = null,
            goldenHourMorningEnd = null,
            goldenHourEveningStart = sunset,
            goldenHourEveningEnd = sunset.plusMinutes(30),
            blueHourEveningStart = sunset.plusMinutes(20),
            blueHourEveningEnd = sunset.plusMinutes(50),
            sunsetAzimuthDegrees = sunsetAzimuth,
        )
    }

    private val fakeSunPosition = SunPosition(
        time = testDate.atTime(18, 45).atZone(ZoneId.of("Asia/Taipei")),
        azimuthDegrees = 285.0,
        altitudeDegrees = 1.0,
        trueAltitudeDegrees = 0.5,
    )

    @Before
    fun setUp() {
        useCase = PredictHotspotsUseCase(sunCalc, moonCalc, tideDataSource, targetSunResolver, targetMoonResolver)
        // positionAt is called for sun trail computation
        every { sunCalc.positionAt(any(), any()) } returns fakeSunPosition
    }

    private fun stubTarget(observer: GeoPoint, azimuth: Double? = 285.0) {
        every { targetSunResolver.resolve(testDate, observer, any()) } returns TowerTargetSunEvent(
            time = testDate.atTime(18, 45).atZone(tz),
            azimuthDegrees = azimuth,
            altitudeDegrees = 1.0,
            targetAltitudeDegrees = 1.0,
        )
    }

    @Test
    fun `returns one prediction per hotspot`() {
        DefaultHotspots.ALL.forEach { hotspot ->
            every { sunCalc.dailyEvents(testDate, hotspot.position) } returns
                fakeSunEvents(hotspot.position)
            stubTarget(hotspot.position)
        }
        val predictions = useCase(testDate)
        assertEquals(DefaultHotspots.ALL.size, predictions.size)
    }

    @Test
    fun `each prediction references the correct hotspot`() {
        DefaultHotspots.ALL.forEach { hotspot ->
            every { sunCalc.dailyEvents(testDate, hotspot.position) } returns
                fakeSunEvents(hotspot.position)
            stubTarget(hotspot.position)
        }
        val predictions = useCase(testDate)
        predictions.forEach { assertNotNull(it.hotspot) }
    }

    @Test
    fun `hotspot farther than 25 km is classified TOO_FAR`() {
        // longitude 120.0 is ~50 km west of the bridge tower
        val farHotspot = Hotspot(
            id = "far_test",
            nameRes = null,
            customName = "遠方測試點",
            position = GeoPoint(25.17, 120.0, 0.0),
        )
        every { sunCalc.dailyEvents(testDate, farHotspot.position) } returns
            fakeSunEvents(farHotspot.position)

        val predictions = useCase(testDate, listOf(farHotspot))

        assertEquals(1, predictions.size)
        assertEquals(AlignmentClass.TOO_FAR, predictions[0].classification)
    }

    @Test
    fun `null sunset azimuth yields UNKNOWN classification`() {
        val hotspot = DefaultHotspots.ALL.first()
        every { sunCalc.dailyEvents(testDate, hotspot.position) } returns
            fakeSunEvents(hotspot.position, sunsetAzimuth = null)
        stubTarget(hotspot.position, azimuth = null)

        val predictions = useCase(testDate, listOf(hotspot))

        assertEquals(AlignmentClass.UNKNOWN, predictions[0].classification)
    }

    @Test
    fun `sun trail is empty when there is no sunset`() {
        val hotspot = DefaultHotspots.ALL.first()
        every { sunCalc.dailyEvents(testDate, hotspot.position) } returns
            fakeSunEvents(hotspot.position).copy(sunset = null, sunsetAzimuthDegrees = null)
        every { targetSunResolver.resolve(testDate, hotspot.position, any()) } returns TowerTargetSunEvent(
            time = null,
            azimuthDegrees = null,
            altitudeDegrees = null,
            targetAltitudeDegrees = 1.0,
        )

        val predictions = useCase(testDate, listOf(hotspot))

        assertTrue(predictions[0].lastHourSunTrail.isEmpty())
    }

    @Test
    fun `distance to tower is positive for all default hotspots`() {
        DefaultHotspots.ALL.forEach { hotspot ->
            every { sunCalc.dailyEvents(testDate, hotspot.position) } returns
                fakeSunEvents(hotspot.position)
            stubTarget(hotspot.position)
        }
        useCase(testDate).forEach { prediction ->
            assertTrue(
                "${prediction.hotspot.id} distance should be > 0",
                prediction.distanceToTowerMeters > 0.0,
            )
        }
    }

    @Test
    fun `bearing to tower is in range 0 to 360`() {
        DefaultHotspots.ALL.forEach { hotspot ->
            every { sunCalc.dailyEvents(testDate, hotspot.position) } returns
                fakeSunEvents(hotspot.position)
            stubTarget(hotspot.position)
        }
        useCase(testDate).forEach { prediction ->
            assertTrue(
                "Bearing should be in [0, 360), got ${prediction.bearingToTowerDegrees}",
                prediction.bearingToTowerDegrees in 0.0..360.0,
            )
        }
    }
}
