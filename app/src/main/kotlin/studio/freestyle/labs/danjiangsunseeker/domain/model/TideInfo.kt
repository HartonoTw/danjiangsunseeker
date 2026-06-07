package studio.freestyle.labs.danjiangsunseeker.domain.model

import java.time.LocalDate
import java.time.ZonedDateTime

/** 高潮 / 低潮。 */
enum class TideKind { HIGH, LOW }

/**
 * 一次潮汐極值 (高潮或低潮)。
 *
 * @param heightMeters 相對於測站基準面 (本 APP 以平均海平面 Z0 為原點) 的潮位
 */
data class TideExtreme(
    val time: ZonedDateTime,
    val heightMeters: Double,
    val kind: TideKind,
)

/**
 * 某日某測站的潮汐資訊 (調和分析推算)。時間皆為 Asia/Taipei 區。
 *
 * @param extremes 當日高低潮，依時間排序
 * @param currentHeightMeters 對「現在」的潮位 (僅日期為今天時有意義)；否則 null
 * @param rising true = 漲潮中、false = 退潮中；無法判定時 null
 */
data class TideInfo(
    val date: LocalDate,
    val extremes: List<TideExtreme>,
    val currentHeightMeters: Double?,
    val rising: Boolean?,
)
