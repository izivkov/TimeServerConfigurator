package org.avmedia.timeserverconfigurator

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class Advertiser(private val context: Context, private val tag: String = "TimeServerAdvertiser") {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null

    // Example service + characteristic UUIDs
    private val LOGS_SERVICE_ID = UUID.fromString("00002001-0000-1000-8000-00805F9B34FB")
    private val LOGS_CHARACTERISTICS_ID = UUID.fromString("00002002-0000-1000-8000-00805F9B34FB")

    private var gattServer: BluetoothGattServer? = null
    private var serviceAdded = false

    private var onDataReceived : (data: ByteArray) -> Unit = {}

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startGattServer(onDataReceived: (data: ByteArray) -> Unit) {
        if (gattServer == null) {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            serviceAdded = false // Reset flag if a new GATT server is opened
        }

        if (gattServer == null) {
            Timber.tag(tag).w("Failed to open GATT server")
            return
        }

        this.onDataReceived = onDataReceived

        if (!serviceAdded) {
            val service = BluetoothGattService(LOGS_SERVICE_ID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            val characteristic = BluetoothGattCharacteristic(
                LOGS_CHARACTERISTICS_ID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
            )

            service.addCharacteristic(characteristic)
            val success = gattServer?.addService(service) ?: false
            if (!success) {
                Timber.tag(tag).e("Failed to add GATT service!")
            } else {
                Timber.tag(tag).i("GATT service added: $LOGS_SERVICE_ID with characteristic $LOGS_CHARACTERISTICS_ID")
                serviceAdded = true
            }
        } else {
            Timber.tag(tag).d("GATT service already added, skipping addition")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopGattServer() {
        try {
            // Stop advertising if ongoing
            try {
                callback?.let { advertiser?.stopAdvertising(it) }
            } catch (e: Exception) {
                Timber.tag(tag).w(e, "Error stopping advertising")
            }

            // Close GATT server
            gattServer?.close()
            gattServer = null
            Timber.tag(tag).d("GATT server stopped and cleaned up")
        } catch (e: Exception) {
            Timber.tag(tag).w(e, "Exception during stopGattServer()")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                println("Central connected: ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                println("Central disconnected: ${device.address}")
                stopAdvertising()
                // Reset data transfer state on disconnect
                resetDataReceptionState()
            }
        }

        // State for managing chunked data transfer
        private var dataBuffer = mutableListOf<Byte>()
        private var expectedDataLength: Int? = null

        private fun resetDataReceptionState() {
            dataBuffer.clear()
            expectedDataLength = null
            Timber.tag(tag).d("Data reception state has been reset.")
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val chunk = value ?: return // Ignore null value writes

            // State 1: Waiting for the 4-byte length header.
            // This is the ONLY time we check the chunk size for the header.
            if (expectedDataLength == null) {
                if (chunk.size == 4) {
                    // Use ByteBuffer for safe and clear endian-handling
                    val length = ByteBuffer.wrap(chunk).order(ByteOrder.BIG_ENDIAN).int
                    if (length <= 0) {
                        Timber.tag(tag).e("Received invalid data length: $length. Resetting.")
                        resetDataReceptionState()
                    } else {
                        expectedDataLength = length
                        dataBuffer.clear() // Ensure buffer is empty before starting accumulation
                        Timber.tag(tag).i("Expecting $length bytes of data.")
                    }
                } else {
                    Timber.tag(tag).w("Received a chunk of size ${chunk.size}, but was expecting the 4-byte length header first. Ignoring.")
                }
            }
            // State 2: Accumulating data chunks.
            // We enter this state AFTER the length has been received and will not check chunk size again.
            else {
                dataBuffer.addAll(chunk.toList())
                Timber.tag(tag).d("Received chunk of ${chunk.size} bytes. Total received: ${dataBuffer.size}/${expectedDataLength}")

                // Check if all data has been received
                val totalExpected = expectedDataLength
                if (totalExpected != null && dataBuffer.size >= totalExpected) {
                    val completeData = dataBuffer.take(totalExpected).toByteArray()
                    val jsonString = String(completeData, Charsets.UTF_8)

                    Timber.tag(tag).d("All data received. Verifying JSON...")
                    try {
                        onDataReceived(completeData)
                    } finally {
                        println("All data processed.")
                    }

                    // Reset for the next potential message, regardless of success
                    resetDataReceptionState()
                }
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    // Advertising methods
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT])
    fun startAdvertising(serviceUuid: UUID = this.LOGS_SERVICE_ID) {

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Timber.tag(tag).w("No Bluetooth adapter available")
            return
        }
        if (!adapter.isEnabled) {
            Timber.tag(tag).w("Bluetooth adapter is disabled")
            return
        }

        try {
            if (tag.isNotBlank()) {
                adapter.name = tag
                Timber.tag(tag).d("Adapter name set to: %s", tag)
            }
        } catch (se: SecurityException) {
            Timber.tag(tag).w(se, "Failed to set adapter name")
        }

        try {
            advertiser = adapter.bluetoothLeAdvertiser
        } catch (e: Exception) {
            Timber.tag(tag).w(e, "Failed to get BluetoothLeAdvertiser")
            advertiser = null
        }

        if (advertiser == null) {
            Timber.tag(tag).w("Device does not support BLE advertising")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(serviceUuid))
            .setIncludeDeviceName(true)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                Timber.tag(tag).d("Advertising started: %s", settingsInEffect)
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Timber.tag(tag).w("Advertising failed: code=$errorCode")
            }
        }

        try {
            advertiser?.startAdvertising(settings, advertiseData, scanResponse, callback)
        } catch (e: Exception) {
            Timber.tag(tag).w(e, "startAdvertising exception")
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising() {
        try {
            callback?.let { advertiser?.stopAdvertising(it) }
            gattServer?.close()
        } catch (e: Exception) {
            Timber.tag(tag).w(e, "stopAdvertising exception")
        } finally {
            callback = null
            advertiser = null
            Timber.tag(tag).d("Advertising stopped / cleaned up")
        }
    }
}
