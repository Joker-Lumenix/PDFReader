package com.peng.pdfreader.feature.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.databinding.FragmentSettingsBinding

/**
 * Settings tab: language, sharing, feedback, privacy policy and terms of use.
 */
class SettingsFragment : BaseFragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleBar.title = getString(R.string.nav_settings)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
