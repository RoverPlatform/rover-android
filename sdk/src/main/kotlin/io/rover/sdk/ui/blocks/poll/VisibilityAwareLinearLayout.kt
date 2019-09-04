package io.rover.sdk.ui.blocks.poll

import android.content.Context
import android.widget.LinearLayout
import io.rover.sdk.logging.log
import io.rover.sdk.streams.PublishSubject

internal open class VisibilityAwareLinearLayout(context: Context?): LinearLayout(context),
    VisibilityAwareView {

    override fun onWindowVisibilityChanged(visible: Int) {
        super.onWindowVisibilityChanged(visible)
        visibilitySubject.onNext(visible)
    }

    override val visibilitySubject: PublishSubject<Int> = PublishSubject()
}

internal interface VisibilityAwareView {
    val visibilitySubject: PublishSubject<Int>
}