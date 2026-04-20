package eu.thepayne.libra.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.thepayne.libra.LibraApplication
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = (app as LibraApplication).userPreferences

    val appPrefs = prefs.prefs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        eu.thepayne.libra.data.repository.AppPrefs()
    )

    private val _restartTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val restartTrigger: SharedFlow<Unit> = _restartTrigger.asSharedFlow()

    fun setWeightRange(minKg: Float, maxKg: Float) =
        viewModelScope.launch { prefs.setWeightRange(minKg, maxKg) }

    fun setUseKg(useKg: Boolean) =
        viewModelScope.launch { prefs.setUseKg(useKg) }

    fun resetScaleUserId() =
        viewModelScope.launch { prefs.setScaleUserId(0L) }

    fun setLanguageCode(code: String) = viewModelScope.launch {
        prefs.setLanguageCode(code)
        _restartTrigger.emit(Unit)
    }

    fun setUserProfile(
        initials: String,
        heightCm: Int,
        genderMale: Boolean,
        birthYear: Int,
        birthMonth: Int,
        birthDay: Int,
        activityLevel: Int,
    ) = viewModelScope.launch {
        prefs.setUserProfile(initials, heightCm, genderMale, birthYear, birthMonth, birthDay, activityLevel)
    }
}
