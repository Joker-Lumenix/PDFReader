package com.peng.pdfreader.ui.dialogs

import android.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.peng.pdfreader.databinding.PreviewDialogFailureBinding

/**
 * The failure dialog of the preview (design: 失败).
 *
 * It is shown when a document cannot be opened - a broken file or a format the
 * renderer does not understand.
 */
class DialogFailure : DialogFragment() {

    private var _binding: PreviewDialogFailureBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = PreviewDialogFailureBinding.inflate(inflater, container, false)
        binding.tvMessage.text = arguments?.getString(ARG_MESSAGE).orEmpty()
        binding.btnOk.setOnClickListener { dismiss() }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // The scrim of the design is rgba(0,0,0,0.75).
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            setDimAmount(SCRIM_ALPHA)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialog?.setCanceledOnTouchOutside(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "preview_failure_dialog"
        private const val ARG_MESSAGE = "message"
        private const val SCRIM_ALPHA = 0.75f

        /** Shows the dialog with the given message. */
        fun show(manager: FragmentManager, message: String) {
            DialogFailure().apply {
                arguments = Bundle().apply { putString(ARG_MESSAGE, message) }
            }.show(manager, TAG)
        }
    }
}
