package eu.thepayne.libra.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.thepayne.libra.R
import eu.thepayne.libra.data.db.MeasurementEntity
import eu.thepayne.libra.ui.viewmodel.MeasurementsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(onBack: () -> Unit, vm: MeasurementsViewModel = viewModel()) {
    var timestampMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var weightText by remember { mutableStateOf("") }
    var weightError by remember { mutableStateOf(false) }
    var fatPctText by remember { mutableStateOf("") }
    var fatKgText by remember { mutableStateOf("") }
    var musclePctText by remember { mutableStateOf("") }
    var muscleKgText by remember { mutableStateOf("") }
    var boneKgText by remember { mutableStateOf("") }
    var waterPctText by remember { mutableStateOf("") }
    var bmiText by remember { mutableStateOf("") }
    var bmrText by remember { mutableStateOf("") }
    var amrText by remember { mutableStateOf("") }
    var impedanceText by remember { mutableStateOf("") }

    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    fun toFloat(s: String) = s.replace(",", ".").toFloatOrNull() ?: 0f

    fun save() {
        val weight = weightText.replace(",", ".").toFloatOrNull()
        if (weight == null || weight <= 0f) { weightError = true; return }
        // Use current seconds/ms within the chosen minute for uniqueness
        val finalTs = Calendar.getInstance().run {
            timeInMillis = timestampMs
            val now = Calendar.getInstance()
            set(Calendar.SECOND, now.get(Calendar.SECOND))
            set(Calendar.MILLISECOND, now.get(Calendar.MILLISECOND))
            timeInMillis
        }
        vm.insertManual(MeasurementEntity(
            timestampMs = finalTs,
            weightKg = weight,
            bodyFatPct = toFloat(fatPctText),
            bodyFatKg = toFloat(fatKgText),
            musclePct = toFloat(musclePctText),
            muscleMassKg = toFloat(muscleKgText),
            boneMassKg = toFloat(boneKgText),
            bodyWaterPct = toFloat(waterPctText),
            bmi = toFloat(bmiText),
            bmr = bmrText.toIntOrNull() ?: 0,
            amr = amrText.toIntOrNull() ?: 0,
            impedanceOhm = impedanceText.toIntOrNull() ?: 0,
        ))
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manual_entry_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerField(
                    label = stringResource(R.string.field_date),
                    value = dateFmt.format(Date(timestampMs)),
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                )
                PickerField(
                    label = stringResource(R.string.field_time),
                    value = timeFmt.format(Date(timestampMs)),
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it; weightError = false },
                label = { Text(stringResource(R.string.field_weight_required)) },
                isError = weightError,
                supportingText = if (weightError) {
                    { Text(stringResource(R.string.error_weight_required)) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                stringResource(R.string.section_body_comp_optional),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionalField(fatPctText, { fatPctText = it },
                    stringResource(R.string.field_fat_pct), Modifier.weight(1f))
                OptionalField(fatKgText, { fatKgText = it },
                    stringResource(R.string.field_fat_kg), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionalField(musclePctText, { musclePctText = it },
                    stringResource(R.string.field_muscle_pct), Modifier.weight(1f))
                OptionalField(muscleKgText, { muscleKgText = it },
                    stringResource(R.string.field_muscle_kg), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionalField(boneKgText, { boneKgText = it },
                    stringResource(R.string.field_bone_kg), Modifier.weight(1f))
                OptionalField(waterPctText, { waterPctText = it },
                    stringResource(R.string.field_water_pct), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionalField(bmiText, { bmiText = it }, "BMI", Modifier.weight(1f))
                OptionalField(bmrText, { bmrText = it },
                    stringResource(R.string.field_bmr), Modifier.weight(1f), isInt = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionalField(amrText, { amrText = it },
                    stringResource(R.string.field_amr), Modifier.weight(1f), isInt = true)
                OptionalField(impedanceText, { impedanceText = it },
                    stringResource(R.string.field_impedance_ohm), Modifier.weight(1f), isInt = true)
            }

            Spacer(Modifier.height(4.dp))
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_save))
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = timestampMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selected ->
                        val cur = Calendar.getInstance().apply { timeInMillis = timestampMs }
                        val sel = Calendar.getInstance().apply { timeInMillis = selected }
                        cur.set(Calendar.YEAR, sel.get(Calendar.YEAR))
                        cur.set(Calendar.MONTH, sel.get(Calendar.MONTH))
                        cur.set(Calendar.DAY_OF_MONTH, sel.get(Calendar.DAY_OF_MONTH))
                        timestampMs = cur.timeInMillis
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.btn_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val state = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Card {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TimePicker(state = state)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(R.string.btn_cancel))
                        }
                        TextButton(onClick = {
                            val updated = Calendar.getInstance().apply {
                                timeInMillis = timestampMs
                                set(Calendar.HOUR_OF_DAY, state.hour)
                                set(Calendar.MINUTE, state.minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            timestampMs = updated.timeInMillis
                            showTimePicker = false
                        }) {
                            Text(stringResource(R.string.btn_ok))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
private fun OptionalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isInt: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isInt) KeyboardType.Number else KeyboardType.Decimal
        ),
        modifier = modifier
    )
}
