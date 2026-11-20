package io.github.interrupt21h.droidrds

import android.os.SystemClock
import java.io.IOException

class Si4703(private val bus: Ft232h) {
    private val r = IntArray(16)
    private fun read() {
        val bytes = bus.readRaw(0x10, 32)
        for (i in 0..15) r[(i+10)%16] = ((bytes[2*i].toInt() and 255) shl 8) or (bytes[2*i+1].toInt() and 255)
    }
    private fun write(last: Int = 6) {
        val bytes = ByteArray((last-1)*2)
        for (i in 2..last) {
            bytes[(i-2)*2] = (r[i] shr 8).toByte()
            bytes[(i-2)*2+1] = r[i].toByte()
        }
        bus.writeCommand(0x10, bytes)
    }
    fun identify(): String {
        read()
        require(r[0] == 0x1242 && ((r[1] ushr 6) and 15) in 8..9) { "Unexpected Si4703 identity: %04X %04X; ACK alone is not identification".format(r[0],r[1]) }
        return "Si4703-family identity: %04X %04X".format(r[0],r[1])
    }
    fun initialize(crystal: Boolean = true): String {
        identify()
        if (crystal) {
            r[7] = 0x8100
            write(7)
            Thread.sleep(500)
            read()
        }
        r[2] = 0x4801 // DMUTE, verbose RDS, ENABLE
        r[3] = 0
        r[4] = 0x1000 // RDS, US 75 us de-emphasis
        r[5] = 0x1911 // seek threshold 25, US/EU band, 100 kHz, volume 1
        r[6] = 0x0044
        write()
        Thread.sleep(120)
        read()
        check(((r[1] ushr 6) and 15) == 9 && r[2] and 1 != 0) { "Receiver did not power up" }
        return report()
    }
    private fun awaitStc(state: Boolean, timeout: Long) {
        val start = SystemClock.elapsedRealtime()
        do {
            read()
            if ((r[10] and 0x4000 != 0) == state) return
            Thread.sleep(60)
        } while (SystemClock.elapsedRealtime()-start < timeout)
        throw IOException("STC timeout waiting for $state")
    }
    fun tune(khz: Int): String {
        require(khz in 87500..108000 && (khz-87500)%100 == 0)
        awaitStc(false, 3000)
        r[3] = 0x8000 or ((khz-87500)/100)
        write(3)
        try { awaitStc(true, 4000) } finally {
            r[3] = r[3] and 0x7fff
            write(3)
        }
        awaitStc(false, 3000)
        return report()
    }
    fun seek(up: Boolean): String {
        awaitStc(false, 3000)
        r[2] = (r[2] and 0xfdff) or 0x0500 or (if(up) 0x0200 else 0)
        write(2)
        var failed = false
        try { awaitStc(true, 15000); failed = r[10] and 0x2000 != 0 } finally {
            r[2] = r[2] and 0xfeff
            write(2)
        }
        awaitStc(false, 3000)
        return (if(failed) "Seek reached band limit or found no station\n" else "") + report()
    }
    fun volume(level: Int): String {
        require(level in 0..15)
        read()
        r[5] = (r[5] and 0xfff0) or level
        write(5)
        return report()
    }
    fun report(): String {
        read()
        return "Si4703 • %.1f MHz • RSSI %d • %s\nRDS sync: %s".format(
            (87500+(r[11] and 1023)*100)/1000.0,r[10] and 255,
            if(r[10] and 0x100 != 0) "Stereo" else "Mono",r[10] and 0x800 != 0)
    }
    fun capture(seconds: Int): String {
        require(seconds in 1..30)
        val end = SystemClock.elapsedRealtime()+seconds*1000L
        val groups = mutableListOf<String>()
        while (SystemClock.elapsedRealtime()<end && groups.size<350) {
            val b=bus.readRaw(0x10,12)
            val w=IntArray(6){i->((b[2*i].toInt() and 255) shl 8) or (b[2*i+1].toInt() and 255)}
            if(w[0] and 0x8000 != 0 && w[0] and 0x800 != 0 &&
                (w[0] shr 9) and 3 != 3 && (w[1] shr 14) and 3 != 3 &&
                (w[1] shr 12) and 3 != 3 && (w[1] shr 10) and 3 != 3) {
                groups.add("%04X %04X %04X %04X".format(w[2],w[3],w[4],w[5]))
            }
            Thread.sleep(20)
        }
        return "Captured ${groups.size} usable snapshots (duplicates possible)\n"+groups.joinToString("\n")
    }
}
