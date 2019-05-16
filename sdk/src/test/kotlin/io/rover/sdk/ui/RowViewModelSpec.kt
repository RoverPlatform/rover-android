package io.rover.sdk.ui

import com.nhaarman.mockitokotlin2.mock
import io.rover.helpers.shouldEqual
import io.rover.sdk.ViewModels
import io.rover.sdk.data.domain.Height
import io.rover.sdk.data.domain.HorizontalAlignment
import io.rover.sdk.data.domain.Position
import io.rover.sdk.data.domain.VerticalAlignment
import io.rover.sdk.ui.blocks.rectangle.RectangleBlockViewModelInterface
import io.rover.sdk.ui.layout.row.RowViewModel
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RowViewModelSpec : Spek({
    describe("integration tests with real block view models") {
        val viewModels = ViewModels(
            apiService = mock(),
            mainScheduler = mock(),
            eventEmitter = mock(),
            sessionTracker = mock(),
            imageOptimizationService = mock(),
            assetService = mock(),
            measurementService = mock()
        )

        context("an autoheight row with stacked blocks") {
            val emptyRow = ModelFactories.emptyRow()

            val exampleRow = emptyRow.copy(
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
            )

            val rowViewModel = viewModels.rowViewModel(exampleRow)

            context("frame()") {
                // row bounds' bottom is given as 0, because rows are always responsible
                // for either setting their own height or measuring their stacked auto-height.
                val frame = rowViewModel.frame(RectF(0f, 0f, 60f, 0f))

                it("expands its height to contain the blocks") {
                    frame.bottom.shouldEqual(90f)
                }
            }

            context("frame() when erroneously given 0 width") {
                val frame = rowViewModel.frame(RectF(0f, 0f, 0f, 0f))

                it("expands its height to contain the blocks") {
                    frame.bottom.shouldEqual(90f)
                }
            }

            context("render()") {
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

            context("render() when erroneously given 0 width") {
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

        context("a non-autoheight row with a floating block that extends outside the top of the row") {
            val exampleRow = ModelFactories.emptyRow()
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
                )

            val rowViewModel = viewModels.rowViewModel(exampleRow)

            context("frame()") {
                // row bounds' bottom is given as 0, because rows are always responsible
                // for either setting their own height or measuring their stacked auto-height.
                val frame = rowViewModel.frame(RectF(0f, 0f, 60f, 0f))
                it("sets its height as the given value") {
                    frame.bottom.shouldEqual(20f)
                }
            }

            context("render()") {
                val layout = rowViewModel.mapBlocksToRectDisplayList(
                    rowViewModel.frame(RectF(0f, 0f, 40f, 0f))
                )

                it("lays out the floating block with a clip to chop the top bit off") {
                    // the amount that should be clipped off the top is -5
                    layout.first().shouldMatch(
                        RectF(0f, -5f, 40f, 5f),
                        RectangleBlockViewModelInterface::class.java,
                        RectF(0f, 5f, 40f, 10f)
                    )
                }
            }
        }

        context("a non-autoheight row with a floating block that extends outside the bottom of the row") {
            val exampleRow = ModelFactories.emptyRow()
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
                )

            val rowViewModel = viewModels.rowViewModel(exampleRow)

            context("frame()") {
                // row bounds' bottom is given as 0, because rows are always responsible
                // for either setting their own height or measuring their stacked auto-height.
                val frame = rowViewModel.frame(RectF(0f, 0f, 60f, 0f))
                it("sets its height as the given value") {
                    frame.bottom.shouldEqual(20f)
                }
            }

            context("render()") {
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

        context("a non-auto-height row with a floating block") {
            val blockHeight = 10.0
            val blockWidth = 30.0
            val rowHeight = 20.0

            fun nonAutoHeightRowWithFloatingBlock(
                verticalAlignment: VerticalAlignment,
                horizontalAlignment: HorizontalAlignment
            ): RowViewModel {
                val exampleRow = ModelFactories.emptyRow()
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
                    )
                return viewModels.rowViewModel(exampleRow)
            }

            context("a non-autoheight row with a floating block is aligned to the bottom") {
                val rowViewModel = nonAutoHeightRowWithFloatingBlock(
                    VerticalAlignment.Bottom(0.0, Height.Static(blockHeight)),
                    HorizontalAlignment.Left(0.0, blockWidth)
                )

                context("render()") {
                    val layout = rowViewModel.mapBlocksToRectDisplayList(
                        rowViewModel.frame(RectF(0f, 0f, 40f, 0f))
                    )

                    it("lays out the block on the bottom") {
                        layout.first().shouldMatch(
                            RectF(0f, 20f - 10f, 30f, 20f),
                            RectangleBlockViewModelInterface::class.java,
                            null
                        )
                    }
                }
            }

            context("a non-autoheight row with a floating block is aligned to the middle (vertical)") {
                val rowViewModel = nonAutoHeightRowWithFloatingBlock(
                    VerticalAlignment.Middle(0.0, Height.Static(blockHeight)),
                    HorizontalAlignment.Left(0.0, blockWidth)
                )

                context("render()") {
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

            context("a non-autoheight row with a floating block is aligned to the center (horizontal)") {
                val rowViewModel = nonAutoHeightRowWithFloatingBlock(
                    VerticalAlignment.Top(0.0, Height.Static(blockHeight)),
                    HorizontalAlignment.Center(0.0, blockWidth)
                )

                context("render()") {
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

            context("a non-autoheight row with a floating block that is aligned to the right") {
                val rowViewModel = nonAutoHeightRowWithFloatingBlock(
                    VerticalAlignment.Top(0.0, Height.Static(blockHeight)),
                    HorizontalAlignment.Right(0.0, blockWidth)
                )

                context("render()") {
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

