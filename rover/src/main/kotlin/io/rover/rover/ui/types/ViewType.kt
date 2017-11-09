package io.rover.rover.ui.types

import io.rover.rover.ui.BlockAndRowRecyclerAdapter

/**
 * The set of possible types of view that can be laid out in the [BlockAndRowRecyclerAdapter].
 * This allows us to distinguish between them without relying on reflection.
 */
enum class ViewType {
    Row,
    Rectangle,
    Text,
    Image,
//    WebView,
//    Barcode,
//    Button
}
