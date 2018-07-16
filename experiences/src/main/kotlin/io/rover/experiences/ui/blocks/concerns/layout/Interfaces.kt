package io.rover.experiences.ui.blocks.concerns.layout

import io.rover.experiences.ui.blocks.concerns.border.BorderViewModel
import io.rover.experiences.ui.navigation.NavigateTo
import io.rover.core.ui.RectF
import io.rover.core.ui.concerns.BindableView
import org.reactivestreams.Publisher

/**
 * Binds [BlockViewModelInterface] properties to that of a view.
 *
 * This is responsible for setting padding and anything else relating to block layout.
 */
interface ViewBlockInterface: BindableView<BlockViewModelInterface>

/**
 * The View-side equivalent to [LayoutPaddingDeflection].  This View-side parallel structure needs
 * to exist because the View mixins must not set the padding directly on the Android view (lest
 * they clobber one another), and moreover , so they must delegate that responsibility to [ViewBlock] which will
 * gather up all contributed padding and ultimately apply it to the view.
 */
interface PaddingContributor {
    val contributedPadding: Padding
}

/**
 * Exposed by a view model that may need to contribute to the padding around the content.  For
 * instance, the [BorderViewModel] exposes this so that content-bearing view models can ensure their
 * content is not occluded by the border.
 *
 * Note that the View mixins will likely need to implement the [PaddingContributor] interface and
 * ensure that they are passed to the [ViewBlock].  Please see the documentation there for more
 * details and the rationale.
 */
interface LayoutPaddingDeflection {
    val paddingDeflection: Padding
}

/**
 * Padding values surrounding a rectilinear UI item, in dp.
 */
data class Padding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * Can vertically measure its content for stacked/autoheight purposes.
 */
interface Measurable {
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
interface CompositeBlockViewModelInterface : BlockViewModelInterface

/**
 * A view model for Blocks (particularly, the dynamic layout concerns thereof) that can
 * be laid out in a Rover experience.
 */
interface BlockViewModelInterface : LayoutableViewModel {

    /**
     * The full amount contributed by this block (including its own height and offsets) to the
     * height of all the stacked blocks within the row.  So, the subsequent stacked block must be
     * laid out at a y position that is the sum of all the [stackedHeight]s of all the prior stacked
     * blocks.
     */
    fun stackedHeight(bounds: RectF): Float

    val insets: Insets

    val isStacked: Boolean

    /**
     * Alpha applied to the entire view.
     *
     * Between 0 (transparent) and 1 (fully opaque).
     */
    val opacity: Float

    // val verticalAlignment: Alignment

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
            val navigateTo: NavigateTo,
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