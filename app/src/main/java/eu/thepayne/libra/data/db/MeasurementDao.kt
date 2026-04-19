package eu.thepayne.libra.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(m: MeasurementEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<MeasurementEntity>): List<Long>

    @Query("SELECT * FROM measurements ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE timestampMs >= :fromMs ORDER BY timestampMs ASC")
    fun observeSince(fromMs: Long): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements ORDER BY timestampMs DESC")
    suspend fun getAll(): List<MeasurementEntity>

    @Query("SELECT MAX(timestampMs) FROM measurements")
    suspend fun latestTimestampMs(): Long?

    @Query("DELETE FROM measurements WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM measurements")
    suspend fun deleteAll()
}
