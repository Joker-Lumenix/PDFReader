package com.peng.pdfreader.ui.permission

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.peng.pdfreader.MainActivity
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.ActivityPermissionBinding
import com.peng.pdfreader.feature.settings.notification.PersistentNotificationHelper
import com.peng.pdfreader.feature.settings.permission.SetDefaultReaderSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The "allow access to all files" step of the start up flow.
 *
 * It is an activity of its own: the start up flow is
 * splash -> onboarding -> permission -> home, every step is its own screen and
 * the home activity stays free of that logic.
 *
 * The app bar of the design carries no title, only the close button.
 */
class PermissionActivity : BaseActivity() {

    private lateinit var binding: ActivityPermissionBinding

    /** Whether the "set as default reader" hint has been shown already. */
    private var defaultReaderAsked = false

    /**
     * The runtime permission of the versions below Android 11.
     *
     * There the access to the files is a normal runtime permission instead of
     * the "access all files" permission.
     */
    private val readStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Logger.i(TAG, "read external storage granted: $granted")
            updatePermissionState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.titleBar.title = ""
        binding.titleBar.addAction(buildCloseAction())

        applyCardSurface()

        binding.llPermissionCard.setOnClickListener { openAllFilesSettings() }
        binding.btnAllow.setOnClickListener { openAllFilesSettings() }

        askForReadPermissionIfNeeded()
    }

    /**
     * Asks for the runtime permission of the versions below Android 11.
     *
     * From Android 11 on the "access all files" permission replaces it, the
     * system screen of the design asks for that one.
     */
    private fun askForReadPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        readStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
        if (hasStoragePermission()) {
            // The documents of the device are collected right away, so the
            // list is complete when the home screen is opened.
            syncSharedDocuments()
            if (exitToHomeAfterGrant()) goHome()
        }
    }

    /** Brings the documents of the shared storage into the list. */
    private fun syncSharedDocuments() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                DocumentRepository.get(applicationContext)
                    .syncSharedDocuments(applicationContext)
            }
        }
    }

    /** The close button of the design. */
    private fun buildCloseAction(): View {
        val action = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(24), dp(24))
            setImageResource(R.mipmap.ic_close)
            contentDescription = getString(R.string.settings_close)
            // Closing the step continues the start up flow, the app works in its
            // own sandbox without the all files access.
            setOnClickListener {
                if (exitToHomeAfterGrant()) goHome() else finish()
            }
        }
        return action
    }

    /** The state card of the design: plain white with an 8dp radius. */
    private fun applyCardSurface() {
        val density = resources.displayMetrics.density
        binding.llPermissionCard.background = GradientDrawable().apply {
            cornerRadius = 8f * density
            setColor(Color.WHITE)
        }
    }

    /** The switch mirrors the state of the all files access permission. */
    private fun updatePermissionState() {
        val granted = hasStoragePermission()
        binding.switchPermission.isChecked = granted
        binding.switchPermission.thumbTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (granted) R.color.brand_primary else R.color.white,
            ),
        )
        binding.switchPermission.trackTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (granted) R.color.brand_primary_container else R.color.surface_border_strong,
            ),
        )
    }

    /** Whether the app may read the shared storage. */
    private fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Below Android 11 the access to the files is a runtime permission.
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun openAllFilesSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        } else {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Logger.w(TAG, "all files access screen is not available")
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    /**
     * Continues the start up flow.
     *
     * The hint about the default reader comes first. It is a hint only:
     * closing it - by tapping outside or with the back button - keeps the user
     * on this page, the flow continues with the button of the sheet (the
     * system screen of the default apps) or with the close button of the page.
     *
     * The notification permission is asked for on the home screen - two seconds
     * after the app is opened there.
     */
    private fun goHome() {
        if (defaultReaderAsked) {
            openHome()
            return
        }
        defaultReaderAsked = true
        val sheet = SetDefaultReaderSheet()
        sheet.onAction = { Logger.d(TAG, "the default app settings are opened") }
        sheet.onDismissed = { Logger.d(TAG, "the default reader hint was closed") }
        sheet.show(supportFragmentManager, SetDefaultReaderSheet.TAG)
    }

    private fun openHome() {
        // The ongoing notification of the design only makes sense once the user
        // allowed the notifications.
        if (hasNotificationPermission()) {
            PersistentNotificationHelper.show(this)
        }
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
        )
        finish()
    }

    /** Whether the app may post notifications. */
    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val manager = getSystemService(android.app.NotificationManager::class.java) ?: return false
        return manager.areNotificationsEnabled()
    }

    /** True when the page was opened as a step of the start up flow. */
    private fun exitToHomeAfterGrant(): Boolean =
        intent?.getBooleanExtra(EXTRA_EXIT_TO_HOME, false) ?: false

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "PermissionActivity"

        /** Set when the page is a step of the start up flow. */
        const val EXTRA_EXIT_TO_HOME = "com.peng.pdfreader.extra.EXIT_TO_HOME"

        /** Opens the page as a step of the start up flow. */
        fun startAsFlowStep(context: android.content.Context) {
            context.startActivity(
                Intent(context, PermissionActivity::class.java)
                    .putExtra(EXTRA_EXIT_TO_HOME, true),
            )
        }
    }
}
