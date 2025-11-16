// In LoggerBleManager.kt

package org.avmedia.loggerble // Make sure package is correct

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver
import timber.log.Timber

class LoggerBleManager(
    context: Context,
) : BleManager(context) {

    // This characteristic is private. Only the manager should interact with it directly.
    private var logCharacteristic: BluetoothGattCharacteristic? = null

    // This flow is public, so the ViewModel can observe it for data.
    private val _logs = MutableStateFlow<String?>(null)
    val logs = _logs.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()

    private var connectionObserver = object : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) {
            Timber.d("Connecting to device: ${device.address}")
        }

        override fun onDeviceConnected(device: BluetoothDevice) {
            Timber.d("Connected to device: ${device.address}")
            _connected.value = true
        }

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            Timber.e("Failed to connect to device: ${device.address}, reason: $reason")
        }

        override fun onDeviceReady(device: BluetoothDevice) {
            println("onDeviceReady")
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            println("onDeviceDisconnecting")
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            Timber.i("Disconnected from device: ${device.address}, reason: $reason")
            // You can notify UI or clean up resources here
            _connected.value = false
        }
    }

    init {
        setConnectionObserver(connectionObserver)
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val service = gatt.getService(LOG_SERVICE_UUID)
        if (service == null) {
            Timber.e("Service not found: $LOG_SERVICE_UUID.")
            return false
        }
        logCharacteristic = service.getCharacteristic(LOG_CHAR_UUID)
        if (logCharacteristic == null) {
            Timber.e("Characteristic not found: $LOG_CHAR_UUID.")
            return false
        }

        val properties = logCharacteristic!!.properties
        val hasNotify = (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0
        val hasWrite = (properties and BluetoothGattCharacteristic.PROPERTY_WRITE) > 0
        return hasNotify && hasWrite
    }

    override fun initialize() {
        // This method is called automatically after service discovery.
        // This is the correct place for all post-connection logic.
        logCharacteristic?.let { char ->
            // 1. Set up the callback for when notifications are received
            setNotificationCallback(char).with { _, data ->
                val text = data.getStringValue(0)
                // Update the flow with the new data
                _logs.value = text
            }

            // 2. Enable notifications on the device
            enableNotifications(char)
                // 3. After enabling, write the "start" command to trigger the ESP32
                .done {
                    Timber.i("Notifications enabled. Sending 'START' command...")
                    writeCharacteristic(
                        char,
                        "START".toByteArray(),
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ).enqueue()
                }
                .fail { _, status ->
                    Timber.e("Failed to enable notifications, status: $status")
                }
                .enqueue()
        }
    }

    override fun onServicesInvalidated() {
        // Clear the reference when the device disconnects
        logCharacteristic = null
    }

    companion object {
        val LOG_SERVICE_UUID = java.util.UUID.fromString("00001001-0000-1000-8000-00805F9B34FB")
        val LOG_CHAR_UUID = java.util.UUID.fromString("00001002-0000-1000-8000-00805F9B34FB")
    }
}
