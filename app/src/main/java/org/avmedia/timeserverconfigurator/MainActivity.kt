// kotlin
package org.avmedia.timeserverconfigurator

import ConfigViewModel
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

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : ComponentActivity() {

    private val configViewModel: ConfigViewModel by viewModels {
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
            if (modelClass.isAssignableFrom(ConfigViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ConfigViewModel(context) as T
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

        setContent {
            CheckPermissions {

                logsViewModel.connect {
                    println("Disconnected from LogsViewModel")
                }

                configViewModel.connect {
                    onConfigDisconnect()
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
                                    viewModel = configViewModel,
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
        logsViewModel.disconnect()
        configViewModel.disconnect()
    }

    private fun onConfigDisconnect() {
        println("onDisconnect called in MainActivity")
        configViewModel.connect {
            onConfigDisconnect()
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
