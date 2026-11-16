package com.peng.pdfreader.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.peng.pdfreader.appcore.widget.AdSlotView
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.databinding.FragmentToolsBinding
import com.peng.pdfreader.ui.activities.LockActivity
import com.peng.pdfreader.ui.activities.UnlockActivity
import com.peng.pdfreader.ui.uiData.AdSamples
import com.peng.pdfreader.ui.activities.ImageToPdfActivity
import com.peng.pdfreader.ui.activities.MergeActivity
import com.peng.pdfreader.ui.activities.SplitActivity
import com.peng.pdfreader.ui.dialogs.BottomSheetToolDocumentPicker
import com.peng.pdfreader.feature.tools.pack.ZipRunner
import com.peng.pdfreader.feature.tools.print.ToolPrintRunner
import com.peng.pdfreader.feature.tools.scanner.ToolScanner

/**
 * Tools tab: the yellow panel with the 2 x 4 grid of the PDF tools (merge,
 * split, image to PDF, scan, lock, unlock, print, zip).
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

        // The banner ad of the design sits below the grid of the tools.
        binding.adBanner.style = AdSlotView.Style.BANNER
        binding.adBanner.content = AdSamples.banner(requireContext())

        initToolSelected()
    }

    private fun initToolSelected() {

        binding.llImageToPDF.setOnClickListener {
            ImageToPdfActivity.start(requireContext())
        }
        binding.llSplitPDF.setOnClickListener {
            SplitActivity.start(requireContext())
        }
        binding.llMergePDF.setOnClickListener {
            MergeActivity.start(requireContext())
        }
        binding.llScanPDF.setOnClickListener {
            ToolScanner.launch(this)
        }
        binding.llLockPDF.setOnClickListener {
            LockActivity.start(requireContext())
        }
        binding.llUnlockPDF.setOnClickListener {
            UnlockActivity.start(requireContext())
        }
        binding.llPrintPDF.setOnClickListener {
            pickDocumentThen { doc -> ToolPrintRunner.print(this, doc) }
        }
        binding.llCompressPDF.setOnClickListener {
            pickDocumentThen { doc -> ZipRunner.pack(this, doc) }
        }
    }

    private fun pickDocumentThen(action: (Document) -> Unit) {
        BottomSheetToolDocumentPicker.show(childFragmentManager) { doc -> action(doc) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
