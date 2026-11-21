package com.student.studypulse

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    const val BANNER_AD_ID = "ca-app-pub-5818608133597474/4510452925"
    const val REWARDED_AD_ID = "ca-app-pub-5818608133597474/3963658010"

    @Volatile
    var isShowingFullscreenAd: Boolean = false
    private var rewardedAd: RewardedAd? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        try {
            MobileAds.initialize(context.applicationContext) {}
            preloadRewarded(context.applicationContext)
        } catch (_: Throwable) {}
    }

    fun preloadRewarded(context: Context) {
        if (rewardedAd != null) return
        try {
            RewardedAd.load(
                context.applicationContext,
                REWARDED_AD_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
                    override fun onAdFailedToLoad(err: LoadAdError) { rewardedAd = null }
                }
            )
        } catch (_: Throwable) {}
    }

    fun showRewardedUnlock(
        activity: Activity,
        onUnlocked: (bonusSlots: Int, isFallback: Boolean) -> Unit,
        onClosedEarly: () -> Unit
    ) {
        val readyAd = rewardedAd
        if (readyAd != null) {
            presentRewarded(activity, readyAd, onUnlocked, onClosedEarly)
            return
        }
        try {
            RewardedAd.load(
                activity.applicationContext,
                REWARDED_AD_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        presentRewarded(activity, ad, onUnlocked, onClosedEarly)
                    }
                    override fun onAdFailedToLoad(err: LoadAdError) {
                        rewardedAd = null
                        onUnlocked(1, true)
                    }
                }
            )
        } catch (_: Throwable) {
            onUnlocked(1, true)
        }
    }

    private fun presentRewarded(
        activity: Activity,
        ad: RewardedAd,
        onUnlocked: (Int, Boolean) -> Unit,
        onClosedEarly: () -> Unit
    ) {
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowingFullscreenAd = true
                rewardedAd = null
            }
            override fun onAdFailedToShowFullScreenContent(err: AdError) {
                isShowingFullscreenAd = false
                rewardedAd = null
                preloadRewarded(activity.applicationContext)
                onUnlocked(1, true)
            }
            override fun onAdDismissedFullScreenContent() {
                isShowingFullscreenAd = false
                preloadRewarded(activity.applicationContext)
                if (!earned) onClosedEarly()
            }
        }
        ad.show(activity) {
            earned = true
            onUnlocked(2, false)
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AdBannerBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = AdManager.BANNER_AD_ID
            try { loadAd(AdRequest.Builder().build()) } catch (_: Throwable) {}
        }
    }
    DisposableEffect(adView) {
        onDispose { try { adView.destroy() } catch (_: Throwable) {} }
    }
    Box(modifier = modifier.fillMaxWidth().height(54.dp), contentAlignment = Alignment.Center) {
        AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth().height(50.dp))
    }
}

@Composable
fun RewardUnlockDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onUnlocked: (Int) -> Unit
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("🔒 $title", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(message)
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Ad load ho raha hai...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !loading,
                onClick = {
                    val act = context.findActivity()
                    if (act == null) {
                        onUnlocked(1)
                        return@Button
                    }
                    loading = true
                    AdManager.showRewardedUnlock(
                        activity = act,
                        onUnlocked = { bonus, isFallback ->
                            loading = false
                            val msg = if (isFallback) "Ad abhi uplabdh nahi, padhai ke liye +$bonus Free Slot mila!" else "Badhai! +$bonus Naye Slots khul gaye 🔓"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            onUnlocked(bonus)
                        },
                        onClosedEarly = {
                            loading = false
                            Toast.makeText(context, "Pura Ad dekhne par hi +2 Slots khulenge", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            ) { Text("Watch Ad (+2 Unlock 🔓)") }
        },
        dismissButton = {
            TextButton(enabled = !loading, onClick = onDismiss) { Text("Baad mein") }
        }
    )
}
// END_OF_FILE
