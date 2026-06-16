package studio.freestyle.labs.danjiangsunseeker.presentation.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Google 官方「測試」橫幅廣告單元 ID。
 * 上架前須換成 AdMob 後台建立的正式 Banner 單元 ID（搭配 Manifest 的正式 App ID）。
 */
private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

/**
 * 底部自適應 (anchored adaptive) 橫幅廣告。高度依螢幕寬度由 SDK 決定。
 *
 * AdView 以 [remember] 持有，避免重組時重新建立；離開組合時 [AdView.destroy] 釋放資源。
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adWidthDp = LocalConfiguration.current.screenWidthDp

    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp))
            adUnitId = TEST_BANNER_AD_UNIT_ID
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(Unit) {
        onDispose { adView.destroy() }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { adView },
    )
}
