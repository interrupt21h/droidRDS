package io.github.interrupt21h.droidrds
import java.io.IOException
import android.os.SystemClock

/** Read-only diagnostics. No RF-enabling command is exposed. */
class Radio(private val bus: Ft232h, private val address: Int) {
    private fun ready(): Int {
        val deadline = SystemClock.elapsedRealtime() + 3000
        do {
            val s = bus.readRaw(address, 1)[0].toInt() and 255
            if (s and 0x80 != 0) {
                if (s and 0x40 != 0) throw IOException("Si47xx ERR status")
                return s
            }
            Thread.sleep(10)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw IOException("CTS timeout")
    }
    fun revision(): String {
        ready()
        bus.writeCommand(address, byteArrayOf(0x10))
        ready()
        val r = bus.readRaw(address, 9)
        if (r[0].toInt() and 0xC0 != 0x80) throw IOException("Invalid response status")
        fun u(i: Int) = r[i].toInt() and 255
        return "Part: 0x%02X\nFirmware: %c.%c\nPatch: %04X\nComponent: %c.%c\nChip revision: %c\nRaw: %s".format(u(1), u(2).toChar(), u(3).toChar(), (u(4) shl 8) or u(5), u(6).toChar(), u(7).toChar(), u(8).toChar(), r.joinToString(" ") { "%02X".format(it.toInt() and 255) })
    }
    fun probe() = bus.probe(address)
}
