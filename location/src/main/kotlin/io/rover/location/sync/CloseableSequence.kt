package io.rover.location.sync

interface CloseableIterator<T>: Iterator<T>, AutoCloseable

/**
 * A Kotlin [Sequence], but one that yields [Iterator]s that must be closed once usage is complete.
 * Consider using [use] with the yielded [iterator].
 */
interface ClosableSequence<T>: Sequence<T> {
    override fun iterator(): CloseableIterator<T>
}