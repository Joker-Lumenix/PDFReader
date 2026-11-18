package com.peng.pdfreader.core.widget

import android.graphics.ColorMatrixColorFilter

/**
 * Filters that turn an icon of the app into the glyph of the design.
 *
 * The icons of the app carry their glyph inside a dark tile, the drawings
 * show the glyph alone. The filter drops the tile and keeps the white glyph.
 */
object IconFilters {

    /**
     * Keeps the white glyph of an icon and drops the dark tile behind it.
     */
    /**
     * Keeps the white glyph of an icon and drops the dark tile behind it.
     *
     * The matrix turns the brightness of a pixel into its colour and its
     * alpha: the dark tile of the icon ends up transparent and the white
     * glyph keeps its shape. Every row carries the same value, so the colour
     * and the alpha of a pixel stay in step.
     */
    fun whiteGlyph(): ColorMatrixColorFilter = ColorMatrixColorFilter(
        floatArrayOf(
            0.417f, 0.417f, 0.417f, 0f, -63.8f,
            0.417f, 0.417f, 0.417f, 0f, -63.8f,
            0.417f, 0.417f, 0.417f, 0f, -63.8f,
            0.417f, 0.417f, 0.417f, 0f, -63.8f,
        ),
    )
}
