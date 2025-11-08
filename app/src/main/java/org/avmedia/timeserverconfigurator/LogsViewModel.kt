import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.media.MediaDrm
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.compose.ui.input.key.type
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.TypeAdapter
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import org.avmedia.loggerble.LoggerBleManager
import org.avmedia.timeserverconfigurator.Utils
import timber.log.Timber
import java.io.IOException
import java.time.LocalDateTime

data class LogEntry(
    val datetime: LocalDateTime,
    @SerializedName("activity_name") val activityName: String?,
    val message: String?,
    @SerializedName("status_code") val statusCode: String?
)

class LogsViewModel(app: Application) : AndroidViewModel(app) {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val manager = LoggerBleManager(app.applicationContext)
    val connected = manager.connected

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

    var accumulatedData = ""
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
                    accumulatedData += it
                    if (Utils.isValidJson(accumulatedData)) {
                        onDataReceived(accumulatedData)
                        accumulatedData = ""
                    }
                }
            }.launchIn(viewModelScope) // Launch in the viewModelScope
        }
    }

    private fun onDataReceived(jsonString: String) {
        Timber.d("Attempting to parse JSON log object: $jsonString")
        val gson = Gson()

        try {
            // Parse a single LogEntry object instead of a list
            val newLog: LogEntry = gson.fromJson(jsonString, LogEntry::class.java)

            // Append the new log to the existing list in _logs
            val updatedLogs = _logs.value.toMutableList().apply {
                add(newLog)
            }

            _logs.value = updatedLogs

            Timber.i("Successfully parsed and added new log: ${newLog.activity_name}")

        } catch (e: JsonSyntaxException) {
            Timber.e(e, "Failed to parse JSON string: $jsonString")
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error while parsing log.")
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
