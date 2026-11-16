package com.peng.pdfreader.appcore.widget

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
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
 *
 * @param dismissOnClickOutside whether tapping the scrim closes the sheet.
 */
abstract class BaseBottomSheetDialog(
    dismissOnClickOutside: Boolean = true,
) : BottomSheetDialogFragment() {

    /**
     * Whether tapping the scrim closes the sheet.
     *
     * It is passed in through the constructor and kept in the fragment
     * arguments, so it survives a recreation of the dialog.
     */
    var dismissOnClickOutside: Boolean = dismissOnClickOutside
        private set

    /** Whether the back button closes the sheet. */
    var dismissOnBackPress: Boolean = true
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getBoolean(ARG_DISMISS_OUTSIDE)?.let { dismissOnClickOutside = it }
        arguments?.getBoolean(ARG_DISMISS_ON_BACK)?.let { dismissOnBackPress = it }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
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
            // The sheet has to grow with its content and it has to stay above
            // the keyboard, the design shows it right above it.
            val behavior = BottomSheetBehavior.from(this)
            behavior.isFitToContents = true
            behavior.skipCollapsed = true
            if (behavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    /** Builds the sheet body. */
    abstract fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View

    companion object {
        /** Argument key of the "tap outside to dismiss" switch. */
        const val ARG_DISMISS_OUTSIDE = "com.peng.pdfreader.appcore.DISMISS_OUTSIDE"

        /** Argument key of the "back press to dismiss" switch. */
        const val ARG_DISMISS_ON_BACK = "com.peng.pdfreader.appcore.DISMISS_ON_BACK"
    }
}

/**
 * Base class for every centred dialog of the app.
 *
 * The base owns the card geometry (336dp wide, 16dp radius, #fffdfd surface),
 * the scrim and the fade + scale in / out animation. Subclasses provide their
 * own layout and their own interaction.
 *
 * @param dismissOnClickOutside whether tapping the scrim closes the dialog.
 */
abstract class BaseAlertDialog(
    dismissOnClickOutside: Boolean = true,
) : DialogFragment() {

    /**
     * Whether tapping the scrim closes the dialog.
     *
     * It is passed in through the constructor and kept in the fragment
     * arguments, so it survives a recreation of the dialog.
     */
    var dismissOnClickOutside: Boolean = dismissOnClickOutside
        private set

    /** Whether the back button closes the dialog. */
    var dismissOnBackPress: Boolean = true
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getBoolean(BaseBottomSheetDialog.ARG_DISMISS_OUTSIDE)
            ?.let { dismissOnClickOutside = it }
        arguments?.getBoolean(BaseBottomSheetDialog.ARG_DISMISS_ON_BACK)
            ?.let { dismissOnBackPress = it }
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
        dialog?.apply {
            setCanceledOnTouchOutside(dismissOnClickOutside)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(screenWidth() - dp(24), ViewGroup.LayoutParams.WRAP_CONTENT)
                setWindowAnimations(R.style.Animation_AppCore_Dialog)
            }
        }
    }

    /** Builds the dialog body. */
    abstract fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
