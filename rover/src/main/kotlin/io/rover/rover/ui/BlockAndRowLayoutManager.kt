package io.rover.rover.ui

import android.support.v7.widget.RecyclerView
import android.view.View
import io.rover.rover.core.domain.Block
import io.rover.rover.core.domain.Row
import io.rover.rover.core.domain.Screen

/**
 * A [RecyclerView.LayoutManager] that can position Rover UI elements (namely, all the [Row]s in
 * a [Screen] and the various varieties of [Block]s they can contain within a [RecyclerView].  It
 * does
 *
 * Most of the heavy lifting is done in an implementation of [RoverLayout].
 */
class BlockAndRowLayoutManager : RecyclerView.LayoutManager() {
    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams = RecyclerView.LayoutParams(
        RecyclerView.LayoutParams.WRAP_CONTENT,
        RecyclerView.LayoutParams.WRAP_CONTENT
    )
}