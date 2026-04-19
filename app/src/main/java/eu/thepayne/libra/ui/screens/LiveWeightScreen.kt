package eu.thepayne.libra.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.thepayne.libra.R
import eu.thepayne.libra.ble.LibraSyncService.SyncState
import eu.thepayne.libra.ui.viewmodel.SyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveWeightScreen(onBack: () -> Unit, vm: SyncViewModel = viewModel()) {
    val state by vm.syncState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is SyncState.MeasurementReceived || state is SyncState.Done) {
            kotlinx.coroutines.delay(2000)
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.live_weight_title)) },
                navigationIcon = {
                    IconButton(onClick = { vm.stopSync(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "weight"
                ) { s ->
                    when (s) {
                        is SyncState.LiveWeight -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "%.2f".format(s.weightKg),
                                    fontSize = 72.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (s.stable) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "kg",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (s.stable) stringResource(R.string.status_stable)
                                    else stringResource(R.string.status_waiting),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (s.stable) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is SyncState.MeasurementReceived -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CheckCircle, null,
                                    Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                Text("%.2f kg".format(s.weightKg),
                                    fontSize = 48.sp, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.status_measurement_complete),
                                    style = MaterialTheme.typography.bodyLarge)
                            }
                        }

                        else -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.status_connecting_short),
                                    style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.status_step_on_scale),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
