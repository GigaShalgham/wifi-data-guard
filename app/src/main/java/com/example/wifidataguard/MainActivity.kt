package com.example.wifidataguard

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.Html
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
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
    private lateinit var tvUsageBig: TextView
    private lateinit var tvUsageSub: TextView
    private lateinit var tvStatusTitle: TextView
    private lateinit var pbUsage: ProgressBar
    private lateinit var spUnlock: Spinner
    private lateinit var btnUnlock: Button

    private var fa = false

    // guards against programmatic listener echo (switch / checkbox / spinner)
    private var suppressSw = false
    private var suppressHard = false
    private var suppressSpinner = false

    private var uiTickCount = 0
    private var cachedChecklist = ""

    private val unlockOptions = intArrayOf(5, 15, 30, 60, 0)   // minutes; 0 = until period end

    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiTick = object : Runnable {
        override fun run() { refreshUi(); uiHandler.postDelayed(this, 1_000) }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { refreshUi() }

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) completeEnable()
        else { setMonitorSwitch(false); toast(tr(T.vpnRefused)) }
    }

    private fun lang() = if (fa) "fa" else "en"
    private fun tr(m: Map<String, String>): String = m[lang()] ?: m["en"] ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fa = Prefs.lang(this) == "fa"
        setContentView(R.layout.activity_main)

        etLimit       = findViewById(R.id.etLimit)
        cbMonthly     = findViewById(R.id.cbMonthly)
        cbHard        = findViewById(R.id.cbHard)
        swMonitor     = findViewById(R.id.swMonitor)
        tvStatus      = findViewById(R.id.tvStatus)
        tvUsageBig    = findViewById(R.id.tvUsageBig)
        tvUsageSub    = findViewById(R.id.tvUsageSub)
        tvStatusTitle = findViewById(R.id.tvStatusTitle)
        pbUsage       = findViewById(R.id.pbUsage)
        spUnlock      = findViewById(R.id.spUnlock)
        btnUnlock     = findViewById(R.id.btnUnlock)

        val limMb = Prefs.limitBytes(this) / (1024 * 1024)
        if (limMb > 0) etLimit.setText(limMb.toString())
        cbMonthly.isChecked = Prefs.monthlyReset(this)
        cbHard.isChecked    = Prefs.hardMode(this)
        swMonitor.isChecked = Prefs.monitoring(this)

        // unlock-duration spinner
        val labels = unlockOptions.map { m ->
            if (m == 0) tr(T.untilPeriod)
            else tr(T.minutesFmt).replace("%d", m.toString())
        }
        val adapter = ArrayAdapter(this, R.layout.spinner_item, labels)
        adapter.setDropDownViewResource(R.layout.spinner_item)
        spUnlock.adapter = adapter
        val savedIdx = unlockOptions.indexOf(Prefs.unlockMinutes(this)).let { if (it < 0) 0 else it }
        spUnlock.setSelection(savedIdx)
        spUnlock.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (suppressSpinner) return
                if (Prefs.monitoring(this@MainActivity)) {
                    // guard is armed: changing the unlock duration needs the PIN
                    revertSpinner()
                    guarded(tr(T.unlockDur)) { applySpinner(pos) }
                } else applySpinner(pos)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.btnLang).setOnClickListener {
            fa = !fa
            Prefs.setLang(this, if (fa) "fa" else "en")
            applyTexts(); refreshUi()
        }

        cbHard.setOnCheckedChangeListener { _, checked ->
            if (suppressHard) return@setOnCheckedChangeListener
            if (Prefs.monitoring(this) || WatchdogService.latched) {
                // FIX: enforcement mode is a security setting -> PIN-gated while armed
                setHardChecked(!checked)
                guarded(if (checked) tr(T.hardOn) else tr(T.hardOff)) {
                    setHardChecked(checked)
                    Prefs.setHardMode(this, checked)
                    immediateReevaluate()
                }
            } else {
                Prefs.setHardMode(this, checked)
                immediateReevaluate()
            }
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
        findViewById<Button>(R.id.btnCloud).setOnClickListener  { showCloudDialog() }

        swMonitor.setOnCheckedChangeListener { _, checked ->
            if (suppressSw) return@setOnCheckedChangeListener
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

    // ================= texts =================

    private object T {
        val vpnRefused = mapOf("en" to "VPN consent refused", "fa" to "مجوز VPN رد شد")
        val save    = mapOf("en" to "Change limit", "fa" to "تغییر حد مصرف")
        val reset   = mapOf("en" to "Reset counter", "fa" to "صفر کردن شمارنده")
        val turnOff = mapOf("en" to "Turn OFF the guard", "fa" to "خاموش کردن محافظ")
        val unlock  = mapOf("en" to "Unlock", "fa" to "باز کردن قفل")
        val unlockDur = mapOf("en" to "Change unlock duration", "fa" to "تغییر مدت قفل باز")
        val hardOn  = mapOf("en" to "Enable hard mode", "fa" to "روشن کردن حالت سخت")
        val hardOff = mapOf("en" to "Disable hard mode", "fa" to "خاموش کردن حالت سخت")

        val mapSave    = mapOf("en" to "Save limit", "fa" to "ذخیره حد")
        val mapReset   = mapOf("en" to "Reset usage counter", "fa" to "صفر کردن شمارنده")
        val mapUnlock  = mapOf("en" to "Unlock (PIN)", "fa" to "باز کردن قفل (رمز)")
        val mapGrant   = mapOf("en" to "Fix permissions", "fa" to "اصلاح مجوزها")
        val mapPin     = mapOf("en" to "Set / change PIN", "fa" to "تنظیم رمز")
        val mapLogs    = mapOf("en" to "View logs", "fa" to "مشاهده لاگ")
        val mapOwner   = mapOf("en" to "Device-owner guide (adb)", "fa" to "راهنمای Device Owner")
        val mapCloud   = mapOf("en" to "☁ Cloud pairing", "fa" to "☁ اتصال به ابر والد")
        val mapMonthly = mapOf("en" to "Monthly reset", "fa" to "ریست ماهانه")
        val mapHard    = mapOf("en" to "Hard mode (VPN blocks ALL internet)",
            "fa" to "حالت سخت (قطع کل اینترنت با VPN)")
        val mapEnforce = mapOf("en" to "Enforce limit", "fa" to "اجرا و نظارت")
        val mapHint    = mapOf("en" to "Limit in MB (e.g. 750)", "fa" to "حد به مگابایت (مثلا ۷۵۰)")
        val minutesFmt = mapOf("en" to "%d minutes", "fa" to "‏%d دقیقه")
        val untilPeriod= mapOf("en" to "Until period end", "fa" to "تا پایان دوره")
        val usage      = mapOf("en" to "USAGE", "fa" to "مصرف")
        val rearm      = mapOf("en" to "Re-arms in %s ⏳", "fa" to "قفل مجدد در %s ⏳")
        val blocked    = mapOf("en" to "BLOCKED", "fa" to "قفل شده")
        val grace      = mapOf("en" to "FREE TIME", "fa" to "مهلت آزاد")
        val lockNow    = mapOf("en" to "Lock again now", "fa" to "قفل کن همین حالا")
        val unlockedMsg= mapOf("en" to "Unlocked", "fa" to "آزاد شد")
        val protected_ = mapOf("en" to "Protected", "fa" to "محافظت فعال")
        val noLimit    = mapOf("en" to "no limit set", "fa" to "حدی تعیین نشده")
        val checklist  = mapOf("en" to "Checklist:", "fa" to "چک‌لیست:")
        val pinLbl     = mapOf("en" to "PIN", "fa" to "رمز")
        val usageAccLbl= mapOf("en" to "Usage access", "fa" to "Usage access")
        val ownerLbl   = mapOf("en" to "Device owner", "fa" to "Device Owner")
        val enforceLbl = mapOf("en" to "Enforce ON", "fa" to "نظارت روشن")
        val cloudLbl   = mapOf("en" to "Cloud linked", "fa" to "متصل به ابر")
    }

    private fun applyTexts() {
        findViewById<Button>(R.id.btnSave).text   = tr(T.mapSave)
        findViewById<Button>(R.id.btnReset).text  = tr(T.mapReset)
        findViewById<Button>(R.id.btnGrant).text  = tr(T.mapGrant)
        findViewById<Button>(R.id.btnPin).text    = tr(T.mapPin)
        findViewById<Button>(R.id.btnLogs).text   = tr(T.mapLogs)
        findViewById<Button>(R.id.btnOwner).text  = tr(T.mapOwner)
        findViewById<Button>(R.id.btnCloud).text  = tr(T.mapCloud)
        findViewById<Button>(R.id.btnLang).text   = if (fa) "🌐 EN" else "🌐 فارسی"
        cbMonthly.text = tr(T.mapMonthly)
        cbHard.text    = tr(T.mapHard)
        swMonitor.text = tr(T.mapEnforce)
        etLimit.hint   = tr(T.mapHint)
        tvStatusTitle.text = tr(T.usage)
        rebuildSpinnerLabels()
    }

    // ---- programmatic-change helpers (prevent listener echo storms) ----

    private fun setMonitorSwitch(checked: Boolean) {
        suppressSw = true
        swMonitor.isChecked = checked
        suppressSw = false
    }

    private fun setHardChecked(checked: Boolean) {
        suppressHard = true
        cbHard.isChecked = checked
        suppressHard = false
    }

    private fun applySpinner(pos: Int) {
        Prefs.setUnlockMinutes(this, unlockOptions[pos])
        spUnlock.setSelection(pos)
        suppressSpinnerBriefly()
    }

    private fun revertSpinner() {
        val idx = unlockOptions.indexOf(Prefs.unlockMinutes(this)).let { if (it < 0) 0 else it }
        spUnlock.setSelection(idx)
        suppressSpinnerBriefly()
    }

    private fun rebuildSpinnerLabels() {
        val labels = unlockOptions.map { m ->
            if (m == 0) tr(T.untilPeriod)
            else tr(T.minutesFmt).replace("%d", m.toString())
        }
        val ad = spUnlock.adapter as? ArrayAdapter<String> ?: return
        ad.clear(); ad.addAll(labels); ad.notifyDataSetChanged()
        val idx = unlockOptions.indexOf(Prefs.unlockMinutes(this)).let { if (it < 0) 0 else it }
        spUnlock.setSelection(idx)
        suppressSpinnerBriefly()
    }

    /** Spinner selection callbacks fire asynchronously (next layout pass) —
     *  stay suppressed long enough to swallow that echo. */
    private fun suppressSpinnerBriefly() {
        suppressSpinner = true
        uiHandler.postDelayed({ suppressSpinner = false }, 300)
    }

    // ================= PIN gate =================

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

    // ================= actions =================

    private fun save() {
        // accept Persian / Arabic-Indic digits from fa keyboards
        val mb = normalizeDigits(etLimit.text.toString()).trim().toDoubleOrNull() ?: 0.0
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
            setMonitorSwitch(false)
            toast(if (fa) "اول رمز بذار" else "First set a PIN")
            askNewPinDouble(if (fa) "ساخت رمز" else "Create PIN"); return
        }
        if (!DataStats.hasUsageAccess(this)) {
            setMonitorSwitch(false)
            toast(if (fa) "Usage access لازم است" else "Grant 'Usage access'")
            DataStats.openUsageAccessScreen(this); return
        }
        if (Prefs.limitBytes(this) <= 0) {
            setMonitorSwitch(false)
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
        OwnerEnforcer.lockdown(this)   // FIX: wire device-owner hardening (no-op otherwise)
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
        val graceLeft = (Prefs.graceUntil(this) - System.currentTimeMillis()) / 1000
        if (graceLeft > 0) {
            // clicking during grace = lock again NOW
            Prefs.setGraceUntil(this, 0L)
            Logger.d(this, "grace cancelled by user -> re-arm immediately")
            immediateReevaluate()
            toast(tr(T.lockNow))
            refreshUi(); return
        }
        guarded(tr(T.unlock)) {
            // FIX: keep the latch and do NOT reset the counter — the grace window
            // merely suspends enforcement, so the lock re-arms the moment it
            // expires (or when the user cancels it with "lock again now").
            val mins = Prefs.unlockMinutes(this)
            val until = if (mins <= 0) endOfPeriod(this)
            else System.currentTimeMillis() + mins * 60_000L
            Prefs.setGraceUntil(this, until)
            Logger.d(this, "UNLOCK: grace ${mins}min (0=period), latch kept")
            immediateReevaluate()
            toast(tr(T.unlockedMsg))
            refreshUi()
        }
    }
    private fun endOfPeriod(c: android.content.Context): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        if (Prefs.monthlyReset(c)) {
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            cal.add(java.util.Calendar.MONTH, 1)
        } else {
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
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

    // ================= cloud pairing =================

    private fun showCloudDialog() {
        val pad = (18 * resources.displayMetrics.density).toInt()
        if (CloudLink.paired(this)) {
            val mins = (System.currentTimeMillis() - Prefs.cloudLastSync(this)) / 60000
            val status = if (fa)
                "✅ متصل به ابر\nنام: ${Prefs.cloudName(this)}\n" +
                "آخرین همگام‌سازی: " + (if (mins < 1) "همین حالا" else "$mins دقیقه پیش") + "\n\n" +
                "والد می‌تواند از داشبورد وب این دستگاه را قفل/باز کند و\nحد مصرف و تنظیمات را تغییر دهد."
            else
                "✅ Linked to the parent dashboard\nName: ${Prefs.cloudName(this)}\n" +
                "Last sync: " + (if (mins < 1) "just now" else "$mins min ago") + "\n\n" +
                "The parent can lock/unlock this device and change its\nlimit and settings from the web dashboard."
            val tv = TextView(this).apply { text = status; setPadding(pad, pad, pad, pad) }
            AlertDialog.Builder(this)
                .setTitle(if (fa) "اتصال ابری" else "Cloud pairing")
                .setView(tv)
                .setPositiveButton(if (fa) "بستن" else "Close", null)
                .setNegativeButton(if (fa) "قطع اتصال" else "Unpair") { _, _ ->
                    guarded(if (fa) "قطع اتصال ابری" else "Unpair from cloud") {
                        CloudLink.unpair(this)
                        toast(if (fa) "اتصال ابری قطع شد — حالت محلی" else "Cloud unlinked — local-only mode")
                        refreshUi()
                    }
                }
                .show()
            return
        }

        // --- not paired: pairing form ---
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0)
        }
        val hintTv = TextView(this).apply {
            text = if (fa)
                "در داشبورد والد (مرورگر) دکمه‌ی «Generate pairing code» را بزن و کد ۶ رقمی را اینجا وارد کن."
            else
                "In the parent dashboard (browser) press 'Generate pairing code', then enter the 6-digit code here."
            textSize = 13f; setPadding(0, 0, 0, pad / 2)
        }
        val etCode = EditText(this).apply {
            setHint(if (fa) "کد ۶ رقمی" else "6-digit code")
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
        }
        val etName = EditText(this).apply {
            setHint(if (fa) "نام دستگاه" else "Device name")
            setText(Build.MODEL)
        }
        wrap.addView(hintTv); wrap.addView(etCode); wrap.addView(etName)

        fun tryPair(dlg: AlertDialog) {
            val code = normalizeDigits(etCode.text.toString()).trim()
            val name = etName.text.toString().trim().ifEmpty { Build.MODEL }
            if (!Regex("^\\d{6}$").matches(code)) {
                toast(if (fa) "کد باید ۶ رقم باشد" else "Code must be 6 digits"); return
            }
            dlg.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
            CloudLink.pair(this, code, name) { ok, msg ->
                if (ok) {
                    toast(if (fa) "☁ متصل شد! والد الان می‌تواند کنترل کند" else "☁ Linked! The parent can control this device now")
                    refreshUi()
                } else {
                    toast((if (fa) "خطا: " else "Error: ") + msg)
                    dlg.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                }
            }
        }

        val dlg = AlertDialog.Builder(this)
            .setTitle(if (fa) "اتصال ابری" else "Cloud pairing")
            .setView(wrap)
            .setPositiveButton(if (fa) "اتصال" else "Pair", null)
            .setNegativeButton(if (fa) "انصراف" else "Cancel", null)
            .show()
        // override so the dialog stays open while pairing runs
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { tryPair(dlg) }
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

    // ================= UI =================

    private fun fmt(sec: Long): String =
        String.format(java.util.Locale.US, "%d:%02d", sec / 60, sec % 60)

    private fun refreshUi() {
        val limit = Prefs.limitBytes(this)
        val used = LiveCounter.currentBytes()
        val graceLeft = (Prefs.graceUntil(this) - System.currentTimeMillis()) / 1000

        // big usage number
        tvUsageBig.text = humanize(used)
        tvUsageSub.text = if (limit > 0)
            "/ ${humanize(limit)} • ${pct(used, limit)}%"
        else tr(T.noLimit)

        // progress bar + color
        if (limit > 0) {
            val p = ((used * 100.0 / limit).toInt()).coerceIn(0, 100)
            pbUsage.progress = p
            pbUsage.progressTintList = android.content.res.ColorStateList.valueOf(
                when {
                    p >= 100 -> Color.parseColor("#E53935")
                    p >= 80  -> Color.parseColor("#FB8C00")
                    else     -> Color.parseColor("#43A047")
                })
        }

        // unlock button + status line
        val statusLine = when {
            graceLeft > 0 -> {
                btnUnlock.text = tr(T.rearm).replace("%s", fmt(graceLeft))
                btnUnlock.backgroundTintList = android.content.res.ColorStateList
                    .valueOf(Color.parseColor("#FB8C00"))
                "⏳ ${tr(T.grace)}"
            }
            WatchdogService.latched -> {
                btnUnlock.text = tr(T.mapUnlock)
                btnUnlock.backgroundTintList = android.content.res.ColorStateList
                    .valueOf(Color.parseColor("#43A047"))
                "🔒 ${tr(T.blocked)}"
            }
            else -> {
                btnUnlock.text = tr(T.mapUnlock)
                btnUnlock.backgroundTintList = android.content.res.ColorStateList
                    .valueOf(Color.parseColor("#43A047"))
                "✅ ${tr(T.protected_)}"
            }
        }

        // binder-heavy checks (usage access / device owner) -> refresh every ~5 ticks
        uiTickCount++
        if (uiTickCount % 5 == 1) {
            cachedChecklist = buildChecklist()
            // keep the hard-mode checkbox in sync with cloud-pushed config
            setHardChecked(Prefs.hardMode(this))
        }

        tvStatus.text = cachedChecklist + "\n" + statusLine
    }

    private fun buildChecklist(): String = buildString {
        append("\n")
        append(tr(T.checklist)).append("\n")
        append(mark(Prefs.pinSet(this@MainActivity))).append(tr(T.pinLbl)).append("\n")
        append(mark(DataStats.hasUsageAccess(this@MainActivity)))
            .append(tr(T.usageAccLbl)).append("\n")
        append(mark(OwnerEnforcer.isDeviceOwner(this@MainActivity)))
            .append(tr(T.ownerLbl)).append("\n")
        append(mark(Prefs.monitoring(this@MainActivity)))
            .append(tr(T.enforceLbl)).append("\n")
        append(mark(CloudLink.paired(this@MainActivity))).append(tr(T.cloudLbl))
    }

    private fun pct(used: Long, limit: Long): Int =
        if (limit <= 0) 0 else ((used * 100.0 / limit).toInt()).coerceIn(0, 100)

    private fun mark(ok: Boolean) = if (ok) "☑ " else "☐ "

    private fun normalizeDigits(s: String): String = buildString {
        for (ch in s) append(when (ch) {
            in '۰'..'۹' -> '0' + (ch - '۰')   // Persian digits
            in '٠'..'٩' -> '0' + (ch - '٠')   // Arabic-Indic digits
            else -> ch
        })
    }

    private fun humanize(b: Long) =
        if (b >= 1073741824) String.format(java.util.Locale.US, "%.2f GB", b / 1073741824.0)
        else String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}