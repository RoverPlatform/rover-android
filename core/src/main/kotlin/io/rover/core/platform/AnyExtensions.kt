@file:JvmName("AnyExtensions")

package io.rover.core.platform

/**
 * Use this extension method evaluate an expression against a value when it is not null.
 */
fun <T : Any, R : Any> T?.whenNotNull(cb: (T) -> R?): R? {
    return if (this != null) {
        cb(this)
    } else {
        null
    }
}
