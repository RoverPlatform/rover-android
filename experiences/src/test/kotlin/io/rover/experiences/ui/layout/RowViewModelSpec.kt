package io.rover.experiences.ui.layout

import android.content.Context
import io.rover.experiences.ExperiencesAssembler
import io.rover.experiences.MeasurementService
import io.rover.experiences.ui.blocks.concerns.layout.CompositeBlockViewModelInterface
import io.rover.experiences.ui.blocks.concerns.layout.LayoutableViewModel
import io.rover.experiences.ui.blocks.rectangle.RectangleBlockViewModelInterface
import io.rover.experiences.ui.layout.row.RowViewModel
import io.rover.core.assets.AssetService
import io.rover.core.assets.ImageOptimizationServiceInterface
import io.rover.core.container.Assembler
import io.rover.core.container.Container
import io.rover.core.container.InjectionContainer
import io.rover.core.container.Resolver
import io.rover.core.container.Scope
import io.rover.core.data.domain.Height
import io.rover.core.data.domain.HorizontalAlignment
import io.rover.core.data.domain.Position
import io.rover.core.data.domain.VerticalAlignment
import io.rover.core.routing.Router
import io.rover.core.streams.Scheduler
import io.rover.core.ui.RectF
import org.amshove.kluent.mock
import org.amshove.kluent.shouldBeInstanceOf
import org.amshove.kluent.shouldEqual
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

class RowViewModelSpec : Spek({
    describe("integration tests with real block view models") {
        val realObjectStack = InjectionContainer(
            listOf(
                ExperiencesAssembler(),
                // now I need to override certain objects in the experiences assembler with mock ones.
                object : Assembler {
                    override fun assemble(container: Container) {
                        container.register(Scope.Singleton, AssetService::class.java) { resolver ->
                            mock()
                        }

                        container.register(Scope.Singleton, ImageOptimizationServiceInterface::class.java) { resolver ->
                            mock()
                        }

                        container.register(
                            Scope.Singleton,
                            Scheduler::class.java,
                            "main"
                        ) { resolver -> mock() }

                        container.register(
                            Scope.Singleton,
                            MeasurementService::class.java
                        ) { _: Resolver -> mock() }

                        container.register(
                            Scope.Singleton,
                            Router::class.java
                        ) { _ -> mock() }

                        container.register(
                            Scope.Singleton,
                            String::class.java,
                            "deepLinkScheme"
                        ) { _ -> "rv-inbox "}

                        container.register(
                            Scope.Singleton,
                            Context::class.java
                        ) { _ -> mock() }
                    }
                }
            )
        )
        realObjectStack.initializeContainer()

        given("an autoheight row with stacked blocks") {
            val emptyRow = ModelFactories
                .emptyRow()
            val rowViewModel = RowViewModel(
                emptyRow
                    .copy(
                        height = Height.Intrinsic(),
                        blocks = listOf(
                            ModelFactories.emptyRectangleBlock().copy(
                                position = Position(
                                    horizontalAlignment = HorizontalAlignment.Left(
                                        0.0, 40.0
                                    ),
                                    verticalAlignment = VerticalAlignment.Stacked(
                                        0.0, 0.0, Height.Static(20.0)
                                    )
                                )
                            ),
                            ModelFactories.emptyRectangleBlock().copy(
                                position = Position(
                                    horizontalAlignment = HorizontalAlignment.Left(
                                        0.0, 40.0
                                    ),
                                    verticalAlignment = VerticalAlignment.Stacked(
                                        0.0, 0.0, Height.Static(70.0)
                                    )
                                )
                            )
                        )
                    ),
                { block -> realObjectStack.resolve(CompositeBlockViewModelInterface::class.java, null, block)!!},
                mock()
            )

            on("frame()") {
                // row bounds' bottom is given as 0, because rows are always responsible
                // for either setting their own height or measuring their stacked auto-height.
                val frame = rowViewModel.frame(RectF(0f, 0f, 60f, 0f))

                it("expands its height to contain the blocks") {
                    frame.bottom.shouldEqual(90f)
                }
            }

            on("frame() when erroneously given 0 width") {
                val frame = rowViewModel.frame(RectF(0f, 0f, 0f, 0f))

                it("expands its height to contain the blocks") {
                    frame.bottom.shouldEqual(90f)
                }
            }

            on("render()") {
                val layout = rowViewModel.mapBlocksToRectDisplayList(
                    // bottom is given here as 90f because mapBlocksToRectDisplayList must be called
                    // with the fully measured dimensions as returned by frame().
                    RectF(0f, 0f, 60f, 90f)
                )

                it("lays out the blocks in vertical order with no clip") {
                    layout.first().shouldMatch(
                        RectF(0f, 0f, 40f, 20f),
                        RectangleBlockViewModelInterface::class.java,
                        null
                    )
                    layout[1].shouldMatch(
                        RectF(0f, 20f, 40f, 90f),
                        RectangleBlockViewModelInterface::class.java,
                        null
                    )
                }
            }

            on("render() when erroneously given 0 width") {
                val layout = rowViewModel.mapBlocksToRectDisplayList(
                    // bottom is given here as 90f because mapBlocksToRectDisplayList must be called
                    // with the fully measured dimensions as returned by frame().
                    RectF(0f, 0f, 0f, 90f)
                )

                it("lays out the blocks in vertical order with a complete clip") {
                    layout.first().shouldMatch(
                        RectF(0f, 0f, 40f, 20f),
                        RectangleBlockViewModelInterface::class.java,
                        RectF(0f, 0f, 0f, 0f)
                    )
                    layout[1].shouldMatch(
                        RectF(0f, 20f, 40f, 90f),
                        RectangleBlockViewModelInterface::class.java,
                        RectF(0f, 20f, 0f, 20f)
                    )
                }
            }
        }

        given("a non-autoheight row with a floating block that extends outside the top of the row") {
            val rowViewModel = RowViewModel(
                ModelFactories
                    .emptyRow()
                    .copy(
                        height = Height.Static(20.0),
                        blocks = listOf(
                            ModelFactories.emptyRectangleBlock().copy(
                                position = Position(
                                    horizontalAlignment = HorizontalAlignment.Left(
                                        0.0, 40.0
                                    ),
                                    verticalAlignment = VerticalAlignment.Top(
                                        -5.0, Height.Static(10.0)
                                    )
                                )
                            )
                        )
                    ),
                { block -> realObjectStack.resolve(CompositeBlockViewModelInterface::class.java, null, block)!!},
                mock()
            )

            on("frame()") {
                // row bounds' bottom is given as 0, because rows are always responsible
                // for either setting their own height or measuring their stacked auto-height.
                val frame = rowViewModel.frame(RectF(0f, 0f, 60f, 0f))
                it("sets its height as the given value") {
                    frame.bottom.shouldEqual(20f)
                }
            }

            on("render()") {
                val layout = rowViewModel.mapBlocksToRectDisplayList(
                    rowViewModel.frame(RectF(0f, 0f, 40f, 0f))
                )

                it("lays out the floating block with a clip to chop the top bit off") {
                    // the amount that should be clipped off the top is -5
                    layout.first().shouldMatch(
                        RectF(0f, -5f, 40f, 5f),
                        RectangleBlockViewModelInterface::class.java,
                        RectF(0f, 5f, 40f, 10f )
                    )
                }
            }
        }

        given("a non-autoheight row with a floating block that extends outside the bottom of the row") {
            val rowViewModel = RowViewModel(
                ModelFactories
                    .emptyRow()
                    .copy(
                        height = Height.Static(20.0),
                        blocks = listOf(
                            ModelFactories.emptyRectangleBlock().copy(
                                position = Position(
                                    horizontalAlignment = HorizontalAlignment.Left(
                                        0.0, 40.0
                                    ),
                                    verticalAlignment = VerticalAlignment.Top(
                                        15.0,
                                        Height.Static(10.0)
                                    )
                                )
                            )
                        )
                    ),
                { block -> realObjectStack.resolve(CompositeBlockViewModelInterface::class.java, null, block)!!},
                mock()
            )

            on("frame()") {
                // row bounds' bottom is given as 0, because rows are always responsible
                // for either setting their own height or measuring their stacked auto-height.
                val frame = rowViewModel.frame(RectF(0f, 0f, 60f, 0f))
                it("sets its height as the given value") {
                    frame.bottom.shouldEqual(20f)
                }
            }

            on("render()") {
                val layout = rowViewModel.mapBlocksToRectDisplayList(
                    rowViewModel.frame(RectF(0f, 0f, 40f, 0f))
                )

                it("lays out the floating block with a clip to chop the bottom bit off") {
                    // the amount that should be clipped off the bottom is 5
                    layout.first().shouldMatch(
                        RectF(0f, 15f, 40f, 25f),
                        RectangleBlockViewModelInterface::class.java,
                        RectF(0f, 0f, 40f, 5f)
                    )
                }
            }
        }

        given("a non-auto-height row with a floating block") {
            val blockHeight = 10.0
            val blockWidth = 30.0
            val rowHeight = 20.0

            fun nonAutoHeightRowWithFloatingBlock(
                verticalAlignment: VerticalAlignment,
                horizontalAlignment: HorizontalAlignment
            ): RowViewModel {
                return RowViewModel(
                    ModelFactories
                        .emptyRow()
                        .copy(
                            height = Height.Static(rowHeight),
                            blocks = listOf(
                                ModelFactories.emptyRectangleBlock().copy(
                                    position = Position(
                                        horizontalAlignment,
                                        verticalAlignment
                                    )
                                )
                            )
                        ),
                    { block -> realObjectStack.resolve(CompositeBlockViewModelInterface::class.java, null, block)!!},
                    mock()
                )
            }

            given("a non-autoheight row with a floating block is aligned to the bottom") {
                val rowViewModel = nonAutoHeightRowWithFloatingBlock(
                    VerticalAlignment.Bottom(0.0, Height.Static(blockHeight)),
                    HorizontalAlignment.Left(0.0, blockWidth)
                )

                on("render()") {
                    val layout = rowViewModel.mapBlocksToRectDisplayList(
                        rowViewModel.frame(RectF(0f, 0f, 40f, 0f))
                    )

                    it("lays out the block on the bottom") {
                        layout.first().shouldMatch(
                            RectF(0f, 20f - 10f , 30f, 20f),
                            RectangleBlockViewModelInterface::class.java,
                            null
                        )
                    }
                }
            }

            given("a non-autoheight row with a floating block is aligned to the middle (vertical)") {
                val rowViewModel = nonAutoHeightRowWithFloatingBlock(
                    VerticalAlignment.Middle(0.0, Height.Static(blockHeight)),
                    HorizontalAlignment.Left(0.0, blockWidth)
                )

                on("render()") {
                    val layout = rowViewModel.mapBlocksToRectDisplayList(
                        rowViewModel.frame(RectF(0f, 0f, 40f, 0f))
                    )

                    it("lays out the block on the bottom") {
                        layout.first().shouldMatch(
                            RectF(0f, 5f, 30f, 15f),
                            RectangleBlockViewModelInterface::class.java,
                            null
                        )
                    }
                }
            }

            given("a non-autoheight row with a floating block is aligned to the center (horizontal)") {
                val rowViewModel = nonAutoHeightRowWithFloatingBlock(
                    VerticalAlignment.Top(0.0, Height.Static(blockHeight)),
                    HorizontalAlignment.Center(0.0, blockWidth)
                )

                on("render()") {
                    val layout = rowViewModel.mapBlocksToRectDisplayList(
                        rowViewModel.frame(RectF(0f, 0f, 40f, 0f))
                    )

                    it("lays out the block on the bottom") {
                        layout.first().shouldMatch(
                            RectF(5f, 0f, 35f, 10f),
                            RectangleBlockViewModelInterface::class.java,
                            null
                        )
                    }
                }
            }

            given("a non-autoheight row with a floating block that is aligned to the right") {
                val rowViewModel = nonAutoHeightRowWithFloatingBlock(
                    VerticalAlignment.Top(0.0, Height.Static(blockHeight)),
                    HorizontalAlignment.Right(0.0, blockWidth)
                )

                on("render()") {
                    val layout = rowViewModel.mapBlocksToRectDisplayList(
                        rowViewModel.frame(RectF(0f, 0f, 40f, 0f))
                    )

                    it("lays out the block on the bottom") {
                        layout.first().shouldMatch(
                            RectF(10f, 0f, 40f, 10f),
                            RectangleBlockViewModelInterface::class.java,
                            null
                        )
                    }
                }
            }
        }
    }

})

fun DisplayItem.shouldMatch(
    position: RectF,
    type: Class<out LayoutableViewModel>,
    clip: RectF? = null
) {
    this.position.shouldEqual(position)
    this.viewModel.shouldBeInstanceOf(type)
    this.clip.shouldEqual(clip)
}
