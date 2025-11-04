import android.annotation.SuppressLint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import android.bluetooth.BluetoothDevice
import android.content.Context
import org.avmedia.timeserverconfigurator.Connection

class ConfigViewModel(
    context: Context
) : ViewModel() {

    val connection = Connection(context)

    lateinit var onDisconnected: () -> Unit

    var isConnected by mutableStateOf(false)
        private set

    var isReady by mutableStateOf(false)
        private set

    var isDisconnected by mutableStateOf(false)
        private set

    @SuppressLint("MissingPermission")
    fun startBleScan(onReadyCallback: () -> Unit) {
        connection.startBleScan (
            { isConnected = true },
            { isConnected = false
                this.onDisconnected()
            },
            {
                isReady = true
                onReadyCallback()
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun connect (onDisconnected: () -> Unit) {
        this.onDisconnected = onDisconnected

        startBleScan {
            println("Ready to send...")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
    }

    fun writeCredentials(jsonString: String) {
        connection.writeCredentials(jsonString)
    }

    fun disconnect() {
        connection.disconnect()
        isConnected = false
        isDisconnected = true
        isReady = false
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}
