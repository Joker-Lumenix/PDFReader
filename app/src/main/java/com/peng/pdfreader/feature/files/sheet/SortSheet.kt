package com.peng.pdfreader.feature.files.sheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.BaseBottomSheetDialog
import com.peng.pdfreader.data.prefs.AppPrefs
import com.peng.pdfreader.data.prefs.FileSort
import com.peng.pdfreader.databinding.FilesSheetSortBinding

/**
 * The sort sheet (design: files-选择).
 *
 * Three sections, every option carries a radio; the selection is written back
 * into [AppPrefs] by the page that shows this sheet.
 */
class SortSheet : BaseBottomSheetDialog() {

    private var _binding: FilesSheetSortBinding? = null
    private val binding get() = _binding!!

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FilesSheetSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val current = AppPrefs.get(requireContext()).fileSort

        bindOption(binding.rowModified, binding.tvModified, binding.radioModified, FileSort.MODIFIED, current)
        bindOption(binding.rowCreated, binding.tvCreated, binding.radioCreated, FileSort.CREATED, current)
        bindOption(binding.rowName, binding.tvName, binding.radioName, FileSort.NAME, current)
        bindOption(binding.rowSize, binding.tvSize, binding.radioSize, FileSort.SIZE, current)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindOption(
        row: View,
        label: TextView,
        radio: ImageView,
        option: FileSort,
        current: FileSort,
    ) {
        val selected = option == current
        label.setTextColor(
            requireContext().getColor(
                if (selected) R.color.brand_primary else R.color.text_secondary,
            ),
        )
        radio.setImageResource(
            if (selected) R.mipmap.ic_checkbox_circle_select else R.mipmap.ic_checkbox_circle_unselect,
        )
        row.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(KEY_SORT to option.name),
            )
            dismissAllowingStateLoss()
        }
    }

    companion object {
        /** Fragment result key of this sheet. */
        const val REQUEST_KEY = "com.peng.pdfreader.files.SORT"

        /** Key of the selected sort order inside the result bundle. */
        const val KEY_SORT = "sort"

        const val TAG = "files.sort"

        /** Shows the sort sheet. */
        fun show(manager: FragmentManager) {
            SortSheet().show(manager, TAG)
        }
    }
}
