package io.github.interrupt21h.droidrds

object TrafficEvents {
    // Selected factual code mappings from ISO 14819-2:2021 Table 2 public preview.
    // Deliberately not advertised as the complete catalogue.
    private val names=mapOf(
        1 to "Traffic problem",
        101 to "Stationary traffic",
        102 to "Stopped traffic — 1 km",
        103 to "Stopped traffic — 2 km",
        129 to "Stopped traffic — 3 km",
        104 to "Stopped traffic — 4 km",
        105 to "Stopped traffic — 6 km",
        106 to "Stopped traffic — 10 km",
        130 to "Possible stopped traffic",
        108 to "Queuing traffic",
        109 to "Traffic queue — 1 km",
        110 to "Traffic queue — 2 km",
        131 to "Traffic queue — 3 km",
        111 to "Traffic queue — 4 km",
        738 to "Roadworks — left lane closed",
        739 to "Roadworks — shoulder closed"
    )
    data class Definition(val code:Int,val name:String,val quantifier:String?,val nature:String?,val urgency:String?,val duration:String?,val directionality:String?,val updateClass:String?)
    @Volatile private var imported:Map<Int,Definition> = emptyMap()
    val count:Int get()=imported.size
    fun load(reader:java.io.Reader):Int {
        val next=linkedMapOf<Int,Definition>()
        reader.buffered().useLines { lines ->
            lines.forEachIndexed { i,line ->
                if(line.isNotBlank() && !line.startsWith("#") && !line.startsWith("code\t")) {
                    val p=line.split('\t')
                    require(p.size>=2) { "Invalid catalogue row ${i+1}" }
                    val code=p[0].toInt()
                    require(code in 0..2047 && p[1].isNotBlank() && code !in next)
                    fun field(n:Int)=p.getOrNull(n)?.trim()?.takeIf { it.isNotEmpty() }
                    next[code]=Definition(code,p[1],field(2),field(3),field(4),field(5),field(6),field(7))
                }
            }
        }
        require(next.isNotEmpty())
        imported=next.toMap()
        return next.size
    }
    fun definition(code:Int)=imported[code]
    fun name(code:Int):String?=definition(code)?.name ?: names[code]
    fun direction(bit:Int)=if(bit==0) "Positive table direction" else "Negative table direction"
    fun diversion(bit:Int)=if(bit==1) "Diversion advised" else "No diversion advice"
}
