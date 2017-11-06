package io.rover.rover.ui.types

import android.graphics.Typeface

/**
 * A specific font in the font-family and style tuple appropriate for Android.
 */
data class Font(
    /**
     * A font family name.
     */
    val fontFamily: String,

    /**
     * An Android style value of either [Typeface.NORMAL], [Typeface.BOLD], [Typeface.ITALIC], or
     * [Typeface.BOLD_ITALIC].
     */
    val fontStyle: Int
)