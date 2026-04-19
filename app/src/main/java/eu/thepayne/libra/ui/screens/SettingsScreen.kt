package eu.thepayne.libra.ui.screens

import android.app.Activity
import androidx.annotation.StringRes
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.thepayne.libra.R
import eu.thepayne.libra.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

private data class LanguageOption(val code: String, @StringRes val labelRes: Int)
private val languageOptions = listOf(
    LanguageOption("", R.string.language_system),
    LanguageOption("en", R.string.language_english),
    LanguageOption("it", R.string.language_italian),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val prefs by vm.appPrefs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.restartTrigger.collect { (context as Activity).recreate() }
    }

    var minText by remember(prefs.weightMinKg) { mutableStateOf("%.1f".format(prefs.weightMinKg)) }
    var maxText by remember(prefs.weightMaxKg) { mutableStateOf("%.1f".format(prefs.weightMaxKg)) }

    var initialsText by remember(prefs.initials) { mutableStateOf(prefs.initials) }
    var heightText by remember(prefs.heightCm) { mutableStateOf(prefs.heightCm.toString()) }
    var genderMale by remember(prefs.genderMale) { mutableStateOf(prefs.genderMale) }
    var birthYearText by remember(prefs.birthYear) { mutableStateOf(prefs.birthYear.toString()) }
    var birthMonthText by remember(prefs.birthMonth) { mutableStateOf(prefs.birthMonth.toString()) }
    var birthDayText by remember(prefs.birthDay) { mutableStateOf(prefs.birthDay.toString()) }
    var activitySlider by remember(prefs.activityLevel) { mutableFloatStateOf(prefs.activityLevel.toFloat()) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.settings_title)) })

        Column(
            Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Lingua ───────────────────────────────────────────────────────────
            Text(stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            var langExpanded by remember { mutableStateOf(false) }
            val currentOption = languageOptions.find { it.code == prefs.languageCode }
                ?: languageOptions[0]

            ExposedDropdownMenuBox(
                expanded = langExpanded,
                onExpandedChange = { langExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = stringResource(currentOption.labelRes),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.settings_language)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = langExpanded,
                    onDismissRequest = { langExpanded = false }
                ) {
                    languageOptions.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(stringResource(opt.labelRes)) },
                            onClick = {
                                langExpanded = false
                                if (opt.code != prefs.languageCode) vm.setLanguageCode(opt.code)
                            }
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 20.dp))

            // ── Filtro peso ───────────────────────────────────────────────────
            Text(stringResource(R.string.settings_weight_filter),
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_weight_filter_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = it },
                    label = { Text(stringResource(R.string.settings_weight_min)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Text("  –  ", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it },
                    label = { Text(stringResource(R.string.settings_weight_max)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val min = minText.replace(",", ".").toFloatOrNull() ?: return@Button
                    val max = maxText.replace(",", ".").toFloatOrNull() ?: return@Button
                    if (min < max) vm.setWeightRange(min, max)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_save_range))
            }

            HorizontalDivider(Modifier.padding(vertical = 20.dp))

            Text(stringResource(R.string.settings_unit),
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_show_kg), Modifier.weight(1f))
                Switch(
                    checked = prefs.useKg,
                    onCheckedChange = { vm.setUseKg(it) }
                )
            }
            Text(
                if (prefs.useKg) stringResource(R.string.settings_unit_kg)
                else stringResource(R.string.settings_unit_lb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(Modifier.padding(vertical = 20.dp))

            Text(stringResource(R.string.settings_user_profile),
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_user_profile_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = initialsText,
                onValueChange = { if (it.length <= 3) initialsText = it.uppercase() },
                label = { Text(stringResource(R.string.settings_initials)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it },
                label = { Text(stringResource(R.string.settings_height)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Text(stringResource(R.string.settings_gender),
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = genderMale,
                    onClick = { genderMale = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text(stringResource(R.string.settings_gender_male)) }
                SegmentedButton(
                    selected = !genderMale,
                    onClick = { genderMale = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text(stringResource(R.string.settings_gender_female)) }
            }
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = birthYearText,
                    onValueChange = { birthYearText = it },
                    label = { Text(stringResource(R.string.settings_birth_year)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(3f)
                )
                Text("  /  ")
                OutlinedTextField(
                    value = birthMonthText,
                    onValueChange = { birthMonthText = it },
                    label = { Text(stringResource(R.string.settings_birth_month)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(2f)
                )
                Text("  /  ")
                OutlinedTextField(
                    value = birthDayText,
                    onValueChange = { birthDayText = it },
                    label = { Text(stringResource(R.string.settings_birth_day)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(2f)
                )
            }
            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.settings_activity_level, activitySlider.roundToInt()),
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = activitySlider,
                onValueChange = { activitySlider = it },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    val initials = initialsText.uppercase().take(3).padEnd(3, 'A')
                    val height = heightText.toIntOrNull()?.coerceIn(100, 220) ?: return@Button
                    val year = birthYearText.toIntOrNull()?.coerceIn(1900, 2100) ?: return@Button
                    val month = birthMonthText.toIntOrNull()?.coerceIn(1, 12) ?: return@Button
                    val day = birthDayText.toIntOrNull()?.coerceIn(1, 31) ?: return@Button
                    val activity = activitySlider.roundToInt().coerceIn(1, 5)
                    vm.setUserProfile(initials, height, genderMale, year, month, day, activity)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_save_profile))
            }

            val scaleUserId = prefs.scaleUserId
            if (scaleUserId != 1234L) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.resetScaleUserId() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_forget_scale_profile, scaleUserId))
                }
            }
        }
    }
}
