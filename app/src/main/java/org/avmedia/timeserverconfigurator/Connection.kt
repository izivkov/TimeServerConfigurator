package org.avmedia.timeserverconfigurator

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Suppress("UNCHECKED_CAST")
class Connection(private val context: Context) {

    companion object {
        private const val TAG = "BleConnector"
        private val SERVICE_UUID = java.util.UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
        private val CHAR_UUID = java.util.UUID.fromString("abcdefab-1234-5678-1234-56789abcdef0")
    }

    lateinit var onConnected: () -> Unit
    lateinit var onDisconnected: () -> Unit
    lateinit var onReady: () -> Unit

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null

    private var scanCallback: ScanCallback? = null

    // Data sending state
    private var dataToSend: ByteArray = byteArrayOf()
    private var offset = 0
    private val chunkSize = 20

    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Timber.i("Connected to GATT server")
                gatt?.discoverServices()

                this@Connection.onConnected()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Timber.i("Disconnected from GATT server")
                bluetoothGatt?.close()
                bluetoothGatt = null
                characteristic = null

                this@Connection.onDisconnected()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(SERVICE_UUID)
                if (service != null) {
                    characteristic = service.getCharacteristic(CHAR_UUID)
                    if (characteristic != null) {
                        Timber.i("Characteristic found, ready for data")
                    } else {
                        Timber.e("Characteristic not found")
                    }
                } else {
                    Timber.e("Service not found")
                }
            } else {
                Timber.e("Service discovery failed with status $status")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.i("Negotiated MTU: $mtu")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Previous chunk sent successfully, send next chunk if any
                sendNextChunk()
            } else {
                Timber.e("Characteristic write failed with status: $status")
                // Handle error or retry logic here if needed
            }
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startBleScan(
        onConnected: () -> Unit,
        onDisconnected: () -> Unit,
        onReady: () -> Unit
    ) {
        startScan { device ->
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            this.onConnected = onConnected
            this.onDisconnected = onDisconnected
            this.onReady = onReady
        }
    }

    private val scanTimeoutMillis = 10000L // 10 seconds timeout
    private val scanHandler = android.os.Handler()
    @SuppressLint("MissingPermission")
    private val scanTimeoutRunnable = Runnable {
        Timber.i("Scan timed out without finding device, restarting scan")
        stopScan()
        onDeviceFoundCallback?.let { startScan(it) } // Use the saved callback lambda, NOT scanCallback
    }

    private var onDeviceFoundCallback: ((BluetoothDevice) -> Unit)? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(onDeviceFound: (BluetoothDevice) -> Unit) {
        onDeviceFoundCallback = onDeviceFound // Save lambda

        val filters = listOf(
            // ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
            ScanFilter.Builder().setDeviceName("TimeServer").build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    bluetoothLeScanner.stopScan(this)
                    scanHandler.removeCallbacks(scanTimeoutRunnable) // Cancel timeout
                    onDeviceFound(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.e("Scan failed with error code: $errorCode")
                scanHandler.removeCallbacks(scanTimeoutRunnable) // Cancel timeout
            }
        }

        bluetoothLeScanner.startScan(filters, settings, scanCallback)
        scanHandler.postDelayed(scanTimeoutRunnable, scanTimeoutMillis)

        Timber.i("Started scanning")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanCallback?.let {
            bluetoothLeScanner.stopScan(it)
            Timber.i("Stopped scanning")
        }
        scanHandler.removeCallbacks(scanTimeoutRunnable)
    }

    @SuppressLint("MissingPermission")
    fun writeCredentials(jsonString: String) {
        val char = characteristic ?: return
        dataToSend = jsonString.toByteArray(Charsets.UTF_8)
        offset = 0

        // Send 4-byte length header first
        val lengthHeader =
            ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(dataToSend.size).array()
        char.value = lengthHeader
        val sent = bluetoothGatt?.writeCharacteristic(char)
        // onCharacteristicWrite callback will send next chunks
    }

    @SuppressLint("MissingPermission")
    private fun sendNextChunk() {
        val char = characteristic ?: return

        if (offset >= dataToSend.size) {
            Timber.i("All data chunks sent")
            return
        }

        val end = minOf(offset + chunkSize, dataToSend.size)
        val chunk = dataToSend.copyOfRange(offset, end)
        char.value = chunk

        val success = bluetoothGatt?.writeCharacteristic(char) ?: false
        if (success) {
            Timber.i("Sending chunk from $offset to $end")
            offset = end
        } else {
            Timber.e("Failed to initiate write for chunk at offset $offset")
            // Optionally implement retry logic here
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        characteristic = null
    }
}
