package io.github.interrupt21h.droidrds

class TrafficFeed {
    private data class Observation(val group:RdsGroup,val first:Long,var last:Long,var count:Int)
    private val entries=LinkedHashMap<RdsGroup,Observation>()
    private val assignments=mutableMapOf<Int,Int>()
    private var samples=0L
    private var rejected=0L
    @Synchronized fun clear() { entries.clear(); assignments.clear(); samples=0; rejected=0 }
    @Synchronized fun accept(s:ReceiverSnapshot,now:Long=System.currentTimeMillis()) {
        samples++
        val g=s.group ?: return
        val a=s.registers[0]; val b=s.registers[1]
        if(s.registers.size==32/2 && listOf((a shr 9) and 3,(b shr 14) and 3,(b shr 12) and 3,(b shr 10) and 3).any { it==3 }) { rejected++; return }
        if(g.type==3 && g.version=="A") {
            if(g.d==0xcd46) assignments[g.a]=g.b and 31
            else if(assignments[g.a]==(g.b and 31)) assignments.remove(g.a)
        }
        if(!(g.type==8 && g.version=="A") && !(g.type==3 && g.version=="A" && g.d==0xcd46)) return
        val old=entries.remove(g)
        entries[g]=if(old==null) Observation(g,now,now,1) else old.apply { last=now; count++ }
        if(entries.size>250) entries.remove(entries.keys.first())
    }
    data class Card(val group:RdsGroup,val first:Long,val last:Long,val count:Int,val confirmed:Boolean)
    data class Frame(val samples:Long,val rejected:Long,val cards:List<Card>)
    @Synchronized fun frame():Frame = Frame(samples,rejected,entries.values.map { Card(it.group,it.first,it.last,it.count,assignments[it.group.a]==16) }.asReversed())
    @Synchronized fun report(limit:Int=30):String=buildString {
        appendLine("TRAFFIC MONITOR • $samples polls • $rejected rejected • ${entries.size} distinct groups")
        appendLine("Snapshot counts may include repeated reads; not unique broadcasts.")
        for(o in entries.values.toList().asReversed().take(limit)) {
            val g=o.group
            appendLine()
            appendLine("PI %04X • %d%s • ×%d".format(g.a,g.type,g.version,o.count))
            appendLine("First: "+java.util.Date(o.first)+" • Last: "+java.util.Date(o.last))
            appendLine("Raw: %04X %04X %04X %04X".format(g.a,g.b,g.c,g.d))
            if(g.type==3) appendLine("ALERT-C announcement • assigned group %02X".format(g.b and 31))
            else if(g.b and 16 != 0) appendLine("System/tuning variant ${g.b and 15}; not an incident")
            else if(g.b and 8 == 0) appendLine("Multi-group fragment • CI ${g.b and 7}; not assembled")
            else {
                appendLine(if(assignments[g.a]==16) "ALERT-C single-group candidate" else "Unconfirmed 8A single-group candidate")
                appendLine("Event field: ${g.c and 2047} • Extent: ${(g.c shr 11) and 7}")
                appendLine("Direction bit: ${(g.c shr 14) and 1} • Diversion bit: ${g.c ushr 15} • Duration code: ${g.b and 7}")
                appendLine("Location word: %d (0x%04X)".format(g.d,g.d))
                appendLine("Unresolved; may be encrypted. Not a geographic identification.")
            }
        }
    }
}
