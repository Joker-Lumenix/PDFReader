package com.peng.pdfreader.ui.activities

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.data.SharedStorage
import com.peng.pdfreader.databinding.ActivityPermissionBinding
import com.peng.pdfreader.notification.PersistentNotificationHelper
import com.peng.pdfreader.permission.SetDefaultReaderSheet
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

    /** Whether the flow was continued already, the page leaves only once. */
    private var flowContinued = false

    /** Whether the home screen was started already. */
    private var homeStarted = false

    /**
     * The runtime permission of the versions below Android 11.
     *
     * There the access to the files is a normal runtime permission instead of
     * the "access all files" permission.
     */
    private val readStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Logger.i(TAG, "read external storage granted: $granted")
            if (granted && exitToHomeAfterGrant()) {
                // The permission is there: the page closes and the home
                // screen takes over.
                continueFlow()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivClose.setOnClickListener {
            if (exitToHomeAfterGrant()) goHome() else finish()
        }

        binding.btnAllow.setOnClickListener { openAllFilesSettings() }

        askForReadPermissionIfNeeded()
    }

    /**
     * Asks for the runtime permission of the versions below Android 11.
     *
     * From Android 11 on the "access all files" permission replaces it: the
     * button of the page opens the system screen of that permission and the
     * page leaves the app once the permission was given.
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
        if (!hasStoragePermission()) return
        // The documents of the device are collected right away, so the list is
        // complete when the home screen is opened.
        syncSharedDocuments()
        if (exitToHomeAfterGrant()) {
            // The permission was given in the system screen, the flow goes on.
            continueFlow()
        }
    }

    /**
     * Continues the start up flow.
     *
     * The page is left only once, no matter how often the permission is
     * reported.
     */
    private fun continueFlow() {
        if (flowContinued) return
        flowContinued = true
        syncSharedDocuments()
        if (exitToHomeAfterGrant()) {
            // The page is a step of the flow: the home screen takes over.
            goHome()
        } else {
            finish()
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

    /** Whether the app may read the shared storage. */
    private fun hasStoragePermission(): Boolean = SharedStorage.canRead(this)

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
        if (homeStarted) return
        if (defaultReaderAsked) {
            openHome()
            return
        }
        defaultReaderAsked = true
        val sheet = SetDefaultReaderSheet()
        sheet.onAction = { Logger.d(TAG, "the default app settings are opened") }
        sheet.onDismissed = {
            Logger.d(TAG, "the default reader hint was closed")
            // The hint is gone, the home screen takes over.
            openHome()
        }
        sheet.show(supportFragmentManager, SetDefaultReaderSheet.TAG)
    }

    private fun openHome() {
        if (homeStarted) return
        homeStarted = true
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
        val manager = getSystemService(NotificationManager::class.java) ?: return false
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
        fun startAsFlowStep(context: Context) {
            context.startActivity(
                Intent(context, PermissionActivity::class.java)
                    .putExtra(EXTRA_EXIT_TO_HOME, true),
            )
        }
    }
}
