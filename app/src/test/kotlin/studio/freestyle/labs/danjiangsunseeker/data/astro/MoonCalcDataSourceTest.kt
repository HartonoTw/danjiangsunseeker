package studio.freestyle.labs.danjiangsunseeker.data.astro

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import studio.freestyle.labs.danjiangsunseeker.domain.model.GeoPoint
import studio.freestyle.labs.danjiangsunseeker.domain.model.LunarPhase
import java.time.LocalDate

/**
 * 整合測試 — 直接使用 commons-suncalc，驗證月相計算與盈虧判斷。
 *
 * 不依賴特定已知日期，改以「掃描一個朔望月 (~30 天)」的方式驗證：
 *  - 該區間必出現滿月 (亮面≈1) 與新月 (亮面≈0)
 *  - 亮面最大日應分類為 FULL；亮面最小日應分類為 NEW
 *  - 盈 (waxing) 與虧 (waning) 兩種狀態都會出現
 */
class MoonCalcDataSourceTest {

    private lateinit var dataSource: MoonCalcDataSource
    private val tamsui = GeoPoint(25.17531, 121.41778, 0.0)

    @Before
    fun setUp() {
        dataSource = MoonCalcDataSource()
    }

    @Test
    fun `fraction lit stays within zero to one`() {
        var d = LocalDate.of(2025, 1, 1)
        repeat(40) {
            val f = dataSource.dailyMoon(d, tamsui).fractionLit
            assertThat(f).isAtLeast(0.0)
            assertThat(f).isAtMost(1.0)
            d = d.plusDays(1)
        }
    }

    @Test
    fun `a synodic month contains both a full moon and a new moon`() {
        val infos = (0 until 30).map { dataSource.dailyMoon(LocalDate.of(2025, 3, 1).plusDays(it.toLong()), tamsui) }
        val maxLit = infos.maxBy { it.fractionLit }
        val minLit = infos.minBy { it.fractionLit }

        assertThat(maxLit.fractionLit).isGreaterThan(0.97)
        assertThat(minLit.fractionLit).isLessThan(0.05)
        assertThat(maxLit.phase).isEqualTo(LunarPhase.FULL)
        assertThat(minLit.phase).isEqualTo(LunarPhase.NEW)
    }

    @Test
    fun `both waxing and waning days occur within a month`() {
        val infos = (0 until 30).map { dataSource.dailyMoon(LocalDate.of(2025, 3, 1).plusDays(it.toLong()), tamsui) }
        assertThat(infos.any { it.waxing }).isTrue()
        assertThat(infos.any { !it.waxing }).isTrue()
    }

    @Test
    fun `moon position azimuth is within zero to 360`() {
        val t = LocalDate.of(2025, 6, 21).atTime(22, 0).atZone(java.time.ZoneId.of("Asia/Taipei"))
        val pos = dataSource.moonPositionAt(t, tamsui)
        assertThat(pos.azimuthDegrees).isAtLeast(0.0)
        assertThat(pos.azimuthDegrees).isAtMost(360.0)
        assertThat(pos.distanceKm).isGreaterThan(300_000.0) // 月地距離 ~356k–406k km
    }
}
