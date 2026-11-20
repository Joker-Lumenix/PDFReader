package com.peng.pdfreader.ui.activities

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.appopen.AppOpenAd
import com.peng.pdfreader.R
import com.peng.pdfreader.ads.Ads
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.BuildConfig
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.data.SharedStorage
import com.peng.pdfreader.databinding.ActivitySplashBinding
import com.peng.pdfreader.notification.NotificationFactory
import com.peng.pdfreader.notification.NotificationPermissionHelper
import com.peng.pdfreader.notification.NotificationPermissionSheet
import com.peng.pdfreader.notification.PersistentNotificationHelper
import com.peng.pdfreader.notification.trigger.NotificationScene
import com.peng.pdfreader.notification.trigger.NotificationTarget
import com.peng.pdfreader.notification.trigger.NotificationTrigger
import com.peng.pdfreader.ui.flow.AppFlow
import kotlinx.coroutines.launch

/**
 * The start up screen.
 *
 * The page shows the brand and a progress bar that fills from left to right
 * within one cycle of twenty seconds. The progress waits while the app asks
 * for the notification permission - the user needs time for it.
 *
 * The start up screen is also the place of the ad of the start up: the ad is
 * loaded as soon as the permission is answered and it is shown as soon as it
 * is there. When the user closes the ad, the progress runs to the end and the
 * flow continues. An ad that was loaded at the end of the last start of the
 * app is shown right away, and a new one is loaded for the next start.
 */
class SplashActivity : BaseActivity() {

    private lateinit var binding: ActivitySplashBinding

    /** How long the progress ran, in milliseconds. */
    private var elapsed = 0L

    /** Whether the progress waits for the user. */
    private var paused = false

    /** Whether the permission was asked for. */
    private var permissionAsked = false

    /** Whether the answer of the permission is known. */
    private var permissionAnswered = false

    /** Whether the ad of the start up was shown. */
    private var adShown = false

    /** Whether the ad of the start up is here. */
    private var adLoaded = false

    /** Whether the ad of the start up was asked for. */
    private var adRequested = false

    /** Whether the flow already continues. */
    private var leaving = false

    /** Whether the app is in the foreground. */
    private var inForeground = false

    /** When the ad of the start up arrived, zero when there is none. */
    private var adLoadedAt = 0L

    /** Whether the progress draws itself. */
    private var ticking = false

    private val handler = Handler(Looper.getMainLooper())

    /** The progress of the design, it runs until the page leaves. */
    private val tick = object : Runnable {
        override fun run() {
            if (leaving) {
                ticking = false
                return
            }
            if (!paused) elapsed += TICK_MS
            val percent = (elapsed * 100 / MAX_CYCLE_MS).toInt().coerceIn(0, 100)
            binding.progress.progress = percent
            if (percent >= 100) {
                ticking = false
                onProgressComplete()
                return
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Logger.i(TAG, "notification permission granted: $granted")
            permissionAnswered = true
            onPermissionAnswered()
            if (granted) PersistentNotificationHelper.show(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvAppName.text = buildTitle()

        startProgress()
        // The permission is asked for a moment after the page was opened, the
        // progress waits while the user decides.
        handler.postDelayed(permissionCheck, PERMISSION_CHECK_DELAY_MS)
        // The ad of the start up is asked for right away - it needs the time
        // of the whole cycle to arrive. It is only shown when the app is in
        // the foreground, the dialog of the permission must not be in the way.
        requestAd()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(permissionCheck)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ progress

    private fun startProgress() {
        ensureTicking()
    }

    /** The progress waits while the user deals with the permission. */
    private fun pauseProgress() {
        paused = true
    }

    /** The progress continues after the user is done. */
    private fun resumeProgress() {
        paused = false
        ensureTicking()
    }

    /** Starts the progress when it does not run already. */
    private fun ensureTicking() {
        if (ticking || leaving) return
        ticking = true
        handler.post(tick)
    }

    /**
     * The progress is over.
     *
     * The page leaves the flow - unless the ad of the start up is here, then
     * the ad is shown first.
     */
    private fun onProgressComplete() {
        if (!adShown && adLoaded) {
            // The ad is here, it is shown before the flow continues.
            showAdWhenReady()
            if (!adShown) return
        }
        leave()
    }

    // ------------------------------------------------------------------ permission

    /**
     * The permission of the notifications.
     *
     * It is asked for a moment after the page was opened. The progress waits
     * while the dialog of the system is on the screen; on the versions below
     * Android 13 the sheet of the design asks for it, the progress waits as
     * well.
     */
    private val permissionCheck = Runnable {
        if (permissionAsked || leaving) return@Runnable
        permissionAsked = true
        if (NotificationPermissionHelper.hasPermission(this)) {
            // The permission is already there, nothing is asked for.
            permissionAnswered = true
            onPermissionAnswered()
            return@Runnable
        }
        pauseProgress()
        if (NotificationPermissionHelper.requiresRuntimeRequest()) {
            NotificationPermissionHelper.markAsked(this)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // The versions below Android 13 know no runtime permission: the
            // sheet of the design guides the user to the settings.
            NotificationPermissionSheet.show(supportFragmentManager) { wentToSettings ->
                if (wentToSettings) {
                    // The user is on the way to the settings of the system -
                    // the progress waits until they are back.
                    return@show
                }
                // The sheet was closed without the settings.
                permissionAnswered = true
                onPermissionAnswered()
            }
        }
    }

    /**
     * The user is back on the page.
     *
     * The progress continues, the ad of the start up is shown as soon as it
     * is there.
     */
    override fun onResume() {
        super.onResume()
        inForeground = true
        if (permissionAsked && !permissionAnswered) {
            // The user was in the settings of the system and is back now.
            permissionAnswered = true
            onPermissionAnswered()
        }
        // The app is in the foreground again - the ad can be shown now.
        showAdWhenReady()
    }

    override fun onPause() {
        inForeground = false
        super.onPause()
    }

    /**
     * The permission is answered.
     *
     * The progress continues and the ad of the start up is shown as soon as
     * it is there.
     */
    private fun onPermissionAnswered() {
        resumeProgress()
        showAdWhenReady()
    }

    // ------------------------------------------------------------------ ad

    /**
     * The ad of the start up.
     *
     * An ad that was loaded at the end of the last start of the app is used
     * right away, otherwise a new one is loaded - it arrives after a few
     * seconds and is shown as soon as the app is in the foreground. When the
     * ad does not arrive before the progress is over, the page continues
     * without it.
     */
    private fun requestAd() {
        if (leaving) return
        adRequested = true
        adLoaded = false
        val pending = Ads.pendingAppOpen()
        if (pending != null) {
            adLoaded = true
            adLoadedAt = SystemClock.elapsedRealtime()
            showAdWhenReady()
            return
        }
        Ads.loadAppOpen(
            context = this,
            onLoaded = { ad ->
                adLoaded = true
                adLoadedAt = SystemClock.elapsedRealtime()
                // The ad is shown right away, as long as the page is still
                // waiting.
                if (!leaving && !adShown) showAdWhenReady()
            },
            onFailed = { message ->
                Logger.i(TAG, "the ad of the start up could not be loaded: $message")
                if (BuildConfig.DEBUG) {
                    Toast.makeText(
                        this,
                        getString(R.string.ad_load_failed, message),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }

    /**
     * Shows the ad as soon as the app is in the foreground.
     *
     * The question of the permission must be settled - the ad of the start up
     * must not fight with the dialog of the system. An ad that waited too long
     * (the user was long in the permission dialog) is stale: a new one is
     * asked for then.
     */
    private fun showAdWhenReady() {
        if (adShown || leaving || !adLoaded) return
        // The question of the permission is not settled yet - the ad of the
        // start up must not fight with the dialog of the system.
        if (!permissionAnswered) return
        if (!inForeground) return
        if (SystemClock.elapsedRealtime() - adLoadedAt > AD_MAX_AGE_MS) {
            // The ad is stale, the user was long in the dialog of the
            // permission. A fresh one is asked for.
            Logger.i(TAG, "the ad of the start up is stale, a new one is asked for")
            requestAd()
            return
        }
        showSplashAd(Ads.pendingAppOpen())
    }

    /** Shows the ad of the start up. */
    private fun showSplashAd(ad: AppOpenAd? = null) {
        if (adShown || leaving) return
        adShown = true
        if (ad == null) {
            // The ad is not there, the flow continues.
            Logger.i(TAG, "the ad of the start up is not there, the flow continues")
            leave()
            return
        }
        Ads.releaseAppOpen()
        Logger.i(TAG, "the ad of the start up is shown")
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onSplashAdClosed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Logger.w(TAG, "the ad of the start up could not be shown: ${error.message}")
                if (BuildConfig.DEBUG) {
                    Toast.makeText(
                        this@SplashActivity,
                        getString(R.string.ad_show_failed, error.message),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                onSplashAdClosed()
            }
        }
        runCatching {
            ad.show(this)
        }.onFailure {
            Logger.w(TAG, "the ad of the start up could not be shown: ${it.message}")
            if (BuildConfig.DEBUG) {
                Toast.makeText(
                    this,
                    getString(R.string.ad_show_failed, it.message),
                    Toast.LENGTH_LONG,
                ).show()
            }
            onSplashAdClosed()
        }
    }

    /**
     * The user closed the ad of the start up.
     *
     * The progress runs to the end - unless it is already over, then the flow
     * continues right away.
     */
    private fun onSplashAdClosed() {
        if (leaving) return
        if (elapsed >= MAX_CYCLE_MS) {
            leave()
            return
        }
        // The progress runs to the end and the flow continues.
        elapsed = MAX_CYCLE_MS
        binding.progress.progress = 100
        ensureTicking()
    }

    // ------------------------------------------------------------------ flow

    /** The page leaves the start up flow. */
    private fun leave() {
        if (leaving) return
        leaving = true
        ticking = false
        handler.removeCallbacks(tick)
        launchNextStep()
    }

    private fun launchNextStep() {
        if (isFinishing || isDestroyed) return

        // A notification was clicked: the start up screen shows the ad and
        // hands the event over to the page of the notification.
        val target = notificationTarget()
        if (target != null) {
            startActivity(landingIntent(target))
            finish()
            return
        }

        val step = AppFlow.nextStep(
            guideShown = GuideActivity.hasBeenShown(this),
            storageGranted = hasStoragePermission(),
        )
        val intent = when (step) {
            AppFlow.Step.GUIDE -> Intent(this, GuideActivity::class.java)
            // The permission page is a step of the flow: it closes itself and
            // the home screen takes over once the permission was given.
            AppFlow.Step.PERMISSION -> Intent(this, PermissionActivity::class.java).apply {
                putExtra(PermissionActivity.EXTRA_EXIT_TO_HOME, true)
            }
            AppFlow.Step.HOME -> Intent(this, MainActivity::class.java)
        }
        startActivity(
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )
        finish()
        // The next start shows an ad again: one is loaded right away.
        preloadSplashAd()
    }

    /** Loads the ad of the start up for the next start of the app. */
    private fun preloadSplashAd() {
        Ads.loadAppOpen(
            context = this,
            onLoaded = { },
            onFailed = { },
        )
    }

    /**
     * The page the clicked notification leads to.
     *
     * Null when the start up screen was opened the ordinary way.
     */
    private fun notificationTarget(): NotificationTarget? {
        if (intent?.action != NotificationFactory.ACTION_NOTIFICATION) return null
        val scene = NotificationScene.of(intent.getStringExtra(NotificationFactory.EXTRA_SCENE))
        val target = NotificationTarget.of(intent.getStringExtra(NotificationFactory.EXTRA_TARGET))
            ?: NotificationTarget.HOME
        // The loop of the timer stops as soon as the notification is clicked,
        // so it is not raised again while the user is on the page.
        scene?.let { NotificationTrigger.onNotificationClicked(this, it) }
        return target
    }

    /** The intent of the page the notification leads to. */
    private fun landingIntent(target: NotificationTarget): Intent = when (target) {
        NotificationTarget.IMAGE_TO_PDF -> Intent(this, ImageToPdfActivity::class.java)
        NotificationTarget.MERGE_PDF -> Intent(this, MergeActivity::class.java)
        NotificationTarget.SPLIT_PDF -> Intent(this, SplitActivity::class.java)
        NotificationTarget.LOCK -> Intent(this, ToolFilePickerActivity::class.java).apply {
            putExtra(ToolFilePickerActivity.EXTRA_MODE, ToolFilePickerActivity.Mode.LOCK.name)
        }
        NotificationTarget.UNLOCK -> Intent(this, ToolFilePickerActivity::class.java).apply {
            putExtra(ToolFilePickerActivity.EXTRA_MODE, ToolFilePickerActivity.Mode.UNLOCK.name)
        }
        NotificationTarget.COMPRESS -> Intent(this, ToolFilePickerActivity::class.java).apply {
            putExtra(ToolFilePickerActivity.EXTRA_MODE, ToolFilePickerActivity.Mode.COMPRESS.name)
        }
        NotificationTarget.PRINT -> Intent(this, ToolFilePickerActivity::class.java).apply {
            putExtra(ToolFilePickerActivity.EXTRA_MODE, ToolFilePickerActivity.Mode.PRINT.name)
        }
        NotificationTarget.SCAN -> Intent(this, ScanActivity::class.java)
        NotificationTarget.FILES -> Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_FILES
        }
        NotificationTarget.HOME -> Intent(this, MainActivity::class.java)
    }

    /**
     * Whether the app may read the shared storage.
     *
     * On Android 11 and above that is the "access all files" permission, below
     * that the runtime permission for reading the external storage.
     */
    private fun hasStoragePermission(): Boolean = SharedStorage.canRead(this)

    private companion object {
        const val TAG = "SplashActivity"

        /** The progress runs at most twenty seconds. */
        const val MAX_CYCLE_MS = 20_000L

        /** How often the progress is drawn, in milliseconds. */
        const val TICK_MS = 100L

        /** The permission is asked for a moment after the page was opened. */
        const val PERMISSION_CHECK_DELAY_MS = 1_500L

        /**
         * How long the ad of the start up stays fresh.
         *
         * The ad of the start up is only valid for a while - a user that was
         * long in the dialog of the permission gets a new one.
         */
        const val AD_MAX_AGE_MS = 30_000L
    }
}
