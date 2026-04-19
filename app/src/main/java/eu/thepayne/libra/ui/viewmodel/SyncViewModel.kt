package eu.thepayne.libra.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import eu.thepayne.libra.ble.LibraSyncService

class SyncViewModel(app: Application) : AndroidViewModel(app) {

    val syncState = LibraSyncService.state

    private var lastAction: String = LibraSyncService.ACTION_START_SYNC

    fun startSync() {
        lastAction = LibraSyncService.ACTION_START_SYNC
        LibraSyncService.resetState()
        startService(LibraSyncService.ACTION_START_SYNC)
    }

    fun startMeasure() {
        lastAction = LibraSyncService.ACTION_START_MEASURE
        LibraSyncService.resetState()
        startService(LibraSyncService.ACTION_START_MEASURE)
    }

    fun startLiveWeight() {
        lastAction = LibraSyncService.ACTION_START_LIVE
        LibraSyncService.resetState()
        startService(LibraSyncService.ACTION_START_LIVE)
    }

    fun retryLast() {
        LibraSyncService.resetState()
        startService(lastAction)
    }

    fun saveMeasurement() {
        startService(LibraSyncService.ACTION_SAVE)
    }

    fun retryMeasurement() {
        startService(LibraSyncService.ACTION_RETRY)
    }

    fun createUser() {
        startService(LibraSyncService.ACTION_CREATE_USER)
    }

    fun deleteUser(userId: Long) {
        val intent = Intent(getApplication(), LibraSyncService::class.java).apply {
            action = LibraSyncService.ACTION_DELETE_USER
            putExtra(LibraSyncService.EXTRA_USER_ID, userId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    fun selectUser(userId: Long) {
        val intent = Intent(getApplication(), LibraSyncService::class.java).apply {
            action = LibraSyncService.ACTION_SELECT_USER
            putExtra(LibraSyncService.EXTRA_USER_ID, userId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    fun stopSync() {
        startService(LibraSyncService.ACTION_STOP)
    }

    private fun startService(action: String) {
        val intent = Intent(getApplication(), LibraSyncService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }
}
