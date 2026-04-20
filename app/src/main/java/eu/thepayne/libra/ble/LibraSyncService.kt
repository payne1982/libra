package eu.thepayne.libra.ble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import eu.thepayne.libra.LibraApplication
import eu.thepayne.libra.MainActivity
import eu.thepayne.libra.R
import eu.thepayne.libra.data.db.MeasurementEntity
import eu.thepayne.libra.data.repository.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class LibraSyncService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var gatt: BluetoothGatt? = null
    private var ffe1Char: BluetoothGattCharacteristic? = null
    private val parser = LibraParser()

    // ─── Operating mode ───────────────────────────────────────────────────────

    private enum class ServiceMode {
        SYNC,    // download history from scale
        MEASURE, // live weighing, asks user whether to save
        LIVE,    // live weight display without saving
    }

    // ─── Protocol state machine ────────────────────────────────────────────────

    private enum class ProtocolStep {
        INIT, WAIT_USER_LIST, WAIT_SETUP, DELETING_USER, CREATING_USER,
        GETTING_MEASUREMENTS, GETTING_UNKNOWN, ASSIGNING_UNKNOWN,
        WAIT_MEASURE, MEASURING
    }

    private enum class WriteIntent { NONE, SET_DATA_TIME, SET_UNIT }

    private var serviceMode = ServiceMode.SYNC
    private var protocolStep = ProtocolStep.INIT
    private var expectedUserCount = 0
    private var receivedUserCount = 0
    private var selectedUserId = 0L
    private var userFound = false
    private val scaleUsers = mutableListOf<ScaleUser>()
    private var writeIntent = WriteIntent.NONE
    private var getUserListRetries = 0
    private var cachedPrefs: AppPrefs? = null

    // Pending measurement awaiting user confirmation (MEASURE mode only)
    private var pendingEntity: MeasurementEntity? = null

    // History download state (getUserMeasurements)
    private var gumTotalSubPkts = 0
    private var gumDownloadedCount = 0
    private var gumCurrentTs = 0L
    private var gumCurrentWeight = 0f
    private var gumCurrentImpedance = 0
    private var gumCurrentBodyFat = 0f
    private var gumBoneMsb = 0

    // Unknown measurements state (getUnknownMeasurements + assignMeasurementToUser)
    private data class UnknownMeasurement(val slotId: Int, val ts: Long, val weight: Float, val imp: Int)
    private val unknownPending = mutableListOf<UnknownMeasurement>()
    private var unknownTotalCount = 0
    private var unknownReceivedCount = 0
    private var unknownAssignIdx = 0
    // Partial state for 0x4C response parsing (assign body comp)
    private var assignBoneMsb = 0
    private var assignBodyFat = 0f

    companion object {
        const val ACTION_START_SYNC    = "eu.thepayne.libra.START_SYNC"
        const val ACTION_START_MEASURE = "eu.thepayne.libra.START_MEASURE"
        const val ACTION_START_LIVE    = "eu.thepayne.libra.START_LIVE"
        const val ACTION_SAVE          = "eu.thepayne.libra.SAVE"
        const val ACTION_RETRY         = "eu.thepayne.libra.RETRY"
        const val ACTION_STOP          = "eu.thepayne.libra.STOP"
        const val ACTION_SELECT_USER   = "eu.thepayne.libra.SELECT_USER"
        const val ACTION_CREATE_USER   = "eu.thepayne.libra.CREATE_USER"
        const val ACTION_DELETE_USER   = "eu.thepayne.libra.DELETE_USER"
        const val EXTRA_USER_ID        = "eu.thepayne.libra.USER_ID"

        private const val NOTIF_CHANNEL = "libra_sync"
        private const val NOTIF_ID = 1
        private const val TAG = "LibraBLE"

        private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
        val state: StateFlow<SyncState> = _state.asStateFlow()

        fun resetState() { _state.value = SyncState.Idle }
    }

    data class ScaleUser(val id: Long, val initials: String)

    sealed class SyncState {
        data object Idle : SyncState()
        data object Scanning : SyncState()
        data class Connecting(val deviceName: String) : SyncState()
        data class Connected(val deviceName: String) : SyncState()
        data class UserSelection(val users: List<ScaleUser>, val canCreate: Boolean) : SyncState()
        data class Syncing(val received: Int = 0, val total: Int = 0) : SyncState()
        data class LiveWeight(val weightKg: Float, val stable: Boolean) : SyncState()
        // Completed weighing: awaiting user decision
        data class MeasurementPending(
            val weightKg: Float,
            val bodyFatPct: Float,
            val musclePct: Float,
            val bmi: Float,
        ) : SyncState()
        data class MeasurementReceived(val saved: Boolean, val weightKg: Float) : SyncState()
        data class SyncComplete(val downloadedCount: Int) : SyncState()
        data class Error(val message: String) : SyncState()
        data object Done : SyncState()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SYNC -> {
                startForeground(NOTIF_ID, buildNotification(getString(R.string.notification_downloading)))
                startScan(ServiceMode.SYNC)
            }
            ACTION_START_MEASURE -> {
                startForeground(NOTIF_ID, buildNotification(getString(R.string.notification_new_measurement)))
                startScan(ServiceMode.MEASURE)
            }
            ACTION_START_LIVE -> {
                startForeground(NOTIF_ID, buildNotification(getString(R.string.notification_live_weight)))
                startScan(ServiceMode.LIVE)
            }
            ACTION_SAVE -> handleSave()
            ACTION_RETRY -> handleRetry()
            ACTION_SELECT_USER -> {
                val userId = intent.getLongExtra(EXTRA_USER_ID, -1L)
                if (userId != -1L) handleUserSelected(userId)
            }
            ACTION_CREATE_USER -> {
                _state.value = SyncState.Syncing()
                updateNotification(getString(R.string.status_creating_profile))
                protocolStep = ProtocolStep.CREATING_USER
                scope.launch { sendCreateUser() }
            }
            ACTION_DELETE_USER -> {
                val userId = intent.getLongExtra(EXTRA_USER_ID, -1L)
                if (userId != -1L) {
                    protocolStep = ProtocolStep.DELETING_USER
                    val cmd = ByteArray(10)
                    cmd[0] = 0xF7.toByte()
                    cmd[1] = 0x32.toByte()
                    longToBE(userId).copyInto(cmd, 2)
                    writeFFE1(cmd)
                }
            }
            ACTION_STOP -> {
                startForeground(NOTIF_ID, buildNotification(getString(R.string.notification_cancelled)))
                pendingEntity = null
                _state.value = SyncState.Idle
                gatt?.disconnect()
                gatt?.close()
                gatt = null
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        gatt?.close()
        gatt = null
        ffe1Char = null
        scope.cancel()
        val cur = _state.value
        if (cur !is SyncState.SyncComplete && cur !is SyncState.Done &&
            cur !is SyncState.MeasurementReceived && cur !is SyncState.MeasurementPending &&
            cur !is SyncState.Error) {
            _state.value = SyncState.Idle
        }
        super.onDestroy()
    }

    private fun handleSave() {
        scope.launch {
            val entity = pendingEntity ?: return@launch
            val saved = (application as LibraApplication).measurementRepository.insert(entity)
            pendingEntity = null
            _state.value = SyncState.MeasurementReceived(saved = saved, weightKg = entity.weightKg)
            delay(1500)
            finishSync()
        }
    }

    private fun handleUserSelected(userId: Long) {
        selectedUserId = userId
        userFound = true
        scope.launch {
            (application as LibraApplication).userPreferences.setScaleUserId(userId)
        }
        onSetupComplete()
    }

    private fun handleRetry() {
        pendingEntity = null
        gatt?.close()
        gatt = null
        ffe1Char = null
        startScan(ServiceMode.MEASURE)
    }

    // ─── BLE scan ────────────────────────────────────────────────────────────

    private fun startScan(mode: ServiceMode) {
        serviceMode = mode
        protocolStep = ProtocolStep.INIT
        expectedUserCount = 0
        receivedUserCount = 0
        selectedUserId = 0L
        userFound = false
        scaleUsers.clear()
        writeIntent = WriteIntent.NONE
        getUserListRetries = 0
        gumTotalSubPkts = 0
        gumDownloadedCount = 0
        parser.reset()
        _state.value = SyncState.Scanning

        scope.launch {
            // Load prefs before BLE so selectedUserId is set when comparing scale users
            val prefs = cachedPrefs
                ?: (application as LibraApplication).userPreferences.prefs.first()
                    .also { cachedPrefs = it }
            selectedUserId = prefs.scaleUserId

            val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val scanner = btManager.adapter?.bluetoothLeScanner ?: run {
                _state.value = SyncState.Error(getString(R.string.error_bluetooth_unavailable))
                stopSelf(); return@launch
            }
            scanner.startScan(null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback)

            delay(30_000)
            if (_state.value == SyncState.Scanning) {
                scanner.stopScan(scanCallback)
                _state.value = SyncState.Error(getString(R.string.error_scale_not_found))
                stopSelf()
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: return
            if (!name.contains("libra", ignoreCase = true)) return

            val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            btManager.adapter?.bluetoothLeScanner?.stopScan(this)

            _state.value = SyncState.Connecting(name)
            updateNotification(getString(R.string.notification_connecting_device, name))
            result.device.connectGatt(this@LibraSyncService, false, gattCallback)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.d(TAG, "onScanFailed errorCode=$errorCode")
            _state.value = SyncState.Error(getString(R.string.error_bluetooth_unavailable))
            stopSelf()
        }
    }

    // ─── GATT ─────────────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            gatt = g
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val name = g.device.name ?: "Libra"
                _state.value = SyncState.Connecting(name)
                updateNotification(getString(R.string.notification_connecting_device, name))
                g.discoverServices()
            } else {
                gatt?.close(); gatt = null; ffe1Char = null
                val cur = _state.value
                when {
                    cur is SyncState.MeasurementPending -> {}
                    cur is SyncState.Done || cur is SyncState.SyncComplete -> stopSelf()
                    else -> {
                        _state.value = SyncState.Error(getString(R.string.error_connection_lost))
                        stopSelf()
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = SyncState.Error(getString(R.string.error_services_not_found))
                g.close(); stopSelf(); return
            }
            enableNotifications(g)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d(TAG, "onCharacteristicWrite: status=$status writeIntent=$writeIntent")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "write FAILED status=$status writeIntent=$writeIntent")
                return
            }
            when (writeIntent) {
                WriteIntent.SET_DATA_TIME -> {
                    Log.d(TAG, "SET_DATA_TIME ack → sending SET_UNIT")
                    writeIntent = WriteIntent.SET_UNIT
                    writeFFE1(byteArrayOf(0xF7.toByte(), 0x4D.toByte(), 0x01.toByte()))
                }
                WriteIntent.SET_UNIT -> {
                    Log.d(TAG, "SET_UNIT ack → onSetupComplete")
                    writeIntent = WriteIntent.NONE
                    onSetupComplete()
                }
                else -> writeIntent = WriteIntent.NONE
            }
        }

        @Deprecated("Needed for API < 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < 33) {
                val src = if (characteristic.uuid.toString().uppercase().contains("FFE2")) "FFE2" else "FFE1"
                Log.d(TAG, "onCharacteristicChanged src=$src")
                handleData(characteristic.value)
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val src = if (characteristic.uuid.toString().uppercase().contains("FFE2")) "FFE2" else "FFE1"
            Log.d(TAG, "onCharacteristicChanged src=$src")
            handleData(value)
        }
    }

    private fun enableNotifications(g: BluetoothGatt) {
        val allChars = g.services.flatMap { it.characteristics }
        val ffe1 = allChars.firstOrNull { it.uuid.toString() == LibraUuids.CHAR_FFE1 } ?: run {
            _state.value = SyncState.Error(getString(R.string.error_ffe1_not_found))
            g.close(); stopSelf(); return
        }
        val ffe2 = allChars.firstOrNull { it.uuid.toString() == LibraUuids.CHAR_FFE2 }

        ffe1Char = ffe1
        g.setCharacteristicNotification(ffe1, true)
        ffe2?.let { g.setCharacteristicNotification(it, true) }

        writeFFE1(byteArrayOf(0xF6.toByte(), 0x01.toByte()))
    }

    // ─── Setup complete: decide next step based on mode ───────────────────────

    private fun onSetupComplete() {
        Log.d(TAG, "onSetupComplete: mode=$serviceMode userFound=$userFound scaleUsers=${scaleUsers.size} selectedUserId=$selectedUserId")
        when {
            !userFound && scaleUsers.isNotEmpty() -> {
                Log.d(TAG, "→ UserSelection (${scaleUsers.size} users on scale)")
                _state.value = SyncState.UserSelection(
                    users = scaleUsers.toList(),
                    canCreate = scaleUsers.size < 8
                )
                updateNotification(getString(R.string.notification_select_profile))
            }
            !userFound -> {
                Log.d(TAG, "→ createUser (no users on scale)")
                protocolStep = ProtocolStep.CREATING_USER
                scope.launch { sendCreateUser() }
            }
            serviceMode == ServiceMode.SYNC -> {
                Log.d(TAG, "→ getMeasurements for userId=$selectedUserId")
                _state.value = SyncState.Syncing()
                updateNotification(getString(R.string.notification_downloading))
                protocolStep = ProtocolStep.GETTING_MEASUREMENTS
                writeFFE1(buildGetMeasurementsCmd(selectedUserId))
            }
            else -> {
                Log.d(TAG, "→ sendCreateUser (profile update before measure)")
                protocolStep = ProtocolStep.CREATING_USER
                scope.launch { sendCreateUser() }
            }
        }
    }

    // ─── Packet handling ─────────────────────────────────────────────────────

    private fun handleData(data: ByteArray) {
        if (data.size < 2) return
        val b0 = data[0].toInt() and 0xFF
        val b1 = data[1].toInt() and 0xFF
        val b2 = if (data.size > 2) data[2].toInt() and 0xFF else -1
        val b3 = if (data.size > 3) data[3].toInt() and 0xFF else -1

        Log.d(TAG, "RX[${data.size}] ${data.take(6).joinToString(" ") { "%02X".format(it) }} step=$protocolStep")

        when {
            b0 == 0xF6 && protocolStep == ProtocolStep.INIT -> {
                Log.d(TAG, "0xF6 init → getUserList (200ms delay)")
                protocolStep = ProtocolStep.WAIT_USER_LIST
                scope.launch {
                    delay(200)
                    writeFFE1(byteArrayOf(0xF7.toByte(), 0x33.toByte()))
                }
            }

            b2 == 0x33 && protocolStep == ProtocolStep.WAIT_USER_LIST -> {
                val status = b3
                val count = if (data.size > 4) data[4].toInt() and 0xFF else 0
                Log.d(TAG, "0x33 getUserList header: status=$status count=$count savedUserId=$selectedUserId")
                expectedUserCount = count
                receivedUserCount = 0
                if (status == 1 || count == 0) {
                    Log.d(TAG, "0x33 no users on scale → sendSetDataTime")
                    userFound = false
                    protocolStep = ProtocolStep.WAIT_SETUP
                    sendSetDataTime()
                }
            }

            b1 == 0x34 && protocolStep == ProtocolStep.WAIT_USER_LIST -> {
                writeFFE1(byteArrayOf(0xF7.toByte(), 0xF1.toByte(), data[1], data[2], data[3]))
                if (data.size >= 12) {
                    val userId = readLongBE(data, 4)
                    val initials = if (data.size >= 15) String(data.sliceArray(12..14)) else ""
                    Log.d(TAG, "0x34 user: id=$userId initials='$initials' match=${userId == selectedUserId}")
                    if (scaleUsers.none { it.id == userId }) {
                        scaleUsers.add(ScaleUser(userId, initials))
                    }
                    if (userId == selectedUserId) userFound = true
                }
                receivedUserCount++
                Log.d(TAG, "0x34 received=$receivedUserCount expected=$expectedUserCount userFound=$userFound")
                if (receivedUserCount >= expectedUserCount) {
                    Log.d(TAG, "0x34 all users received → sendSetDataTime (scaleUsers=${scaleUsers.size} userFound=$userFound)")
                    protocolStep = ProtocolStep.WAIT_SETUP
                    sendSetDataTime()
                }
            }

            b2 == 0x32 && protocolStep == ProtocolStep.DELETING_USER -> {
                Log.d(TAG, "0x32 deleteUser: status=$b3")
                if (b3 == 0) {
                    val deletedId = scaleUsers.firstOrNull()?.id ?: 0L
                    scaleUsers.removeAll { it.id == deletedId }
                    _state.value = SyncState.UserSelection(
                        users = scaleUsers.toList(),
                        canCreate = true
                    )
                    updateNotification(getString(R.string.notification_profile_deleted))
                } else {
                    _state.value = SyncState.Error(getString(R.string.error_delete_profile, b3))
                    gatt?.disconnect()
                }
            }

            b2 == 0x31 && protocolStep == ProtocolStep.CREATING_USER -> {
                Log.d(TAG, "0x31 createUser: status=$b3 userFound=$userFound selectedUserId=$selectedUserId")
                when (b3) {
                    0 -> {
                        if (serviceMode == ServiceMode.SYNC) {
                            protocolStep = ProtocolStep.GETTING_MEASUREMENTS
                            writeFFE1(buildGetMeasurementsCmd(selectedUserId))
                        } else {
                            protocolStep = ProtocolStep.WAIT_MEASURE
                            writeFFE1(buildTakeMeasureCmd(selectedUserId))
                        }
                    }
                    1, 2, 3 -> {
                        if (!userFound) {
                            Log.d(TAG, "0x31 code=$b3 !userFound → re-request user list")
                            protocolStep = ProtocolStep.WAIT_USER_LIST
                            scaleUsers.clear()
                            expectedUserCount = 0
                            receivedUserCount = 0
                            writeFFE1(byteArrayOf(0xF7.toByte(), 0x33.toByte()))
                        } else {
                            Log.d(TAG, "0x31 code=$b3 userFound → proceed")
                            if (serviceMode == ServiceMode.SYNC) {
                                protocolStep = ProtocolStep.GETTING_MEASUREMENTS
                                writeFFE1(buildGetMeasurementsCmd(selectedUserId))
                            } else {
                                protocolStep = ProtocolStep.WAIT_MEASURE
                                writeFFE1(buildTakeMeasureCmd(selectedUserId))
                            }
                        }
                    }
                    else -> {
                        _state.value = SyncState.Error(getString(R.string.error_create_profile, b3))
                        gatt?.disconnect()
                    }
                }
            }

            b2 == 0x4D && protocolStep == ProtocolStep.GETTING_MEASUREMENTS -> {
                Log.d(TAG, "0x4D getMeasurements retry")
                scope.launch {
                    delay(200)
                    writeFFE1(buildGetMeasurementsCmd(selectedUserId))
                }
            }

            b2 == 0x41 && protocolStep == ProtocolStep.GETTING_MEASUREMENTS -> {
                gumTotalSubPkts = b3
                val status = if (data.size > 4) data[4].toInt() and 0xFF else 0
                Log.d(TAG, "0x41 getMeasurements header: subPkts=$gumTotalSubPkts status=$status")
                gumDownloadedCount = 0
                if (status == 1 || gumTotalSubPkts == 0) {
                    onMeasurementsDownloaded()
                } else {
                    _state.value = SyncState.Syncing(received = 0, total = gumTotalSubPkts)
                    updateNotification(getString(R.string.notification_downloading))
                }
            }

            b1 == 0x42 && protocolStep == ProtocolStep.GETTING_MEASUREMENTS -> {
                handleGetMeasurementSubPkt(data, b3)
            }

            b2 == 0x40 && protocolStep == ProtocolStep.WAIT_MEASURE -> {
                Log.d(TAG, "0x40 takeMeasure: status=$b3")
                if (b3 == 0) {
                    protocolStep = ProtocolStep.MEASURING
                    val name = gatt?.device?.name ?: "Libra"
                    updateNotification(getString(R.string.notification_step_on_scale))
                    _state.value = SyncState.Connected(name)
                } else {
                    _state.value = SyncState.Error(getString(R.string.error_measurement_rejected, b3))
                    gatt?.disconnect(); stopSelf()
                }
            }

            b2 == 0x46 && protocolStep == ProtocolStep.GETTING_UNKNOWN -> {
                val count46 = if (data.size > 4) data[4].toInt() and 0xFF else 0
                Log.d(TAG, "0x46 getUnknown header: b3=$b3 count=$count46")
                if (b3 == 1 || count46 == 0) {
                    onAllDone()
                }
            }

            b1 == 0x47 && protocolStep == ProtocolStep.GETTING_UNKNOWN -> {
                if (data.size < 13) return
                writeFFE1(byteArrayOf(0xF7.toByte(), 0xF1.toByte(), data[1], data[2], data[3]))
                val total = b2
                val idx   = b3
                val slotId = data[4].toInt() and 0xFF
                val ts     = readIntBE(data, 5)
                val weight = readShortBE(data, 9) / 20f
                val imp    = readShortBE(data, 11)
                unknownTotalCount = total
                unknownReceivedCount++
                scope.launch {
                    val prefs = cachedPrefs
                        ?: (application as LibraApplication).userPreferences.prefs.first()
                            .also { cachedPrefs = it }
                    if (weight in prefs.weightMinKg..prefs.weightMaxKg) {
                        unknownPending.add(UnknownMeasurement(slotId, ts, weight, imp))
                    }
                    if (unknownReceivedCount >= unknownTotalCount) {
                        assignNextUnknown()
                    }
                }
            }

            b2 == 0x4B && protocolStep == ProtocolStep.ASSIGNING_UNKNOWN -> {
                if (b3 != 0) {
                    unknownAssignIdx++
                    assignNextUnknown()
                }
            }

            b1 == 0x4C && protocolStep == ProtocolStep.ASSIGNING_UNKNOWN -> {
                if (data.size < 13) return
                val m = unknownPending.getOrNull(unknownAssignIdx) ?: run {
                    unknownAssignIdx++; assignNextUnknown(); return
                }
                writeFFE1(byteArrayOf(0xF7.toByte(), 0xF1.toByte(), data[1], data[2], data[3]))
                if (data[2].toInt() != data[3].toInt()) {
                    assignBodyFat = readShortBE(data, 12) / 10f
                    assignBoneMsb = data[14].toInt() and 0xFF
                } else {
                    val bodyWaterPct = ((data[4].toInt() and 0xFF) or (assignBoneMsb shl 8)) / 10f
                    val musclePct   = readShortBE(data, 5) / 10f
                    val boneMassKg  = readShortBE(data, 7) / 20f
                    val bmr         = readShortBE(data, 9)
                    val amr         = readShortBE(data, 11)
                    val bmi         = readShortBE(data, 13) / 10f
                    val entity = MeasurementEntity(
                        timestampMs    = m.ts * 1000L,
                        weightKg       = m.weight,
                        impedanceOhm   = m.imp,
                        bodyFatKg      = assignBodyFat / 100f * m.weight,
                        bodyFatPct     = assignBodyFat,
                        muscleMassKg   = musclePct / 100f * m.weight,
                        musclePct      = musclePct,
                        boneMassKg     = boneMassKg,
                        bodyWaterPct   = bodyWaterPct,
                        bmi            = bmi,
                        bmr            = bmr,
                        amr            = amr,
                    )
                    scope.launch {
                        val prefs = cachedPrefs
                            ?: (application as LibraApplication).userPreferences.prefs.first()
                        if (m.weight in prefs.weightMinKg..prefs.weightMaxKg) {
                            val saved = (application as LibraApplication).measurementRepository.insert(entity)
                            if (saved) gumDownloadedCount++
                        }
                        unknownAssignIdx++
                        assignNextUnknown()
                    }
                }
            }

            b0 == 0xE0 -> {
                // Scale "session end" signal — means no more data (no unknown measurements)
                Log.d(TAG, "0xE0 scale end signal step=$protocolStep")
                when (protocolStep) {
                    ProtocolStep.GETTING_UNKNOWN, ProtocolStep.ASSIGNING_UNKNOWN -> onAllDone()
                    ProtocolStep.WAIT_USER_LIST -> {
                        if (getUserListRetries < 3) {
                            getUserListRetries++
                            Log.d(TAG, "0xE0 in WAIT_USER_LIST → retry getUserList ($getUserListRetries/3)")
                            scope.launch {
                                delay(500)
                                writeFFE1(byteArrayOf(0xF7.toByte(), 0x33.toByte()))
                            }
                        } else {
                            Log.d(TAG, "0xE0 in WAIT_USER_LIST → max retries reached, giving up")
                            gatt?.disconnect()
                            _state.value = SyncState.Error(getString(R.string.error_connection_lost))
                        }
                    }
                    ProtocolStep.WAIT_SETUP -> {
                        gatt?.disconnect()
                        _state.value = SyncState.Error(getString(R.string.error_connection_lost))
                    }
                    else -> {}
                }
            }

            b0 == 0xF7 -> handleF7Packet(data, b1, b2)

            else -> {}
        }
    }

    private fun handleGetMeasurementSubPkt(data: ByteArray, idx: Int) {
        // Format: [0xF7, 0x42, totalSubPkts, currentIdx, payload...]
        // payload starts at data[4]
        if (data.size < 15) return
        writeFFE1(byteArrayOf(0xF7.toByte(), 0xF1.toByte(), data[1], data[2], data[3]))
        Log.d(TAG, "0x42 sub[$idx/${gumTotalSubPkts}] raw: ${data.drop(4).take(11).joinToString(" ") { "%02X".format(it) }}")

        if (idx % 2 == 1) {
            gumCurrentTs = readIntBE(data, 4)
            gumCurrentWeight = readShortBE(data, 8) / 20f
            gumCurrentImpedance = readShortBE(data, 10)
            gumCurrentBodyFat = readShortBE(data, 12) / 10f
            gumBoneMsb = data[14].toInt() and 0xFF
        } else {
            val boneLsb = data[4].toInt() and 0xFF
            val bodyWaterPct = ((boneLsb) or (gumBoneMsb shl 8)) / 10f
            val musclePct = readShortBE(data, 5) / 10f
            val boneMassKg = readShortBE(data, 7) / 20f
            val bmr = readShortBE(data, 9)
            val amr = readShortBE(data, 11)
            val bmi = readShortBE(data, 13) / 10f
            val entity = MeasurementEntity(
                timestampMs = gumCurrentTs * 1000L,
                weightKg = gumCurrentWeight,
                impedanceOhm = gumCurrentImpedance,
                bodyFatKg = gumCurrentBodyFat / 100f * gumCurrentWeight,
                bodyFatPct = gumCurrentBodyFat,
                muscleMassKg = musclePct / 100f * gumCurrentWeight,
                musclePct = musclePct,
                boneMassKg = boneMassKg,
                bodyWaterPct = bodyWaterPct,
                bmi = bmi,
                bmr = bmr,
                amr = amr,
            )
            scope.launch {
                val prefs = cachedPrefs
                    ?: (application as LibraApplication).userPreferences.prefs.first()
                if (gumCurrentWeight in prefs.weightMinKg..prefs.weightMaxKg) {
                    val saved = (application as LibraApplication).measurementRepository.insert(entity)
                    if (saved) gumDownloadedCount++
                }
            }
        }

        _state.value = SyncState.Syncing(received = idx, total = gumTotalSubPkts)
        updateNotification(getString(R.string.notification_downloading))

        if (idx == gumTotalSubPkts) {
            // Delay to let the ACK write complete before sending 0x46
            scope.launch { delay(100); onMeasurementsDownloaded() }
        }
    }

    private fun onMeasurementsDownloaded() {
        unknownPending.clear()
        unknownTotalCount = 0
        unknownReceivedCount = 0
        unknownAssignIdx = 0
        protocolStep = ProtocolStep.GETTING_UNKNOWN
        updateNotification("Ricerca misurazioni non assegnate…")
        writeFFE1(byteArrayOf(0xF7.toByte(), 0x46.toByte()))
    }

    private fun onAllDone() {
        _state.value = SyncState.SyncComplete(gumDownloadedCount)
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        stopSelf()
    }

    private fun assignNextUnknown() {
        if (unknownAssignIdx >= unknownPending.size) {
            onAllDone()
            return
        }
        val m = unknownPending[unknownAssignIdx]
        protocolStep = ProtocolStep.ASSIGNING_UNKNOWN
        writeFFE1(buildAssignCmd(selectedUserId, m.ts, m.weight, m.imp, m.slotId))
    }

    private fun buildAssignCmd(userId: Long, ts: Long, weightKg: Float, imp: Int, slotId: Int): ByteArray {
        // {0xF7, 0x4B, userId[8 BE], timestamp[4 BE], weight_raw[2 BE], impedance[2 BE], slotId[1]}
        val cmd = ByteArray(19)
        cmd[0] = 0xF7.toByte()
        cmd[1] = 0x4B.toByte()
        longToBE(userId).copyInto(cmd, 2)
        val tsInt = ts.toInt()
        cmd[10] = (tsInt ushr 24).toByte()
        cmd[11] = (tsInt ushr 16).toByte()
        cmd[12] = (tsInt ushr 8).toByte()
        cmd[13] = tsInt.toByte()
        val weightRaw = (weightKg * 20f).toInt()
        cmd[14] = (weightRaw ushr 8).toByte()
        cmd[15] = weightRaw.toByte()
        cmd[16] = (imp ushr 8).toByte()
        cmd[17] = imp.toByte()
        cmd[18] = slotId.toByte()
        return cmd
    }

    private fun handleF7Packet(data: ByteArray, b1: Int, b2: Int) {
        if ((b1 == 0x59 || b1 == 0x53) && data.size >= 4) {
            val subPkt = data[3].toInt() and 0xFF
            if (subPkt == 1 || subPkt == 2) {
                writeFFE1(byteArrayOf(0xF7.toByte(), 0xF1.toByte(),
                    b1.toByte(), b2.toByte(), subPkt.toByte()))
            }
        }

        when (val pkt = parser.parse(data)) {
            is LibraPacket.LiveWeight -> {
                // In SYNC mode the scale may broadcast live weight while we download history — ignore it
                if (serviceMode != ServiceMode.SYNC) {
                    _state.value = SyncState.LiveWeight(pkt.weightKg, pkt.stable)
                }
                if (serviceMode == ServiceMode.MEASURE && pkt.stable) {
                    updateNotification("Peso: ${formatWeight(pkt.weightKg)} kg")
                }
            }
            is LibraPacket.Measurement -> {
                when (serviceMode) {
                    ServiceMode.LIVE -> finishSync()
                    ServiceMode.MEASURE -> scope.launch { handleMeasureComplete(pkt) }
                    ServiceMode.SYNC -> { /* non dovrebbe accadere */ }
                }
            }
            null -> {}
        }
    }

    private suspend fun handleMeasureComplete(pkt: LibraPacket.Measurement) {
        val entity = MeasurementEntity(
            timestampMs = pkt.timestampMs,
            weightKg = pkt.weightKg,
            impedanceOhm = pkt.impedanceOhm,
            bodyFatKg = pkt.bodyFatPct / 100f * pkt.weightKg,
            bodyFatPct = pkt.bodyFatPct,
            muscleMassKg = pkt.musclePct / 100f * pkt.weightKg,
            musclePct = pkt.musclePct,
            boneMassKg = pkt.boneMassKg,
            bodyWaterPct = pkt.bodyWaterPct,
            bmi = pkt.bmi,
            bmr = pkt.bmr,
            amr = pkt.amr,
        )
        pendingEntity = entity
        _state.value = SyncState.MeasurementPending(
            weightKg = pkt.weightKg,
            bodyFatPct = pkt.bodyFatPct,
            musclePct = pkt.musclePct,
            bmi = pkt.bmi,
        )
        updateNotification("Pesata: ${formatWeight(pkt.weightKg)} kg — scegli cosa fare")
    }

    private fun finishSync() {
        _state.value = SyncState.Done
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        stopSelf()
    }

    // ─── Comandi BLE ─────────────────────────────────────────────────────────

    private fun sendSetDataTime() {
        val ts = (System.currentTimeMillis() / 1000).toInt()
        Log.d(TAG, "sendSetDataTime ts=$ts")
        val cmd = byteArrayOf(0xF9.toByte(),
            (ts ushr 24).toByte(), (ts ushr 16).toByte(), (ts ushr 8).toByte(), ts.toByte())
        writeIntent = WriteIntent.SET_DATA_TIME
        writeFFE1(cmd)
    }

    private suspend fun sendCreateUser() {
        val userPreferences = (application as LibraApplication).userPreferences
        val prefs = cachedPrefs
            ?: userPreferences.prefs.first()
        var userId = prefs.scaleUserId
        if (userId <= 0L) {
            userId = (100_000_000L..999_999_999L).random()
            userPreferences.setScaleUserId(userId)
            cachedPrefs = prefs.copy(scaleUserId = userId)
        }
        selectedUserId = userId
        val initials = prefs.initials.uppercase().take(3).padEnd(3, 'A')
        val actGender = prefs.activityLevel or (if (prefs.genderMale) 128 else 0)

        // AbstractC1296.mo2694() prepends 0xF7 before opcode; C0950.mo2695()=0x31
        val cmd = ByteArray(18)
        cmd[0] = 0xF7.toByte()
        cmd[1] = 0x31.toByte()
        longToBE(userId).copyInto(cmd, 2)
        for (i in 0..2) cmd[10 + i] = initials[i].code.toByte()
        cmd[13] = (prefs.birthYear - 1900).toByte()
        cmd[14] = prefs.birthMonth.toByte()
        cmd[15] = prefs.birthDay.toByte()
        cmd[16] = prefs.heightCm.toByte()
        cmd[17] = actGender.toByte()
        writeFFE1(cmd)
    }

    private fun buildTakeMeasureCmd(userId: Long): ByteArray {
        // AbstractC1296.mo2694() prepends 0xF7; C1332.mo2695()=0x40
        val cmd = ByteArray(10)
        cmd[0] = 0xF7.toByte()
        cmd[1] = 0x40.toByte()
        longToBE(userId).copyInto(cmd, 2)
        return cmd
    }

    private fun buildGetMeasurementsCmd(userId: Long): ByteArray {
        val cmd = ByteArray(10)
        cmd[0] = 0xF7.toByte()
        cmd[1] = 0x41.toByte()
        longToBE(userId).copyInto(cmd, 2)
        return cmd
    }

    private fun writeFFE1(data: ByteArray) {
        val char = ffe1Char ?: return
        val g = gatt ?: return
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = data
            @Suppress("DEPRECATION")
            g.writeCharacteristic(char)
        }
    }

    // ─── Byte order helpers (Big Endian — usato dal protocollo Libra) ─────────

    private fun readLongBE(data: ByteArray, offset: Int): Long {
        var r = 0L
        for (i in 0..7) r = (r shl 8) or (data[offset + i].toLong() and 0xFF)
        return r
    }

    private fun longToBE(value: Long): ByteArray =
        ByteArray(8) { i -> ((value ushr ((7 - i) * 8)) and 0xFF).toByte() }

    private fun readShortBE(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun readIntBE(data: ByteArray, offset: Int): Long =
        ((data[offset].toLong() and 0xFF) shl 24) or
        ((data[offset + 1].toLong() and 0xFF) shl 16) or
        ((data[offset + 2].toLong() and 0xFF) shl 8) or
        (data[offset + 3].toLong() and 0xFF)

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val ch = NotificationChannel(NOTIF_CHANNEL,
            getString(R.string.notification_channel_sync), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle(getString(R.string.notification_sync_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) =
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))

    private fun formatWeight(kg: Float) = "%.1f".format(kg)
}
