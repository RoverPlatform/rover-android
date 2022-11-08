package io.rover.sdk.experiences.ui.blocks.poll

import android.graphics.RectF

/**
 * Class for tying together some of the drawing concerns of Image and Text polls, the reasoning for
 * this is to reduce the complexity of the views responsible for drawing the poll options.
 */
internal data class RoundRect(val rectF: RectF, val borderRadius: Float, val borderStrokeWidth: Float)
