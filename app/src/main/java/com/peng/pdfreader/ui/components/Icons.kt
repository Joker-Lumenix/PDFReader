package com.peng.pdfreader.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Minimal Material-style icon set. The project only ships compose-material3 (core icons),
 * so the extra glyphs used by the designs are defined here as plain vector paths.
 */
object PdfIcons {

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = addPathNodes(pathData),
                fill = SolidColor(Color.Black),
            )
        }.build()

    val Document: ImageVector by lazy {
        icon(
            "Document",
            "M6,2h8l4,4v16H6zM13,3.5V7h3.5z",
        )
    }
}
