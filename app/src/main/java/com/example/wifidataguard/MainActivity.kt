package com.example.wifidataguard

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.Html
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.roundToLong

class MainActivity : AppCompatActivity() {

    private lateinit var etLimit: EditText
    private lateinit var cbMonthly: CheckBox
    private lateinit var cbHard: CheckBox
    private lateinit var swMonitor: Switch
    private lateinit var tvStatus: TextView
    private lateinit var pbUsage: ProgressBar

    private var fa = false

    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiTick = object : Runnable {
        override fun run() { refreshUi(); uiHandler.postDelayed(this, 1_000) }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { refreshUi() }

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) completeEnable()
        else {
            swMonitor.isChecked = false
            toast(tr(T.vpnRefused))
        }
    }

    private fun lang() = if (fa) "fa" else "en"
    private fun tr(m: Map<String, String>): String = m[lang()] ?: m["en"] ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fa = Prefs.lang(this) == "fa"
        setContentView(R.layout.activity_main)

        etLimit   = findViewById(R.id.etLimit)
        cbMonthly = findViewById(R.id.cbMonthly)
        cbHard    = findViewById(R.id.cbHard)
        swMonitor = findViewById(R.id.swMonitor)
        tvStatus  = findViewById(R.id.tvStatus)
        pbUsage   = findViewById(R.id.pbUsage)

        val limMb = Prefs.limitBytes(this) / (1024 * 1024)
        if (limMb > 0) etLimit.setText(limMb.toString())
        cbMonthly.isChecked = Prefs.monthlyReset(this)
        cbHard.isChecked    = Prefs.hardMode(this)
        swMonitor.isChecked = Prefs.monitoring(this)

        findViewById<Button>(R.id.btnLang).setOnClickListener {
            fa = !fa
            Prefs.setLang(this, if (fa) "fa" else "en")
            applyTexts(); refreshUi()
        }

        cbHard.setOnCheckedChangeListener { _, checked ->
            Prefs.setHardMode(this, checked)
            immediateReevaluate()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            guarded(tr(T.save)) { save() } }
        findViewById<Button>(R.id.btnReset).setOnClickListener {
            guarded(tr(T.reset)) { resetCounter() } }
        findViewById<Button>(R.id.btnUnlock).setOnClickListener { unlockFlow() }
        findViewById<Button>(R.id.btnGrant).setOnClickListener  { fixPermissions() }
        findViewById<Button>(R.id.btnPin).setOnClickListener    { pinManageFlow() }
        findViewById<Button>(R.id.btnLogs).setOnClickListener   { showLogs() }
        findViewById<Button>(R.id.btnOwner).setOnClickListener  { showOwnerGuide() }

        swMonitor.setOnCheckedChangeListener { _, checked ->
            if (checked) beginEnable()
            else guarded(tr(T.turnOff)) { disableEverything() }
        }

        applyTexts()
    }

    override fun onResume() { super.onResume(); refreshUi(); uiTick.run() }
    override fun onPause()  { super.onPause(); uiHandler.removeCallbacks(uiTick) }

    private fun immediateReevaluate() {
        ContextCompat.startForegroundService(this,
            Intent(this, WatchdogService::class.java))
        refreshUi()
    }

    private object T {
        val vpnRefused = mapOf("en" to "VPN consent refused", "fa" to "مجوز VPN رد شد")
        val save    = mapOf("en" to "Change limit", "fa" to "تغییر حد مصرف")
        val reset   = mapOf("en" to "Reset counter", "fa" to "صفر کردن شمارنده")
        val turnOff = mapOf("en" to "Turn OFF the guard", "fa" to "خاموش کردن محافظ")
        val unlock  = mapOf("en" to "Unlock", "fa" to "باز کردن قفل")

        val mapSave    = mapOf("en" to "Save limit", "fa" to "ذخیره حد")
        val mapReset   = mapOf("en" to "Reset usage counter", "fa" to "صفر کردن شمارنده")
        val mapUnlock  = mapOf("en" to "Unlock (PIN)", "fa" to "باز کردن قفل (رمز)")
        val mapGrant   = mapOf("en" to "Fix permissions", "fa" to "اصلاح مجوزها")
        val mapPin     = mapOf("en" to "Set / change PIN", "fa" to "تنظیم رمز")
        val mapLogs    = mapOf("en" to "View logs", "fa" to "مشاهده لاگ")
        val mapOwner   = mapOf("en" to "Device-owner guide (adb)", "fa" to "راهنمای Device Owner")
        val mapMonthly = mapOf("en" to "Monthly reset", "fa" to "ریست ماهانه")
        val mapHard    = mapOf("en" to "Hard mode (VPN blocks ALL internet)",
            "fa" to "حالت سخت (قطع کل اینترنت با VPN)")
        val mapEnforce = mapOf("en" to "Enforce limit", "fa" to "اجرا و نظارت")
        val mapHint    = mapOf("en" to "Limit in MB (e.g. 750)", "fa" to "حد به مگابایت (مثلا ۷۵۰)")
    }

    private fun applyTexts() {
        findViewById<Button>(R.id.btnSave).text   = tr(T.mapSave)
        findViewById<Button>(R.id.btnReset).text  = tr(T.mapReset)
        findViewById<Button>(R.id.btnGrant).text  = tr(T.mapGrant)
        findViewById<Button>(R.id.btnPin).text    = tr(T.mapPin)
        findViewById<Button>(R.id.btnLogs).text   = tr(T.mapLogs)
        findViewById<Button>(R.id.btnOwner).text  = tr(T.mapOwner)
        findViewById<Button>(R.id.btnLang).text   = if (fa) "🌐 EN" else "🌐 فارسی"
        cbMonthly.text = tr(T.mapMonthly)
        cbHard.text    = tr(T.mapHard)
        swMonitor.text = tr(T.mapEnforce)
        etLimit.hint   = tr(T.mapHint)
    }

    // ---------- PIN gate ----------

    private fun guarded(actionTitle: String, action: () -> Unit) {
        if (!Prefs.pinSet(this)) {
            if (!OwnerEnforcer.isDeviceOwner(this) && !Prefs.monitoring(this)) {
                action(); return
            }
            askNewPinDouble(actionTitle, action); return
        }
        askPin("$actionTitle — ${if (fa) "رمز را وارد کن" else "enter PIN"}") { action() }
    }

    private fun askPin(title: String, onOk: () -> Unit) {
        val cd = Prefs.pinCooldownLeftMs(this)
        if (cd > 0) { toast("⏳ ${cd / 1000}s"); return }
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_pin, null)
        v.findViewById<TextView>(R.id.tvMsg).text = title
        val et = v.findViewById<EditText>(R.id.etPin)
        AlertDialog.Builder(this)
            .setTitle(if (fa) "امنیت" else "Security")
            .setView(v)
            .setPositiveButton("OK") { _, _ ->
                if (Prefs.checkPin(this, et.text.toString())) onOk()
                else toast(if (fa) "رمز اشتباه" else "Wrong PIN")
            }
            .setNegativeButton(if (fa) "انصراف" else "Cancel", null)
            .show()
    }

    private fun askNewPinDouble(title: String, after: (() -> Unit)? = null) {
        val a = LayoutInflater.from(this).inflate(R.layout.dialog_pin, null)
        a.findViewById<TextView>(R.id.tvMsg).text =
            if (fa) "$title — رمز جدید (حداقل ۴ رقم)"
            else "$title — choose new PIN (min 4 digits)"
        val eta = a.findViewById<EditText>(R.id.etPin)
        AlertDialog.Builder(this)
            .setTitle(if (fa) "امنیت" else "Security").setView(a)
            .setPositiveButton(if (fa) "بعدی" else "Next") { _, _ ->
                val p1 = eta.text.toString()
                if (p1.length < 4) {
                    toast(if (fa) "حداقل ۴ رقم" else "Min 4 digits")
                    return@setPositiveButton
                }
                val b = LayoutInflater.from(this).inflate(R.layout.dialog_pin, null)
                b.findViewById<TextView>(R.id.tvMsg).text =
                    if (fa) "تکرار رمز" else "Repeat the PIN"
                val etb = b.findViewById<EditText>(R.id.etPin)
                AlertDialog.Builder(this)
                    .setTitle(if (fa) "تایید" else "Confirm").setView(b)
                    .setPositiveButton(if (fa) "ذخیره" else "Save") { _, _ ->
                        if (etb.text.toString() == p1) {
                            Prefs.setPin(this, p1)
                            toast(if (fa) "رمز ذخیره شد" else "PIN saved")
                            after?.invoke()
                        } else toast(if (fa) "رمزها یکی نیستند" else "Pins differ")
                    }
                    .setNegativeButton(if (fa) "انصراف" else "Cancel", null).show()
            }
            .setNegativeButton(if (fa) "انصراف" else "Cancel", null).show()
    }

    private fun pinManageFlow() {
        if (Prefs.pinSet(this)) askPin(if (fa) "رمز فعلی" else "Current PIN") {
            askNewPinDouble(if (fa) "تغییر رمز" else "Change PIN")
        } else askNewPinDouble(if (fa) "ساخت رمز" else "Create PIN")
    }

    // ---------- actions ----------

    private fun save() {
        val mb = etLimit.text.toString().trim().toDoubleOrNull() ?: 0.0
        if (mb <= 0) { toast(if (fa) "عدد معتبر بده" else "Enter a limit in MB"); return }
        Prefs.setLimitBytes(this, (mb * 1024.0 * 1024.0).roundToLong())
        Prefs.setMonthlyReset(this, cbMonthly.isChecked)
        immediateReevaluate()
        toast(if (fa) "ذخیره شد" else "Saved!")
    }

    private fun resetCounter() {
        DataStats.resetBaseline(this)
        LiveCounter.resetToZero()
        WatchdogService.unlatch(this)
        Prefs.setGraceUntil(this, 0L)
        immediateReevaluate()
        toast(if (fa) "شمارنده صفر شد" else "Counter reset")
    }

    private fun beginEnable() {
        if (!Prefs.pinSet(this)) {
            swMonitor.isChecked = false
            toast(if (fa) "اول رمز بذار" else "First set a PIN")
            askNewPinDouble(if (fa) "ساخت رمز" else "Create PIN"); return
        }
        if (!DataStats.hasUsageAccess(this)) {
            swMonitor.isChecked = false
            toast(if (fa) "Usage access لازم است" else "Grant 'Usage access'")
            DataStats.openUsageAccessScreen(this); return
        }
        if (Prefs.limitBytes(this) <= 0) {
            swMonitor.isChecked = false
            toast(if (fa) "اول حد بذار" else "Set a limit first"); return
        }
        if (!OwnerEnforcer.isDeviceOwner(this)) {
            val prep = VpnService.prepare(this)
            if (prep != null) { vpnLauncher.launch(prep); return }
        }
        completeEnable()
    }

    private fun completeEnable() {
        Prefs.setMonitoring(this, true)
        askIgnoreBatteryOptimization()
        ContextCompat.startForegroundService(this,
            Intent(this, WatchdogService::class.java))
        toast(if (fa) "محافظ فعال شد 🛡️" else "Guard armed 🛡️")
        refreshUi()
    }

    private fun disableEverything() {
        Prefs.setMonitoring(this, false)
        WatchdogService.unlatch(this)
        Prefs.setGraceUntil(this, 0L)
        OwnerEnforcer.relax(this)
        OwnerEnforcer.trySilentWifiOn(this)
        toast(if (fa) "محافظ خاموش شد" else "Guard disabled")
        refreshUi()
    }

    private fun unlockFlow() {
        guarded(tr(T.unlock)) {
            WatchdogService.unlatch(this)
            DataStats.resetBaseline(this)
            LiveCounter.resetToZero()
            Prefs.setGraceUntil(this, System.currentTimeMillis() + 5 * 60 * 1000L)
            Logger.d(this, "UNLOCK: unlatch + rebaseline + grace 5min")
            immediateReevaluate()
            toast(if (fa) "آزاد شد — تایمر روی دکمه" else "Unlocked — timer on button")
            refreshUi()
        }
    }

    private fun fixPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)); return
        }
        if (!DataStats.hasUsageAccess(this)) {
            DataStats.openUsageAccessScreen(this); return
        }
        if (!Prefs.pinSet(this)) {
            askNewPinDouble(if (fa) "ساخت رمز" else "Create PIN"); return
        }
        toast(if (fa) "مجوزها آماده ✅" else "Permissions ready ✅")
    }

    private fun askIgnoreBatteryOptimization() {
        val pm = getSystemService(PowerManager::class.java)
        @Suppress("DEPRECATION")
        if (!pm.isIgnoringBatteryOptimizations(packageName)) try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:$packageName")))
        } catch (_: Exception) {}
    }

    private fun showOwnerGuide() {
        val cmd = "adb shell dpm set-device-owner $packageName/.AdminReceiver"
        val cmg = getSystemService(ClipboardManager::class.java)
        cmg.setPrimaryClip(ClipData.newPlainText("cmd", cmd))
        val msg = if (fa) """
            <b>یک‌بار، از کامپیوتر:</b><br>
            ۱. روشن کردن USB debugging در گوشی<br>
            ۲. خروج همه اکانت‌های گوگل<br>
            ۳. اجرای دستور:<br><code>$cmd</code>
        """.trimIndent() else """
            <b>One-time, from a computer:</b><br>
            1. Enable USB debugging<br>
            2. Remove all Google accounts<br>
            3. Run:<br><code>$cmd</code>
        """.trimIndent()
        AlertDialog.Builder(this).setTitle("Device Owner")
            .setMessage(Html.fromHtml(msg, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton("OK", null).show()
    }

    private fun showLogs() {
        val text = Logger.readAll(this)
        if (text.isBlank()) { toast(if (fa) "لاگ خالی است" else "Log empty"); return }
        val sv = android.widget.ScrollView(this)
        val tv = TextView(this).apply {
            setText(text); typeface = android.graphics.Typeface.MONOSPACE
            textSize = 11f; setPadding(24, 24, 24, 24)
        }
        sv.addView(tv)
        AlertDialog.Builder(this)
            .setTitle(if (fa) "لاگ" else "Guard log")
            .setView(sv)
            .setPositiveButton(if (fa) "کپی" else "Copy") { _, _ ->
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("log", text))
                toast(if (fa) "کپی شد" else "Copied")
            }
            .setNeutralButton(if (fa) "پاک" else "Clear") { _, _ ->
                Logger.clear(this); toast(if (fa) "پاک شد" else "Cleared")
            }
            .setNegativeButton(if (fa) "بستن" else "Close", null)
            .show()
    }

    // ---------- UI ----------

    private fun fmt(sec: Long): String =
        String.format(java.util.Locale.US, "%d:%02d", sec / 60, sec % 60)

    private fun refreshUi() {
        val limit = Prefs.limitBytes(this)
        val used = LiveCounter.currentBytes()
        val graceLeft = (Prefs.graceUntil(this) - System.currentTimeMillis()) / 1000

        var line = ""
        if (limit <= 0) line += if (fa) "حدی تعیین نشده\n" else "No limit set\n"
        else {
            val pct = ((used * 100.0 / limit).toInt()).coerceIn(0, 100)
            pbUsage.progress = pct
            line += (if (fa) "مصرف: " else "Usage: ") +
                    "${humanize(used)} / ${humanize(limit)} ($pct%)\n"
        }

        val btnU = findViewById<Button>(R.id.btnUnlock)
        line += when {
            graceLeft > 0 -> {
                btnU.text = if (fa) "قفل دوباره در ${fmt(graceLeft)} ⏳"
                else "Re-arms in ${fmt(graceLeft)} ⏳"
                if (fa) "وضعیت: مهلت آزاد ⏳\n" else "STATUS: grace ⏳\n"
            }
            WatchdogService.latched -> {
                btnU.text = tr(T.mapUnlock)
                if (fa) "وضعیت: قفل 🔒\n" else "STATUS: BLOCKED 🔒\n"
            }
            else -> { btnU.text = tr(T.mapUnlock); "" }
        }

        line += "\n" + (if (fa) "چک‌لیست:\n" else "Checklist:\n") +
                mark(Prefs.pinSet(this)) + (if (fa) "رمز\n" else "PIN\n") +
                mark(DataStats.hasUsageAccess(this)) +
                (if (fa) "Usage access\n" else "Usage access\n") +
                mark(OwnerEnforcer.isDeviceOwner(this)) +
                (if (fa) "Device Owner\n" else "Device owner\n") +
                mark(Prefs.monitoring(this)) +
                (if (fa) "نظارت روشن\n" else "Enforce ON\n")
        tvStatus.text = line
    }

    private fun mark(ok: Boolean) = if (ok) "[x] " else "[ ] "

    private fun humanize(b: Long) =
        if (b >= 1073741824) String.format(java.util.Locale.US, "%.2f GB", b / 1073741824.0)
        else String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}