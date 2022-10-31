package io.rover.experiences.ui.blocks.concerns.layout

import io.rover.experiences.ui.RectF
import io.rover.experiences.ui.concerns.MeasuredBindableView
import io.rover.experiences.ui.navigation.NavigateToFromBlock
import org.reactivestreams.Publisher

/**
 * Binds [BlockViewModelInterface] properties to that of a view.
 *
 * This is responsible for setting padding and anything else relating to block layout.
 */
internal interface ViewBlockInterface : MeasuredBindableView<BlockViewModelInterface>

/**
 * Exposed by a view model that may need to contribute to the Android view padding around the
 * content, which insets.
 *
 * It should be then passed into [BlockViewModel] as a Padding Contributor, because it is the
 * [BlockViewModel] and associated [ViewBlock] that are responsible for managing the Android padding
 * values.
 *
 * Note that this should not be added to a view model's interface; it that is done it will be mixed
 * into any surrounding view block, where it is not used and will be misleading.
 */
internal interface LayoutPaddingDeflection {
    val paddingDeflection: Padding
}

/**
 * Padding values surrounding a rectilinear UI item, in dp.
 */
internal data class Padding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    operator fun plus(increment: Padding): Padding {
        return Padding(
            left + increment.left,
            top + increment.top,
            right + increment.right,
            bottom + increment.bottom
        )
    }
}

/**
 * Can vertically measure its content for stacked/autoheight purposes.
 */
internal interface Measurable {
    /**
     * Measure the "natural" height for the content contained in this block (for
     * example, a wrapped block of text will consume up to some height depending on content and
     * other factors), given the width of the bounds.  Used for our auto-height feature.
     */
    fun intrinsicHeight(bounds: RectF): Float
}

/**
 * This is a complete "top-level" block within an experience row.
 *
 * This exists to discriminate between BlockViewModelInterface, which is specifically only block
 * level concerns shared by all blocks, but has its own mixin implementation -- BlockViewModel --
 * that would cause an ambiguity a category for the fully block objects themselves.
  */
internal interface CompositeBlockViewModelInterface : BlockViewModelInterface

/**
 * A view model for Blocks (particularly, the dynamic layout concerns thereof) that can
 * be laid out in a Rover experience.
 */
internal interface BlockViewModelInterface : LayoutableViewModel {

    /**
     * The full amount contributed by this block (including its own height and offsets) to the
     * height of all the stacked blocks within the row.  So, the subsequent stacked block must be
     * laid out at a y position that is the sum of all the [stackedHeight]s of all the prior stacked
     * blocks.
     */
    fun stackedHeight(bounds: RectF): Float

    val insets: Insets

    val padding: Padding

    val isStacked: Boolean

    /**
     * Alpha applied to the entire view.
     *
     * Between 0 (transparent) and 1 (fully opaque).
     */
    val opacity: Float

    fun width(bounds: RectF): Float

    // TODO: the following may be moved into a mixin view model for Interactions, even though our
    // domain model atm has block actions being included for all blocks.

    /**
     * The view is clickable.
     */
    val isClickable: Boolean

    /**
     * User has clicked the view.
     */
    fun click()

    /**
     * User has touched the view, but not necessarily clicked it.
     */
    fun touched()

    /**
     * User has released the view, but not necessarily clicked it.
     */
    fun released()

    sealed class Event(
        open val blockId: String
    ) {
        /**
         * Block has been clicked, requesting that we [navigateTo] something.
         */
        data class Clicked(
            val navigateTo: NavigateToFromBlock,
            override val blockId: String
        ) : Event(blockId)

        /**
         * Block has been touched, but not clicked.
         */
        class Touched(
            override val blockId: String
        ) : Event(blockId)

        /**
         * Block has been released, but not necessarily clicked.
         */
        class Released(
            override val blockId: String
        ) : Event(blockId)
    }

    val events: Publisher<Event>
}

data class Insets(
    val top: Int,
    val left: Int,
    val bottom: Int,
    val right: Int
)