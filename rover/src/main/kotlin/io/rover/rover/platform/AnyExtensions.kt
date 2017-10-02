package io.rover.rover.platform

/**
 * Use this extension method to execute a side-effect when a given expression is not null.
 */
fun <T: Any, R: Any> T?.whenNotNull(cb: (T) -> R?): R? {
    return if(this != null) {
        cb(this)
    } else {
        null
    }
}
