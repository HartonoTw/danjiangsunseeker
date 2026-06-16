package studio.freestyle.labs.danjiangsunseeker.presentation.common

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * 獎勵式影片廣告 (Rewarded Video) 載入/播放。
 *
 * 目前使用 Google 官方「測試」獎勵廣告單元；上架前須換成 AdMob 後台建立的正式單元。
 * 看完廣告（使用者真正獲得獎勵）才會回呼 [onReward]；載入失敗則回呼 [onFailed]。
 */
object RewardedAdLoader {

    private const val TAG = "RewardedAdLoader"

    /** Google 官方測試獎勵廣告單元 ID。 */
    private const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    fun loadAndShow(
        activity: Activity,
        onReward: () -> Unit,
        onFailed: () -> Unit,
    ) {
        RewardedAd.load(
            activity,
            TEST_REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    ad.show(activity, OnUserEarnedRewardListener { onReward() })
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
                    onFailed()
                }
            },
        )
    }
}
