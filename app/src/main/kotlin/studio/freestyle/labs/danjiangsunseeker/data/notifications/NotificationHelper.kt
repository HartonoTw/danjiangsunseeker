package studio.freestyle.labs.danjiangsunseeker.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import studio.freestyle.labs.danjiangsunseeker.MainActivity
import studio.freestyle.labs.danjiangsunseeker.R
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.GoldenDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object NotificationHelper {

    const val CHANNEL_GOLDEN_SUNSET = "golden_sunset"
    private const val NOTIFICATION_ID_PREFIX = 7000

    /** 在啟動時呼叫，確保通知頻道存在。 */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_GOLDEN_SUNSET) != null) return
        val channel = NotificationChannel(
            CHANNEL_GOLDEN_SUNSET,
            "頂級拍攝日提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "當未來幾天有「夕陽穿塔」黃金機會時推播提醒"
        }
        mgr.createNotificationChannel(channel)
    }

    /** 傳送一個「黃金拍攝日」通知；若使用者未授權 POST_NOTIFICATIONS (Android 13+) 則靜默 skip。 */
    fun postGoldenDate(context: Context, golden: GoldenDate) {
        if (!hasPermission(context)) return
        ensureChannel(context)

        val name = golden.hotspot.nameRes?.let { context.getString(it) }
            ?: golden.hotspot.customName.orEmpty()
        val dateStr = golden.date.format(DateTimeFormatter.ofPattern("M/d (E)", Locale.TAIWAN))
        val timeStr = golden.sunsetTime?.toLocalTime()
            ?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "—"

        val intent = Intent(context, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pending = PendingIntent.getActivity(
            context,
            golden.date.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_GOLDEN_SUNSET)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$dateStr 夕陽穿塔機會")
            .setContentText("$name · 日落 $timeStr · 偏差 ${"%+.2f".format(golden.alignmentOffsetDegrees)}°")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "預測 $dateStr $timeStr 在 $name 可拍到「夕陽穿塔」。\n" +
                        "對齊偏差 ${"%+.2f".format(golden.alignmentOffsetDegrees)}°"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId(golden), notif)
    }

    private fun notificationId(golden: GoldenDate): Int =
        NOTIFICATION_ID_PREFIX + (golden.date.toEpochDay().toInt() % 1000)

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
