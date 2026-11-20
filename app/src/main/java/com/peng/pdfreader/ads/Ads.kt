package com.peng.pdfreader.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.peng.pdfreader.BuildConfig
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.core.widget.AdSlotView

/**
 * The ads of the app.
 *
 * The slots of the design show the local sample artwork until an ad arrives,
 * the ad then takes the place of the sample. The ad unit ids are build
 * constants, so the owner only has to replace them with the ids of the own
 * AdMob account.
 */
object Ads {

    /** The width of the banner of the design, in dp. */
    private const val BANNER_WIDTH_DP = 360

    /**
     * Loads the banner ad of the design into the slot.
     *
     * @param slot the slot of the design.
     * @param adUnitId the ad unit of the banner, the test unit by default.
     * @param onClicked called when the user clicks the ad - the app opens the
     *   page of the ad and goes to the background, that is the moment the
     *   scene "ad click" of the notifications waits for.
     */
    fun loadBanner(
        slot: AdSlotView,
        adUnitId: String = BuildConfig.AD_UNIT_ID_BANNER,
        onClicked: (() -> Unit)? = null,
    ) {
        val context = slot.context
        // The automatic initialisation of the sdk is switched off in the
        // manifest, so the slot starts it before it asks for an ad.
        initialize(context)
        val adView = runCatching {
            AdView(context).apply {
                this.adUnitId = adUnitId
                setAdSize(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                        context,
                        BANNER_WIDTH_DP,
                    ),
                )
            }
        }.getOrElse {
            Logger.e(TAG, "the banner ad could not be created", it)
            return
        }
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                slot.setAdView(adView)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                // The sample artwork of the design stays visible.
                Logger.w(TAG, "the banner ad could not be loaded: ${error.code}")
            }

            override fun onAdClicked() {
                Logger.i(TAG, "the ad was clicked")
                onClicked?.invoke()
            }
        }
        runCatching { adView.loadAd(AdRequest.Builder().build()) }
            .onFailure { Logger.e(TAG, "the banner ad could not be requested", it) }
    }

    /**
     * Loads the card ad of the design into the slot.
     *
     * @param slot the slot of the design.
     * @param adUnitId the ad unit of the card, the test unit by default.
     * @param onClicked called when the user clicks the ad.
     */
    fun loadCard(
        slot: AdSlotView,
        adUnitId: String = BuildConfig.AD_UNIT_ID_CARD,
        onClicked: (() -> Unit)? = null,
    ) {
        loadBanner(slot, adUnitId, onClicked)
    }

    /**
     * Loads the ad of the start up screen.
     *
     * The ad of the start up screen is shown as soon as it arrives, the start
     * up page waits for it.
     *
     * @param onLoaded called with the ad that can be shown now.
     * @param onFailed called when the ad could not be loaded.
     */
    fun loadAppOpen(
        context: Context,
        adUnitId: String = BuildConfig.AD_UNIT_ID_APP_OPEN,
        onLoaded: (AppOpenAd) -> Unit,
        onFailed: (String) -> Unit = {},
    ) {
        initialize(context)
        runCatching {
            AppOpenAd.load(
                context,
                adUnitId,
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        onLoaded(ad)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Logger.w(TAG, "the ad of the start up screen could not be loaded: ${error.code}")
                        onFailed("the ad could not be loaded: ${error.code}")
                    }
                },
            )
        }.onFailure {
            Logger.e(TAG, "the ad of the start up screen could not be requested", it)
            onFailed("the request failed")
        }
    }

    /** Starts the ad sdk, it is called once with the application. */
    fun initialize(context: Context) {
        runCatching {
            MobileAds.initialize(context) {
                Logger.d(TAG, "the ad sdk is ready")
            }
        }.onFailure {
            // A missing or placeholder app id must not stop the app.
            Logger.e(TAG, "the ad sdk could not be started", it)
        }
    }

    private const val TAG = "Ads"
}
