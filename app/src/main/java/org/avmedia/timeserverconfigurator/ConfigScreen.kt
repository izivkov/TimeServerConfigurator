package org.avmedia.timeserverconfigurator

import ConnectionViewModel
import android.content.Context
import android.telephony.TelephonyManager
import android.util.TypedValue
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
        "US", "LR", "MH", "VI", "FM", "KY", "BS", "BZ", "PW", "PR", "CY", "TC",
        "AG", "KN", "VG", "AS", "MP", "MS"
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

fun getThemeColors(context: Context): ThemeColors {
    val typedValue = TypedValue()

    // Resolve foreground/text color - using android:textColorPrimary as an example
    context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
    val foregroundColor = typedValue.data

    // Resolve background color - using android:windowBackground as an example
    context.theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true)
    val backgroundColor = typedValue.data

    return ThemeColors(foreground = foregroundColor, background = backgroundColor)
}

@Composable
fun ConfigScreen(
    modifier: Modifier = Modifier,
    viewModel: ConnectionViewModel,
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
            val (dateFormat, timeFormat) = getDateTimeFormat()

            val (hexForeground, hexBackground) = GetHexColors()

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

                        put("foreground_color", hexForeground)
                        put("background_color", hexBackground)
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

@Composable
fun GetHexColors(): Pair<String, String> {
    val colorScheme = MaterialTheme.colorScheme
    val bgHex = colorScheme.background.toHex()
    val fgHex = colorScheme.onBackground.toHex()
    return fgHex to bgHex
}

