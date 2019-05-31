package io.rover.sdk.ui.blocks.poll

import io.rover.sdk.data.domain.QuestionStyle
import io.rover.sdk.data.domain.TextPollBlock
import io.rover.sdk.data.domain.TextPollBlockOptionStyle
import io.rover.sdk.streams.Publishers
import io.rover.sdk.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.sdk.ui.blocks.concerns.border.BorderViewModelInterface
import io.rover.sdk.ui.blocks.concerns.layout.BlockViewModelInterface
import io.rover.sdk.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.sdk.ui.layout.ViewType
import org.reactivestreams.Publisher

internal class TextPollBlockViewModel(
    val textPollBlock: TextPollBlock,
    private val blockViewModel: BlockViewModelInterface,
    private val backgroundViewModel: BackgroundViewModelInterface,
    private val borderViewModel: BorderViewModelInterface
) : CompositeBlockViewModelInterface,
    BlockViewModelInterface by blockViewModel,
    BackgroundViewModelInterface by backgroundViewModel,
    BorderViewModelInterface by borderViewModel {
    override val viewType: ViewType = ViewType.Poll

    val pollState: Publisher<PollState> = Publishers.just(PollState.LoadingState)
}

sealed class PollState {
    object ResultState : PollState()
    data class VotingState(val textPollBlock: TextPollBlock) : PollState()
    object LoadingState : PollState()
}


interface PollsService {
    fun vote()
    fun getVotes()
    fun getUserVote()
}

