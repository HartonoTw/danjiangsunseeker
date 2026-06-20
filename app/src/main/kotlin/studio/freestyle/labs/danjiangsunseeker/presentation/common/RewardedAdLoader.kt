package studio.freestyle.labs.danjiangsunseeker.presentation.common

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import studio.freestyle.labs.danjiangsunseeker.R

/**
 * 獎勵式影片廣告 (Rewarded Video) 載入/播放。
 *
 * 廣告單元 ID 見 res/values/ads.xml。
 * 看完廣告（使用者真正獲得獎勵）才會回呼 [onReward]；載入失敗則回呼 [onFailed]。
 */
object RewardedAdLoader {

    private const val TAG = "RewardedAdLoader"

    fun loadAndShow(
        activity: Activity,
        onReward: () -> Unit,
        onFailed: () -> Unit,
    ) {
        RewardedAd.load(
            activity,
            activity.getString(R.string.admob_rewarded_unit_id),
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
