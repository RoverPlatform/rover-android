package io.rover.campaigns.core.platform

/**
 * Merge two hashes together. In the event of the same key existing, [transform] is invoked to
 * merge the two values.
 */
fun <TKey, TValue> Map<TKey, TValue>.merge(other: Map<TKey, TValue>, transform: (TValue, TValue) -> TValue): Map<TKey, TValue> {
    val keysSet = this.keys + other.keys

    return keysSet.map { key ->
        val left = this[key]
        val right = other[key]

        val value = when {
            left != null && right != null -> transform(left, right)
            left != null -> left
            right != null -> right
            else -> throw RuntimeException("Value for $key unexpectedly disappeared from both hashes being merged by Map.merge().")
        }

        Pair(key, value)
    }.associate { it }
}
