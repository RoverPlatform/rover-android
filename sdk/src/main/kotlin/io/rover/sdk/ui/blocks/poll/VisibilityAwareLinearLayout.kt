package io.rover.sdk.ui.blocks.poll

import android.content.Context
import android.widget.LinearLayout
import io.rover.sdk.streams.PublishSubject

internal open class VisibilityAwareLinearLayout(context: Context?): LinearLayout(context),
    VisibilityAwareView {

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        visibilitySubject.onNext(hasWindowFocus)
    }

    override val visibilitySubject: PublishSubject<Boolean> = PublishSubject()
}

internal interface VisibilityAwareView {
    val visibilitySubject: PublishSubject<Boolean>
}