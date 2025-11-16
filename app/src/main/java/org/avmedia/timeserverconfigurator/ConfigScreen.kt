package org.avmedia.timeserverconfigurator

import ConfigViewModel
import android.content.Context
import android.telephony.TelephonyManager
import androidx.annotation.ColorInt
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
import androidx.compose.material3.MaterialTheme
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
import androidx.core.content.ContextCompat.getSystemService
import org.json.JSONObject
import java.text.SimpleDateFormat

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.VisualTransformation

enum class DateFormat { DAY_MONTH, MONTH_DAY }
enum class TimeFormat { TWELVE_HOURS, TWENTY_FOUR_HOURS }

fun getDateTimeFormat(): Pair<DateFormat, TimeFormat> {
    // Use SimpleDateFormat.getDateTimeInstance() once
    val pattern = SimpleDateFormat().toPattern()
    val parts = pattern.split(' ')
    val datePattern = parts.getOrNull(0)?.lowercase() ?: ""
    val timePattern = parts.getOrNull(1)?.lowercase() ?: ""

    val dateFormat = if (datePattern.startsWith("d")) DateFormat.DAY_MONTH else DateFormat.MONTH_DAY
    val timeFormat =
        if (timePattern.startsWith("h")) TimeFormat.TWELVE_HOURS else TimeFormat.TWENTY_FOUR_HOURS

    return Pair(dateFormat, timeFormat)
}

fun temperatureUnitByCountry(countryCode: String): String {
    // List of country codes where Fahrenheit is used
    val fahrenheitCountries = setOf(
        "US", "VI", "PR", "AS", "MP"
    )
    return if (fahrenheitCountries.contains(countryCode.uppercase())) {
        "F"
    } else {
        "C"
    }
}

data class ThemeColors(
    @ColorInt val foreground: Int,
    @ColorInt val background: Int
)

@Composable
fun ConfigScreen(
    modifier: Modifier = Modifier,
    viewModel: ConfigViewModel,
    context: Context
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

            var passwordVisible by remember { mutableStateOf(false) }

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = if (it.length < 8) "Min 8 characters" else null
                },
                label = { Text("Password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = passwordError != null,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        val icon = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        val desc = if (passwordVisible) "Hide password" else "Show password"
                        Icon(imageVector = icon, contentDescription = desc)
                    }
                },
                supportingText = { passwordError?.let { Text(it, color = Color.Red) } }
            )

            Spacer(Modifier.height(24.dp))
            val (dateFormat, timeFormat) = getDateTimeFormat()

            val (foreground, background) = getColors()

            Button(
                enabled = ssidError == null && passwordError == null && ssid.isNotBlank() && password.isNotBlank() && connected,
                onClick = {
                    val jsonObj = JSONObject().apply {
                        put("ssid", ssid.trim())
                        put("password", password.trim())
                        put("timezone", timezone.trim())
                        put(
                            "dateformat",
                            if (dateFormat == DateFormat.DAY_MONTH) "DD/MM" else "MM/DD"
                        )
                        put(
                            "timeformat",
                            if (timeFormat == TimeFormat.TWELVE_HOURS) "12H" else "24H"
                        )

                        put(
                            "temperature_unit", temperatureUnitByCountry(
                                (getSystemService(
                                    context,
                                    TelephonyManager::class.java
                                )?.networkCountryIso ?: "US")
                            )
                        )

                        put("foreground_color", foreground)
                        put("background_color", background)
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
        Text(
            text = "v" + BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomStart) // Position in the lower-left
                .padding(16.dp)
        )
    }
}

@Composable
private fun getAppVersionName(context: Context): String? {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
    } catch (e: Exception) {
        // In case of an error, return a fallback string
        "N/A"
    }
}

fun Color.toHex(includeAlpha: Boolean = true): String {
    val alpha = (alpha * 255).toInt().coerceIn(0, 255)
    val red = (red * 255).toInt().coerceIn(0, 255)
    val green = (green * 255).toInt().coerceIn(0, 255)
    val blue = (blue * 255).toInt().coerceIn(0, 255)

    return if (includeAlpha) {
        String.format("#%02X%02X%02X%02X", alpha, red, green, blue)
    } else {
        String.format("#%02X%02X%02X", red, green, blue)
    }
}

fun Color.toRgbInt(): Int {
    // Convert the float components (0.0 to 1.0) to 0-255 range and pack them into an Int
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return (r shl 16) or (g shl 8) or b
}

@Composable
fun getColors(): Pair<Int, Int> {
    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val fg = colorScheme.onBackground

    return Pair(fg.toRgbInt(), bg.toRgbInt())
}

