package io.rover.campaigns.experiences.ui.toolbar

import androidx.appcompat.widget.Toolbar
import org.reactivestreams.Publisher

internal interface ViewExperienceToolbarInterface {
    /**
     * Set the toolbar view model.  However, uncharacteristically of the other bindable view mixins,
     * this one is a method that returns a new [Toolbar] view.  This must be done because
     * Android's Toolbar has limitations that prevent all of its styling being configurable after
     * creation. Thus, uncharacteristically of the View mixins, this one is responsible for actually
     * creating the view.
     */
    fun setViewModelAndReturnToolbar(
        toolbarViewModel: ExperienceToolbarViewModelInterface
    ): Toolbar
}

internal interface ExperienceToolbarViewModelInterface {
    val toolbarEvents: Publisher<Event>

    val configuration: ToolbarConfiguration

    fun pressedBack()

    fun pressedClose()

    sealed class Event {
        class PressedBack : Event()
        class PressedClose : Event()
    }
}