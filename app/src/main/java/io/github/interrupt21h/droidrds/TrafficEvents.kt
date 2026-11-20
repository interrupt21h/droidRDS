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
        111 to "Traffic queue — 4 km"
    )
    fun name(code:Int):String?=names[code]
    fun direction(bit:Int)=if(bit==0) "Positive table direction" else "Negative table direction"
    fun diversion(bit:Int)=if(bit==1) "Diversion advised" else "No diversion advice"
}
