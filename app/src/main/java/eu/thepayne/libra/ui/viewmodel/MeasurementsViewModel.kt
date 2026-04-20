package eu.thepayne.libra.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.thepayne.libra.LibraApplication
import eu.thepayne.libra.R
import eu.thepayne.libra.data.db.MeasurementEntity
import eu.thepayne.libra.util.ImportExport
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class ChartPeriod(@StringRes val labelRes: Int, val days: Long?) {
    WEEK(R.string.period_7_days, 7),
    MONTH(R.string.period_30_days, 30),
    THREE_MONTHS(R.string.period_3_months, 90),
    SIX_MONTHS(R.string.period_6_months, 180),
    YEAR(R.string.period_1_year, 365),
    ALL(R.string.period_all, null),
}

class MeasurementsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as LibraApplication).measurementRepository

    val allMeasurements: StateFlow<List<MeasurementEntity>> =
        repo.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedPeriod = MutableStateFlow(ChartPeriod.MONTH)

    val measurements: StateFlow<List<MeasurementEntity>> = selectedPeriod
        .flatMapLatest { period ->
            val fromMs = period.days?.let { System.currentTimeMillis() - TimeUnit.DAYS.toMillis(it) } ?: 0L
            repo.observeSince(fromMs)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) = viewModelScope.launch { repo.deleteById(id) }

    fun insertManual(entity: eu.thepayne.libra.data.db.MeasurementEntity) =
        viewModelScope.launch { repo.insert(entity) }

    private val _importResult = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val importResult: SharedFlow<Int> = _importResult.asSharedFlow()

    fun importMeasurements(uri: Uri) = viewModelScope.launch {
        val items = ImportExport.importFromUri(uri, getApplication<LibraApplication>().contentResolver)
        val count = repo.insertAll(items)
        _importResult.emit(count)
    }
}
