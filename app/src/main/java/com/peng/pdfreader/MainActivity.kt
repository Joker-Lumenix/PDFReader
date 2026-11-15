package com.peng.pdfreader

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.peng.pdfreader.appcore.widget.BottomNavItem
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.base.BaseBackHandler
import com.peng.pdfreader.databinding.ActivityMainBinding
import com.peng.pdfreader.feature.files.fragment.FilesFragment
import com.peng.pdfreader.feature.home.fragment.HomeFragment
import com.peng.pdfreader.feature.settings.fragment.SettingsFragment
import com.peng.pdfreader.feature.tools.fragment.ToolsFragment

/**
 * Application shell.
 *
 * Hosts the four top level tabs and nothing else - the tabs themselves are
 * independent fragments under `feature/<domain>/fragment`.
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
    }

    override fun handleBackPressed(): Boolean {
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current is BaseBackHandler && current.onBackHandled()) {
            return true
        }
        return false
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
    }

    private fun createFragment(tab: Int): Fragment = when (tab) {
        TAB_FILES -> FilesFragment()
        TAB_TOOLS -> ToolsFragment()
        TAB_SETTINGS -> SettingsFragment()
        else -> HomeFragment()
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
        const val TAB_HOME = 0
        const val TAB_FILES = 1
        const val TAB_TOOLS = 2
        const val TAB_SETTINGS = 3
    }
}
