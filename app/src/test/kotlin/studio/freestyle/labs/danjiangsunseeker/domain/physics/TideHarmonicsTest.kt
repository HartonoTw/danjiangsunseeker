package studio.freestyle.labs.danjiangsunseeker.domain.physics

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import studio.freestyle.labs.danjiangsunseeker.data.astro.TideStations
import java.time.LocalDate
import java.time.ZoneId

/**
 * 調和分析引擎的「內部一致性」驗證。
 *
 * 因內建淡水站常數為待校正初值 (見 TideStations)，這裡不驗絕對潮時，而驗物理一致性：
 *  - 半日潮為主 → 一天約 3–4 個高低潮極值
 *  - 高潮高度 > 低潮高度、且高低潮交替
 *  - heightMeters 連續；isRising 與相鄰取樣方向一致
 */
class TideHarmonicsTest {

    private val tz = ZoneId.of("Asia/Taipei")
    private val station = TideStations.TAMSUI
    private val date = LocalDate.of(2025, 6, 21)

    @Test
    fun `semidiurnal tide yields three to five extremes per day`() {
        val extremes = TideHarmonics.dailyExtremes(station, date, tz)
        assertThat(extremes.size).isIn(3..5)
    }

    @Test
    fun `extremes alternate between high and low`() {
        val extremes = TideHarmonics.dailyExtremes(station, date, tz)
        for (i in 1 until extremes.size) {
            assertThat(extremes[i].high).isNotEqualTo(extremes[i - 1].high)
        }
    }

    @Test
    fun `high extremes are higher than low extremes`() {
        val extremes = TideHarmonics.dailyExtremes(station, date, tz)
        val highs = extremes.filter { it.high }.map { it.heightMeters }
        val lows = extremes.filter { !it.high }.map { it.heightMeters }
        assertThat(highs).isNotEmpty()
        assertThat(lows).isNotEmpty()
        assertThat(highs.min()).isGreaterThan(lows.max())
    }

    @Test
    fun `extremes are time-ordered and fall on the requested date`() {
        val extremes = TideHarmonics.dailyExtremes(station, date, tz)
        for (i in 1 until extremes.size) {
            assertThat(extremes[i].time).isGreaterThan(extremes[i - 1].time)
        }
        extremes.forEach { assertThat(it.time.toLocalDate()).isEqualTo(date) }
    }

    @Test
    fun `isRising agrees with the sign of the local height gradient`() {
        val t = date.atTime(9, 0).atZone(tz)
        val before = TideHarmonics.heightMeters(station, t.minusMinutes(6))
        val after = TideHarmonics.heightMeters(station, t.plusMinutes(6))
        val rising = TideHarmonics.isRising(station, t)
        assertThat(rising).isEqualTo(after > before)
    }

    @Test
    fun `tidal range is physically plausible for Tamsui (large semidiurnal)`() {
        val extremes = TideHarmonics.dailyExtremes(station, date, tz)
        val range = extremes.maxOf { it.heightMeters } - extremes.minOf { it.heightMeters }
        // 淡水潮差大；初值常數下單日潮差應落在 1–6 m 的合理量級
        assertThat(range).isGreaterThan(1.0)
        assertThat(range).isLessThan(6.0)
    }
}
