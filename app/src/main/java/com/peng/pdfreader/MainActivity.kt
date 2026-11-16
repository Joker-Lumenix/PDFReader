package com.peng.pdfreader

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.appcore.widget.BottomNavItem
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.base.BaseBackHandler
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.ActivityMainBinding
import com.peng.pdfreader.feature.files.activity.FileSearchActivity
import com.peng.pdfreader.feature.files.activity.FileSelectActivity
import com.peng.pdfreader.feature.files.fragment.FilesFragment
import com.peng.pdfreader.feature.home.fragment.HomeFragment
import com.peng.pdfreader.feature.home.model.QuickAction
import com.peng.pdfreader.feature.home.sheet.ExitRetentionSheet
import com.peng.pdfreader.feature.lock.activity.LockActivity
import com.peng.pdfreader.feature.preview.activity.PreviewActivity
import com.peng.pdfreader.feature.settings.fragment.SettingsFragment
import com.peng.pdfreader.feature.settings.notification.NotificationPermissionHelper
import com.peng.pdfreader.feature.settings.notification.NotificationPermissionSheet
import com.peng.pdfreader.feature.settings.notification.PersistentNotificationHelper
import com.peng.pdfreader.feature.settings.widget.QuickEntryPopupView
import com.peng.pdfreader.feature.tools.activity.CompressActivity
import com.peng.pdfreader.feature.tools.activity.ImageToPdfActivity
import com.peng.pdfreader.feature.tools.activity.MergeActivity
import com.peng.pdfreader.feature.tools.fragment.ToolsFragment
import com.peng.pdfreader.feature.tools.scanner.ScanActivity
import com.peng.pdfreader.ui.permission.PermissionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The home of the app.
 *
 * It only hosts the four tabs and routes the actions the tabs report; the
 * steps of the start up flow (splash, onboarding, permission) live in their own
 * activities.
 */
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Whether the custom notification sheet was shown already. */
    private var notificationSheetShown = false

    /** Whether the exit retention sheet is on the screen. */
    private var exitRetentionShown = false

    /** The tab that is on the screen. */
    private var currentTab = TAB_HOME

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Logger.i(TAG, "notification permission granted: $granted")
            if (granted) {
                PersistentNotificationHelper.show(this)
            } else {
                // The system refuses to show its dialog again, the custom sheet
                // of the design guides the user to the settings.
                showNotificationPermissionSheet()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setItems(navItems(), TAB_HOME) { tab -> showTab(tab) }
        if (savedInstanceState == null) {
            showTab(TAB_HOME)
        }
        handleIntent(intent)
        // The documents of the device are collected right away, so the list is
        // complete when the home screen is opened.
        syncSharedDocuments()
        // The notification permission is asked for a moment after the home
        // screen was opened, see the notification permission handling.
        scheduleNotificationPermission()
    }

    /** Brings the documents of the shared storage into the library. */
    private fun syncSharedDocuments() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                DocumentRepository.get(applicationContext)
                    .syncSharedDocuments(applicationContext)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Routes the intent of the shell, of the quick actions of the ongoing
     * notification and of the shortcuts of the launcher.
     */
    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            PersistentNotificationHelper.ACTION_SCAN -> openScanner()
            PersistentNotificationHelper.ACTION_LOCK -> LockActivity.start(this)
            PersistentNotificationHelper.ACTION_COMPRESS -> CompressActivity.start(this)
            PersistentNotificationHelper.ACTION_PRINT -> showTab(TAB_FILES)
            ACTION_MERGE -> MergeActivity.start(this)
            ACTION_IMAGE_TO_PDF -> ImageToPdfActivity.start(this)
            ACTION_OPEN_FILES -> showTab(TAB_FILES)
            Intent.ACTION_VIEW, Intent.ACTION_SEND -> handleIncomingDocument(intent)
        }
    }

    /**
     * Opens a document that was handed over by another app.
     *
     * The file is copied into the private sandbox first, then previewed.
     */
    private fun handleIncomingDocument(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtraCompat(Intent.EXTRA_STREAM)
            else -> null
        } ?: return
        lifecycleScope.launch {
            val document = withContext(Dispatchers.IO) {
                DocumentRepository.get(this@MainActivity).importFromUri(this@MainActivity, uri)
            }
            if (document != null) openDocument(document)
        }
    }

    override fun handleBackPressed(): Boolean {
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if ((current as? BaseBackHandler)?.onBackHandled() == true) return true
        // Leaving the app from the home tab shows the retention sheet of the
        // design, it offers one of the tools before the user goes.
        if (currentTab == TAB_HOME) {
            showExitRetention()
            return true
        }
        return false
    }

    /** Shows the exit retention sheet of the design. */
    private fun showExitRetention() {
        if (exitRetentionShown || isFinishing || isDestroyed) return
        exitRetentionShown = true
        ExitRetentionSheet.show(
            supportFragmentManager,
            onExit = { finishAffinity() },
            onDismissed = { exitRetentionShown = false },
        )
    }

    private fun showTab(tab: Int) {
        val tag = tagOf(tab)
        val manager = supportFragmentManager
        val transaction = manager.beginTransaction()
        manager.fragments.forEach { transaction.hide(it) }
        val existing = manager.findFragmentByTag(tag)
        if (existing == null) {
            transaction.add(R.id.fragmentContainer, createFragment(tab), tag)
        } else {
            transaction.show(existing)
        }
        transaction.commitAllowingStateLoss()
        binding.bottomNav.selectTab(tab)
        currentTab = tab
    }

    private fun createFragment(tab: Int): Fragment = when (tab) {
        TAB_FILES -> FilesFragment()
        TAB_TOOLS -> ToolsFragment()
        TAB_SETTINGS -> SettingsFragment()
        else -> HomeFragment().apply {
            onOpenDocument = { document -> openDocument(document) }
            onQuickAction = { action -> onQuickAction(action) }
            onScanClick = { openScanner() }
            onSearchClick = { FileSearchActivity.start(this@MainActivity) }
            onSelectClick = { FileSelectActivity.start(this@MainActivity) }
            onOpenSettings = { openStoragePermission() }
        }
    }

    // ---------------------------------------------------------------- routing

    private fun openDocument(document: Document) {
        PreviewActivity.start(this, document.id)
    }


    private fun onQuickAction(action: QuickAction) {
        when (action) {
            QuickAction.IMAGE_TO_PDF, QuickAction.MERGE_PDF -> showTab(TAB_TOOLS)
            QuickAction.MORE -> showTab(TAB_TOOLS)
        }
    }

    private fun openScanner() {
        try {
            startActivity(Intent(this, ScanActivity::class.java))
        } catch (error: ActivityNotFoundException) {
            Logger.e(TAG, "scan flow unavailable", error)
        }
    }

    // ------------------------------------------------------- notification permission


    /**
     * Asks for the notification permission two seconds after the home screen
     * was opened.
     *
     * The start up screen already asked for it - when the user denied it there,
     * the request is repeated here. As long as the system still shows its own
     * dialog the system dialog is used, otherwise the custom sheet of the
     * design takes over.
     */
    private fun scheduleNotificationPermission() {
        lifecycleScope.launch {
            delay(NOTIFICATION_PERMISSION_DELAY_MS)
            requestNotificationPermission()
        }
    }

    private fun requestNotificationPermission() {
        if (isFinishing || isDestroyed) return
        if (NotificationPermissionHelper.hasPermission(this)) {
            PersistentNotificationHelper.show(this)
            return
        }
        if (NotificationPermissionHelper.requiresRuntimeRequest() &&
            NotificationPermissionHelper.canAskSystem(
                this,
                NotificationPermissionHelper.wasAsked(this),
            )
        ) {
            NotificationPermissionHelper.markAsked(this)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        // The system does not show its dialog anymore - the custom sheet of the
        // design guides the user to the settings of the system.
        showNotificationPermissionSheet()
    }

    private fun showNotificationPermissionSheet() {
        if (notificationSheetShown || isFinishing || isDestroyed) return
        notificationSheetShown = true
        NotificationPermissionSheet.show(supportFragmentManager)
    }

    private fun openStoragePermission() {
        startActivity(Intent(this, PermissionActivity::class.java))
    }

    private fun tagOf(tab: Int): String = "tab_$tab"

    private fun navItems() = listOf(
        BottomNavItem(
            id = TAB_HOME,
            iconSelected = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_home_select),
            iconUnselected = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_home_unselect),
            label = getString(R.string.nav_home),
        ),
        BottomNavItem(
            id = TAB_FILES,
            iconSelected = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_files_select),
            iconUnselected = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_files_unselect),
            label = getString(R.string.nav_files),
        ),
        BottomNavItem(
            id = TAB_TOOLS,
            iconSelected = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_tools_select),
            iconUnselected = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_tools_unselect),
            label = getString(R.string.nav_tools),
        ),
        BottomNavItem(
            id = TAB_SETTINGS,
            iconSelected = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_setting_select),
            iconUnselected = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_setting_unselect),
            label = getString(R.string.nav_settings),
        ),
    )

    companion object {
        private const val TAG = "MainActivity"

        /** The notification permission is asked for two seconds after the start. */
        private const val NOTIFICATION_PERMISSION_DELAY_MS = 2_000L

        const val TAB_HOME = 0
        const val TAB_FILES = 1
        const val TAB_TOOLS = 2
        const val TAB_SETTINGS = 3

        /** The shortcut of the launcher that opens the merge tool. */
        const val ACTION_MERGE = "com.peng.pdfreader.action.MERGE"

        /** The shortcut of the launcher that opens the image to pdf tool. */
        const val ACTION_IMAGE_TO_PDF = "com.peng.pdfreader.action.IMAGE_TO_PDF"

        /** The shortcut of the launcher that opens the files tab. */
        const val ACTION_OPEN_FILES = "com.peng.pdfreader.action.OPEN_FILES"

        /** Reads a stream uri from an intent, whatever the platform version. */
        private fun Intent.getParcelableExtraCompat(name: String): android.net.Uri? =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(name, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(name) as? android.net.Uri
            }
    }
}
