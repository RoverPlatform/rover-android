package io.rover.rover.services.network.requests

import io.rover.rover.core.domain.Experience
import io.rover.rover.core.domain.ID
import io.rover.rover.services.network.NetworkRequest
import io.rover.rover.services.network.WireEncoderInterface
import org.json.JSONObject

class FetchExperienceRequest(
    val id: ID
) : NetworkRequest<Experience> {
    override val operationName: String = "FetchExperience"

    override val query: String = """
        query FetchExperience(${"\$"}id: ID!) {
            experience(id: ${"\$"}id) {
                homeScreenId
                id
                screens {
                    ...backgroundFields
                    autoColorStatusBar
                    experienceId
                    id
                    isStretchyHeaderEnabled
                    rows {
                        ...backgroundFields
                        autoHeight
                        blocks {
                            __typename
                            action {
                                __typename
                                ... on GoToScreenAction {
                                    experienceId
                                    screenId
                                }
                                ... on OpenUrlAction {
                                    url
                                }
                            }
                            autoHeight
                            experienceId
                            height {
                                value
                                unit
                            }
                            id
                            insets {
                                bottom
                                left
                                right
                                top
                            }
                            horizontalAlignment
                            offsets {
                                bottom {
                                    value
                                    unit
                                }
                                center {
                                    value
                                    unit
                                }
                                left {
                                    value
                                    unit
                                }
                                middle {
                                    value
                                    unit
                                }
                                right {
                                    value
                                    unit
                                }
                                top {
                                    value
                                    unit
                                }
                            }
                            opacity
                            position
                            rowId
                            screenId
                            verticalAlignment
                            width {
                                value
                                unit
                            }
                            ... on Background {
                                ...backgroundFields
                            }
                            ... on Border {
                                ...borderFields
                            }
                            ... on Text {
                                ...textFields
                            }
                            ... on BarcodeBlock {
                                barcodeScale
                                barcodeText
                                barcodeFormat
                            }
                            ... on ButtonBlock {
                                disabled {
                                    ...buttonStateFields
                                }
                                normal {
                                    ...buttonStateFields
                                }
                                highlighted {
                                    ...buttonStateFields
                                }
                                selected {
                                    ...buttonStateFields
                                }
                            }
                            ... on ImageBlock {
                                image {
                                    height
                                    isURLOptimizationEnabled
                                    name
                                    size
                                    width
                                    url
                                }
                            }
                            ... on WebViewBlock {
                                isScrollingEnabled
                                url
                            }
                        }
                        experienceId
                        height {
                            value
                            unit
                        }
                        id
                        screenId
                    }
                    statusBarStyle
                    statusBarColor {
                        red
                        green
                        blue
                        alpha
                    }
                    titleBarBackgroundColor {
                        red
                        green
                        blue
                        alpha
                    }
                    titleBarButtons
                    titleBarButtonColor {
                        red
                        green
                        blue
                        alpha
                    }
                    titleBarText
                    titleBarTextColor {
                        red
                        green
                        blue
                        alpha
                    }
                    useDefaultTitleBarStyle
                }
            }
        }

        fragment buttonStateFields on ButtonState {
            ...backgroundFields
            ...borderFields
            ...textFields
        }

        fragment backgroundFields on Background {
            backgroundColor {
                red
                green
                blue
                alpha
            }
            backgroundContentMode
            backgroundImage {
                height
                isURLOptimizationEnabled
                name
                size
                width
                url
            }
            backgroundScale
        }

        fragment borderFields on Border {
            borderColor {
                red
                green
                blue
                alpha
            }
            borderRadius
            borderWidth
        }

        fragment textFields on Text {
            text
            textAlignment
            textColor {
                red
                green
                blue
                alpha
            }
            textFont {
                size
                weight
            }
        }
    """
    override val variables: JSONObject = JSONObject().apply {
        put("id", id.rawValue)
    }

    override fun decodePayload(responseObject: JSONObject, wireEncoder: WireEncoderInterface): Experience {
        return wireEncoder.decodeExperience(
            responseObject.getJSONObject("data").getJSONObject("experience")
        )
    }
}
