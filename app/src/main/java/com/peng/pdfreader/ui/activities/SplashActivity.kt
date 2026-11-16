package com.peng.pdfreader.ui.activities

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.SharedStorage
import com.peng.pdfreader.databinding.ActivitySplashBinding
import com.peng.pdfreader.notification.NotificationPermissionHelper
import com.peng.pdfreader.ui.flow.AppFlow

/**
 * The start up screen.
 *
 * It shows the brand and a progress bar that fills from left to right within
 * one cycle of three seconds. When the progress reaches 100 the start up flow
 * continues with the guide, the permission request or the home screen.
 *
 * The notification permission belongs to the start up screen as well: on
 * Android 13 and above it is asked for with the runtime dialog, below that
 * version the app only remembers that the notifications are still switched off
 * and the custom sheet of the design takes over two seconds after the home
 * screen was opened.
 */
class SplashActivity : BaseActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var animator: ValueAnimator? = null

    /** Whether the progress cycle is over. */
    private var progressDone = false

    /** Whether the answer of the system dialog is known. */
    private var permissionAnswered = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Logger.i(TAG, "notification permission granted: $granted")
            permissionAnswered = true
            advance()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvAppName.text = buildTitle()
        startProgress()
    }

    override fun onDestroy() {
        animator?.cancel()
        animator = null
        super.onDestroy()
    }

    private fun startProgress() {
        animator = ValueAnimator.ofInt(0, PROGRESS_MAX).apply {
            duration = PROGRESS_CYCLE_MS
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                binding.progress.progress = animation.animatedValue as Int
            }
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        progressDone = true
                        advance()
                    }
                },
            )
            start()
        }
    }

    /**
     * Continues the flow.
     *
     * The notification permission is asked for first - only on the versions that
     * know the runtime permission; the flow continues in every case, the second
     * attempt is made on the home screen.
     */
    private fun advance() {
        if (!progressDone) return
        if (!permissionAnswered) {
            if (!NotificationPermissionHelper.requiresRuntimeRequest()) {
                // Below Android 13 there is no runtime permission, the custom
                // sheet of the design guides the user later on.
                permissionAnswered = true
            } else if (NotificationPermissionHelper.hasPermission(this)) {
                permissionAnswered = true
            } else {
                NotificationPermissionHelper.markAsked(this)
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        launchNextStep()
    }

    private fun launchNextStep() {
        if (isFinishing || isDestroyed) return
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
        const val PROGRESS_MAX = 100
        const val PROGRESS_CYCLE_MS = 3_000L
    }
}
