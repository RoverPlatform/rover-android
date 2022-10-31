package io.rover.campaigns.experiences.ui.layout.row

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import androidx.annotation.RequiresApi
import android.util.AttributeSet
import android.view.View
import io.rover.campaigns.experiences.ui.blocks.concerns.ViewComposition
import io.rover.campaigns.experiences.ui.blocks.concerns.background.ViewBackground
import io.rover.campaigns.experiences.ui.blocks.concerns.layout.LayoutableView
import io.rover.campaigns.experiences.ui.concerns.MeasuredBindableView
import io.rover.campaigns.experiences.ui.concerns.ViewModelBinding

internal class RowView : View, LayoutableView<RowViewModelInterface> {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    private val viewComposition = ViewComposition()
    private val viewBackground = ViewBackground(this)

    override var viewModelBinding: MeasuredBindableView.Binding<RowViewModelInterface>? by ViewModelBinding { binding, _ ->
        viewBackground.viewModelBinding = binding
    }

    override fun draw(canvas: Canvas) {
        viewComposition.beforeDraw(canvas)
        super.draw(canvas)
        viewComposition.afterDraw(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewComposition.onSizeChanged(w, h, oldw, oldh)
    }
}
