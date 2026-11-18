package com.peng.pdfreader.base

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.peng.pdfreader.R
import com.peng.pdfreader.core.util.SystemBarInsets

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

    /**
     * The container of the sheet body.
     *
     * It carries the rounded surface of the design. A sheet that draws its own
     * surface - the rating sheet with its illustration - clears it.
     */
    protected var sheetContainer: FrameLayout? = null
        private set

    /**
     * The body of the sheet.
     *
     * It must not paint a surface of its own: a surface of its own would
     * cover the rounded corners of the container.
     */
    private var sheetContent: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A key that is not in the bundle must not switch the behaviour off:
        // the bundle of a sheet usually carries other values and getBoolean
        // answers false for a key that is not there.
        arguments?.let { args ->
            if (args.containsKey(ARG_DISMISS_OUTSIDE)) {
                dismissOnClickOutside = args.getBoolean(ARG_DISMISS_OUTSIDE)
            }
            if (args.containsKey(ARG_DISMISS_ON_BACK)) {
                dismissOnBackPress = args.getBoolean(ARG_DISMISS_ON_BACK)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        dialog.setCanceledOnTouchOutside(dismissOnClickOutside)
        // The window must not paint a surface of its own. Material gives the
        // window a white panel and that panel shows behind the rounded top
        // corners of the design, which makes them look square.
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        isCancelable = dismissOnBackPress
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val sheet = inflater.inflate(R.layout.widget_bottom_sheet, container, false) as FrameLayout
        // A dialog fragment has no container, so the inflated root carries no
        // layout params at all. The bottom sheet would then fall back to
        // MATCH_PARENT x MATCH_PARENT, cover the whole page and leave no room
        // for the scrim. The sheet is as tall as its content.
        sheet.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        // The surface of the design is the background of the container itself
        // (16dp radius at the top, square corners at the bottom). The panel
        // of the dialog stays transparent, see onStart.
        sheetContainer = sheet
        val content = createContentView(inflater, sheet)
        content.background = null
        sheetContent = content
        sheet.addView(content)
        return sheet
    }

    override fun onStart() {
        super.onStart()
        // The scrim of the design is a 75 % black layer over the page.
        dialog?.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(SCRIM_ALPHA)
        }
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            // The sheet has to grow with its content and it has to stay above
            // the keyboard, the design shows it right above it.
            val behavior = BottomSheetBehavior.from(this)
            behavior.isFitToContents = true
            behavior.skipCollapsed = true
            // The sheets of the design are fixed panels: they are opened
            // completely and they cannot be dragged.
            behavior.isDraggable = false
            // A sheet that is taller than the screen stops at the height of
            // the screen, its body scrolls inside of it.
            behavior.maxHeight = screenHeight()
            if (behavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            // The panel of the Material dialog paints a white surface of its
            // own and that surface is square, so it shows through the rounded
            // corners of the design. Material applies its background in the
            // onStart of the dialog, which runs after this callback, so the
            // panel is cleared once the dialog is on the screen.
            //
            // After that the only surface that is visible is the one of the
            // container: the panel of the design, a white surface with a 16dp
            // radius at the top and square corners at the bottom. The body of
            // the sheet stays transparent, a surface of its own would cover
            // the rounded corners as well.
            post {
                background = null
                dialog?.window?.decorView?.setBackgroundColor(Color.TRANSPARENT)
                sheetContainer?.background = if (hasSurface) {
                    ContextCompat.getDrawable(requireContext(), R.drawable.bg_sheet_top_r16)
                } else {
                    null
                }
                if (hasSurface) {
                    sheetContent?.background = null
                }
                applyNavigationBarInset()
            }
        }
        // Tapping the page behind the sheet closes it.
        dialog?.findViewById<View>(com.google.android.material.R.id.touch_outside)
            ?.setOnClickListener {
                if (dismissOnClickOutside) dismissAllowingStateLoss()
            }
    }

    /**
     * The bottom of the sheet stays above the navigation bar.
     *
     * The app is drawn edge to edge, so on Android 15 and later the sheet
     * reaches the bottom of the screen and the navigation bar would cover its
     * last row. The inset is added to the padding of the container. Before
     * Android 15 the window of the dialog is already inset by the system and
     * the inset is zero, so nothing changes there.
     */
    private fun applyNavigationBarInset() {
        val container = sheetContainer ?: return
        val base = container.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            view.updatePadding(bottom = base + SystemBarInsets.bottom(insets))
            insets
        }
        ViewCompat.requestApplyInsets(container)
    }

    /**
     * Whether the sheet shows the surface of the design.
     *
     * The rating sheet stands on the scrim of the page, it has no surface of
     * its own.
     */
    protected open val hasSurface: Boolean = true

    /** The height of the screen, a sheet never grows beyond it. */
    protected fun screenHeight(): Int = resources.displayMetrics.heightPixels

    /** Builds the sheet body. */
    abstract fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View

    override fun onDestroyView() {
        super.onDestroyView()
        sheetContainer = null
        sheetContent = null
    }

    companion object {
        /** Argument key of the "tap outside to dismiss" switch. */
        const val ARG_DISMISS_OUTSIDE = "com.peng.pdfreader.core.DISMISS_OUTSIDE"

        /** Argument key of the "back press to dismiss" switch. */
        const val ARG_DISMISS_ON_BACK = "com.peng.pdfreader.core.DISMISS_ON_BACK"

        /** The scrim of the design: 75 % black over the page. */
        private const val SCRIM_ALPHA = 0.75f

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
        // A key that is not in the bundle must not switch the behaviour off:
        // the bundle of a dialog usually carries other values and getBoolean
        // answers false for a key that is not there.
        arguments?.let { args ->
            if (args.containsKey(BaseBottomSheetDialog.ARG_DISMISS_OUTSIDE)) {
                dismissOnClickOutside = args.getBoolean(BaseBottomSheetDialog.ARG_DISMISS_OUTSIDE)
            }
            if (args.containsKey(BaseBottomSheetDialog.ARG_DISMISS_ON_BACK)) {
                dismissOnBackPress = args.getBoolean(BaseBottomSheetDialog.ARG_DISMISS_ON_BACK)
            }
        }
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





