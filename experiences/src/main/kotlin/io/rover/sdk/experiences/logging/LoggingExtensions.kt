package io.rover.sdk.experiences.logging

import android.util.Log
import java.util.ArrayDeque
import java.util.concurrent.Executors

/**
 * A very simple facade wrapped around the Android logger using Kotlin extension methods.
 */
internal interface LogReceiver {
    fun e(message: String)
    fun w(message: String)
    fun v(message: String)
    fun i(message: String)
    fun d(message: String)
}

internal interface LogEmitter {
    fun e(logTag: String, message: String)
    fun w(logTag: String, message: String)
    fun v(logTag: String, message: String)
    fun i(logTag: String, message: String)
    fun d(logTag: String, message: String)
}

internal class GlobalStaticLogHolder {
    companion object {
        // This is the only example of a global scope, mutable, allocated-at-runtime value.  This is
        // to avoid the complexity of trying to inject a logger into all and sundry location.
        var globalLogEmitter: LogEmitter? = LogBuffer(
            // uses the resolver to discover when the EventEmitter is ready and can be used
            // to submit the logs.
            AndroidLogger()
        )
    }
}

internal val Any.log: LogReceiver
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

internal class AndroidLogger : LogEmitter {
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

internal class JvmLogger : LogEmitter {
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
 * A logger that passes through all logs to [nextLogger], but will also maintain a buffer up to
 * [bufferLineSize] of those log messages such that they can be retrieved, perhaps to include with a
 * crash report or similar.
 */
internal class LogBuffer(
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