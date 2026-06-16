package studio.freestyle.labs.danjiangsunseeker.domain.premium

/**
 * 可「看廣告免費解鎖」的頁面 — 每頁的暫時解鎖**分開計算**（各自獨立計時）。
 *
 * 注意：地圖 / AR 沒有自己的解鎖入口（無月亮切換 chip），改為「任一頁已解鎖即視為解鎖」
 * （見 [PremiumGate.isAnyUnlocked]），因此不在此列舉中。
 *
 * [key] 用於 DataStore 的偏好鍵；請勿任意更動，以免既有解鎖狀態失效。
 */
enum class PremiumPage(val key: String) {
    HOTSPOTS("hotspots"),
    SIMULATOR("simulator"),
    CALENDAR("calendar"),
}
