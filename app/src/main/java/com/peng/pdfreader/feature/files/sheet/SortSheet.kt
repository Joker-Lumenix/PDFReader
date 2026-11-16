package com.peng.pdfreader.feature.files.sheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.BaseBottomSheetDialog
import com.peng.pdfreader.databinding.FilesSheetSortBinding
import com.peng.pdfreader.feature.files.util.FileSortOption
import com.peng.pdfreader.feature.files.util.FilesPrefs

/**
 * The sort sheet (design: files-选择).
 *
 * Three sections, every option carries a radio. The selection is staged: it is
 * only written back when the sheet is applied, so cancelling keeps the order
 * of the list untouched.
 */
class SortSheet : BaseBottomSheetDialog() {

    private var _binding: FilesSheetSortBinding? = null
    private val binding get() = _binding!!

    /** The option that is currently marked. */
    private var selected = FileSortOption.MODIFIED_NEWEST

    /** All rendered options, so the radios can be refreshed at once. */
    private val options = mutableListOf<Option>()

    private data class Option(
        val label: TextView,
        val radio: ImageView,
        val option: FileSortOption,
    )

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FilesSheetSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selected = FilesPrefs.get(requireContext()).sortOption

        bindOption(binding.tvModifiedDesc, binding.radioModifiedDesc, FileSortOption.MODIFIED_NEWEST)
        bindOption(binding.tvModifiedAsc, binding.radioModifiedAsc, FileSortOption.MODIFIED_OLDEST)
        bindOption(binding.tvNameAsc, binding.radioNameAsc, FileSortOption.NAME_ASC)
        bindOption(binding.tvNameDesc, binding.radioNameDesc, FileSortOption.NAME_DESC)
        bindOption(binding.tvSizeDesc, binding.radioSizeDesc, FileSortOption.SIZE_LARGEST)
        bindOption(binding.tvSizeAsc, binding.radioSizeAsc, FileSortOption.SIZE_SMALLEST)

        binding.rowModifiedDesc.setOnClickListener { pick(FileSortOption.MODIFIED_NEWEST) }
        binding.rowModifiedAsc.setOnClickListener { pick(FileSortOption.MODIFIED_OLDEST) }
        binding.rowNameAsc.setOnClickListener { pick(FileSortOption.NAME_ASC) }
        binding.rowNameDesc.setOnClickListener { pick(FileSortOption.NAME_DESC) }
        binding.rowSizeDesc.setOnClickListener { pick(FileSortOption.SIZE_LARGEST) }
        binding.rowSizeAsc.setOnClickListener { pick(FileSortOption.SIZE_SMALLEST) }

        binding.btnCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnApply.setOnClickListener { apply() }

        renderOptions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        options.clear()
        _binding = null
    }

    private fun bindOption(label: TextView, radio: ImageView, option: FileSortOption) {
        options += Option(label, radio, option)
    }

    private fun pick(option: FileSortOption) {
        selected = option
        renderOptions()
    }

    private fun renderOptions() {
        options.forEach { item ->
            val active = item.option == selected
            item.label.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (active) R.color.brand_primary else R.color.text_secondary,
                ),
            )
            item.radio.setImageResource(
                if (active) {
                    R.mipmap.ic_checkbox_circle_select
                } else {
                    R.mipmap.ic_checkbox_circle_unselect
                },
            )
        }
    }

    private fun apply() {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(KEY_SORT to selected.name),
        )
        dismissAllowingStateLoss()
    }

    companion object {
        /** Fragment result key of this sheet. */
        const val REQUEST_KEY = "com.peng.pdfreader.files.SORT"

        /** Key of the selected sort option inside the result bundle. */
        const val KEY_SORT = "sort"

        const val TAG = "files.sort"

        /** Shows the sort sheet. */
        fun show(manager: FragmentManager) {
            SortSheet().show(manager, TAG)
        }
    }
}
