package io.github.interrupt21h.droidrds

class RdsDecoder {
    private var pi = -1
    private var ps = CharArray(8){' '}
    private var rt = CharArray(64){' '}
    private var textFlag = -1
    private var textVersion = ""
    private var psMask = 0
    private var rtMask = 0
    private val counts = sortedMapOf<String,Int>()
    private fun put(target:CharArray,offset:Int,word:Int) {
        target[offset]=(word ushr 8).toChar()
        target[offset+1]=(word and 255).toChar()
    }
    fun accept(g:RdsGroup) {
        if(pi!=g.a) {
            pi=g.a; ps.fill(' '); rt.fill(' '); psMask=0; rtMask=0; textFlag=-1; counts.clear()
        }
        val key="${g.type}${g.version}"
        counts[key]=(counts[key]?:0)+1
        if(g.type==0) {
            val segment=g.b and 3
            put(ps,segment*2,g.d); psMask=psMask or (1 shl segment)
        }
        if(g.type==2) {
            val flag=(g.b ushr 4) and 1
            if(flag!=textFlag || textVersion!=g.version) {
                rt.fill(' '); rtMask=0; textFlag=flag; textVersion=g.version
            }
            val segment=g.b and 15
            if(g.version=="A") { put(rt,segment*4,g.c); put(rt,segment*4+2,g.d) }
            else put(rt,segment*2,g.d)
            rtMask=rtMask or (1 shl segment)
        }
    }
    fun report():String {
        val text=String(rt).substringBefore('\r').trimEnd()
        return "PI: %04X\nPS: %s (%d/4 segments)\nRadioText: %s (%d/16 segments)\nGroups: %s".format(pi,String(ps),Integer.bitCount(psMask),text,Integer.bitCount(rtMask),counts)
    }
}
