package com.peng.pdfreader.feature.tools.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.AdSlotView
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.databinding.FragmentToolsBinding
import com.peng.pdfreader.feature.lock.activity.LockActivity
import com.peng.pdfreader.feature.lock.activity.UnlockActivity
import com.peng.pdfreader.feature.settings.widget.AdSamples
import com.peng.pdfreader.feature.tools.activity.ImageToPdfActivity
import com.peng.pdfreader.feature.tools.activity.MergeActivity
import com.peng.pdfreader.feature.tools.activity.SplitActivity
import com.peng.pdfreader.feature.tools.adapter.ToolGridAdapter
import com.peng.pdfreader.feature.tools.adapter.ToolGridDecoration
import com.peng.pdfreader.feature.tools.dialog.ToolDocumentPickerDialog
import com.peng.pdfreader.feature.tools.model.ToolAction
import com.peng.pdfreader.feature.tools.pack.ZipRunner
import com.peng.pdfreader.feature.tools.print.ToolPrintRunner
import com.peng.pdfreader.feature.tools.scanner.ToolScanner
import com.peng.pdfreader.feature.tools.util.ToolShapes

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
        binding.titleBar.title = getString(R.string.tools)

        // The yellow panel of the design only exists in the design, so its
        // surface is built here.
        binding.panel.background = ToolShapes.roundRect(
            requireContext(),
            ToolShapes.PANEL_YELLOW,
            13f,
        )

        val adapter = ToolGridAdapter { item -> onToolSelected(item.action) }
        binding.rvTools.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvTools.adapter = adapter
        binding.rvTools.addItemDecoration(
            ToolGridDecoration(
                spanCount = 2,
                cellWidthPx = dp(CARD_WIDTH_DP),
                rowSpacingPx = dp(ROW_SPACING_DP),
                itemCount = { adapter.itemCount },
            ),
        )

        // The banner ad of the design sits below the grid of the tools.
        binding.adBanner.style = AdSlotView.Style.BANNER
        binding.adBanner.content = AdSamples.banner(requireContext())
    }

    private fun onToolSelected(action: ToolAction) {
        when (action) {
            ToolAction.MERGE -> MergeActivity.start(requireContext())
            ToolAction.SPLIT -> SplitActivity.start(requireContext())
            ToolAction.IMAGE_TO_PDF -> ImageToPdfActivity.start(requireContext())
            ToolAction.SCAN -> ToolScanner.launch(this)
            // Lock and unlock live in their own feature, it owns the password sheet.
            ToolAction.LOCK -> LockActivity.start(requireContext())
            ToolAction.UNLOCK -> UnlockActivity.start(requireContext())
            ToolAction.PRINT -> pickDocumentThen { doc -> ToolPrintRunner.print(this, doc) }
            ToolAction.ZIP -> pickDocumentThen { doc -> ZipRunner.pack(this, doc) }
        }
    }

    private fun pickDocumentThen(action: (Document) -> Unit) {
        ToolDocumentPickerDialog.show(childFragmentManager) { doc -> action(doc) }
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Width of a tool card of the design. */
        private const val CARD_WIDTH_DP = 133

        /** Distance between two rows of the tool grid of the design. */
        private const val ROW_SPACING_DP = 20
    }
}
