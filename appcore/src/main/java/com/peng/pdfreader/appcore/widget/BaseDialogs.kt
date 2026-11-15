package com.peng.pdfreader.appcore.widget

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.peng.pdfreader.appcore.R

/**
 * Base class for every bottom sheet of the app.
 *
 * The base owns the parts that must look and behave the same everywhere:
 *
 * - the surface (#fffdfd, 16dp top corners, square bottom corners)
 * - the scrim (75% black) and the slide in / slide out animation
 * - the "tap outside to dismiss" and "back press to dismiss" switches
 *
 * Subclasses only provide their own layout and their own behaviour.
 */
abstract class BaseBottomSheetDialog : BottomSheetDialogFragment() {

    /**
     * Whether tapping the scrim closes the sheet.
     *
     * Subclasses may override it or pass it through the constructor.
     */
    open val dismissOnClickOutside: Boolean get() = true

    /** Whether the back button closes the sheet. */
    open val dismissOnBackPress: Boolean get() = true

    /** Builds the sheet body. */
    abstract fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        dialog.setCanceledOnTouchOutside(dismissOnClickOutside)
        isCancelable = dismissOnBackPress
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val sheet = inflater.inflate(R.layout.widget_bottom_sheet, container, false) as FrameLayout
        sheet.addView(createContentView(inflater, sheet))
        return sheet
    }

    override fun onStart() {
        super.onStart()
        // The container draws the rounded surface itself, so the default sheet
        // background has to go.
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            background = ColorDrawable(Color.TRANSPARENT)
        }
    }
}

/**
 * Base class for every centred dialog of the app.
 *
 * The base owns the card geometry (336dp wide, 16dp radius, #fffdfd surface),
 * the scrim and the fade + scale in / out animation. Subclasses provide their
 * own layout and their own interaction.
 */
abstract class BaseAlertDialog : DialogFragment() {

    /** Whether tapping the scrim closes the dialog. */
    open val dismissOnClickOutside: Boolean get() = true

    /** Whether the back button closes the dialog. */
    open val dismissOnBackPress: Boolean get() = true

    /** Builds the dialog body. */
    abstract fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = dismissOnBackPress
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val card = inflater.inflate(R.layout.widget_base_dialog, container, false) as FrameLayout
        card.addView(createContentView(inflater, card))
        return card
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(screenWidth() - dp(24), ViewGroup.LayoutParams.WRAP_CONTENT)
            setWindowAnimations(R.style.Animation_AppCore_Dialog)
        }
    }

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
