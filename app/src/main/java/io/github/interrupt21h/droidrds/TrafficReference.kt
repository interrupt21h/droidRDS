package io.github.interrupt21h.droidrds

object TrafficReference {
    fun locationClass(code: Int): String = when(code) {
        0 -> "Reserved"
        in 1..63487 -> "Normal location reference"
        in 63488..64511 -> "Special purpose"
        in 64512..65532 -> "Inter-road reference"
        in 65533..65535 -> "Special function"
        else -> error("Expected unsigned 16-bit code")
    }
    fun describe(groups: List<RdsGroup>): String = buildString {
        val advertised = mutableMapOf<Int,Int>()
        var candidates = 0
        for(g in groups) {
            if(g.type==3 && g.version=="A" && g.d==0xcd46) {
                advertised[g.a]=g.b and 31
                appendLine("ALERT-C ODA announcement • PI %04X • assigned group code %02X".format(g.a,g.b and 31))
                appendLine("Service payload: %04X".format(g.c))
            } else if(g.type==8 && g.version=="A") {
                candidates++
                appendLine("Traffic candidate • PI %04X".format(g.a))
                appendLine(if(advertised[g.a]==16) "Matching ALERT-C announcement observed" else "Service assignment not established")
                appendLine("Raw payload: %04X %04X %04X".format(g.b,g.c,g.d))
                appendLine("Location and event not resolved; service context and authorized tables required.")
            }
        }
        appendLine("Traffic candidates: $candidates")
    }
    val guide = """
        ALERT-C reference • ISO 14819
        Event catalogue: ISO 14819-2
        Location database: ISO 14819-3
        Traffic categories include congestion, collisions, closures, roadworks, weather, hazards and restrictions.
        Location identity requires LTCC, LTECC, LTN and the applicable table version.
        A location can represent an area, point or road segment. Direction and extent depend on table relationships.
        North American commercial services may encrypt location information. Raw payloads are not verified incidents.
        No US or Michigan location database is bundled.
    """.trimIndent()
}
