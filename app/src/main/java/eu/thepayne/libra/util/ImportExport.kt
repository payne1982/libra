package eu.thepayne.libra.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import eu.thepayne.libra.R
import eu.thepayne.libra.data.db.MeasurementEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImportExport {

    private val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val fileSdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    // ─── Export ──────────────────────────────────────────────────────────────

    suspend fun exportJson(context: Context, measurements: List<MeasurementEntity>) {
        if (measurements.isEmpty()) { toast(context, context.getString(R.string.toast_no_data)); return }
        val json = buildString {
            append("[\n")
            measurements.forEachIndexed { i, m ->
                append("  {\n")
                append("    \"date\": \"${sdf.format(Date(m.timestampMs))}\",\n")
                append("    \"weightKg\": ${m.weightKg},\n")
                append("    \"bodyFatPct\": ${m.bodyFatPct},\n")
                append("    \"bodyFatKg\": ${m.bodyFatKg},\n")
                append("    \"musclePct\": ${m.musclePct},\n")
                append("    \"muscleMassKg\": ${m.muscleMassKg},\n")
                append("    \"boneMassKg\": ${m.boneMassKg},\n")
                append("    \"bodyWaterPct\": ${m.bodyWaterPct},\n")
                append("    \"bmi\": ${m.bmi},\n")
                append("    \"bmr\": ${m.bmr},\n")
                append("    \"amr\": ${m.amr},\n")
                append("    \"impedanceOhm\": ${m.impedanceOhm}\n")
                append("  }${if (i < measurements.size - 1) "," else ""}\n")
            }
            append("]")
        }
        shareFile(context, json.toByteArray(), "libra_${fileSdf.format(Date())}.json", "application/json")
    }

    suspend fun exportCsv(context: Context, measurements: List<MeasurementEntity>) {
        if (measurements.isEmpty()) { toast(context, context.getString(R.string.toast_no_data)); return }
        val csv = buildString {
            append("date,weightKg,bodyFatPct,bodyFatKg,musclePct,muscleMassKg,boneMassKg,bodyWaterPct,bmi,bmr,amr,impedanceOhm\n")
            measurements.forEach { m ->
                append("${sdf.format(Date(m.timestampMs))},")
                append("${m.weightKg},${m.bodyFatPct},${m.bodyFatKg},")
                append("${m.musclePct},${m.muscleMassKg},${m.boneMassKg},")
                append("${m.bodyWaterPct},${m.bmi},${m.bmr},${m.amr},${m.impedanceOhm}\n")
            }
        }
        shareFile(context, csv.toByteArray(), "libra_${fileSdf.format(Date())}.csv", "text/csv")
    }

    // ─── Import ──────────────────────────────────────────────────────────────

    fun parseImportedJson(json: String): List<MeasurementEntity> {
        // Parser JSON minimalista senza dipendenze esterne
        val result = mutableListOf<MeasurementEntity>()
        val objectPattern = Regex("""\{([^}]+)\}""", RegexOption.DOT_MATCHES_ALL)
        val fieldPattern = Regex(""""(\w+)"\s*:\s*"?([^",}\n]+)"?""")

        objectPattern.findAll(json).forEach { obj ->
            val fields = fieldPattern.findAll(obj.value).associate { it.groupValues[1] to it.groupValues[2].trim() }
            try {
                result.add(MeasurementEntity(
                    timestampMs = sdf.parse(fields["date"] ?: return@forEach)?.time ?: return@forEach,
                    weightKg = fields["weightKg"]?.toFloat() ?: return@forEach,
                    impedanceOhm = fields["impedanceOhm"]?.toInt() ?: 0,
                    bodyFatKg = fields["bodyFatKg"]?.toFloat() ?: 0f,
                    bodyFatPct = fields["bodyFatPct"]?.toFloat() ?: 0f,
                    muscleMassKg = fields["muscleMassKg"]?.toFloat() ?: 0f,
                    musclePct = fields["musclePct"]?.toFloat() ?: 0f,
                    boneMassKg = fields["boneMassKg"]?.toFloat() ?: 0f,
                    bodyWaterPct = fields["bodyWaterPct"]?.toFloat() ?: 0f,
                    bmi = fields["bmi"]?.toFloat() ?: 0f,
                    bmr = fields["bmr"]?.toInt() ?: 0,
                    amr = fields["amr"]?.toInt() ?: 0,
                ))
            } catch (_: Exception) {}
        }
        return result
    }

    fun parseCsv(csv: String): List<MeasurementEntity> {
        val lines = csv.lines().drop(1).filter { it.isNotBlank() }
        return lines.mapNotNull { line ->
            val cols = line.split(",")
            if (cols.size < 12) return@mapNotNull null
            try {
                MeasurementEntity(
                    timestampMs = sdf.parse(cols[0])?.time ?: return@mapNotNull null,
                    weightKg = cols[1].toFloat(),
                    bodyFatPct = cols[2].toFloat(),
                    bodyFatKg = cols[3].toFloat(),
                    musclePct = cols[4].toFloat(),
                    muscleMassKg = cols[5].toFloat(),
                    boneMassKg = cols[6].toFloat(),
                    bodyWaterPct = cols[7].toFloat(),
                    bmi = cols[8].toFloat(),
                    bmr = cols[9].toInt(),
                    amr = cols[10].toInt(),
                    impedanceOhm = cols[11].toInt(),
                )
            } catch (_: Exception) { null }
        }
    }

    fun importFromUri(uri: Uri, contentResolver: ContentResolver): List<MeasurementEntity> {
        val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return emptyList()
        val mimeType = contentResolver.getType(uri) ?: ""
        val name = uri.lastPathSegment ?: ""
        return when {
            mimeType == "text/csv" || name.endsWith(".csv", ignoreCase = true) -> parseCsv(text)
            else -> parseImportedJson(text)
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun shareFile(context: Context, bytes: ByteArray, filename: String, mimeType: String) {
        val file = File(context.cacheDir, filename)
        file.writeBytes(bytes)
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_chooser_title)))
    }

    private fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
