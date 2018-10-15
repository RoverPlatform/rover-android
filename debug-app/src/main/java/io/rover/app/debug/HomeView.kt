package io.rover.app.debug

import android.content.Context
import android.support.constraint.ConstraintLayout
import android.util.AttributeSet

class HomeView : ConstraintLayout {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        // host PreferenceFragment somehow?
    }
}
