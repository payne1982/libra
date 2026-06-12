package eu.thepayne.libra.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.thepayne.libra.R
import eu.thepayne.libra.ble.LibraSyncService
import eu.thepayne.libra.ble.LibraSyncService.ScaleUser
import eu.thepayne.libra.ble.LibraSyncService.SyncState
import eu.thepayne.libra.ui.viewmodel.SyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onOpenLiveWeight: () -> Unit,
    vm: SyncViewModel = viewModel()
) {
    val state by vm.syncState.collectAsStateWithLifecycle()

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) vm.startSync()
    }

    val retryPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) vm.retryLast()
    }

    val measurePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) vm.startMeasure()
    }

    val livePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            vm.startLiveWeight()
            onOpenLiveWeight()
        }
    }

    fun requiredPermissions() = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.sync_title)) })

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                when (state) {
                    is SyncState.Idle -> {
                        Icon(Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = { permissionsLauncher.launch(requiredPermissions()) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.btn_sync_history))
                        }
                        Text(
                            stringResource(R.string.desc_sync_history),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        Button(
                            onClick = { measurePermissionsLauncher.launch(requiredPermissions()) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) {
                            Text(stringResource(R.string.btn_new_measurement))
                        }
                        Text(
                            stringResource(R.string.desc_new_measurement),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        OutlinedButton(
                            onClick = { livePermissionsLauncher.launch(requiredPermissions()) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.btn_live_weight))
                        }
                        Text(
                            stringResource(R.string.desc_live_weight),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    is SyncState.Scanning -> {
                        val infiniteTransition = rememberInfiniteTransition(label = "scan")
                        val angle by infiniteTransition.animateFloat(
                            initialValue = 0f, targetValue = 360f,
                            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing),
                                RepeatMode.Restart),
                            label = "rotation"
                        )
                        Icon(Icons.AutoMirrored.Filled.BluetoothSearching,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).rotate(angle),
                            tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.status_scanning),
                            style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = { vm.stopSync() }) {
                            Text(stringResource(R.string.btn_cancel))
                        }
                    }

                    is SyncState.Connecting -> {
                        CircularProgressIndicator(Modifier.size(60.dp))
                        Text(stringResource(R.string.status_connecting,
                                (state as SyncState.Connecting).deviceName),
                            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        OutlinedButton(onClick = { vm.stopSync() }) {
                            Text(stringResource(R.string.btn_cancel))
                        }
                    }

                    is SyncState.UserSelection -> {
                        val s = state as SyncState.UserSelection
                        Icon(Icons.Default.Bluetooth, null, Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.title_select_profile),
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.label_profiles_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        s.users.forEach { user ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(user.initials.trim(),
                                            style = MaterialTheme.typography.titleLarge)
                                        Text(stringResource(R.string.label_user_id, user.id),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    androidx.compose.material3.IconButton(
                                        onClick = { vm.deleteUser(user.id) }
                                    ) {
                                        Icon(Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.btn_delete),
                                            tint = MaterialTheme.colorScheme.error)
                                    }
                                    Button(onClick = { vm.selectUser(user.id) }) {
                                        Text(stringResource(R.string.btn_use))
                                    }
                                }
                            }
                        }
                        if (s.canCreate) {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            OutlinedButton(
                                onClick = { vm.createUser() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.btn_create_profile)) }
                            Text(stringResource(R.string.desc_create_profile),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                        }
                        TextButton(onClick = { vm.stopSync() }) {
                            Text(stringResource(R.string.btn_cancel))
                        }
                    }

                    is SyncState.Syncing -> {
                        val s = state as SyncState.Syncing
                        if (s.total > 0) {
                            CircularProgressIndicator(
                                progress = { s.received.toFloat() / s.total },
                                modifier = Modifier.size(60.dp)
                            )
                            Text(stringResource(R.string.status_downloading),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center)
                            Text(stringResource(R.string.status_downloading_progress,
                                    s.received, s.total, s.total / 2),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                        } else {
                            CircularProgressIndicator(Modifier.size(60.dp))
                            Text(stringResource(R.string.status_downloading_in_progress),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center)
                        }
                    }

                    is SyncState.Connected -> {
                        Icon(Icons.Default.Scale, null, Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.status_connected),
                            style = MaterialTheme.typography.bodyLarge)
                        CircularProgressIndicator(Modifier.size(40.dp))
                    }

                    is SyncState.LiveWeight -> {
                        val s = state as SyncState.LiveWeight
                        Icon(Icons.Default.Scale, null, Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.fmt_weight_kg, s.weightKg),
                            style = MaterialTheme.typography.headlineLarge)
                        Text(if (s.stable) stringResource(R.string.status_stabilizing)
                             else stringResource(R.string.status_measuring),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center)
                        CircularProgressIndicator(Modifier.size(40.dp))
                    }

                    is SyncState.MeasurementPending -> {
                        val s = state as SyncState.MeasurementPending
                        Icon(Icons.Default.Scale, null, Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.fmt_weight_kg, s.weightKg),
                            style = MaterialTheme.typography.headlineLarge)
                        Text(stringResource(R.string.label_body_comp,
                                s.bodyFatPct, s.musclePct, s.bmi),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { vm.saveMeasurement() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.btn_save))
                        }
                        OutlinedButton(
                            onClick = { vm.retryMeasurement() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.btn_retry))
                        }
                        TextButton(
                            onClick = { vm.stopSync() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.btn_exit_without_saving))
                        }
                    }

                    is SyncState.MeasurementReceived -> {
                        val s = state as SyncState.MeasurementReceived
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(80.dp),
                            tint = if (s.saved) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (s.saved)
                            stringResource(R.string.status_saved, s.weightKg)
                        else
                            stringResource(R.string.status_already_present, s.weightKg),
                            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    }

                    is SyncState.SyncComplete -> {
                        val s = state as SyncState.SyncComplete
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text(
                            if (s.downloadedCount == 0)
                                stringResource(R.string.status_sync_complete_none)
                            else
                                stringResource(R.string.status_sync_complete_count, s.downloadedCount),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { LibraSyncService.resetState() }) {
                            Text(stringResource(R.string.btn_ok))
                        }
                    }

                    is SyncState.Done -> {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.status_done),
                            style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { LibraSyncService.resetState() }) {
                            Text(stringResource(R.string.btn_ok))
                        }
                    }

                    is SyncState.Error -> {
                        Icon(Icons.Default.Error, null, Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Text((state as SyncState.Error).message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.stopSync() }) {
                                Text(stringResource(R.string.btn_close))
                            }
                            Button(onClick = { retryPermissionsLauncher.launch(requiredPermissions()) }) {
                                Text(stringResource(R.string.btn_retry))
                            }
                        }
                    }
                }
            }
        }
    }
}
