package eu.thepayne.libra.ui.screens

import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import eu.thepayne.libra.R
import eu.thepayne.libra.data.db.MeasurementEntity
import eu.thepayne.libra.ui.viewmodel.ChartPeriod
import eu.thepayne.libra.ui.viewmodel.MeasurementsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun daysSinceEpoch(ms: Long): Float = (ms / 86_400_000L).toFloat()
private fun msFromDays(days: Float): Long = days.toLong() * 86_400_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChartScreen(vm: MeasurementsViewModel = viewModel()) {
    val selectedPeriod by vm.selectedPeriod.collectAsStateWithLifecycle()
    val measurements by vm.measurements.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.chart_title)) })

        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChartPeriod.entries.forEach { period ->
                FilterChip(
                    selected = period == selectedPeriod,
                    onClick = { vm.selectedPeriod.value = period },
                    label = { Text(stringResource(period.labelRes)) }
                )
            }
        }

        if (measurements.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.chart_no_data),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
            val showYear = selectedPeriod.days == null || selectedPeriod.days!! >= 365
            val weightLabel = stringResource(R.string.chart_weight_kg)
            val fatLabel = stringResource(R.string.chart_fat_pct)
            val muscleLabel = stringResource(R.string.chart_muscle_pct)
            val boneLabel = stringResource(R.string.chart_bone_pct)

            WeightChart(
                measurements = measurements,
                textColor = textColor,
                showYear = showYear,
                weightLabel = weightLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(16.dp)
            )

            BodyCompPctChart(
                measurements = measurements,
                textColor = textColor,
                showYear = showYear,
                fatLabel = fatLabel,
                muscleLabel = muscleLabel,
                boneLabel = boneLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

private fun configureChart(chart: LineChart, textColor: Int) {
    chart.apply {
        description.isEnabled = false
        setTouchEnabled(true)
        isDragEnabled = true
        setScaleEnabled(true)
        legend.apply {
            isEnabled = true
            verticalAlignment = Legend.LegendVerticalAlignment.TOP
            horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
            setTextColor(textColor)
        }
        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            setTextColor(textColor)
            setLabelCount(5, false)
        }
        axisLeft.apply {
            setDrawGridLines(true)
            setTextColor(textColor)
        }
        axisRight.isEnabled = false
    }
}

private fun xFormatter(showYear: Boolean) = object : ValueFormatter() {
    val fmt = SimpleDateFormat(if (showYear) "MM/yy" else "dd/MM", Locale.getDefault())
    override fun getFormattedValue(value: Float) =
        fmt.format(Date(msFromDays(value)))
}

@Composable
private fun WeightChart(
    measurements: List<MeasurementEntity>,
    textColor: Int,
    showYear: Boolean,
    weightLabel: String,
    modifier: Modifier = Modifier
) {
    val sorted = measurements.sortedBy { it.timestampMs }
    val weightEntries = sorted.map { Entry(daysSinceEpoch(it.timestampMs), it.weightKg) }

    AndroidView(
        modifier = modifier,
        factory = { ctx -> LineChart(ctx).also { configureChart(it, textColor) } },
        update = { chart ->
            val weightSet = LineDataSet(weightEntries, weightLabel).apply {
                color = Color.rgb(25, 118, 210)
                setCircleColor(Color.rgb(25, 118, 210))
                lineWidth = 2f
                circleRadius = 3f
                setDrawValues(false)
            }
            chart.xAxis.valueFormatter = xFormatter(showYear)
            chart.xAxis.axisMinimum = weightEntries.minOfOrNull { it.x }?.minus(1f) ?: 0f
            chart.xAxis.axisMaximum = weightEntries.maxOfOrNull { it.x }?.plus(1f) ?: 1f
            val minY = weightEntries.minOfOrNull { it.y } ?: 0f
            chart.axisLeft.axisMinimum = (minY - 1f).coerceAtLeast(0f)
            chart.data = LineData(weightSet)
            chart.invalidate()
        }
    )
}

@Composable
private fun BodyCompPctChart(
    measurements: List<MeasurementEntity>,
    textColor: Int,
    showYear: Boolean,
    fatLabel: String,
    muscleLabel: String,
    boneLabel: String,
    modifier: Modifier = Modifier
) {
    val sorted = measurements.sortedBy { it.timestampMs }
    val fatEntries    = sorted.map { Entry(daysSinceEpoch(it.timestampMs), it.bodyFatPct) }
    val muscleEntries = sorted.map { Entry(daysSinceEpoch(it.timestampMs), it.musclePct) }
    val boneEntries   = sorted.map { Entry(daysSinceEpoch(it.timestampMs), if (it.weightKg > 0) it.boneMassKg / it.weightKg * 100f else 0f) }

    AndroidView(
        modifier = modifier,
        factory = { ctx -> LineChart(ctx).also { configureChart(it, textColor) } },
        update = { chart ->
            val fatSet = LineDataSet(fatEntries, fatLabel).apply {
                color = Color.rgb(211, 47, 47)
                setCircleColor(Color.rgb(211, 47, 47))
                lineWidth = 2f
                circleRadius = 3f
                setDrawValues(false)
            }
            val muscleSet = LineDataSet(muscleEntries, muscleLabel).apply {
                color = Color.rgb(56, 142, 60)
                setCircleColor(Color.rgb(56, 142, 60))
                lineWidth = 2f
                circleRadius = 3f
                setDrawValues(false)
            }
            val boneSet = LineDataSet(boneEntries, boneLabel).apply {
                color = Color.rgb(245, 127, 23)
                setCircleColor(Color.rgb(245, 127, 23))
                lineWidth = 2f
                circleRadius = 3f
                setDrawValues(false)
            }
            chart.xAxis.valueFormatter = xFormatter(showYear)
            val allEntries = fatEntries + muscleEntries + boneEntries
            chart.xAxis.axisMinimum = allEntries.minOfOrNull { it.x }?.minus(1f) ?: 0f
            chart.xAxis.axisMaximum = allEntries.maxOfOrNull { it.x }?.plus(1f) ?: 1f
            val minY = allEntries.minOfOrNull { it.y } ?: 0f
            chart.axisLeft.axisMinimum = (minY - 1f).coerceAtLeast(0f)
            chart.data = LineData(fatSet, muscleSet, boneSet)
            chart.invalidate()
        }
    )
}
