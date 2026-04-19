package eu.thepayne.libra.ble

// UUID della bilancia ricostruito dalla reverse-engineering dell'APK originale
object LibraUuids {
    const val CHAR_FFE1 = "0000ffe1-0000-1000-8000-00805f9b34fb"
    const val CHAR_FFE2 = "0000ffe2-0000-1000-8000-00805f9b34fb"
    const val DESCRIPTOR_CCCD = "00002902-0000-1000-8000-00805f9b34fb"
}

sealed class LibraPacket {
    data class LiveWeight(val weightKg: Float, val stable: Boolean) : LibraPacket()
    data class Measurement(
        val timestampMs: Long,
        val weightKg: Float,
        val impedanceOhm: Int,
        val bodyFatPct: Float,
        val musclePct: Float,
        val boneMassKg: Float,
        val bodyWaterPct: Float,
        val bmi: Float,
        val bmr: Int,
        val amr: Int,
    ) : LibraPacket()
}

// Parser stateful: accumula i 3 sotto-pacchetti di finishMeasurement (0xF7 0x59)
// Confirmed by C0968.java (DataInputStream = Big Endian) and C1156.java (liveWeight)
class LibraParser {

    private var flag: Byte = 0
    private var userId: Long = 0
    private var timestampSec: Long = 0
    private var weightKg: Float = 0f
    private var impedanceOhm: Int = 0
    private var bodyFatPct: Float = 0f
    private var boneMsbPartial: Int = 0
    private var musclePct: Float = 0f
    private var boneMassKg: Float = 0f
    private var bmr: Int = 0
    private var amr: Int = 0
    private var bmi: Float = 0f

    fun parse(data: ByteArray): LibraPacket? {
        if (data.size < 2) return null

        val b0 = data[0].toInt() and 0xFF
        val b1 = data[1].toInt() and 0xFF

        // Live weight: 0xF7 0x58 — confirmed by C1156.java
        if (b0 == 0xF7 && b1 == 0x58 && data.size >= 5) {
            val stable = data[2].toInt() != 0
            val raw = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
            return LibraPacket.LiveWeight(raw / 20f, stable)
        }

        // Full measurement: 0xF7 0x59 (or 0x53) in 3 sub-packets — confirmed by C0968.java
        if (b0 == 0xF7 && (b1 == 0x59 || b1 == 0x53) && data.size >= 5) {
            return when (data[3].toInt() and 0xFF) {
                1 -> parsePkt1(data)
                2 -> parsePkt2(data)
                3 -> parsePkt3(data)
                else -> null
            }
        }
        return null
    }

    // pkt1: byte[4]=flag, byte[5..12]=userId (BE, per C0968 readLong via DataInputStream)
    private fun parsePkt1(d: ByteArray): LibraPacket? {
        if (d.size < 14) return null
        flag = d[4]
        userId = readLongBE(d, 5)
        return null
    }

    // pkt2: byte[4..7]=timestamp BE, byte[8..9]=weight/20, byte[10..11]=impedance,
    //       byte[12..13]=fatPct/10, byte[14]=boneMsb — all BE per C0968 DataInputStream
    private fun parsePkt2(d: ByteArray): LibraPacket? {
        if (d.size < 15) return null
        timestampSec = readIntBE(d, 4)
        weightKg = readShortBE(d, 8) / 20f
        impedanceOhm = readShortBE(d, 10)
        bodyFatPct = readShortBE(d, 12) / 10f
        boneMsbPartial = d[14].toInt() and 0xFF
        return null
    }

    // pkt3: byte[4]=bodyWaterLsb (combines with boneMsb from pkt2),
    //       byte[5..6]=musclePct/10, byte[7..8]=boneMass/20, byte[9..10]=bmr,
    //       byte[11..12]=amr, byte[13..14]=bmi/10 — all BE per C0968
    private fun parsePkt3(d: ByteArray): LibraPacket? {
        if (d.size < 15) return null
        val bodyWaterRaw = ((d[4].toInt() and 0xFF) or (boneMsbPartial shl 8)) and 0xFFFF
        musclePct = readShortBE(d, 5) / 10f
        boneMassKg = readShortBE(d, 7) / 20f
        bmr = readShortBE(d, 9)
        amr = readShortBE(d, 11)
        bmi = readShortBE(d, 13) / 10f

        return LibraPacket.Measurement(
            timestampMs = timestampSec * 1000L,
            weightKg = weightKg,
            impedanceOhm = impedanceOhm,
            bodyFatPct = bodyFatPct,
            musclePct = musclePct,
            boneMassKg = boneMassKg,
            bodyWaterPct = bodyWaterRaw / 10f,
            bmi = bmi,
            bmr = bmr,
            amr = amr,
        )
    }

    fun reset() {
        flag = 0; userId = 0; timestampSec = 0; weightKg = 0f; impedanceOhm = 0
        bodyFatPct = 0f; boneMsbPartial = 0; musclePct = 0f; boneMassKg = 0f
        bmr = 0; amr = 0; bmi = 0f
    }

    // Big Endian — per DataInputStream (Java standard), confermato da C0968.java
    private fun readShortBE(d: ByteArray, offset: Int): Int =
        ((d[offset].toInt() and 0xFF) shl 8) or (d[offset + 1].toInt() and 0xFF)

    private fun readIntBE(d: ByteArray, offset: Int): Long =
        ((d[offset].toLong() and 0xFF) shl 24) or
        ((d[offset + 1].toLong() and 0xFF) shl 16) or
        ((d[offset + 2].toLong() and 0xFF) shl 8) or
        (d[offset + 3].toLong() and 0xFF)

    private fun readLongBE(d: ByteArray, offset: Int): Long {
        var r = 0L
        for (i in 0..7) r = (r shl 8) or (d[offset + i].toLong() and 0xFF)
        return r
    }
}
