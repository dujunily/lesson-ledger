package cn.yiweiqiu.lessonledger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.yiweiqiu.lessonledger.core.Dates
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        trailingIcon = suffix?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false,
    onChange: (LocalDate?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp),
        ) {
            Text(
                text = date?.let { Dates.formatDate(it) } ?: "$label（未设置）",
                style = MaterialTheme.typography.bodyLarge,
                color = if (date == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (allowClear && date != null) {
            TextButton(onClick = { onChange(null) }) { Text("清除") }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.let { Dates.startOfDay(it) },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Dates.toDate(it)) }
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    onChange: (String?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val parsed = Dates.parseTimeOrNull(value) ?: LocalTime.of(18, 0)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp),
        ) {
            Text(
                text = value?.takeIf { it.isNotBlank() } ?: "$label（未设置）",
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (!value.isNullOrBlank()) {
            TextButton(onClick = { onChange(null) }) { Text("清除") }
        }
    }

    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = parsed.hour,
            initialMinute = parsed.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val text = String.format("%02d:%02d", state.hour, state.minute)
                    onChange(text)
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
            text = { TimePicker(state = state) },
            title = { Text(label) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipSelector(
    options: List<Pair<String, String>>,
    selected: Set<String>,
    modifier: Modifier = Modifier,
    onToggle: (String) -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (key, label) ->
            FilterChip(
                selected = selected.contains(key),
                onClick = { onToggle(key) },
                label = { Text(label) },
                modifier = Modifier.heightIn(min = 40.dp),
            )
        }
    }
}

fun formatTimeText(time: LocalTime?): String? =
    time?.format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
fun ColumnGap(height: Int) {
    Column(Modifier.padding(top = height.dp)) {}
}
