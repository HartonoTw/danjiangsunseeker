package studio.freestyle.labs.danjiangsunseeker.presentation.common

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 依付費狀態切換桌面（launcher）圖示：免費版 alias ↔ 尊榮版（Pro）alias。
 *
 * 兩個 `<activity-alias>` 各自掛 LAUNCHER intent-filter 但同時只啟用一個，避免桌面出現兩個圖示。
 * 啟用/停用以 [PackageManager.setComponentEnabledSetting] 在執行期切換。
 *
 * Note: alias 類別名以 **namespace** 為基準（即使 debug build 的 applicationId 帶 `.debug` 後綴，
 *   元件類名仍是 namespace），因此這裡硬編 namespace 全名；packageName 則用執行期的 [Context.getPackageName]。
 */
object ProIconManager {

    private const val ALIAS_DEFAULT = "studio.freestyle.labs.danjiangsunseeker.MainActivityDefault"
    private const val ALIAS_PRO = "studio.freestyle.labs.danjiangsunseeker.MainActivityPro"

    /** 套用桌面圖示：[pro] = true 顯示尊榮版圖示，否則顯示預設圖示。已是目標狀態則不動作。 */
    fun apply(context: Context, pro: Boolean) {
        val pm = context.packageManager
        val pkg = context.packageName
        val enableName = if (pro) ALIAS_PRO else ALIAS_DEFAULT
        val disableName = if (pro) ALIAS_DEFAULT else ALIAS_PRO

        val enableComponent = ComponentName(pkg, enableName)
        // 目標 alias 已是「明確啟用」就略過，避免重複呼叫（每次切換可能讓 launcher 重整）。
        if (pm.getComponentEnabledSetting(enableComponent) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return
        }
        pm.setComponentEnabledSetting(
            ComponentName(pkg, disableName),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        pm.setComponentEnabledSetting(
            enableComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
