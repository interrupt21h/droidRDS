package io.github.interrupt21h.droidrds

enum class ReceiverKind { SI4703, RDA5807M }
data class ReceiverSnapshot(val registers: List<Int>, val group: RdsGroup?, val rssi: Int, val stereo: Boolean)
class Receiver(private val bus: Ft232h, private val kind: ReceiverKind) {
    fun snapshot(): ReceiverSnapshot {
        // Sequential read starts at 0x0A on both chips. Receiver must already be configured.
        val bytes = bus.readRaw(0x10, if (kind == ReceiverKind.SI4703) 32 else 12)
        val words = bytes.toList().chunked(2).map { ((it[0].toInt() and 255) shl 8) or (it[1].toInt() and 255) }
        val status = words[0]
        val quality = words[1]
        val ready = status and 0x8000 != 0
        val synced = status and (if(kind == ReceiverKind.SI4703) 0x0800 else 0x1000) != 0
        val group = if (ready && synced) RdsGroup(words[2], words[3], words[4], words[5]) else null
        val rssi = if (kind == ReceiverKind.SI4703) status and 255 else (quality ushr 9) and 127
        val stereo = if (kind == ReceiverKind.SI4703) status and 0x0100 != 0 else status and 0x0400 != 0
        return ReceiverSnapshot(words, group, rssi, stereo)
    }
}
