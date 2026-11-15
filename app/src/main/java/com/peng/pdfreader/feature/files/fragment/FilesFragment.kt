package com.peng.pdfreader.feature.files.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.databinding.FragmentFilesBinding

/**
 * Files tab: the full document library with search, filter, sorting and
 * multi select. The list itself lives in a dedicated adapter.
 */
class FilesFragment : BaseFragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleBar.title = getString(R.string.nav_files)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
