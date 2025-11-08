import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import org.avmedia.loggerble.LoggerBleManager
import timber.log.Timber

// Renamed class to match the file name
class LogsViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext
    private val manager = LoggerBleManager(context)

    // Keep a reference to the scanner and callback to be able to stop the scan
    private val scanner = BluetoothLeScannerCompat.getScanner()
    private var scanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun startScan() {
        // Stop any previous scan
        stopScan()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0) // Report results immediately
            .build()

        scanCallback = object : ScanCallback() {
            // Nordic scanner can return a list of results
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Check for the device name here as well
                if (result.device.name == "ESP32_Logger") {
                    stopScan()
                    connectToDevice(result.device)
                }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                results.firstOrNull { it.device.name == "ESP32_Logger" }?.let { result ->
                    stopScan()
                    connectToDevice(result.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.e("Scan failed with error code: $errorCode")
            }
        }
        scanner.startScan(null, settings, scanCallback!!)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        scanCallback?.let {
            scanner.stopScan(it)
            scanCallback = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch(Dispatchers.IO) {
            Timber.i("Connecting to ${device.address}")
            manager.connect(device)
                .useAutoConnect(false)
                .retry(3, 100)
                .done {
                    println("Connected with autoConnect!")
                }
                .fail { _, status -> println("Failed with status $status") }
                .enqueue()

            // Use onEach for continuous collection
            manager.logs.onEach { logData ->
                logData?.let {
                    // Using Timber for logging consistency
                    println("📥 Logs from ESP32: $it")
                }
            }.launchIn(viewModelScope) // Launch in the viewModelScope
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        manager.disconnect().enqueue()
    }

    @SuppressLint("MissingPermission")
    override fun onCleared() {
        super.onCleared()
        stopScan()
        disconnect()
    }
}
