package studio.freestyle.labs.danjiangsunseeker.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import studio.freestyle.labs.danjiangsunseeker.R
import studio.freestyle.labs.danjiangsunseeker.domain.premium.PremiumPage

/**
 * 「月亮」模式切換 chip。鎖定時右下角疊一個小鎖頭🔒，點擊改觸發 [onClick]（呼叫端負責跳出解鎖視窗）。
 *
 * @param selected 是否選中（鎖定時一律視為未選）
 * @param locked   是否未解鎖（顯示鎖頭）
 * @param onClick  解鎖時：切到月亮模式；鎖定時：開啟解鎖視窗
 * @param labelRes chip 文字（各頁略有不同：月亮 / 月亮日）
 */
@Composable
fun MoonModeChip(
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    labelRes: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        FilterChip(
            selected = selected && !locked,
            onClick = onClick,
            label = { Text(stringResource(labelRes)) },
        )
        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-2).dp, y = (-2).dp)
                    .size(15.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.premium_status_locked),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

/**
 * 解鎖視窗的便捷宿主：自帶 [PremiumViewModel]，處理升級/看廣告動作與提示 toast。
 * 各頁只需保留一個 `showUnlockDialog` 布林，鎖定的月亮 chip 點擊時設為 true。
 *
 * @param page 此頁對應的解鎖目標（每頁分開計算）。看廣告播畢後只解鎖該頁。
 */
@Composable
fun PremiumUnlockDialogHost(
    visible: Boolean,
    page: PremiumPage,
    onDismiss: () -> Unit,
    vm: PremiumViewModel = hiltViewModel(),
) {
    if (!visible) return
    val ctx = LocalContext.current
    PremiumUnlockDialog(
        onDismiss = onDismiss,
        onUpgrade = {
            vm.upgradeToPro()
            Toast.makeText(ctx, ctx.getString(R.string.premium_unlocked_pro_toast), Toast.LENGTH_SHORT).show()
            onDismiss()
        },
        onWatchAd = {
            val activity = ctx.findActivity()
            if (activity == null) {
                Toast.makeText(ctx, ctx.getString(R.string.premium_ad_unavailable), Toast.LENGTH_SHORT).show()
            } else {
                // 真正載入並播放獎勵式影片廣告；看完（獲得獎勵）才解鎖該頁。
                RewardedAdLoader.loadAndShow(
                    activity = activity,
                    onReward = {
                        vm.unlockViaAd(page)
                        Toast.makeText(ctx, ctx.getString(R.string.premium_unlocked_ad_toast), Toast.LENGTH_SHORT).show()
                    },
                    onFailed = {
                        Toast.makeText(ctx, ctx.getString(R.string.premium_ad_unavailable), Toast.LENGTH_SHORT).show()
                    },
                )
            }
            onDismiss()
        },
    )
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * 月相/潮汐功能解鎖視窗：升級專業版（主按鈕）或看廣告免費解鎖（次按鈕），右上角可關閉。
 */
@Composable
fun PremiumUnlockDialog(
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit,
    onWatchAd: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 18.dp)) {
                // 右上角關閉
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.premium_dialog_dismiss),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.premium_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.premium_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(20.dp))

                // 主按鈕：升級專業版
                Button(
                    onClick = onUpgrade,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.premium_dialog_upgrade),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.premium_dialog_upgrade_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 次按鈕：看廣告免費解鎖（顏色較淡）
                OutlinedButton(
                    onClick = onWatchAd,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.premium_dialog_watch_ad),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.premium_dialog_watch_ad_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.premium_dialog_dismiss),
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
