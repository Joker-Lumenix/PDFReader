package com.peng.pdfreader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.appcore.widget.BottomNavItem
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.base.BaseBackHandler
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.databinding.ActivityMainBinding
import com.peng.pdfreader.feature.files.fragment.FilesFragment
import com.peng.pdfreader.feature.home.fragment.HomeFragment
import com.peng.pdfreader.feature.home.model.QuickAction
import com.peng.pdfreader.feature.onboarding.OnboardingLauncher
import com.peng.pdfreader.feature.preview.activity.PreviewActivity
import com.peng.pdfreader.feature.settings.fragment.SettingsFragment
import com.peng.pdfreader.feature.settings.notification.NotificationPermissionSheet
import com.peng.pdfreader.feature.settings.permission.StoragePermissionActivity
import com.peng.pdfreader.feature.tools.fragment.ToolsFragment
import com.peng.pdfreader.feature.tools.scanner.ScanActivity

/**
 * Application shell.
 *
 * Hosts the four top level tabs and routes the actions the tabs report. Every
 * tab is an independent fragment under `feature/<domain>/fragment`; the shell
 * only decides where a reported action leads.
 */
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    private val onboardingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // The notification permission sheet is the next step of the first run.
        showNotificationPermissionSheet()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setItems(navItems(), TAB_HOME) { tab -> showTab(tab) }
        if (savedInstanceState == null) {
            showTab(TAB_HOME)
            startOnboardingIfNeeded()
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
            onDocumentMenu = { document -> openDocumentMenu(document) }
            onQuickAction = { action -> onQuickAction(action) }
            onScanClick = { openScanner() }
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

    private fun openDocumentMenu(document: Document) {
        // The file action sheet lives in the files feature; the home row menu
        // reuses the same entry point.
        showTab(TAB_FILES)
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
        startActivity(Intent(this, StoragePermissionActivity::class.java))
    }

    // ---------------------------------------------------------------- first run

    private fun startOnboardingIfNeeded() {
        OnboardingLauncher.showIfNeeded(this) { intent -> onboardingLauncher.launch(intent) }
    }

    private fun showNotificationPermissionSheet() {
        if (supportFragmentManager.findFragmentByTag(NotificationPermissionSheet.TAG) != null) return
        NotificationPermissionSheet().show(supportFragmentManager, NotificationPermissionSheet.TAG)
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
    }
}
