package studio.freestyle.labs.danjiangsunseeker.domain.physics

import kotlin.math.tan

/**
 * 大氣折射修正。
 *
 * Why: 太陽近地平線時，大氣折射讓「視位置」比「幾何位置」抬高約 0.57°（相當於一個太陽直徑）。
 * 這是「夕陽穿塔」預測誤差的最大來源 — 沒做這個修正會讓 APP 預報誤差到 ~1 公里的拍攝位置。
 *
 * commons-suncalc 預設已內含此修正並回傳 apparent altitude；本物件提供獨立可測算式：
 *
 * **Bennett 公式** (Bennett, 1982)：從真實 (幾何) 高度算折射量
 *     R = cot( h + 7.31 / (h + 4.4) )  [arc minutes], h 為度
 *
 * **Sæmundsson 公式**：從視高度反推折射，常用於從觀測資料反推真實位置。
 */
object AtmosphericRefraction {

    private const val DEG_TO_RAD = Math.PI / 180.0

    /**
     * 給定真實仰角 [trueAltitudeDegrees]，回傳大氣折射造成的視位置抬升量（度）。
     * 公式有效範圍約 -1° 至 +90°；低於 -1° 已大幅沉入地平線下，折射效應失去定義。
     */
    fun refractionFromTrue(trueAltitudeDegrees: Double): Double {
        if (trueAltitudeDegrees < -1.0) return 0.0
        val h = trueAltitudeDegrees
        // Bennett: arcminutes
        val arcMin = 1.0 / tan(DEG_TO_RAD * (h + 7.31 / (h + 4.4)))
        return arcMin / 60.0
    }

    /**
     * 給定視仰角 [apparentAltitudeDegrees]，回傳折射量（度）。Sæmundsson 公式。
     */
    fun refractionFromApparent(apparentAltitudeDegrees: Double): Double {
        if (apparentAltitudeDegrees < -1.0) return 0.0
        val h = apparentAltitudeDegrees
        // Sæmundsson, arc minutes
        val arcMin = 1.02 / tan(DEG_TO_RAD * (h + 10.3 / (h + 5.11)))
        return arcMin / 60.0
    }

    /** 由幾何仰角算視仰角 (含折射修正)。 */
    fun apparentFromTrue(trueAltitudeDegrees: Double): Double =
        trueAltitudeDegrees + refractionFromTrue(trueAltitudeDegrees)

    /** 由視仰角算幾何仰角 (扣除折射)。 */
    fun trueFromApparent(apparentAltitudeDegrees: Double): Double =
        apparentAltitudeDegrees - refractionFromApparent(apparentAltitudeDegrees)
}
