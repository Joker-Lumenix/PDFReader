package com.peng.pdfreader.feature.tools.scanner

import android.content.Intent
import androidx.fragment.app.Fragment
import com.peng.pdfreader.ui.activities.ScanActivity

/**
 * Starts the "scan to PDF" flow.
 */
object ToolScanner {

    /**
     * Opens the camera and turns the photo into a document.
     */
    fun launch(fragment: Fragment) {
        val context = fragment.requireContext()
        fragment.startActivity(Intent(context, ScanActivity::class.java))
    }
}
