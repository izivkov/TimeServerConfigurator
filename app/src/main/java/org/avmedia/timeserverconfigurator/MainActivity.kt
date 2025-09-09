package org.avmedia.timeserverconfigurator

import ConnectionViewModel
import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.avmedia.timeserverconfigurator.ui.theme.TimeServerConfiguratorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ConnectionViewModel by viewModels {
        ConnectionViewModelFactory(this) // 'this' is a Context
    }

    class ConnectionViewModelFactory(
        private val context: Context
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ConnectionViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ConnectionViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CheckPermissions {
                viewModel.connect {
                    onDisconnect()
                }

                TimeServerConfiguratorTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        ConfigScreen(
                            modifier = Modifier.padding(innerPadding),
                            viewModel = viewModel,
                            context = this
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.disconnect()
    }

    private fun onDisconnect () {
        println("onDisconnect called in MainActivity")
        viewModel.connect {
            onDisconnect()
        }
    }
}

