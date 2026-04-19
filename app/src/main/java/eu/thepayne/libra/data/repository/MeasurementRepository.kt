package eu.thepayne.libra.data.repository

import eu.thepayne.libra.data.db.AppDatabase
import eu.thepayne.libra.data.db.MeasurementEntity
import kotlinx.coroutines.flow.Flow

class MeasurementRepository(db: AppDatabase) {

    private val dao = db.measurementDao()

    fun observeAll(): Flow<List<MeasurementEntity>> = dao.observeAll()

    fun observeSince(fromMs: Long): Flow<List<MeasurementEntity>> = dao.observeSince(fromMs)

    suspend fun insert(m: MeasurementEntity): Boolean = dao.insert(m) != -1L

    suspend fun insertAll(items: List<MeasurementEntity>): Int =
        dao.insertAll(items).count { it != -1L }

    suspend fun getAll(): List<MeasurementEntity> = dao.getAll()

    suspend fun latestTimestampMs(): Long? = dao.latestTimestampMs()

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()
}
