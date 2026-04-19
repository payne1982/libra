package eu.thepayne.libra.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "measurements",
    indices = [Index(value = ["timestampMs", "impedanceOhm"], unique = true)]
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val weightKg: Float,
    val impedanceOhm: Int,
    val bodyFatKg: Float,
    val bodyFatPct: Float,
    val muscleMassKg: Float,
    val musclePct: Float,
    val boneMassKg: Float,
    val bodyWaterPct: Float,
    val bmi: Float,
    val bmr: Int,
    val amr: Int,
    val createdAt: Long = System.currentTimeMillis()
)
