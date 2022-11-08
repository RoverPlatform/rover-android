package io.rover.sdk.experiences.ui.toolbar

import io.rover.sdk.core.streams.PublishSubject
import io.rover.sdk.core.streams.share

internal class ExperienceToolbarViewModel(
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
