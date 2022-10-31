package io.rover.campaigns.core.events.contextproviders

import android.bluetooth.BluetoothAdapter
import io.rover.campaigns.core.data.domain.DeviceContext
import io.rover.campaigns.core.events.ContextProvider

class BluetoothContextProvider(
    bluetoothAdapter: BluetoothAdapter
) : ContextProvider {
    // cache the value at startup, since the context providers get used for each event.  It's fine
    // if we transmit the old value until the app gets restarted if the bluetooth state changes.
    private val bluetoothEnabled = bluetoothAdapter.isEnabled

    override fun captureContext(deviceContext: DeviceContext): DeviceContext {
        return deviceContext.copy(
            isBluetoothEnabled = bluetoothEnabled
        )
    }
}
