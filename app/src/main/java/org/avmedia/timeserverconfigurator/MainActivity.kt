// kotlin
package org.avmedia.timeserverconfigurator

import ConnectionViewModel
import LogsScreen
import LogsViewModel
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.avmedia.timeserverconfigurator.ui.theme.TimeServerConfiguratorTheme

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.vector.ImageVector
import parseLogEntries

class MainActivity : ComponentActivity() {

    private lateinit var advertiser: Advertiser

    private val viewModel: ConnectionViewModel by viewModels {
        ViewModelFactory(this) // 'this' is a Context
    }

    private val logsViewModel: LogsViewModel by viewModels {
        ViewModelFactory(this)
    }

    class ViewModelFactory(
        private val context: Context
    ) : ViewModelProvider.Factory {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ConnectionViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ConnectionViewModel(context) as T
            }
            if (modelClass.isAssignableFrom(LogsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return LogsViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        advertiser = Advertiser(this, "TimeServerConfigurator")

        setContent {
            CheckPermissions {
                advertiser.startAdvertising()

                advertiser.startGattServer {
                    data: ByteArray ->
                        val dataStr = String(data, Charsets.UTF_8)
                        logsViewModel.clearAll()

                        val logs = parseLogEntries(dataStr)
                        for (log in logs) {
                            logsViewModel.addLogEntry(log)
                        }

                        println("received: $dataStr")
                }

                viewModel.connect {
                    onDisconnect()
                }

                TimeServerConfiguratorTheme {
                    var selectedRoute by rememberSaveable { mutableStateOf(BottomNavItem.Settings.route) }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            NavigationBar {
                                BottomNavItem.values().forEach { item ->
                                    NavigationBarItem(
                                        icon = { Icon(item.icon, contentDescription = item.label) },
                                        label = { Text(item.label) },
                                        selected = selectedRoute == item.route,
                                        onClick = { selectedRoute = item.route }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        when (selectedRoute) {
                            BottomNavItem.Settings.route -> {
                                ConfigScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    viewModel = viewModel,
                                    context = this
                                )
                            }

                            BottomNavItem.Logs.route -> {
                                LogsScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    logsViewModel = this.logsViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        advertiser.stopAdvertising()
        viewModel.disconnect()
    }

    private fun onDisconnect() {
        println("onDisconnect called in MainActivity")
        viewModel.connect {
            onDisconnect()
        }
    }
}

private enum class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Settings("settings", "Settings", Icons.Filled.Settings),
    Logs("logs", "Logs", Icons.Filled.List);

    companion object {
        fun values() = listOf(Settings, Logs)
    }
}
