// kotlin
package org.avmedia.timeserverconfigurator

import ConfigViewModel
import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.avmedia.timeserverconfigurator.ui.theme.TimeServerConfiguratorTheme

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : ComponentActivity() {

    private val configViewModel: ConfigViewModel by viewModels {
        ViewModelFactory(this) // 'this' is a Context
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
                return LogsViewModel(context.applicationContext as Application ) as T
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

<<<<<<< HEAD
=======
                logsViewModel.startScan()

>>>>>>> SendLogs
                configViewModel.connect {
                    onConfigDisconnect()
                }

                TimeServerConfiguratorTheme {
                    // The Scaffold no longer has a bottomBar
                    Scaffold(
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        // The content is now directly set to ConfigScreen
                        ConfigScreen(
                            modifier = Modifier.padding(innerPadding),
                            viewModel = configViewModel,
                            context = this
                        )
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
        configViewModel.disconnect()
    }

    private fun onConfigDisconnect() {
        println("onDisconnect called in MainActivity")
        configViewModel.connect {
            onConfigDisconnect()
        }
    }
}
