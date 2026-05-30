package studio.freestyle.labs.danjiangsunseeker

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import studio.freestyle.labs.danjiangsunseeker.data.notifications.DailyScoreWorker
import studio.freestyle.labs.danjiangsunseeker.data.notifications.NotificationHelper
import studio.freestyle.labs.danjiangsunseeker.data.settings.LocaleManager
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import javax.inject.Inject

@HiltAndroidApp
class DanjiangApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.wrap(base))
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // MapLibre 不需金鑰；apiKey 傳 null。WellKnownTileServer.MapLibre 用其
        // 預設遙測 endpoint (可進一步透過 TelemetryEnabledChangeListener 關閉)。
        MapLibre.getInstance(this, null, WellKnownTileServer.MapLibre)

        // 通知頻道 + 每日掃描排程
        NotificationHelper.ensureChannel(this)
        DailyScoreWorker.schedule(this)
    }
}
