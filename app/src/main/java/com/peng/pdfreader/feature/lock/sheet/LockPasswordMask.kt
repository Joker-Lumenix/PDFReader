package com.peng.pdfreader.feature.lock.sheet

import android.text.method.PasswordTransformationMethod

/**
 * The password mask of the design.
 *
 * The design shows the typed password as a row of asterisks and not as the
 * system bullets, so the sheet uses its own transformation.
 */
class LockPasswordMask : PasswordTransformationMethod() {

    override fun getTransformation(source: CharSequence, view: android.view.View): CharSequence =
        MaskedText(source)

    /** Shows one asterisk per character, the text itself stays untouched. */
    private class MaskedText(private val source: CharSequence) : CharSequence {

        override val length: Int get() = source.length

        override fun get(index: Int): Char {
            if (index < 0 || index >= source.length) throw IndexOutOfBoundsException(index.toString())
            return '*'
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            MaskedText(source.subSequence(startIndex, endIndex))

        override fun toString(): String = buildString { repeat(length) { append('*') } }
    }
}
