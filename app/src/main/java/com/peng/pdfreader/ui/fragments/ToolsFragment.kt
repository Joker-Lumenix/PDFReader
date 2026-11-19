package com.peng.pdfreader.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.peng.pdfreader.core.widget.AdSlotView
import com.peng.pdfreader.ads.Ads
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.databinding.FragmentToolsBinding
import com.peng.pdfreader.ui.activities.ToolFilePickerActivity
import com.peng.pdfreader.ui.uiData.AdSamples
import com.peng.pdfreader.ui.activities.ImageToPdfActivity
import com.peng.pdfreader.ui.activities.MergeActivity
import com.peng.pdfreader.feature.tools.scanner.ToolScanner

/**
 * Tools tab: the yellow panel with the 2 x 4 grid of the PDF tools (merge,
 * split, image to PDF, scan, lock, unlock, print, zip).
 */
class ToolsFragment : BaseFragment() {

    /**
     * The tab sits above the bottom navigation bar of the shell, the bar takes
     * the inset of the navigation bar itself.
     */
    override val takesBottomInset: Boolean = false

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

        // The banner ad of the design sits below the grid of the tools. The
        // slot shows the sample artwork until the ad arrives.
        binding.adBanner.style = AdSlotView.Style.BANNER
        binding.adBanner.content = AdSamples.banner(requireContext())
        Ads.loadBanner(binding.adBanner)

        initToolSelected()
    }

    private fun initToolSelected() {

        binding.llImageToPDF.setOnClickListener {
            ImageToPdfActivity.start(requireContext())
        }
        // Every tool that works on a document of the sandbox starts with the
        // same file selection page (design "lock PDF").
        binding.llSplitPDF.setOnClickListener {
            ToolFilePickerActivity.start(requireContext(), ToolFilePickerActivity.Mode.SPLIT)
        }
        binding.llMergePDF.setOnClickListener {
            MergeActivity.start(requireContext())
        }
        binding.llScanPDF.setOnClickListener {
            ToolScanner.launch(this)
        }
        binding.llLockPDF.setOnClickListener {
            ToolFilePickerActivity.start(requireContext(), ToolFilePickerActivity.Mode.LOCK)
        }
        binding.llUnlockPDF.setOnClickListener {
            ToolFilePickerActivity.start(requireContext(), ToolFilePickerActivity.Mode.UNLOCK)
        }
        binding.llPrintPDF.setOnClickListener {
            ToolFilePickerActivity.start(requireContext(), ToolFilePickerActivity.Mode.PRINT)
        }
        binding.llCompressPDF.setOnClickListener {
            ToolFilePickerActivity.start(requireContext(), ToolFilePickerActivity.Mode.COMPRESS)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
