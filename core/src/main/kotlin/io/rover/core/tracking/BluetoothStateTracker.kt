package io.rover.core.tracking

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.rover.core.events.EventQueueServiceInterface
import io.rover.core.events.domain.Event

class BluetoothStateTracker(
   application: Application,
   eventQueueService: EventQueueServiceInterface
) {
    init {
        val intentFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action

                if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_ON -> eventQueueService.trackEvent(
                            Event(
                                "Bluetooth Enabled",
                                hashMapOf()
                            )
                        )
                        BluetoothAdapter.STATE_OFF -> eventQueueService.trackEvent(
                            Event(
                                "Bluetooth Disabled",
                                hashMapOf()
                            )
                        )
                    }
                }
            }
        }

        application.registerReceiver(receiver, intentFilter)
    }
}
