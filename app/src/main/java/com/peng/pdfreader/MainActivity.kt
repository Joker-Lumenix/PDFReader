package com.peng.pdfreader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.appcore.widget.BottomNavItem
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.base.BaseBackHandler
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.ActivityMainBinding
import com.peng.pdfreader.feature.files.fragment.FilesFragment
import com.peng.pdfreader.feature.home.fragment.HomeFragment
import com.peng.pdfreader.feature.home.model.QuickAction
import com.peng.pdfreader.feature.lock.activity.LockActivity
import com.peng.pdfreader.feature.preview.activity.PreviewActivity
import com.peng.pdfreader.feature.settings.fragment.SettingsFragment
import com.peng.pdfreader.feature.settings.notification.PersistentNotificationHelper
import com.peng.pdfreader.feature.settings.widget.QuickEntryPopupView
import com.peng.pdfreader.feature.tools.activity.CompressActivity
import com.peng.pdfreader.feature.tools.activity.MergeActivity
import com.peng.pdfreader.feature.tools.fragment.ToolsFragment
import com.peng.pdfreader.feature.tools.scanner.ScanActivity
import com.peng.pdfreader.ui.permission.PermissionActivity
import kotlinx.coroutines.Dispatchers
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setItems(navItems(), TAB_HOME) { tab -> showTab(tab) }
        if (savedInstanceState == null) {
            showTab(TAB_HOME)
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Routes the intent of the shell and of the quick actions of the ongoing
     * notification.
     */
    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            PersistentNotificationHelper.ACTION_SCAN -> startActivity(Intent(this, ScanActivity::class.java))
            PersistentNotificationHelper.ACTION_LOCK -> LockActivity.start(this)
            PersistentNotificationHelper.ACTION_COMPRESS -> CompressActivity.start(this)
            PersistentNotificationHelper.ACTION_PRINT -> showTab(TAB_FILES)
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
        return (current as? BaseBackHandler)?.onBackHandled() ?: false
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
    }

    private fun createFragment(tab: Int): Fragment = when (tab) {
        TAB_FILES -> FilesFragment()
        TAB_TOOLS -> ToolsFragment()
        TAB_SETTINGS -> SettingsFragment()
        else -> HomeFragment().apply {
            onOpenDocument = { document -> openDocument(document) }
            onQuickAction = { action -> onQuickAction(action) }
            onScanClick = { openScanner() }
            onQuickEntry = { anchor -> showQuickEntry(anchor) }
            onShareClick = { shareApp() }
            onPrintClick = { openPrinter() }
            onMoreClick = { showTab(TAB_FILES) }
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

    /**
     * The quick entry panel of the design: it opens above the round button of
     * the home tab and offers Scan PDF, Merge PDF and Print PDF.
     */
    private fun showQuickEntry(anchor: View) {
        val popup = QuickEntryPopupView(this)
        popup.onEntryClick = { entry ->
            popup.dismiss()
            when (entry.id) {
                "scan" -> openScanner()
                "merge" -> MergeActivity.start(this)
                "print" -> showTab(TAB_FILES)
            }
        }
        popup.show(anchor)
    }

    private fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.app_name))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun openPrinter() {
        // Printing a concrete document is handled by the preview and tool flows.
        Logger.d(TAG, "print requested from the home tab")
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

        const val TAB_HOME = 0
        const val TAB_FILES = 1
        const val TAB_TOOLS = 2
        const val TAB_SETTINGS = 3

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
