package studio.freestyle.labs.danjiangsunseeker.domain.physics

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.cos

/**
 * 潮汐調和分析推算引擎 (offline)。
 *
 * 潮位 h(t) = Z0 + Σ Aᵢ · cos( Vᵢ(t) − gᵢ )
 *   Z0    : 平均海平面 (公尺)，本 APP 以此為高度原點
 *   Aᵢ    : 第 i 個分潮振幅 (公尺)
 *   Vᵢ(t) : 平衡潮天文相角 (度)，由月/日平均經度與平太陰時組合而成
 *   gᵢ    : 分潮遲角 (度)，測站特性，見 [studio.freestyle.labs.danjiangsunseeker.data.astro.TideStations]
 *
 * 涵蓋八個主要分潮 (半日潮 M2/S2/N2/K2、全日潮 K1/O1/P1/Q1)。
 *
 * 天文相角來源:
 *   令 d = 自 J2000.0 (JD 2451545.0) 起算之日數，
 *      s = 月球平均經度、h = 太陽平均經度、p = 月球近地點平均經度 (Meeus 線性近似)，
 *      τ = 平太陰時 = 15°·UT時 + h − s。
 *   各分潮相角由 (τ, s, h, p) 之整數組合 + 90° 修正給出 (Doodson/Schureman)。
 *   八個分潮角速度經此式微分後與標準值一致 (M2=28.9841°/h、K1=15.0411°/h…)。
 *
 * 注意 (與 BridgeTower 座標相同慣例)：本引擎天文相角嚴謹，但 [gᵢ] 為「相對」相位 —
 * 任何整體時間參考框架偏移都會被各分潮 gᵢ 的校正吸收。實測校正後即對齊絕對潮時。
 */
object TideHarmonics {

    private const val DEG2RAD = Math.PI / 180.0

    /** 單一分潮：名稱 + 振幅 (m) + 遲角 (度)。 */
    data class Constituent(val name: String, val amplitudeMeters: Double, val phaseDegrees: Double)

    /** 測站調和常數集合。 */
    data class Station(
        val name: String,
        val meanSeaLevelMeters: Double, // Z0
        val constituents: List<Constituent>,
    )

    /** 計算某瞬間各分潮的平衡潮天文相角 V (度)。key 為分潮名稱。 */
    private fun equilibriumArgsDeg(time: ZonedDateTime): Map<String, Double> {
        val utc = time.withZoneSameInstant(ZoneOffset.UTC)
        // Julian Date (UT) 與自 J2000 起算日數
        val jd = utc.toInstant().toEpochMilli() / 86_400_000.0 + 2_440_587.5
        val d = jd - 2_451_545.0

        // 平均經度 (度)，Meeus 線性項；mod 360 不影響 cos，留待組合
        val s = 218.3164477 + 13.17639648 * d   // 月球
        val h = 280.4664567 + 0.98564736 * d    // 太陽
        val p = 83.3532465 + 0.11140353 * d     // 月球近地點

        // 平太陰時：UT 當日小時 (含小數)
        val hh = utc.hour + utc.minute / 60.0 + utc.second / 3600.0
        val tau = 15.0 * hh + h - s

        return mapOf(
            // 半日潮
            "M2" to 2 * tau,
            "S2" to 2 * tau + 2 * s - 2 * h,
            "N2" to 2 * tau - s + p,
            "K2" to 2 * tau + 2 * s,
            // 全日潮
            "K1" to tau + s + 90.0,
            "O1" to tau - s - 90.0,
            "P1" to tau + s - 2 * h - 90.0,
            "Q1" to tau - 2 * s + p - 90.0,
        )
    }

    /** 推算某瞬間潮位 (公尺，相對 Z0)。 */
    fun heightMeters(station: Station, time: ZonedDateTime): Double {
        val args = equilibriumArgsDeg(time)
        var h = station.meanSeaLevelMeters
        for (c in station.constituents) {
            val v = args[c.name] ?: continue
            h += c.amplitudeMeters * cos((v - c.phaseDegrees) * DEG2RAD)
        }
        return h
    }

    /** 某瞬間是否漲潮中 (對 6 分鐘後比較)。 */
    fun isRising(station: Station, time: ZonedDateTime): Boolean =
        heightMeters(station, time.plusMinutes(6)) > heightMeters(station, time)

    /**
     * 找出指定日期 (該時區整日) 的所有高/低潮極值。
     *
     * 以 6 分鐘間隔取樣 (M2 週期 ~12.42h，遠大於取樣間隔，不會漏極值)，
     * 偵測局部極值後以拋物線頂點內插細化時刻，回傳落在當日的極值。
     */
    fun dailyExtremes(station: Station, date: LocalDate, zone: ZoneId): List<TideExtremeRaw> {
        val stepMin = 6L
        val start = date.atStartOfDay(zone).minusHours(2) // 前後各留 2h 緩衝避免邊界漏抓
        val totalMin = 28L * 60L
        val n = (totalMin / stepMin).toInt()

        val times = ArrayList<ZonedDateTime>(n + 1)
        val heights = DoubleArray(n + 1)
        for (i in 0..n) {
            val t = start.plusMinutes(i * stepMin)
            times.add(t)
            heights[i] = heightMeters(station, t)
        }

        val out = ArrayList<TideExtremeRaw>()
        for (i in 1 until n) {
            val h0 = heights[i - 1]; val h1 = heights[i]; val h2 = heights[i + 1]
            val isMax = h1 > h0 && h1 >= h2
            val isMin = h1 < h0 && h1 <= h2
            if (!isMax && !isMin) continue

            // 拋物線頂點內插：相對 index i 的偏移 (−0.5..0.5 step)
            val denom = h0 - 2 * h1 + h2
            val frac = if (denom != 0.0) 0.5 * (h0 - h2) / denom else 0.0
            val refined = times[i].plusSeconds((frac * stepMin * 60).toLong())
            val height = heightMeters(station, refined)
            if (refined.toLocalDate() == date) {
                out.add(TideExtremeRaw(refined, height, high = isMax))
            }
        }
        return out.sortedBy { it.time }
    }

    /** [dailyExtremes] 的原始輸出 (未綁定 domain model)。 */
    data class TideExtremeRaw(val time: ZonedDateTime, val heightMeters: Double, val high: Boolean)
}
