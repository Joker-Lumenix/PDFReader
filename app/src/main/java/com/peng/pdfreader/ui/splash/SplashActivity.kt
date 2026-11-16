package com.peng.pdfreader.ui.splash

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.animation.LinearInterpolator
import com.peng.pdfreader.MainActivity
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.databinding.ActivitySplashBinding
import com.peng.pdfreader.feature.onboarding.OnboardingActivity
import com.peng.pdfreader.ui.flow.AppFlow
import com.peng.pdfreader.ui.permission.PermissionActivity

/**
 * The start up screen.
 *
 * It shows the brand and a progress bar that fills from left to right within
 * one cycle of three seconds. When the progress reaches 100 the start up flow
 * continues with the onboarding, the permission request or the home screen.
 */
class SplashActivity : BaseActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var animator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.tvTagline.setText(R.string.offline_badge)
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
                        goNext()
                    }
                },
            )
            start()
        }
    }

    private fun goNext() {
        val step = AppFlow.nextStep(
            onboardingShown = OnboardingActivity.hasBeenShown(this),
            storageGranted = hasStoragePermission(),
        )
        val intent = when (step) {
            AppFlow.Step.ONBOARDING -> Intent(this, OnboardingActivity::class.java)
            AppFlow.Step.PERMISSION -> Intent(this, PermissionActivity::class.java)
            AppFlow.Step.HOME -> Intent(this, MainActivity::class.java)
        }
        startActivity(
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )
        finish()
    }

    private fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

    private companion object {
        const val PROGRESS_MAX = 100
        const val PROGRESS_CYCLE_MS = 3_000L
    }
}
