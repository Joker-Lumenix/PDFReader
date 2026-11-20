package com.peng.pdfreader

import androidx.lifecycle.ProcessLifecycleOwner
import com.peng.pdfreader.ads.Ads
import com.peng.pdfreader.ads.Analytics
import com.peng.pdfreader.base.BaseApplication
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.notification.trigger.NotificationAppState
import com.peng.pdfreader.notification.trigger.NotificationScene
import com.peng.pdfreader.notification.trigger.NotificationTimerService
import com.peng.pdfreader.notification.trigger.NotificationTrigger

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

        // The scenes of the notifications that need the state of the app.
        NotificationAppState.observe(
            ProcessLifecycleOwner.get().lifecycle,
            object : NotificationAppState.Listener {
                override fun onForeground() {
                    // The app is used, the notifications stay away.
                    Logger.d(TAG, "the app is in the foreground")
                }

                override fun onBackground() {
                    // The app is in the background now: the scene of the ad
                    // that was clicked runs, then the one of the home button,
                    // otherwise the one of the background.
                    val scene = when {
                        NotificationAppState.consumeAdClicked() -> NotificationScene.AD_CLICK
                        NotificationAppState.consumeHomeButtonUsed() -> NotificationScene.HOME_KEY
                        else -> NotificationScene.APP_BACKGROUND
                    }
                    NotificationTrigger.trigger(this@PdfReaderApp, scene)
                    // The timer of the notifications runs while the app is in
                    // the background, it stops when the user clicks one.
                    NotificationTimerService.start(this@PdfReaderApp)
                }
            },
        )
    }

    private companion object {
        const val TAG = "PdfReaderApp"
    }
}
