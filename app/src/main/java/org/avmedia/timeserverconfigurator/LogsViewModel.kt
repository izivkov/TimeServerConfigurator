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
class LogsViewModel(
    context: Context
) : ViewModel() {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

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

    init {
        _logs.value = emptyList<LogEntry>() // sampleLogs()
        // _logs.value = sampleLogs()
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
