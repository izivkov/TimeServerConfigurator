import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.avmedia.timeserverconfigurator.Advertiser
import java.lang.reflect.Type
import java.time.LocalDateTime

@RequiresApi(Build.VERSION_CODES.O)

data class LogEntry(
    val datetime: LocalDateTime,
    @SerializedName("activity_name") val activityName: String?,
    val message: String?,
    @SerializedName("status_code") val statusCode: String?
)

@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("MissingPermission")

class LogsViewModel(
    context: Context
) : ViewModel() {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val advertiser = Advertiser(context, "TimeServerConfigurator")

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private var advertisingStartTime = 0L

    init {
        _logs.value = emptyList()
        _connected.value = false
    }

    @SuppressLint("MissingPermission")
    fun connect(onDisconnected: () -> Unit) {
        advertiser.startAdvertising()
        _connected.value = true
        advertisingStartTime = System.currentTimeMillis()

        clearAll()
        advertiser.startGattServer { data: ByteArray ->
            val dataStr = String(data, Charsets.UTF_8)

            val logs = parseLogEntries(dataStr)
            for (log in logs) {
                addLogEntry(log)
            }

            println("received: $dataStr")
            disconnect()
        }
    }


    fun refreshLogs() {
        viewModelScope.launch {
            connect() {
                // onDisconnected
            }
        }
    }

    fun addLogEntry(entry: LogEntry) {
        viewModelScope.launch {
            _logs.value = _logs.value + entry
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            _logs.value = emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        // advertiser.stopAdvertising()
        advertiser.stopGattServer ()
        _connected.value = false
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
            json?.asJsonArray ?: throw JsonParseException("Expected JSON array for LocalDateTime")
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
fun List<LogEntry>.toSafeList(): List<LogEntry> = this.map {
    it.copy(
        activityName = it.activityName,
        message = it.message,
        statusCode = it.statusCode
    )
}

@SuppressLint("NewApi")
fun parseLogEntries(jsonString: String): List<LogEntry> {
    val gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeDeserializer())
        .create()

    val listType = object : TypeToken<List<LogEntry>>() {}.type
    val logEntries: List<LogEntry> = gson.fromJson(jsonString, listType)
    return logEntries.sortedByDescending { it.datetime }
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
