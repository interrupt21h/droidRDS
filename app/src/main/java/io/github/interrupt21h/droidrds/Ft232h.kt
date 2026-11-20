package io.github.interrupt21h.droidrds

import android.hardware.usb.*
import java.io.IOException

/** FT232H MPSSE GPIO-based open-drain I2C master. D0=SCL, D1=SDA output, D2=SDA input. */
class Ft232h(manager: UsbManager, device: UsbDevice) : AutoCloseable {
    private val conn = manager.openDevice(device) ?: throw IOException("Cannot open USB device")
    private val iface = device.getInterface(0)
    private val tx: UsbEndpoint
    private val rx: UsbEndpoint
    private var closed = false
    private var resetHeldHigh = false
    init {
        try {
            if (!conn.claimInterface(iface, true)) throw IOException("USB interface claim failed")
            tx = (0 until iface.endpointCount).map { iface.getEndpoint(it) }
                .first { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }
            rx = (0 until iface.endpointCount).map { iface.getEndpoint(it) }
                .first { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN }
            control(0,0); control(9,1); control(11,0); control(11,0x0200)
            Thread.sleep(60)
            send(0x8a,0x97,0x8d,0x85,0x80,0,0,0x82,0,0)
            // Defer idle-bus validation until after receiver mode selection.
        } catch (e: Exception) { close(); throw e }
    }
    private fun control(request:Int,value:Int) {
        if (conn.controlTransfer(0x40,request,value,1,null,0,2000)<0) throw IOException("Control transfer failed")
    }
    private fun send(vararg bytes:Int) {
        val b=ByteArray(bytes.size) { bytes[it].toByte() }
        if (conn.bulkTransfer(tx,b,b.size,2000)!=b.size) throw IOException("USB write failed")
    }
    private fun pins():Int {
        send(0x81,0x87)
        val b=ByteArray(rx.maxPacketSize)
        repeat(20) {
            val n=conn.bulkTransfer(rx,b,b.size,2000)
            if(n>2) return b[2].toInt() and 255
        }
        throw IOException("MPSSE read timed out")
    }
    private fun gpio(scl:Boolean,sda:Boolean) {
        val dir=(if(scl)0 else 1) or (if(sda)0 else 2)
        send(0x80,if(resetHeldHigh) 8 else 0,dir or (if(resetHeldHigh) 8 else 0))
    }
    /** D3 -> RST, SEN high via board pull-up. Exclusive receiver bus only. */
    @Synchronized fun resetSi4703() {
        resetHeldHigh = false
        send(0x80, 0, 0x08)
        Thread.sleep(5)
        send(0x80, 0, 0x0a)
        Thread.sleep(5)
        val sample = pins()
        if (sample and 0x05 != 0x01) {
            send(0x80, 0, 0)
            throw IOException("Reset strap failed: SCL must be high and SDA low. Check D0, D1+D2, pull-ups and shorts.")
        }
        send(0x80, 0x08, 0x0a)
        Thread.sleep(5)
        resetHeldHigh = true
        send(0x80, 8, 8)
        Thread.sleep(5)
        checkIdle()
    }
    @Synchronized fun checkIdle() {
        val p = pins()
        if (p and 5 != 5) throw IOException("I2C idle failure: SCL=${p and 1 != 0}, SDA=${p and 4 != 0}. Check pull-ups, wiring and reset.")
    }
    @Synchronized fun lineReport(): String {
        val p = pins()
        return "GPIO %02X • SCL(D0)=%d SDA(D2)=%d RST(D3)=%d".format(p,p and 1,(p shr 2) and 1,(p shr 3) and 1)
    }
    private fun start() { gpio(true,true); gpio(true,false); gpio(false,false) }
    private fun stop() { gpio(false,false); gpio(true,false); gpio(true,true) }
    private fun byte(value:Int):Boolean {
        for(bit in 7 downTo 0) {
            val high=value and (1 shl bit)!=0
            gpio(false,high); gpio(true,high); gpio(false,high)
        }
        gpio(false,true); gpio(true,true)
        val ack=pins() and 4 == 0
        gpio(false,true)
        return ack
    }
    private fun readByte(ack: Boolean): Int {
        var value = 0
        gpio(false, true)
        for (bit in 7 downTo 0) {
            gpio(true, true)
            if (pins() and 0x04 != 0) value = value or (1 shl bit)
            gpio(false, true)
        }
        gpio(false, !ack)
        gpio(true, !ack)
        gpio(false, true)
        return value
    }
    /** Atomic I2C transaction with repeated START; register address is one byte. */
    @Synchronized fun readRegister(address: Int, register: Int, count: Int = 1): ByteArray {
        require(address in 0x08..0x77 && register in 0..255 && count in 1..32)
        start()
        try {
            if (!byte(address shl 1)) throw IOException("Address write NACK")
            if (!byte(register)) throw IOException("Register NACK")
            start()
            if (!byte((address shl 1) or 1)) throw IOException("Address read NACK")
            return ByteArray(count) { readByte(it < count - 1).toByte() }
        } finally { stop() }
    }
    /** Raw I2C read, for command/response devices such as Si47xx. */
    @Synchronized fun readRaw(address: Int, count: Int): ByteArray {
        require(address in 0x08..0x77 && count in 1..32)
        start()
        try {
            if (!byte((address shl 1) or 1)) throw IOException("Address read NACK")
            return ByteArray(count) { readByte(it < count - 1).toByte() }
        } finally { stop() }
    }
    /** Command-only write, for explicitly confirmed diagnostic actions. */
    @Synchronized fun writeCommand(address: Int, command: ByteArray) {
        require(address in 0x08..0x77 && command.size in 1..32)
        start()
        try {
            if (!byte(address shl 1)) throw IOException("Address NACK")
            command.forEachIndexed { index, b ->
                if (!byte(b.toInt() and 255)) throw IOException("Data NACK at byte $index")
            }
        } finally { stop() }
    }
    @Synchronized fun probe(address:Int):Boolean {
        require(address in 0x08..0x77)
        start()
        return try { byte(address shl 1) } finally { stop() }
    }
    override fun close() {
        if(closed)return
        closed=true
        try { send(0x80,0,0,0x82,0,0) } catch (_:Exception) {}
        try { conn.releaseInterface(iface) } catch (_:Exception) {}
        conn.close()
    }
}
