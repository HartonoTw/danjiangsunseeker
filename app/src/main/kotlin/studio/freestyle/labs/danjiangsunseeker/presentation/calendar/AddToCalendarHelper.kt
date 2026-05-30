package studio.freestyle.labs.danjiangsunseeker.presentation.calendar

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import studio.freestyle.labs.danjiangsunseeker.R
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.GoldenDate
import java.time.ZoneId

/**
 * 透過 CalendarContract.ACTION_INSERT Intent 開啟系統行事曆 UI 預填日落拍攝事件。
 *
 * Why Intent 而非直接寫入: 避免要 WRITE_CALENDAR 權限與處理使用者的多帳號。Intent 方式
 * 把控制權交給系統行事曆 App (Google Calendar / 內建)，使用者按確認才會真正存入。
 */
object AddToCalendarHelper {

    /**
     * 把 [golden] 轉為行事曆事件 Intent (時間 = 日落前 30 分到日落後 15 分；地點 = 熱點名稱)。
     */
    fun buildIntent(context: Context, golden: GoldenDate): Intent {
        val tz = ZoneId.of("Asia/Taipei")
        val sunset = golden.sunsetTime ?: golden.date.atTime(18, 30).atZone(tz)
        val startMillis = sunset.minusMinutes(30).toInstant().toEpochMilli()
        val endMillis = sunset.plusMinutes(15).toInstant().toEpochMilli()

        val name = golden.hotspot.nameRes?.let { context.getString(it) }
            ?: golden.hotspot.customName.orEmpty()
        val title = context.getString(R.string.calendar_event_title, name)
        val description = buildString {
            appendLine(context.getString(R.string.calendar_event_observer, name))
            appendLine(context.getString(R.string.calendar_event_sunset_time, sunset.toLocalTime().toString()))
            appendLine(context.getString(R.string.calendar_event_sunset_az, "%.2f".format(golden.sunsetAzimuthDegrees)))
            appendLine(context.getString(R.string.calendar_event_tower_bearing, "%.2f".format(golden.towerBearingDegrees)))
            appendLine(context.getString(R.string.calendar_event_offset, "%+.2f".format(golden.alignmentOffsetDegrees)))
            append(context.getString(R.string.calendar_event_footer))
        }

        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.Events.EVENT_LOCATION, "${golden.hotspot.position.latitude}, ${golden.hotspot.position.longitude}")
            putExtra(CalendarContract.Events.HAS_ALARM, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
