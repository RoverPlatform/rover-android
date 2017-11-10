package io.rover.rover.ui.viewmodels

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import io.rover.rover.core.domain.FontWeight
import io.rover.rover.core.domain.TextAlignment
import io.rover.rover.core.domain.TextBlock
import io.rover.rover.core.domain.WebViewBlock
import io.rover.rover.ui.MeasurementService
import io.rover.rover.ui.types.Font
import io.rover.rover.ui.types.FontAppearance
import io.rover.rover.ui.types.ViewType
import io.rover.rover.ui.views.asAndroidColor

class TextBlockViewModel(
    private val block: TextBlock,
    private val measurementService: MeasurementService,
    private val blockViewModel: BlockViewModelInterface,
    private val textViewModel: TextViewModelInterface,
    private val backgroundViewModel: BackgroundViewModelInterface,
    private val borderViewModel: BorderViewModelInterface
): TextBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    BackgroundViewModelInterface by backgroundViewModel,
    TextViewModelInterface by textViewModel,
    BorderViewModelInterface by borderViewModel {

    override val viewType: ViewType = ViewType.Text
}
