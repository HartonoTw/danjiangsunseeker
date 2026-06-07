package studio.freestyle.labs.danjiangsunseeker.data.astro

import studio.freestyle.labs.danjiangsunseeker.domain.physics.TideHarmonics.Constituent
import studio.freestyle.labs.danjiangsunseeker.domain.physics.TideHarmonics.Station

/**
 * 內建潮汐測站調和常數。
 *
 * ⚠️ 校正提醒 (比照 [studio.freestyle.labs.danjiangsunseeker.domain.model.BridgeTower] 座標
 *    「通車後需以實測 GPS 校正」之慣例)：
 *    以下淡水站振幅 (A) 與遲角 (g) 為**估計初值**，數量級依台灣西北岸半日潮為主、潮差大
 *    (大潮可達 ~3m) 之特性設定。**正式發佈前需以交通部港灣技術研究中心 / 中央氣象署
 *    觀測潮位回歸校正**，使高/低潮時刻與實測對齊 (目標 ±10–20 分)。所有待校正數值集中於此檔。
 *
 * Z0 (meanSeaLevelMeters) 為平均海平面，本 APP 以此為潮位原點 (顯示值可為負，代表低於平均海平面)。
 */
object TideStations {

    /** 淡水 (河口) 潮汐測站 — 大橋拍攝點之代表測站。 */
    val TAMSUI: Station = Station(
        name = "淡水",
        meanSeaLevelMeters = 0.0,
        constituents = listOf(
            // 半日潮 (semidiurnal) — 淡水以 M2 為絕對主導
            Constituent("M2", amplitudeMeters = 1.05, phaseDegrees = 120.0),
            Constituent("S2", amplitudeMeters = 0.36, phaseDegrees = 165.0),
            Constituent("N2", amplitudeMeters = 0.22, phaseDegrees = 100.0),
            Constituent("K2", amplitudeMeters = 0.10, phaseDegrees = 162.0),
            // 全日潮 (diurnal)
            Constituent("K1", amplitudeMeters = 0.30, phaseDegrees = 200.0),
            Constituent("O1", amplitudeMeters = 0.24, phaseDegrees = 175.0),
            Constituent("P1", amplitudeMeters = 0.10, phaseDegrees = 198.0),
            Constituent("Q1", amplitudeMeters = 0.05, phaseDegrees = 160.0),
        ),
    )
}
