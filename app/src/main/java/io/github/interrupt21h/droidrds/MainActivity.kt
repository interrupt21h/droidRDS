package io.github.interrupt21h.droidrds
import android.app.*
import android.content.*
import android.hardware.usb.*
import android.os.*
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var usb: UsbManager
    private lateinit var output: TextView
    private lateinit var address: EditText
    private var bus: Ft232h? = null
    private var busy = false
    private lateinit var live: TextView
    private lateinit var autoRefresh: CheckBox
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    private var generation = 0
    private var deviceName: String? = null
    private var failures = 0
    private val traffic = TrafficFeed()
    private lateinit var trafficView: TrafficDashboard
    private lateinit var trafficEnabled: CheckBox
    private lateinit var radioStatus: TextView
    private lateinit var frequencyDisplay: TextView
    private lateinit var signalMeter: ProgressBar
    private var selectedKhz=99500
    private var lastTrafficRender=0L
    private var pendingOperation: (() -> String)? = null
    private val poll = object: Runnable {
        override fun run() {
            if (!foreground) return
            if (!busy && (autoRefresh.isChecked || trafficEnabled.isChecked) && (si4703 != null || rda != null)) {
                val s = si4703
                val d = rda
                val b = bus
                val token = generation
                val collect = trafficEnabled.isChecked
                busy = true
                thread {
                    val result = runCatching {
                        if (collect && b != null) {
                            val sample = Receiver(b, if(s != null) ReceiverKind.SI4703 else ReceiverKind.RDA5807M).snapshot()
                            traffic.accept(sample)
                            val khz=(if(s!=null) 87500 else 87000)+(sample.registers[1].let { if(s!=null) it else sample.registers[0] } and 1023)*100
                            runOnUiThread { if (!isDestroyed && token==generation) {
                                signalMeter.progress=sample.rssi.coerceIn(0,127)
                                frequencyDisplay.text="%.1f MHz".format(khz/1000.0)
                                radioStatus.text="RSSI "+sample.rssi+" • "+(if(sample.stereo) "Stereo" else "Mono")+" • "+(if(sample.group!=null) "RDS received" else "Waiting for RDS")
                            } }
                            "RSSI: "+sample.rssi+" • Stereo: "+sample.stereo
                        } else (s?.diagnostics() ?: d?.report() ?: error("No receiver")) +
                            "\n" + (b?.lineReport() ?: "")
                    }
                    runOnUiThread {
                        busy = false
                        if (isDestroyed || token != generation) {
                            b?.close()
                            if (bus === b) bus = null
                        } else {
                            result.fold(onSuccess = {
                                failures = 0
                                if (collect && SystemClock.elapsedRealtime()-lastTrafficRender>=750) { trafficView.update(traffic.frame()); lastTrafficRender=SystemClock.elapsedRealtime() }
                                live.text = "Updated " + java.text.DateFormat.getTimeInstance().format(java.util.Date()) + "\n" + it
                            }, onFailure = {
                                failures++
                                live.text = "Telemetry stale • failure $failures: " + it.message
                                if (failures >= 3) {
                                    si4703 = null; rda = null
                                    live.append("\nReinitialize receiver to resume.")
                                }
                            })
                        }
                        pendingOperation?.let { pendingOperation=null; work(it) }
                    }
                }
            }
            handler.postDelayed(this, if(trafficEnabled.isChecked) 150 else 2000)
        }
    }
    private var si4703: Si4703? = null
    private var rda: Rda5807? = null
    private val permission = "io.github.interrupt21h.droidrds.USB"
    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == permission) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)) connect()
                else output.text = "USB permission denied"
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                val detached = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (detached?.deviceName != deviceName) return
                generation++
                si4703 = null
                rda = null
                deviceName = null
                if (!busy) { bus?.close(); bus = null }
                live.text = "Disconnected"
                output.text = "USB detached; reconnect before diagnostics"
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usb = getSystemService(USB_SERVICE) as UsbManager
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply { addAction(permission); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(13,22,34)) }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,24,32,32) }
        scroll.addView(body)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, i ->
            val bars = i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); i
        }
        fun dp(n:Int) = (n * resources.displayMetrics.density).toInt()
        fun panel(color:Int) = android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius=dp(14).toFloat() }
        body.setBackgroundColor(Color.rgb(13,22,34))
        fun label(s: String, size: Float = 16f) { body.addView(TextView(this).apply { text=s; textSize=size; setTextColor(if(size>=20) Color.rgb(105,230,210) else Color.rgb(210,223,235)); setPadding(0,dp(12),0,dp(8)); if(size>=20) typeface=android.graphics.Typeface.DEFAULT_BOLD }) }
        fun button(s: String, f: () -> Unit) { body.addView(Button(this).apply { text=s; isAllCaps=false; setTextColor(Color.WHITE); background=panel(Color.rgb(34,68,88)); minHeight=dp(48); layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(6) }; setOnClickListener { f() } }) }
        val nav=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val radioTab=Button(this).apply { text="Radio"; isAllCaps=false }
        val setupTab=Button(this).apply { text="Setup & diagnostics"; isAllCaps=false }
        nav.addView(radioTab,LinearLayout.LayoutParams(0,-2,1f)); nav.addView(setupTab,LinearLayout.LayoutParams(0,-2,1f))
        root.addView(nav)
        val radioPane=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(12)) }
        root.addView(radioPane,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        scroll.visibility=android.view.View.GONE
        fun radioText(value:String,size:Float,color:Int=Color.WHITE)=TextView(this).apply { text=value; textSize=size; setTextColor(color); setPadding(0,dp(4),0,dp(4)) }
        fun radioButton(value:String,action:()->Unit)=Button(this).apply { text=value; isAllCaps=false; setTextColor(Color.WHITE); background=panel(Color.rgb(34,68,88)); minHeight=dp(48); setOnClickListener { action() } }
        radioPane.addView(radioText("droidRDS  /  FM receiver",15f,Color.rgb(105,230,210)))
        frequencyDisplay=radioText("— MHz",34f,Color.rgb(105,230,210)).apply { gravity=android.view.Gravity.CENTER; typeface=android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL) }
        radioPane.addView(frequencyDisplay)
        radioStatus=radioText("Initialize receiver in Setup",14f)
        radioPane.addView(radioStatus)
        signalMeter=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=127; progressTintList=android.content.res.ColorStateList.valueOf(Color.rgb(105,230,210)) }
        radioPane.addView(signalMeter)
        val dial=SeekBar(this).apply { max=205; progress=120; contentDescription="FM frequency" }
        val chosen=radioText("Tune to 99.5 MHz",16f)
        radioPane.addView(chosen); radioPane.addView(dial)
        fun select(khz:Int) { selectedKhz=khz.coerceIn(87500,108000); dial.progress=(selectedKhz-87500)/100; chosen.text="Tune to %.1f MHz".format(selectedKhz/1000.0) }
        dial.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(v:SeekBar?,p:Int,fromUser:Boolean) { if(fromUser) { selectedKhz=87500+p*100; chosen.text="Tune to %.1f MHz".format(selectedKhz/1000.0) } }; override fun onStartTrackingTouch(v:SeekBar?){}; override fun onStopTrackingTouch(v:SeekBar?){} })
        fun tuneSelected() { val k=selectedKhz; work { si4703?.tune(k) ?: rda?.tune(k) ?: error("Initialize receiver in Setup") } }
        val controls=LinearLayout(this)
        for(v in listOf(radioButton("−") { select(selectedKhz-100) },radioButton("Tune") { tuneSelected() },radioButton("+") { select(selectedKhz+100) })) controls.addView(v,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(4) })
        radioPane.addView(controls)
        val seekRow=LinearLayout(this)
        for(up in listOf(false,true)) seekRow.addView(radioButton(if(up) "Seek ›" else "‹ Seek") { work { si4703?.seek(up) ?: rda?.seek(up) ?: error("Initialize receiver in Setup") } },LinearLayout.LayoutParams(0,-2,1f))
        radioPane.addView(seekRow,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(5) })
        radioTab.setOnClickListener { radioPane.visibility=android.view.View.VISIBLE; scroll.visibility=android.view.View.GONE }
        setupTab.setOnClickListener { radioPane.visibility=android.view.View.GONE; scroll.visibility=android.view.View.VISIBLE }
        label("droidRDS", 32f)
        label("FT232H • FM/RDS laboratory diagnostics")
        label("Build: "+packageManager.getPackageInfo(packageName,0).versionName, 13f)
        val selector = Spinner(this)
        selector.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Si4703 receiver", "RDA5807M receiver", "Si4713 diagnostics", "Offline tools"))
        body.addView(selector)
        live = TextView(this).apply { text = "No receiver initialized"; textSize = 15f; setTextIsSelectable(true); typeface = android.graphics.Typeface.MONOSPACE }
        live.setTextColor(Color.rgb(105,230,210)); live.background=panel(Color.rgb(22,39,53)); live.setPadding(dp(16),dp(16),dp(16),dp(16))
        body.addView(live)
        label("Last operation / initialization trace")
        output = TextView(this).apply { setTextIsSelectable(true); text=getPreferences(MODE_PRIVATE).getString("lastDiagnostics","Ready — power diagnostics v3"); textSize=14f; typeface=android.graphics.Typeface.MONOSPACE }
        output.setTextColor(Color.rgb(224,233,242)); output.background=panel(Color.rgb(22,39,53)); output.setPadding(dp(16),dp(16),dp(16),dp(16))
        body.addView(output)
        button("Copy operation diagnostics") { (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("droidRDS",output.text)) }
        autoRefresh = CheckBox(this).apply { text = "Live telemetry (every 2 seconds when idle)"; isChecked = true }
        body.addView(autoRefresh)
        
        label("D0 → SCL; D1+D2 → SDA; common GND. Verify pull-ups and board voltage. RF commands are not exposed.")
        button("Connect FT232H") { connect() }
        button("Check I2C lines") { work { (bus ?: error("Connect FT232H first")).lineReport() } }
        radioPane.addView(radioText("TRAFFIC  /  ALERT-C",18f,Color.rgb(105,230,210)))
        trafficEnabled = CheckBox(this).apply { text="Record traffic"; isChecked=true; setTextColor(Color.WHITE) }
        radioPane.addView(trafficEnabled)
        trafficView = TrafficDashboard(this)
        radioPane.addView(trafficView,LinearLayout.LayoutParams(-1,0,1f))
        val sessionRow=LinearLayout(this)
        radioPane.addView(sessionRow)
        sessionRow.addView(radioButton("Copy session") { (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("Traffic observations",traffic.report(250))) },LinearLayout.LayoutParams(0,-2,1f))
        sessionRow.addView(radioButton("Clear") { traffic.clear(); trafficView.reset(traffic.frame()) },LinearLayout.LayoutParams(0,-2,1f))
        val txStart = body.childCount
        address = EditText(this).apply { setSingleLine(); setText("63"); hint="7-bit hexadecimal address" }; body.addView(address)
        button("Check I2C bus (non-destructive)") { work {
            val b = bus ?: error("Connect FT232H first")
            b.checkIdle()
            "SCL and SDA idle high. This confirms line levels, not chip identity. Si4703 may require reset before ACK."
        } }
        button("Probe and read revision") { work {
            val a = address.text.toString().trim().removePrefix("0x").toInt(16)
            require(a == 0x63 || a == 0x11) { "Use a verified Si4713 address" }
            val radio = Radio(bus ?: error("Connect FT232H first"), a)
            if (!radio.probe()) "No ACK" else "ACK observed\n" + radio.revision()
        } }
        val siStart = body.childCount
        label("Si4703 FM receiver", 24f)
        label("D3 → RST; SEN → 3.3 V through 4.7 kΩ if no onboard pull-up. D1 and D2 must both connect to SDIO. Use a dedicated Si4703 bus. Detection checks reset strap, idle levels, ACK and chip ID.")
        val rxFreq = EditText(this).apply { setSingleLine(); inputType=8194; setText("99.5"); hint="MHz" }
        body.addView(rxFreq)
        val crystal = CheckBox(this).apply { text="Module has 32.768 kHz crystal"; isChecked=true }
        body.addView(crystal)
        val delay = EditText(this).apply { setSingleLine(); inputType=2; setText("650"); hint="Crystal startup delay (500–10000 ms)" }
        body.addView(delay)
        button("Detect and initialize Si4703") {
            if (!busy) AlertDialog.Builder(this).setTitle("Reset receiver")
                .setMessage("Confirm D3 is connected to Si4703 RST, SEN is high, and no other target shares this bus during reset.")
                .setNegativeButton("Cancel",null).setPositiveButton("Initialize") { _, _ ->
                    val useCrystal = crystal.isChecked
                    val wait = delay.text.toString().toLongOrNull()?.coerceIn(500,10000) ?: 650L
                    work {
                        val b = bus ?: error("Connect FT232H first")
                        si4703 = null
                        rda = null
                        b.resetSi4703()
                        if (!b.probe(0x10)) error("No ACK at 0x10 after reset. Verify SEN high, D3 to RST, D1+D2 to SDIO, 3.3 V and common ground.")
                        val chip = Si4703(b)
                        val result = chip.initialize(useCrystal, wait) { trace -> runOnUiThread { if (!isDestroyed) output.text = trace } }
                        si4703 = chip
                        result
                    }
                }.show()
        }
        label("Advanced startup diagnostics", 20f)
        button("1. Reset only (dedicated Si4703 bus)") {
            if (!busy) AlertDialog.Builder(this).setMessage("Confirm D3 is wired to RST and only the Si4703 is connected.").setNegativeButton("Cancel",null).setPositiveButton("Reset") { _,_ ->
                work {
                    val b=bus ?: error("Connect FT232H first")
                    si4703=null; rda=null
                    b.resetSi4703()
                    b.lineReport()+"\n"+Si4703(b).rawDiagnostics()
                }
            }.show()
        }
        button("2. Enable crystal only") {
            val wait=delay.text.toString().toLongOrNull()?.coerceIn(500,10000) ?: 650L
            work { si4703=null; rda=null; Si4703(bus ?: error("Connect FT232H first")).oscillatorTest(wait) }
        }
        button("3. Power enable (register 02 only)") {
            work { si4703=null; rda=null; Si4703(bus ?: error("Connect FT232H first")).powerTest(false) }
        }
        button("3b. Power enable (reference-style registers 02–07)") {
            work { si4703=null; rda=null; Si4703(bus ?: error("Connect FT232H first")).powerTest(true) }
        }
        button("Read raw Si4703 registers (even if initialization failed)") { work { Si4703(bus ?: error("Connect FT232H first")).rawDiagnostics() } }
        button("Tune receiver") {
            val khz = rxFreq.text.toString().toBigDecimalOrNull()?.multiply(java.math.BigDecimal(1000))
            work { (si4703 ?: error("Initialize receiver first")).tune(khz?.intValueExact() ?: error("Invalid MHz")) }
        }
        button("Seek down") { work { (si4703 ?: error("Initialize receiver first")).seek(false) } }
        button("Seek up") { work { (si4703 ?: error("Initialize receiver first")).seek(true) } }
        button("Refresh receiver status") { work { (si4703 ?: error("Initialize receiver first")).report() } }
        val volume = SeekBar(this).apply { max=15; progress=1 }
        body.addView(volume)
        button("Apply volume") {
            val level = volume.progress
            work { (si4703 ?: error("Initialize receiver first")).volume(level) }
        }
        button("Capture RDS for 10 seconds") {
            work { (si4703 ?: error("Initialize receiver first")).capture(10) }
        }
        val rdaStart = body.childCount
        label("RDA5807M receiver", 24f)
        val rdaFreq = EditText(this).apply { setSingleLine(); inputType=8194; setText("99.5"); hint="MHz" }
        body.addView(rdaFreq)
        val rdaVolume = SeekBar(this).apply { max=15; progress=1 }
        body.addView(rdaVolume)
        label("Use a dedicated receiver bus; do not connect Si4713 at 0x11.")
        button("Initialize RDA5807M") {
            work {
                rda=null
                si4703=null
                (bus ?: error("Connect FT232H first")).checkIdle()
                val chip=Rda5807(bus ?: error("Connect FT232H first"))
                val result=chip.initialize()
                rda=chip
                result
            }
        }
        button("Tune RDA5807M to entered frequency") {
            val khz=rdaFreq.text.toString().toBigDecimalOrNull()?.multiply(java.math.BigDecimal(1000))
            work { (rda ?: error("Initialize RDA5807M first")).tune(khz?.intValueExact() ?: error("Invalid frequency")) }
        }
        button("RDA5807M seek down") { work { (rda ?: error("Initialize first")).seek(false) } }
        button("RDA5807M seek up") { work { (rda ?: error("Initialize first")).seek(true) } }
        button("RDA5807M status") { work { (rda ?: error("Initialize first")).report() } }
        button("Apply RDA5807M volume") {
            val level=rdaVolume.progress
            work { (rda ?: error("Initialize first")).volume(level) }
        }
        val snapshotStart = body.childCount
        label("Receiver register capture", 22f)
        label("Select the known receiver model. A shared 0x10 ACK is not identification. Receiver must be powered, tuned and configured for RDS externally.")
        for (kind in ReceiverKind.entries) {
            button("Read " + kind.name + " snapshot") {
                work {
                    val s = Receiver(bus ?: error("Connect FT232H first"), kind).snapshot()
                    "RSSI: ${s.rssi} Stereo: ${s.stereo}\nRegisters: " +
                        s.registers.joinToString(" ") { "%04X".format(it) } +
                        "\n\n" + (s.group?.describe() ?: "No synchronized ready RDS group")
                }
            }
        }
        val offlineStart = body.childCount
        label("Offline frequency planner", 22f)
        val freq = EditText(this).apply { setSingleLine(); inputType=8194; setText("99.5") }; body.addView(freq)
        button("Validate frequency (no RF)") {
            val mhz = freq.text.toString().toDoubleOrNull()
            output.text = if (mhz != null && mhz in 76.0..108.0 && kotlin.math.abs(mhz*20-kotlin.math.round(mhz*20)) < 0.00001) "Valid 50 kHz grid frequency: $mhz MHz (offline only)" else "Expected 76–108 MHz on 50 kHz grid"
        }
        label("Offline RDS group inspector",22f)
        val raw = EditText(this).apply { hint="Four hexadecimal blocks per line"; minLines=3 }; body.addView(raw)
        button("Decode captured groups") {
            output.text = try { raw.text.toString().lineSequence().filter { it.isNotBlank() }.take(500).map { RdsGroup.parse(it).describe() }.joinToString("\n\n") } catch(e: Exception) { e.message }
        }
        button("Assemble station and RadioText") {
            try {
                val decoder=RdsDecoder()
                raw.text.toString().lineSequence().filter { it.isNotBlank() }.take(500).forEach { decoder.accept(RdsGroup.parse(it)) }
                output.text=decoder.report()
            } catch(e:Exception) { output.text=e.message }
        }
        label("Traffic intelligence / ALERT-C", 24f)
        label(TrafficReference.guide)
        button("Inspect traffic service announcements") {
            output.text = runCatching {
                TrafficReference.describe(raw.text.toString().lineSequence().filter { it.isNotBlank() }.take(500).map { RdsGroup.parse(it) }.toList())
            }.getOrElse { "Invalid capture: "+it.message }
        }
        val locationCode=EditText(this).apply { inputType=2; hint="Location reference (decimal)" }
        body.addView(locationCode)
        button("Classify location reference") {
            output.text=runCatching {
                val code=locationCode.text.toString().toInt()
                "%d / 0x%04X • %s".format(code,code,TrafficReference.locationClass(code))+
                    "\nGeographic resolution requires the matching authorized location table."
            }.getOrElse { it.message ?: "Invalid code" }
        }
        val offlineEnd = body.childCount
        
        button("Copy report") { (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("droidRDS",output.text)) }
        fun showProfile(position: Int) {
            for (i in txStart until offlineEnd) {
                val visible = when {
                    i < siStart -> position == 2
                    i < rdaStart -> position == 0
                    i < snapshotStart -> position == 1
                    i < offlineStart -> position == 3
                    else -> position == 3
                }
                body.getChildAt(i).visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
        showProfile(0)
        selector.onItemSelectedListener = object: android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                showProfile(position)
            }
        }
        button("Copy live diagnostics") { (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("droidRDS diagnostics",live.text)) }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
    }
    private fun connect() {
        if (busy) return
        val device = usb.deviceList.values.firstOrNull { it.vendorId==0x0403 && it.productId==0x6014 }
        if (device == null) { output.text="FT232H not found"; return }
        if (!usb.hasPermission(device)) {
            usb.requestPermission(device,PendingIntent.getBroadcast(this,0,Intent(permission).setPackage(packageName),PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)); return
        }
        deviceName = device.deviceName
        live.text = "Connecting FT232H • 0403:6014"
        work { si4703=null; rda=null; bus?.close(); bus=null; bus=Ft232h(usb,device); "FT232H connected • " + device.deviceName + "\n" + (bus?.lineReport() ?: "") }
    }
    private fun work(task: () -> String) {
        if (busy) { pendingOperation=task; radioStatus.text="Operation queued…"; return }
        busy=true; output.text="Working…"
        val token = generation
        thread {
            val result=try { task() } catch(e: Exception) { "Error: "+e.message }
            runOnUiThread {
                busy=false
                if (!isDestroyed && token == generation) { output.text=result; radioStatus.text=result.lineSequence().firstOrNull() ?: "Ready"; failures=0; getPreferences(MODE_PRIVATE).edit().putString("lastDiagnostics",result.take(32000)).apply() }
                else { bus?.close(); bus=null; si4703=null; rda=null }
                if (!isDestroyed && token == generation) pendingOperation?.let { pendingOperation=null; work(it) }
            }
        }
    }
    override fun onStart() { super.onStart(); foreground=true; handler.post(poll) }
    override fun onStop() { foreground=false; handler.removeCallbacks(poll); super.onStop() }
    override fun onDestroy() { unregisterReceiver(receiver); if (!busy) bus?.close(); super.onDestroy() }
}
