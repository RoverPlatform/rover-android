package io.rover.experiences.ui.layout

import android.content.Context
import io.rover.experiences.ExperiencesAssembler
import io.rover.experiences.MeasurementService
import io.rover.experiences.ui.blocks.concerns.background.BackgroundViewModelInterface
import io.rover.experiences.ui.blocks.rectangle.RectangleBlockViewModel
import io.rover.experiences.ui.layout.row.RowViewModel
import io.rover.experiences.ui.layout.screen.ScreenViewModelInterface
import io.rover.core.assets.AssetService
import io.rover.core.assets.ImageOptimizationServiceInterface
import io.rover.core.container.Assembler
import io.rover.core.container.Container
import io.rover.core.container.InjectionContainer
import io.rover.core.container.Resolver
import io.rover.core.container.Scope
import io.rover.core.data.domain.Background
import io.rover.core.data.domain.Height
import io.rover.core.data.domain.HorizontalAlignment
import io.rover.core.data.domain.ID
import io.rover.core.data.domain.Position
import io.rover.core.data.domain.VerticalAlignment
import io.rover.core.routing.Router
import io.rover.core.ui.RectF
import org.amshove.kluent.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

class ScreenViewModelSpec : Spek({
    given("integration tests with real row view models") {

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

                        container.register(Scope.Transient, BackgroundViewModelInterface::class.java) { resolver, background: Background ->
                            mock()
                        }

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

        given("a basic screen with one row with a rectangle block") {
            val screen = ModelFactories.emptyScreen().copy(
                rows = listOf(
                    ModelFactories.emptyRow().copy(
                        height = Height.Static(10.0),
                        blocks = listOf(
                            ModelFactories.emptyRectangleBlock().copy(
                                position = Position(
                                    HorizontalAlignment.Fill(0.0,0.0),
                                    VerticalAlignment.Top(0.0, Height.Static(10.0))
                                )
                            )
                        )
                    )
                )
            )

            val screenViewModel = realObjectStack.resolve(ScreenViewModelInterface::class.java, null, screen)!!

            on("rendering") {
                val rendered = screenViewModel.render(
                    40f
                )

                it("puts the block directly on top of the row") {
                    // we have two
                    rendered.coordinatesAndViewModels[0].shouldMatch(
                        RectF(0f, 0f, 40f, 10f),
                        RowViewModel::class.java
                    )

                    rendered.coordinatesAndViewModels[1].shouldMatch(
                        RectF(0f, 0f, 40f, 10f),
                        RectangleBlockViewModel::class.java
                    )
                }
            }
        }

        given("a screen with two rows") {
            val screen = ModelFactories.emptyScreen().copy(
                rows = listOf(
                    ModelFactories.emptyRow().copy(
                        id = ID("1"),
                        height = Height.Static(10.0),
                        blocks = listOf(
                            ModelFactories.emptyRectangleBlock().copy(
                                position = Position(
                                    HorizontalAlignment.Fill(0.0, 0.0),
                                    VerticalAlignment.Top(0.0, Height.Static(10.0))
                                )
                            )
                        )
                    ),
                    ModelFactories.emptyRow().copy(
                        id = ID("2"),
                        height = Height.Static(42.0)
                    )
                )
            )
            val screenViewModel = realObjectStack.resolve(ScreenViewModelInterface::class.java, null, screen)!!

            on("rendering") {
                val rendered = screenViewModel.render(
                    40f
                )

                it("puts the block directly on top of the row") {
                    // we have two
                    rendered.coordinatesAndViewModels[0].shouldMatch(
                        RectF(0f, 0f, 40f, 10f),
                        RowViewModel::class.java
                    )

                    rendered.coordinatesAndViewModels[1].shouldMatch(
                        RectF(0f, 0f, 40f, 10f),
                        RectangleBlockViewModel::class.java
                    )

                    rendered.coordinatesAndViewModels[2].shouldMatch(
                        // check that the rows are stacked properly: 0 + 10 + 42 = 52
                        RectF(0f, 10f, 40f, 52f),
                        RowViewModel::class.java
                    )
                }
            }
        }

        given("a screen with a row and a block with a vertical fill offset and a horizontal left offset") {
            val screen = ModelFactories.emptyScreen().copy(
                rows = listOf(
                    ModelFactories.emptyRow().copy(
                        id = ID("1"),
                        height = Height.Static(100.0),
                        blocks = listOf(
                            ModelFactories.emptyRectangleBlock().copy(
                                position = Position(
                                    HorizontalAlignment.Left(
                                        5.0,
                                        20.0
                                    ),
                                    VerticalAlignment.Fill(
                                        10.0,
                                        10.0
                                    )
                                )
                            )
                        )
                    ),
                    ModelFactories.emptyRow().copy(
                        id = ID("2"),
                        height = Height.Static(42.0)
                    )
                )
            )
            val screenViewModel = realObjectStack.resolve(ScreenViewModelInterface::class.java, null, screen)!!

            on("rendering") {
                val rendered = screenViewModel.render(
                    40f
                )

                it("renders the block with the offsets") {
                    rendered.coordinatesAndViewModels[1].shouldMatch(
                        RectF(5f, 10f, 25f, 90f),
                        RectangleBlockViewModel::class.java
                    )
                }
            }
        }

        given("a screen with a row with a vertically centered block within") {
            val screen = ModelFactories.emptyScreen().copy(
                rows = listOf(
                    ModelFactories.emptyRow().copy(
                        height = Height.Static(100.0),
                        blocks = listOf(
                            ModelFactories.emptyRectangleBlock().copy(
                                position = Position(
                                    HorizontalAlignment.Fill(
                                        0.0, 0.0
                                    ),
                                    VerticalAlignment.Middle(
                                        0.0, Height.Static(20.0)
                                    )
                                )
                            )
                        )
                    )
                )
            )
            val screenViewModel = realObjectStack.resolve(ScreenViewModelInterface::class.java, null, screen)!!

            on("rendering") {
                val rendered = screenViewModel.render(
                    300f
                )

                it("centers the block inside the row") {
                    rendered.coordinatesAndViewModels[1].shouldMatch(
                        RectF(0f, 40f, 300f, 60f),
                        RectangleBlockViewModel::class.java
                    )
                }
            }
        }
    }
})
