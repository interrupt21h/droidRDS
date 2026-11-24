package io.github.interrupt21h.droidrds

import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object OnlineCatalogue {
    const val SOURCE = "https://wiki.openstreetmap.org/wiki/TMC/Event_Code_List"
    private const val API = "https://wiki.openstreetmap.org/w/api.php?action=parse&page=TMC%2FEvent_Code_List&prop=text&format=json&formatversion=2"
    private fun download(address:String):String {
        val connection=URL(address).openConnection() as HttpsURLConnection
        connection.connectTimeout=15000
        connection.readTimeout=30000
        connection.setRequestProperty("User-Agent","droidRDS/1.0 (https://github.com/interrupt21h/droidRDS)")
        try {
            check(connection.responseCode==200) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val result=StringBuilder()
                val buffer=CharArray(8192)
                while(true) {
                    val n=reader.read(buffer)
                    if(n<0) break
                    result.append(buffer,0,n)
                    require(result.length<=8_000_000) { "Response too large" }
                }
                result.toString()
            }
        } finally { connection.disconnect() }
    }
    // HTML table cells are parsed by Android's HTML decoder, including entities.
    fun parse(html:String):String {
        val rows=Regex("<tr\\b[^>]*>(.*?)</tr\\s*>",setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL))
        val cells=Regex("<t[dh]\\b[^>]*>(.*?)</t[dh]\\s*>",setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL))
        val events=sortedMapOf<Int,List<String>>()
        var active=false
        for(row in rows.findAll(html)) {
            val fields=cells.findAll(row.groupValues[1]).map {
                android.text.Html.fromHtml(it.groupValues[1],android.text.Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+")," ").trim()
            }.toList()
            if(fields==listOf("Code","Description","N","Q","T","D","U","C","R")) { active=true; continue }
            if(!active || fields.size!=9) continue
            val code=fields[0].toIntOrNull() ?: continue
            require(code in 0..2047 && fields[1].isNotBlank() && code !in events)
            events[code]=listOf(fields[0],fields[1],fields[3],fields[2],fields[6],fields[4],fields[5],fields[7],fields[8])
        }
        require(events.size>=1400 && listOf(101,201,701,738,739).all { it in events }) { "Incomplete event table: ${events.size} rows" }
        return buildString {
            appendLine("# OpenStreetMap Wiki contributors — $SOURCE — CC BY-SA 2.0")
            appendLine("code\tdescription\tquantifier\tnature\turgency\tduration\tdirectionality\tupdateClass\treference")
            events.values.forEach { appendLine(it.joinToString("\t")) }
        }
    }
    fun fetch():String = runCatching { parse(download(SOURCE)) }.getOrElse {
        parse(JSONObject(download(API)).getJSONObject("parse").getString("text"))
    }
}
