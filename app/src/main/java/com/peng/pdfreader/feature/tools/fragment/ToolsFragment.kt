package com.peng.pdfreader.feature.tools.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.databinding.FragmentToolsBinding

/**
 * Tools tab: the grid of PDF tools (merge, split, compress, image to PDF,
 * scan, lock, unlock, print).
 */
class ToolsFragment : BaseFragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleBar.title = getString(R.string.nav_tools)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
