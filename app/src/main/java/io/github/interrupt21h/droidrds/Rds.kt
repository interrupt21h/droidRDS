package io.github.interrupt21h.droidrds

data class RdsGroup(val a: Int, val b: Int, val c: Int, val d: Int) {
    init { require(listOf(a,b,c,d).all { it in 0..65535 }) }
    val type get() = b ushr 12
    val version get() = if (b and 0x800 == 0) "A" else "B"
    val tp get() = b and 0x400 != 0
    val pty get() = (b ushr 5) and 31
    fun describe(): String = buildString {
        append("PI: %04X\nGroup: %d%s\nTP: %s\nPTY: %d\nBlocks: %04X %04X %04X %04X".format(a,type,version,tp,pty,a,b,c,d))
        if (type == 8 && version == "A") {
            append("\nPotential TMC group; verify ODA/service context.")
            append("\nPayload retained raw; event and location interpretation requires the applicable ALERT-C tables.")
        }
        if (type == 3 && version == "A") append("\nODA application identifier: %04X".format(d))
    }
    companion object {
        fun parse(line: String): RdsGroup {
            val words = line.trim().split(Regex("\\s+"))
            require(words.size == 4 && words.all { it.matches(Regex("[0-9a-fA-F]{4}")) }) { "Expected four 16-bit hex blocks" }
            val v = words.map { it.toInt(16) }
            return RdsGroup(v[0],v[1],v[2],v[3])
        }
    }
}
