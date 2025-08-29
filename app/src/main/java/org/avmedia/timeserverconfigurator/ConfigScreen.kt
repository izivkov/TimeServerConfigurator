package org.avmedia.timeserverconfigurator

import ConnectionViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun ConfigScreen(
    modifier: Modifier = Modifier,
    viewModel: ConnectionViewModel
) {
    val connected = viewModel.isConnected
    val timezone = remember { java.util.TimeZone.getDefault().id }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ssidError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                value = ssid,
                onValueChange = {
                    ssid = it
                    ssidError = if (it.isBlank()) "SSID required" else null
                },
                isError = ssidError != null,
                label = { Text("SSID") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { ssidError?.let { Text(it, color = Color.Red) } }
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = if (it.length < 8) "Min 8 characters" else null
                },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                isError = passwordError != null,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { passwordError?.let { Text(it, color = Color.Red) } }
            )

            Spacer(Modifier.height(24.dp))

            Button(
                enabled = ssidError == null && passwordError == null && ssid.isNotBlank() && password.isNotBlank() && connected,
                onClick = {
                    val jsonObj = JSONObject().apply {
                        put("ssid", ssid.trim())
                        put("password", password.trim())
                        put("timezone", timezone.trim())
                    }
                    val jsonString = jsonObj.toString()
                    viewModel.connection.writeCredentials(jsonString)
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Submit")
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(20.dp)
                .background(
                    color = if (connected) Color.Green else Color.Red,
                    shape = CircleShape
                )
        )
    }
}
