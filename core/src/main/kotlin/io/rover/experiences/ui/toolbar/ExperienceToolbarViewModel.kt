package io.rover.experiences.ui.toolbar

import io.rover.core.streams.PublishSubject
import io.rover.core.streams.share

class ExperienceToolbarViewModel(
    override val configuration: ToolbarConfiguration
) : ExperienceToolbarViewModelInterface {

    override fun pressedBack() {
        actions.onNext(ExperienceToolbarViewModelInterface.Event.PressedBack())
    }

    override fun pressedClose() {
        actions.onNext(ExperienceToolbarViewModelInterface.Event.PressedClose())
    }

    private val actions = PublishSubject<ExperienceToolbarViewModelInterface.Event>()

    override val toolbarEvents = actions.share()
}
