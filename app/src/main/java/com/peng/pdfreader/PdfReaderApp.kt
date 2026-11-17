package com.peng.pdfreader

import com.peng.pdfreader.ads.Ads
import com.peng.pdfreader.ads.Analytics
import com.peng.pdfreader.base.BaseApplication
import com.peng.pdfreader.core.util.Logger

/**
 * Application entry point.
 *
 * Only the initialisation that belongs to this concrete app lives here; the
 * shared part is in [BaseApplication].
 */
class PdfReaderApp : BaseApplication() {

    override fun onCreate() {
        super.onCreate()
        // The advertising and the analytics of the design. Both are optional:
        // a missing or still placeholder configuration must never take the app
        // down, the app works without them.
        runCatching { Ads.initialize(this) }
            .onFailure { Logger.e(TAG, "the ad sdk could not be started", it) }
        runCatching { Analytics.appOpen(this) }
            .onFailure { Logger.e(TAG, "the analytics could not be started", it) }
    }

    private companion object {
        const val TAG = "PdfReaderApp"
    }
}
