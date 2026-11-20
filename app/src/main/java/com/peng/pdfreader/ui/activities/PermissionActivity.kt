package com.peng.pdfreader.ui.activities

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.data.SharedStorage
import com.peng.pdfreader.databinding.ActivityPermissionBinding
import com.peng.pdfreader.notification.PersistentNotificationHelper
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
 * The page never asks for anything by itself: the permission is requested from
 * the system when the user taps the button of the design. When the app may
 * already read the files the page is not shown at all.
 *
 * The app bar of the design carries no title, only the close button.
 */
class PermissionActivity : BaseActivity() {

    private lateinit var binding: ActivityPermissionBinding

    /** Whether the flow was continued already, the page leaves only once. */
    private var flowContinued = false

    /** Whether the home screen was started already. */
    private var homeStarted = false

    /**
     * The watcher of the permission while the screen of the system is open.
     *
     * The screen of the system does not close itself when the permission is
     * given: the user stays on it and has to go back by hand. The watcher
     * looks at the permission and brings the app back as soon as it is there.
     */
    private val watcher = Handler(Looper.getMainLooper())

    /** The runnable that looks at the permission, it repeats until it is given. */
    private val permissionWatcher = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            if (!hasStoragePermission()) {
                watcher.postDelayed(this, PERMISSION_POLL_INTERVAL_MS)
                return
            }
            Logger.i(TAG, "the permission was given in the screen of the system")
            stopWatchingPermission()
            bringAppToFront()
        }
    }

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

    /**
     * The screen of the system for the permission.
     *
     * The result comes back as soon as the user leaves the screen of the
     * system: the app goes on by itself when the permission is there.
     */
    private val allFilesSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Logger.i(TAG, "the screen of the system was left")
            if (hasStoragePermission()) {
                Logger.i(TAG, "the permission was given in the screen of the system")
                continueFlow()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The page is only shown when the permission is missing. When it is
        // already granted the page closes right away, the user never sees it.
        if (hasStoragePermission()) {
            Logger.i(TAG, "the access to the files is granted already, the page is skipped")
            continueFlow()
            return
        }

        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivClose.setOnClickListener {
            if (exitToHomeAfterGrant()) goHome() else finish()
        }

        // The page never asks for anything by itself: the request goes to the
        // system when the user taps the button of the design.
        binding.btnAllow.setOnClickListener { requestStoragePermission() }
    }

    /**
     * Asks the system for the permission of the page.
     *
     * From Android 11 on the "access all files" permission
     * (MANAGE_EXTERNAL_STORAGE) is a special access permission: the system
     * ignores a runtime request for it, the user grants it in the screen of
     * the system. Below that version the access to the files is a normal
     * runtime permission and the system shows its own dialog.
     */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openAllFilesSettings()
            return
        }
        readStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    override fun onResume() {
        super.onResume()
        // The app is in the foreground again, the watcher is not needed.
        stopWatchingPermission()
        if (!hasStoragePermission()) return
        // The documents of the device are collected right away, so the list is
        // complete when the home screen is opened.
        syncSharedDocuments()
        if (exitToHomeAfterGrant()) {
            // The permission was given in the system screen, the flow goes on.
            continueFlow()
        } else {
            // The page was opened from the app itself, the permission is there
            // now: the page has nothing left to show.
            finish()
        }
    }

    override fun onDestroy() {
        stopWatchingPermission()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Logger.i(TAG, "the page of the permission came to the front again")
    }

    /**
     * Looks at the permission while the screen of the system is open.
     *
     * The screen of the system stays open after the permission was given, the
     * app comes back on its own as soon as the watcher sees the permission.
     */
    private fun watchPermission() {
        watcher.removeCallbacks(permissionWatcher)
        watcher.post(permissionWatcher)
    }

    /** Stops the watcher of the permission. */
    private fun stopWatchingPermission() {
        watcher.removeCallbacks(permissionWatcher)
    }

    /**
     * Brings the app back to the front.
     *
     * The screen of the system is in the task of the app - it was started for
     * a result - so it is closed and the page of the permission comes to the
     * top of the task. The flow goes on there: the page leaves itself for the
     * home screen.
     */
    private fun bringAppToFront() {
        // The task of the app comes to the front first, the app is in the
        // foreground afterwards and may show its own page again.
        val manager = getSystemService(ActivityManager::class.java)
        val currentTaskId = taskId
        runCatching {
            manager?.appTasks
                ?.firstOrNull { it.taskInfo?.id == currentTaskId }
                ?.moveToFront()
        }
        // The clear top closes the screen of the system: it is above the page
        // in the task of the app. The single top keeps the page that is
        // already running: it takes the new intent instead of a second page
        // being started.
        startActivity(
            Intent(this, javaClass).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
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
        // The screen of the system for MANAGE_EXTERNAL_STORAGE: the user
        // switches the "access all files" permission there.
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        } else {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }

        try {
            // The launcher comes back as soon as the user leaves the screen of
            // the system, the app goes on by itself then.
            allFilesSettingsLauncher.launch(intent)
            // The screen of the system stays open after the permission was
            // given, the watcher brings the app back as soon as it is there.
            watchPermission()
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
     * The app does not ask for the default app here: the question comes after
     * an effective read (see [com.peng.pdfreader.permission.DefaultAppPrompt])
     * and the settings tab always carries the entry.
     *
     * The notification permission is asked for on the home screen - two seconds
     * after the app is opened there.
     */
    private fun goHome() {
        openHome()
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

        /** How often the permission is looked at while the system screen is open. */
        private const val PERMISSION_POLL_INTERVAL_MS = 400L

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
