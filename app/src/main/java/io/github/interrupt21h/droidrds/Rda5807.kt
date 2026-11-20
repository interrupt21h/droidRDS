package io.github.interrupt21h.droidrds

import android.os.SystemClock
import java.io.IOException

class Rda5807(private val bus: Ft232h) {
    private fun read(reg:Int):Int {
        val v=bus.readRegister(0x11,reg,2)
        return ((v[0].toInt() and 255) shl 8) or (v[1].toInt() and 255)
    }
    private fun write(reg:Int,value:Int) {
        bus.writeCommand(0x11,byteArrayOf(reg.toByte(),(value shr 8).toByte(),value.toByte()))
    }
    fun initialize():String {
        require(read(0) ushr 8 == 0x58) { "RDA5807M ID mismatch" }
        write(2,0x0002)
        Thread.sleep(100)
        write(2,0xc00d)
        Thread.sleep(300)
        return tune(99500)
    }
    private fun complete() {
        val deadline=SystemClock.elapsedRealtime()+12000
        do {
            if(read(10) and 0x4000 != 0) return
            Thread.sleep(40)
        } while(SystemClock.elapsedRealtime()<deadline)
        throw IOException("RDA5807M STC timeout")
    }
    fun tune(khz:Int):String {
        require(khz in 87000..108000 && (khz-87000)%100==0)
        write(3,(((khz-87000)/100) shl 6) or 0x10)
        complete()
        return report()
    }
    fun seek(up:Boolean):String {
        write(2,(read(2) and 0xfdff) or 0x100 or (if(up) 0x200 else 0))
        complete()
        return report()
    }
    fun volume(level:Int):String {
        require(level in 0..15)
        write(5,(read(5) and 0xfff0) or level)
        return report()
    }
    fun report():String {
        val a=read(10); val b=read(11)
        return "RDA5807M • %.1f MHz • RSSI %d • %s\nRDS sync: %s • Seek failed: %s".format((87000+(a and 1023)*100)/1000.0,(b ushr 9) and 127,if(a and 0x400!=0) "Stereo" else "Mono",a and 0x1000!=0,a and 0x2000!=0)
    }
}
