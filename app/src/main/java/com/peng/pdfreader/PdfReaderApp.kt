package com.peng.pdfreader

import com.peng.pdfreader.ads.Ads
import com.peng.pdfreader.ads.Analytics
import com.peng.pdfreader.base.BaseApplication

/**
 * Application entry point.
 *
 * Only the initialisation that belongs to this concrete app lives here; the
 * shared part is in [BaseApplication].
 */
class PdfReaderApp : BaseApplication() {

    override fun onCreate() {
        super.onCreate()
        // The advertising and the analytics of the design.
        Ads.initialize(this)
        Analytics.appOpen(this)
    }
}
