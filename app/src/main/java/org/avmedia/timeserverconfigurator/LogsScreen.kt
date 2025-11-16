import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd h:mm:ss a")

@SuppressLint("NewApi")
@Composable
fun LogsScreen(
    logsViewModel: LogsViewModel,
    modifier: Modifier = Modifier,
) {
    val logs by logsViewModel.logs.collectAsState()
    val isConnected by logsViewModel.connected.collectAsState()
    val listState = rememberLazyListState()

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(logs) { entry ->
                    LogRow(entry)
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(20.dp)
                .background(
                    color = if (isConnected) Color.Green else Color.Red,
                    shape = CircleShape
                )
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun LogRow(entry: LogEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically // Align items vertically
            ) {
                Text(
                    text = entry.datetime.format(timeFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Group watchName and statusCode together
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically // Add this line
                ) {
                    entry.watchName.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    entry.statusCode?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1E88E5) // blue-ish badge color
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            entry.activityName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF1E88E5) // blue-ish badge color
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            entry.message?.let {
                Text(
                    text = AnnotatedString.fromHtml(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF1E88E5) // blue-ish badge color
                )
            }
        }
    }
}
