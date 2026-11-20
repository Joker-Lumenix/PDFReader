package com.peng.pdfreader.ui.activities

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.peng.pdfreader.R
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.data.SharedStorage
import com.peng.pdfreader.core.widget.BottomNavItem
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.base.BaseBackHandler
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.databinding.ActivityMainBinding
import com.peng.pdfreader.ui.fragments.FilesFragment
import com.peng.pdfreader.ui.fragments.HomeFragment
import com.peng.pdfreader.ui.uiData.QuickAction
import com.peng.pdfreader.ui.dialogs.BottomSheetExit
import com.peng.pdfreader.ui.fragments.SettingsFragment
import com.peng.pdfreader.notification.NotificationPermissionHelper
import com.peng.pdfreader.notification.NotificationPermissionSheet
import com.peng.pdfreader.notification.PersistentNotificationHelper
import com.peng.pdfreader.notification.trigger.NotificationAppState
import com.peng.pdfreader.ui.fragments.ToolsFragment
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

    /**
     * The tabs of the app have a top bar of their own and take the inset of the
     * status bar there, the shell must not move them a second time.
     */
    override val takesStatusBarInset: Boolean = false

    /**
     * The bottom navigation bar takes the inset of the navigation bar itself,
     * the shell must not move its content a second time.
     */
    override val takesBottomInset: Boolean = false


    private lateinit var binding: ActivityMainBinding

    /** Whether the exit retention sheet is on the screen. */
    private var exitRetentionShown = false

    /** The tab that is on the screen. */
    private var currentTab = TAB_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // The scan button belongs to the home and the files tab.
        binding.fabScan.setOnClickListener {
            openScanner()
        }

        binding.bottomBar.setItems(navItems(), TAB_HOME) { tab -> showTab(tab) }
        if (savedInstanceState == null) {
            showTab(TAB_HOME)
        }
        handleIntent(intent)
        // The documents of the device are collected right away, so the list is
        // complete when the home screen is opened.
        syncSharedDocuments()
        // The start up screen asked for the notification permission, the
        // notification of the design shows up when it was granted.
        showPersistentNotification()
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
     * The user left the app with the home button.
     *
     * The app is still in the foreground at this moment, so only the fact is
     * remembered here: the scene "home key" runs when the app went to the
     * background, the same moment the scene "app background" would run.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        NotificationAppState.markHomeButtonUsed()
    }

    /**
     * Routes the intent of the shell, of the quick actions of the ongoing
     * notification and of the shortcuts of the launcher.
     */
    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            PersistentNotificationHelper.ACTION_SCAN -> openScanner()
            PersistentNotificationHelper.ACTION_LOCK -> ToolFilePickerActivity.start(this, ToolFilePickerActivity.Mode.LOCK)
            PersistentNotificationHelper.ACTION_COMPRESS -> ToolFilePickerActivity.start(this, ToolFilePickerActivity.Mode.COMPRESS)
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
        BottomSheetExit.show(
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
        binding.bottomBar.selectTab(tab)
        currentTab = tab
        applyScanButton(tab)
    }

    /**
     * The scan button belongs to the home and to the files tab.
     *
     * On the tools and on the settings tab the button is gone, so it cannot
     * be tapped there either.
     */
    private fun applyScanButton(tab: Int) {
        val visible = tab == TAB_HOME || tab == TAB_FILES
        binding.fabScan.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun createFragment(tab: Int): Fragment = when (tab) {
        TAB_FILES -> FilesFragment()
        TAB_TOOLS -> ToolsFragment()
        TAB_SETTINGS -> SettingsFragment()
        else -> HomeFragment().apply {
            onOpenDocument = { document -> openDocument(document) }
            onQuickAction = { action -> onQuickAction(action) }
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
            // The two cards of the home tab open their own flow.
            QuickAction.IMAGE_TO_PDF -> ImageToPdfActivity.start(this)
            QuickAction.MERGE_PDF -> MergeActivity.start(this)
            // The narrow chevron opens the full tool list.
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
     * The notification of the design is shown when the user allowed the
     * notifications.
     *
     * The start up screen asks for the permission - it waits for the answer
     * before it continues, so the home screen never asks on its own.
     */
    private fun showPersistentNotification() {
        if (isFinishing || isDestroyed) return
        if (!NotificationPermissionHelper.hasPermission(this)) return
        PersistentNotificationHelper.show(this)
    }

    private fun openStoragePermission() {
        if (SharedStorage.canRead(this)) {
            // The access to the files is granted already, the page of the
            // permission has nothing left to show.
            Logger.i(TAG, "the access to the files is granted already")
            return
        }
        startActivity(Intent(this, PermissionActivity::class.java))
    }

    private fun tagOf(tab: Int): String = "tab_$tab"

    private fun navItems() = listOf(
        BottomNavItem(
            id = TAB_HOME,
            iconSelected = ContextCompat.getDrawable(this, R.mipmap.ic_home_select),
            iconUnselected = ContextCompat.getDrawable(this, R.mipmap.ic_home_unselect),
            label = getString(R.string.nav_home),
        ),
        BottomNavItem(
            id = TAB_FILES,
            iconSelected = ContextCompat.getDrawable(this, R.mipmap.ic_files_select),
            iconUnselected = ContextCompat.getDrawable(this, R.mipmap.ic_files_unselect),
            label = getString(R.string.nav_files),
        ),
        BottomNavItem(
            id = TAB_TOOLS,
            iconSelected = ContextCompat.getDrawable(this, R.mipmap.ic_tools_select),
            iconUnselected = ContextCompat.getDrawable(this, R.mipmap.ic_tools_unselect),
            label = getString(R.string.nav_tools),
        ),
        BottomNavItem(
            id = TAB_SETTINGS,
            iconSelected = ContextCompat.getDrawable(this, R.mipmap.ic_setting_select),
            iconUnselected = ContextCompat.getDrawable(this, R.mipmap.ic_setting_unselect),
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
        private fun Intent.getParcelableExtraCompat(name: String): Uri? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(name, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(name) as? Uri
            }
    }
}
