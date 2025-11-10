import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
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
import java.lang.reflect.Type
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
                    accumulatedData += it
                    if (Utils.isValidJson(accumulatedData)) {
                        onDataReceived(accumulatedData)
                        accumulatedData = ""
                    }
                }
            }.launchIn(viewModelScope) // Launch in the viewModelScope
        }
    }

    class LocalDateTimeDeserializer : JsonDeserializer<LocalDateTime> {
        @SuppressLint("NewApi")
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): LocalDateTime {
            val arr =
                json?.asJsonArray
                    ?: throw JsonParseException("Expected JSON array for LocalDateTime")
            val year = arr[0].asInt
            val month = arr[1].asInt
            val day = arr[2].asInt
            val hour = arr[3].asInt
            val minute = arr[4].asInt
            val second = arr[5].asInt
            return LocalDateTime.of(year, month, day, hour, minute, second)
        }
    }
    @SuppressLint("NewApi")
    private fun onDataReceived(jsonString: String) {
        Timber.d("Attempting to parse JSON log object: $jsonString")

        val gson = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeDeserializer())
            .create()

        try {
            val newLog: LogEntry = gson.fromJson(jsonString, LogEntry::class.java)

            // Create an updated list by adding the new log and then sorting it.
            val updatedLogs = _logs.value.toMutableList().apply {
                add(newLog)
                // Sort the entire list by the 'datetime' field in descending (reverse) order.
                sortByDescending { it.datetime }

                // Limit to 20 entries
                if (size > 20) {
                    removeLast()
                }
            }

            // Update the StateFlow with the new, sorted list.
            _logs.value = updatedLogs

            Timber.i("Successfully parsed and added new log: ${newLog.activityName}")

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


@RequiresApi(Build.VERSION_CODES.O)
private fun sampleLogs(): List<LogEntry> {
    val base = LocalDateTime.of(2025, 10, 29, 17, 7, 47)
    return List(30) { i ->
        LogEntry(
            datetime = base.plusSeconds(i.toLong() * 37L),
            activityName = "Setting Time",
            message = "Time set to 10/29 ${5 + (i / 60)}:${(7 + i) % 60} PM for watch CASIO GW-B5600, mode: MANUAL${if (i % 3 == 0) " WITH DISPLAY" else ""}",
            statusCode = "TIME_SET"
        )
    }
}

