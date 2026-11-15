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
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.prefs.AppPrefs
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.FilesSheetFilterBinding

/**
 * The type filter sheet (design: files-筛选).
 *
 * Every option shows how many documents of that type the sandbox holds; the
 * selection is written back into [AppPrefs] by the page that shows this sheet.
 */
class FileFilterSheet : BaseBottomSheetDialog() {

    private var _binding: FilesSheetFilterBinding? = null
    private val binding get() = _binding!!

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FilesSheetFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val current = AppPrefs.get(requireContext()).fileFilter
        val counts = DocumentRepository.get(requireContext()).all()
            .groupingBy { it.type }
            .eachCount()
        val total = counts.values.sum()

        bindOption(binding.rowAll, binding.tvAll, binding.radioAll, null, total, current)
        bindOption(binding.rowPdf, binding.tvPdf, binding.radioPdf, DocType.PDF, counts[DocType.PDF] ?: 0, current)
        bindOption(binding.rowWord, binding.tvWord, binding.radioWord, DocType.WORD, counts[DocType.WORD] ?: 0, current)
        bindOption(binding.rowExcel, binding.tvExcel, binding.radioExcel, DocType.EXCEL, counts[DocType.EXCEL] ?: 0, current)
        bindOption(binding.rowPpt, binding.tvPpt, binding.radioPpt, DocType.PPT, counts[DocType.PPT] ?: 0, current)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindOption(
        row: View,
        label: TextView,
        radio: ImageView,
        type: DocType?,
        count: Int,
        current: DocType?,
    ) {
        val selected = type == current
        val name = if (type == null) getString(R.string.all) else getString(type.labelRes)
        label.text = "$name($count)"
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
                bundleOf(KEY_FILTER to (type?.name ?: FILTER_ALL)),
            )
            dismissAllowingStateLoss()
        }
    }

    companion object {
        /** Fragment result key of this sheet. */
        const val REQUEST_KEY = "com.peng.pdfreader.files.FILTER"

        /** Key of the selected type inside the result bundle. */
        const val KEY_FILTER = "filter"

        /** Value of [KEY_FILTER] for the "all types" option. */
        const val FILTER_ALL = "ALL"

        const val TAG = "files.filter"

        /** Shows the type filter sheet. */
        fun show(manager: FragmentManager) {
            FileFilterSheet().show(manager, TAG)
        }
    }
}
