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
    private val permission = "io.github.interrupt21h.droidrds.USB"
    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == permission) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)) connect()
                else output.text = "USB permission denied"
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                output.text = "USB detached; reconnect before diagnostics"
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usb = getSystemService(USB_SERVICE) as UsbManager
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply { addAction(permission); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,24,32,32) }
        scroll.addView(body)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, i ->
            val bars = i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); i
        }
        fun label(s: String, size: Float = 16f) { body.addView(TextView(this).apply { text=s; textSize=size; setPadding(0,12,0,8) }) }
        fun button(s: String, f: () -> Unit) { body.addView(Button(this).apply { text=s; setOnClickListener { f() } }) }
        label("droidRDS", 32f)
        label("FT232H / Si4713 • laboratory diagnostics")
        label("D0 → SCL; D1+D2 → SDA; common GND. Verify pull-ups and board voltage. RF commands are not exposed.")
        address = EditText(this).apply { setSingleLine(); setText("63"); hint="7-bit hexadecimal address" }; body.addView(address)
        button("Connect FT232H") { connect() }
        button("Probe and read revision") { work {
            val a = address.text.toString().trim().removePrefix("0x").toInt(16)
            require(a == 0x63 || a == 0x11) { "Use a verified Si4713 address" }
            val radio = Radio(bus ?: error("Connect FT232H first"), a)
            if (!radio.probe()) "No ACK" else "ACK observed\n" + radio.revision()
        } }
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
        output = TextView(this).apply { setTextIsSelectable(true); text="Ready"; textSize=15f; setTextColor(Color.rgb(130,220,210)) }; body.addView(output)
        button("Copy report") { (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("droidRDS",output.text)) }
        setContentView(scroll)
        ViewCompat.requestApplyInsets(scroll)
    }
    private fun connect() {
        if (busy) return
        val device = usb.deviceList.values.firstOrNull { it.vendorId==0x0403 && it.productId==0x6014 }
        if (device == null) { output.text="FT232H not found"; return }
        if (!usb.hasPermission(device)) {
            usb.requestPermission(device,PendingIntent.getBroadcast(this,0,Intent(permission).setPackage(packageName),PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)); return
        }
        work { bus?.close(); bus=null; bus=Ft232h(usb,device); "FT232H connected" }
    }
    private fun work(task: () -> String) {
        if (busy) return
        busy=true; output.text="Working…"
        thread { val result=try { task() } catch(e: Exception) { "Error: "+e.message }; runOnUiThread { busy=false; if (!isDestroyed) output.text=result else bus?.close() } }
    }
    override fun onDestroy() { unregisterReceiver(receiver); if (!busy) bus?.close(); super.onDestroy() }
}
