package com.peng.pdfreader.feature.home.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.databinding.FragmentHomeBinding

/**
 * Home tab.
 *
 * Shows the quick actions, the file type filter strip and the recent document
 * list. The list itself lives in a dedicated adapter, see [HomeFileAdapter].
 */
class HomeFragment : BaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleBar.title = getString(R.string.app_name)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
