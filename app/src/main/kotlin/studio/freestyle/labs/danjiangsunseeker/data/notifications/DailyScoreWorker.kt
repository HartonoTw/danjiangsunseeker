package studio.freestyle.labs.danjiangsunseeker.data.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.ComputeSunsetScoreUseCase
import studio.freestyle.labs.danjiangsunseeker.domain.usecase.ScanGoldenCalendarUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * 每日凌晨 3 點掃描未來 7 天，若任一熱點 alignment ≤ 5° 且分數 ≥ 85 即發送通知。
 * 純離線運算（天氣 API 已移除），不需要網路連線。
 */
@HiltWorker
class DailyScoreWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val scanCalendar: ScanGoldenCalendarUseCase,
    private val computeScore: ComputeSunsetScoreUseCase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        runCatching {
            val today = LocalDate.now(ZoneId.of("Asia/Taipei"))
            val candidates = scanCalendar(
                fromDate = today,
                days = 7,
                maxOffsetDegrees = 5.0,
            )
            // 每一天最多發一則通知（取對齊度最佳的熱點）
            candidates.groupBy { it.date }.forEach { (_, list) ->
                val best = list.minByOrNull { kotlin.math.abs(it.alignmentOffsetDegrees) } ?: return@forEach
                val score = computeScore(best.alignmentOffsetDegrees)
                if (score.overall >= 85.0) {
                    NotificationHelper.postGoldenDate(applicationContext, best)
                }
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        private const val UNIQUE_NAME = "daily_score_worker"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyScoreWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelayMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun initialDelayMillis(): Long {
            val tz = ZoneId.of("Asia/Taipei")
            val now = ZonedDateTime.now(tz)
            var target = now.toLocalDate().atTime(3, 0).atZone(tz)
            if (target.isBefore(now)) target = target.plusDays(1)
            return Duration.between(now, target).toMillis()
        }
    }
}
