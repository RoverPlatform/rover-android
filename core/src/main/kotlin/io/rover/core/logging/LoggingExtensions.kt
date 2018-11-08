package io.rover.core.logging

import android.util.Log
import io.rover.core.container.Resolver
import io.rover.core.data.domain.AttributeValue
import io.rover.core.events.EventQueueService.Companion.ROVER_NAMESPACE
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.events.domain.Event
import io.rover.core.streams.filter
import io.rover.core.streams.map
import io.rover.core.streams.subscribe
import java.util.ArrayDeque
import java.util.concurrent.Executors

/**
 * A very simple facade wrapped around the Android logger using Kotlin extension methods.
 */
interface LogReceiver {
    fun e(message: String)
    fun w(message: String)
    fun v(message: String)
    fun i(message: String)
    fun d(message: String)
}

interface LogEmitter {
    fun e(logTag: String, message: String)
    fun w(logTag: String, message: String)
    fun v(logTag: String, message: String)
    fun i(logTag: String, message: String)
    fun d(logTag: String, message: String)
}

class GlobalStaticLogHolder {
    companion object {
        // This is the only example of a global scope, mutable, allocated-at-runtime value.  This is
        // to avoid the complexity of trying to inject a logger into all and sundry location.
        var globalLogEmitter: LogEmitter? = null
    }
}

val Any.log: LogReceiver
    get() {
        val receiver = GlobalStaticLogHolder.globalLogEmitter ?: AndroidLogger() // default to a simple Android logger if logger not configured.

        val logTag = "Rover::${this.javaClass.simpleName}"

        return object : LogReceiver {

            override fun e(message: String) {
                receiver.e(logTag, message)
            }

            override fun w(message: String) {
                receiver.w(logTag, message)
            }

            override fun v(message: String) {
                receiver.v(logTag, message)
            }

            override fun i(message: String) {
                receiver.i(logTag, message)
            }

            override fun d(message: String) {
                receiver.d(logTag, message)
            }
        }
    }

class AndroidLogger : LogEmitter {
    override fun e(logTag: String, message: String) {
        Log.e(logTag, message)
    }

    override fun w(logTag: String, message: String) {
        Log.w(logTag, message)
    }

    override fun v(logTag: String, message: String) {
        Log.v(logTag, message)
    }

    override fun i(logTag: String, message: String) {
        Log.i(logTag, message)
    }

    override fun d(logTag: String, message: String) {
        Log.d(logTag, message)
    }
}

class JvmLogger : LogEmitter {
    override fun e(logTag: String, message: String) {
        System.out.println("E/$logTag $message")
    }

    override fun w(logTag: String, message: String) {
        System.out.println("/$logTag $message")
    }

    override fun v(logTag: String, message: String) {
        System.out.println("V/$logTag $message")
    }

    override fun i(logTag: String, message: String) {
        System.out.println("I/$logTag $message")
    }

    override fun d(logTag: String, message: String) {
        System.out.println("D/$logTag $message")
    }
}

/**
 * Log error and warning messages as Rover events.
 *
 * Will lazily wait for the EventQueueService to become available, and will buffer log messages in
 * the meantime.
 */
class EventQueueLogger(
    resolver: Resolver,
    private val nextLogger: LogEmitter
) : LogEmitter by nextLogger {

    private val serialExecutor = Executors.newSingleThreadExecutor()

    override fun e(logTag: String, message: String) {
        nextLogger.e(logTag, message)
        logMessage(
            Event(
                "Error",
                hashMapOf(
                    Pair("message", AttributeValue.Scalar.String("$logTag: $message"))
                )
            )
        )
    }

    private val bufferedMessages = mutableListOf<Event>()

    private var eventQueueServiceInterface: EventQueueServiceInterface? = null

    init {
        resolver
            .activations
            .filter { it.type == EventQueueServiceInterface::class.java }
            .map { it.instance }
            .subscribe { eventQueueServiceInterface ->
                this.eventQueueServiceInterface = (eventQueueServiceInterface as EventQueueServiceInterface)
            }
    }

    private fun logMessage(event: Event) {
        serialExecutor.execute {
            if (eventQueueServiceInterface != null) {
                bufferedMessages.forEach { queuedMessage ->
                    eventQueueServiceInterface!!.trackEvent(queuedMessage, ROVER_NAMESPACE)
                }
                eventQueueServiceInterface!!.trackEvent(event, ROVER_NAMESPACE)
            } else {
                bufferedMessages.add(event)
            }
        }
    }
}

/**
 * A logger that passes through all logs to [nextLogger], but will also maintain a buffer up to
 * [bufferLineSize] of those log messages such that they can be retrieved, perhaps to include with a
 * crash report or similar.
 */
class LogBuffer(
    private val nextLogger: LogEmitter,
    private val bufferLineSize: Int = 40
) : LogEmitter by nextLogger {

    private val bufferAccessExecutor = Executors.newSingleThreadExecutor()

    private val buffer = ArrayDeque<BufferLogEntry>(bufferLineSize)

    override fun e(logTag: String, message: String) {
        captureLog("e", logTag, message)
        nextLogger.e(logTag, message)
    }

    override fun w(logTag: String, message: String) {
        captureLog("w", logTag, message)
        nextLogger.w(logTag, message)
    }

    override fun v(logTag: String, message: String) {
        captureLog("v", logTag, message)
        nextLogger.v(logTag, message)
    }

    override fun i(logTag: String, message: String) {
        captureLog("i", logTag, message)
        nextLogger.i(logTag, message)
    }

    override fun d(logTag: String, message: String) {
        captureLog("d", logTag, message)
        nextLogger.d(logTag, message)
    }

    /**
     * Obtain a list of all the buffered logs.
     */
    fun getLogs(): List<BufferLogEntry> {
        return synchronized(buffer) {
            buffer.toList()
        }
    }

    fun getLogsAsText(): String {
        return getLogs().map { entry -> "${entry.level.capitalize()}/ ${entry.tag}: ${entry.message}" }.joinToString("\n")
    }

    private fun captureLog(level: String, tag: String, message: String) {
        bufferAccessExecutor.execute {
            buffer.addLast(
                BufferLogEntry(level, tag, message)
            )
            // emulate a ring buffer by removing any older entries than `bufferLineSize`
            for (i in 1..buffer.size - bufferLineSize) {
                buffer.removeFirst()
            }
        }
    }

    data class BufferLogEntry(
        val level: String,
        val tag: String,
        val message: String
    )
}