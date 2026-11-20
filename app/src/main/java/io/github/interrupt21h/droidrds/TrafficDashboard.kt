package io.github.interrupt21h.droidrds

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrafficDashboard(context:Context): LinearLayout(context) {
    private val ink=Color.rgb(233,240,247)
    private val muted=Color.rgb(164,184,199)
    private val teal=Color.rgb(105,230,210)
    private val amber=Color.rgb(240,190,110)
    private val summary=label("",12f,muted)
    private val list=ListView(context)
    private val follow=Switch(context).apply { text="Follow latest"; isChecked=true; setTextColor(ink) }
    private var frame=TrafficFeed.Frame(0,0,emptyList())
    private var rows=emptyList<TrafficFeed.Card>()
    private var filter=0
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun label(value:String,size:Float,color:Int=ink)=TextView(context).apply { text=value; textSize=size; setTextColor(color); setPadding(0,dp(2),0,dp(2)) }
    private fun background(color:Int)=GradientDrawable().apply { setColor(color); cornerRadius=dp(12).toFloat() }
    private fun isAlert(c:TrafficFeed.Card)=c.group.type==8 && c.group.b and 24==8
    private val adapter=object:BaseAdapter() {
        override fun getCount()=rows.size
        override fun getItem(position:Int)=rows[position]
        override fun getItemId(position:Int)=position.toLong()
        override fun getView(position:Int,convertView:View?,parent:android.view.ViewGroup?):View {
            val c=getItem(position); val g=c.group
            val box=LinearLayout(context).apply { orientation=VERTICAL; setPadding(dp(14),dp(11),dp(14),dp(11)); background=background(Color.rgb(22,39,53)) }
            val title=when { isAlert(c)->"Event ${g.c and 2047}"; g.type==3->"ALERT-C service"; g.b and 16!=0->"System / tuning"; else->"Multi-group fragment" }
            box.addView(label(title,20f,if(isAlert(c)) teal else ink).apply { typeface=Typeface.DEFAULT_BOLD })
            val clock=SimpleDateFormat("HH:mm:ss",Locale.US)
            box.addView(label("PI %04X  •  %d%s  •  %s  •  ×%d reads".format(g.a,g.type,g.version,clock.format(Date(c.last)),c.count),12f,muted))
            if(isAlert(c)) {
                val pair=LinearLayout(context)
                val loc=LinearLayout(context).apply { orientation=VERTICAL }
                loc.addView(label("LOCATION WORD",11f,muted))
                loc.addView(label("0x%04X".format(g.d),24f,teal).apply { typeface=Typeface.MONOSPACE })
                loc.addView(label("${g.d} decimal",13f))
                pair.addView(loc,LayoutParams(0,-2,1f))
                val detail=LinearLayout(context).apply { orientation=VERTICAL }
                detail.addView(label("EXTENT / DURATION",11f,muted))
                detail.addView(label("${(g.c shr 11) and 7} / ${g.b and 7}",22f))
                detail.addView(label("Direction ${(g.c shr 14) and 1} · Diversion ${g.c ushr 15}",12f,muted))
                pair.addView(detail,LayoutParams(0,-2,1f))
                box.addView(pair)
                box.addView(label("Unresolved location • may be encrypted",12f,amber))
                box.addView(label(if(c.confirmed) "ALERT-C assignment observed" else "Unconfirmed service assignment",12f,muted))
            } else box.addView(label(if(g.type==3) "AID CD46 • assigned group %02X".format(g.b and 31) else if(g.b and 16!=0) "Variant ${g.b and 15}" else "Continuity index ${g.b and 7}",14f))
            box.addView(label("%04X  %04X  %04X  %04X".format(g.a,g.b,g.c,g.d),12f,muted).apply { typeface=Typeface.MONOSPACE })
            return box
        }
    }
    init {
        orientation=VERTICAL
        val bar=LinearLayout(context)
        bar.addView(summary,LayoutParams(0,-2,1f)); bar.addView(follow)
        addView(bar)
        val filters=LinearLayout(context)
        listOf("All","Alerts","Service").forEachIndexed { i,s ->
            filters.addView(Button(context).apply { text=s; isAllCaps=false; setOnClickListener { filter=i; refresh(true) } },LayoutParams(0,-2,1f))
        }
        addView(filters)
        list.adapter=adapter
        list.divider=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        list.dividerHeight=dp(8)
        list.setOnScrollListener(object:AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view:AbsListView?,state:Int) { if(state==AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) follow.isChecked=false }
            override fun onScroll(view:AbsListView?,first:Int,visible:Int,total:Int) {}
        })
        follow.setOnCheckedChangeListener { _,checked -> if(checked) refresh(true) }
        addView(list,LayoutParams(-1,0,1f))
        refresh(true)
    }
    fun update(value:TrafficFeed.Frame) { frame=value; refresh(false) }
    fun reset(value:TrafficFeed.Frame) { frame=value; refresh(true) }
    private fun refresh(force:Boolean) {
        summary.text="${frame.cards.size} groups · ${frame.samples} polls\n"+if(follow.isChecked) "LIVE" else "Browsing · feed still recording"
        if(!force && !follow.isChecked) return
        rows=frame.cards.filter { when(filter) { 1->isAlert(it); 2->!isAlert(it); else->true } }
        adapter.notifyDataSetChanged()
        if(follow.isChecked) list.setSelection(0)
    }
}
