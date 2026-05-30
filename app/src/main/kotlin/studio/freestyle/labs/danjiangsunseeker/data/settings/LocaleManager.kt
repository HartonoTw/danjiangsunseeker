package studio.freestyle.labs.danjiangsunseeker.data.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** App 內語言選項。SYSTEM 代表沿用裝置 / 系統 per-app 語系。 */
enum class AppLanguage(val tag: String) {
    SYSTEM("system"),
    CHINESE("zh"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * 應用內語言管理：以 SharedPreferences 儲存使用者選擇（同步讀取，供 attachBaseContext 使用），
 * 並可覆寫裝置語系。SYSTEM 時不覆寫、沿用裝置語系。
 */
object LocaleManager {
    private const val PREFS = "locale_prefs"
    private const val KEY_LANG = "app_language"

    fun getLanguage(context: Context): AppLanguage {
        // 注意：不可用 context.applicationContext —— Application.attachBaseContext 階段它仍為 null。
        // 傳入的 base context 本身即可提供 SharedPreferences。
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppLanguage.fromTag(prefs.getString(KEY_LANG, AppLanguage.SYSTEM.tag))
    }

    /** 儲存選擇並立即套用到 application context 的資源（讓 @ApplicationContext.getString 跟著更新）。 */
    fun setLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, language.tag)
            .apply()
        applyToResources(context.applicationContext ?: context, language)
    }

    /** 依設定包裝 context；SYSTEM 時回傳原 context（沿用裝置語系）。 */
    fun wrap(context: Context): Context {
        val language = getLanguage(context)
        if (language == AppLanguage.SYSTEM) return context
        val locale = localeOf(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun applyToResources(context: Context, language: AppLanguage) {
        val locale = if (language == AppLanguage.SYSTEM) {
            Resources.getSystem().configuration.locales[0]
        } else {
            localeOf(language)
        }
        Locale.setDefault(locale)
        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
    }

    private fun localeOf(language: AppLanguage): Locale = when (language) {
        AppLanguage.ENGLISH -> Locale.ENGLISH
        AppLanguage.CHINESE -> Locale.TRADITIONAL_CHINESE
        AppLanguage.SYSTEM -> Locale.getDefault()
    }
}
