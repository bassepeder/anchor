package app.anchor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@Composable
fun TabColumn(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun AnchorCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) { content() }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) Muted(subtitle)
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    locked: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Muted(description)
            }
            if (locked) {
                Icon(Icons.Filled.Lock, contentDescription = "Always on", tint = MaterialTheme.colorScheme.primary)
            } else {
                Switch(
                    checked = checked,
                    onCheckedChange = onChange,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        disabledCheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        disabledCheckedIconColor = MaterialTheme.colorScheme.primary,
                    ),
                    thumbContent = if (checked && !enabled) {
                        { Icon(Icons.Filled.Lock, contentDescription = "Locked", modifier = Modifier.size(SwitchDefaults.IconSize)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
fun EntryField(placeholder: String, button: String, enabled: Boolean = true, onSubmit: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val entry = BlockList.normalize(input)
        if (entry == null) {
            error = "That doesn't look like a site address."
        } else {
            onSubmit(entry)
            input = ""
        }
    }

    val err = error
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                error = null
            },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            singleLine = true,
            placeholder = { Text(placeholder) },
            isError = err != null,
            supportingText = err?.let { message -> @Composable { Text(message) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) submit() }),
        )
        Button(
            onClick = { submit() },
            enabled = enabled && input.isNotBlank(),
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(button) }
    }
}

@Composable
fun EntryRow(text: String, action: String, enabled: Boolean, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onAction, enabled = enabled) { Text(action) }
    }
}

class Confirm(
    val title: String,
    val text: String,
    val confirmLabel: String = "Confirm",
    val destructive: Boolean = false,
    val action: () -> Unit,
)

@Composable
fun ConfirmDialog(confirm: Confirm?, onDismiss: () -> Unit) {
    if (confirm == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(confirm.title) },
        text = { Text(confirm.text) },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    confirm.action()
                },
                colors = if (confirm.destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Text(confirm.confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun formatDuration(ms: Long): String {
    val hours = ms / 3_600_000
    if (hours >= 48 && hours % 24 == 0L) return "${hours / 24} days"
    if (hours >= 1) return if (hours == 1L) "1 hour" else "$hours hours"
    val minutes = ms / 60_000
    return if (minutes == 1L) "1 minute" else "$minutes minutes"
}

fun formatTime(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

fun formatDate(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

fun formatCount(n: Long): String = NumberFormat.getIntegerInstance().format(n)

fun formatCountdown(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
