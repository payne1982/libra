package eu.thepayne.libra.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.thepayne.libra.R
import eu.thepayne.libra.data.db.MeasurementEntity
import eu.thepayne.libra.ui.viewmodel.MeasurementsViewModel
import eu.thepayne.libra.util.ImportExport
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onAddManual: () -> Unit = {}, vm: MeasurementsViewModel = viewModel()) {
    val measurements by vm.allMeasurements.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expandedId by remember { mutableLongStateOf(-1L) }
    var confirmDeleteId by remember { mutableLongStateOf(-1L) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importMeasurements(it) } }

    LaunchedEffect(Unit) {
        vm.importResult.collect { count ->
            val msg = if (count > 0)
                context.getString(R.string.toast_import_success, count)
            else
                context.getString(R.string.toast_import_none)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                actions = {
                    IconButton(onClick = {
                        scope.launch { ImportExport.exportJson(context, measurements) }
                    }) { Icon(Icons.Default.FileUpload, stringResource(R.string.cd_export_json)) }
                    IconButton(onClick = {
                        scope.launch { ImportExport.exportCsv(context, measurements) }
                    }) { Icon(Icons.Default.FileUpload, stringResource(R.string.cd_export_csv)) }
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/csv", "*/*"))
                    }) { Icon(Icons.Default.FileDownload, stringResource(R.string.cd_import)) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddManual) {
                Icon(Icons.Default.Add, stringResource(R.string.cd_add_entry))
            }
        }
    ) { innerPadding ->
        if (measurements.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(measurements, key = { it.id }) { m ->
                    MeasurementCard(
                        m = m,
                        expanded = expandedId == m.id,
                        onClick = { expandedId = if (expandedId == m.id) -1L else m.id },
                        onDelete = { confirmDeleteId = m.id }
                    )
                }
            }
        }
    }

    if (confirmDeleteId != -1L) {
        AlertDialog(
            onDismissRequest = { confirmDeleteId = -1L },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_text)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(confirmDeleteId); confirmDeleteId = -1L }) {
                    Text(stringResource(R.string.btn_delete),
                        color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = -1L }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun MeasurementCard(
    m: MeasurementEntity,
    expanded: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onClick() }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(fmt.format(Date(m.timestampMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.fmt_weight_kg, m.weightKg),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.label_fat_muscle, m.bodyFatPct, m.musclePct),
                        style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, stringResource(R.string.cd_delete),
                        tint = MaterialTheme.colorScheme.error)
                }
            }

            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                MetricRow(stringResource(R.string.metric_bmi), "%.1f".format(m.bmi))
                MetricRow(stringResource(R.string.metric_fat_mass),
                    "%.2f kg  (%.1f%%)".format(m.bodyFatKg, m.bodyFatPct))
                MetricRow(stringResource(R.string.metric_muscle_mass),
                    "%.2f kg  (%.1f%%)".format(m.muscleMassKg, m.musclePct))
                MetricRow(stringResource(R.string.metric_bone_mass),
                    "%.2f kg".format(m.boneMassKg))
                MetricRow(stringResource(R.string.metric_body_water),
                    "%.1f%%".format(m.bodyWaterPct))
                MetricRow(stringResource(R.string.metric_bmr), "${m.bmr} kcal/g")
                MetricRow(stringResource(R.string.metric_amr), "${m.amr} kcal/g")
                MetricRow(stringResource(R.string.metric_impedance), "${m.impedanceOhm} Ω")
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
