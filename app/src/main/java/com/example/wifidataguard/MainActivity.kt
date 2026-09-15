package com.example.wifidataguard

import android.Manifest
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.BatteryManager
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
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.json.JSONArray
import java.util.TimeZone
import kotlin.math.roundToLong

class MainActivity : AppCompatActivity() {

    private lateinit var etLimit: EditText
    private lateinit var cbMonthly: CheckBox
    private lateinit var cbHard: CheckBox
    private lateinit var swMonitor: Switch
    private lateinit var tvStatus: TextView
    private lateinit var tvUsageBig: TextView
    private lateinit var tvUsageBigSub: TextView
    private lateinit var tvUsageSub: TextView
    private lateinit var heroDot: View
    private lateinit var heroHalo: View
    private lateinit var heroState: TextView
    private lateinit var btnUnlock: Button

    // tabs (spec-010)
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var tabStatus: View
    private lateinit var tabUsage: View
    private lateinit var tabParent: View
    private var parentAuthed = false          // PIN gate, once per session
    private var suppressNav = false

    // usage tab
    private lateinit var tvStatLeft: TextView
    private lateinit var tvStatLimit: TextView
    private lateinit var tvStatPeriod: TextView
    private lateinit var tvStatBattery: TextView
    private lateinit var chartHistory: HistoryBarView
    private lateinit var tvHistoryNote: TextView
    private var historyFetchInFlight = false

    // ring + count-up
    private lateinit var ringUsage: UsageRingView
    private var countedUp = false

    // unlock-duration chips (replaces the Spinner — spec-010 FR-105)
    private val chipOptions = intArrayOf(5, 15, 30, 60, 0)   // 0 = until period end
    private lateinit var chips: List<Button>

    private var fa = false

    // guards against programmatic listener echo (switch / checkbox)
    private var suppressSw = false
    private var suppressHard = false

    private var uiTickCount = 0
    private var cachedChecklist = ""

    // glass super-UI (spec-007)
    private lateinit var glass: GlassUi
    private var guardPrev: GuardStateUi.S? = null
    private var suppressNextTransition = false

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
        tvUsageBigSub = findViewById(R.id.tvUsageBigSub)
        tvUsageSub    = findViewById(R.id.tvUsageSub)
        heroDot       = findViewById(R.id.heroDot)
        heroHalo      = findViewById(R.id.heroHalo)
        heroState     = findViewById(R.id.heroState)
        btnUnlock     = findViewById(R.id.btnUnlock)

        bottomNav = findViewById(R.id.bottomNav)
        tabStatus = findViewById(R.id.tabStatus)
        tabUsage  = findViewById(R.id.tabUsage)
        tabParent = findViewById(R.id.tabParent)

        ringUsage     = findViewById(R.id.ringUsage)
        tvStatLeft    = findViewById(R.id.tvStatLeft)
        tvStatLimit   = findViewById(R.id.tvStatLimit)
        tvStatPeriod  = findViewById(R.id.tvStatPeriod)
        tvStatBattery = findViewById(R.id.tvStatBattery)
        chartHistory  = findViewById(R.id.chartHistory)
        tvHistoryNote = findViewById(R.id.tvHistoryNote)

        glass = GlassUi(this)

        val limMb = Prefs.limitBytes(this) / (1024 * 1024)
        if (limMb > 0) etLimit.setText(limMb.toString())
        cbMonthly.isChecked = Prefs.monthlyReset(this)
        cbHard.isChecked    = Prefs.hardMode(this)
        swMonitor.isChecked = Prefs.monitoring(this)

        // ---- unlock-duration chips (same PIN-gated semantics as the old Spinner) ----
        chips = listOf(
            findViewById(R.id.btnChip5), findViewById(R.id.btnChip15),
            findViewById(R.id.btnChip30), findViewById(R.id.btnChip60),
            findViewById(R.id.btnChipEnd))
        chips.forEachIndexed { i, btn ->
            btn.setOnClickListener {
                if (Prefs.monitoring(this)) {
                    renderChips()   // revert visual until the gate approves
                    guarded(tr(T.unlockDur)) { applyChip(i) }
                } else applyChip(i)
            }
        }
        renderChips()

        // ---- bottom navigation (spec-010 FR-101/102) ----
        bottomNav.setOnItemSelectedListener { item: MenuItem ->
            if (suppressNav) return@setOnItemSelectedListener true
            when (item.itemId) {
                R.id.nav_status -> { showTab(tabStatus); true }
                R.id.nav_usage  -> { showTab(tabUsage); onUsageEntered(false); true }
                R.id.nav_parent -> {
                    // returning false keeps the item unselected when gated —
                    // a failed/cancelled PIN leaves the user where they were
                    if (parentAuthed) { showTab(tabParent); true }
                    else { maybeGateParent(); false }
                }
                else -> false
            }
        }

        findViewById<Button>(R.id.btnLang).setOnClickListener {
            fa = !fa
            Prefs.setLang(this, if (fa) "fa" else "en")
            applyTexts(); refreshUi()
        }

        cbHard.setOnCheckedChangeListener { _, checked ->
            if (suppressHard) return@setOnCheckedChangeListener
            if (Prefs.monitoring(this) || WatchdogService.latched) {
                // enforcement mode is a security setting -> PIN-gated while armed
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
        findViewById<Button>(R.id.btnHistRefresh).setOnClickListener {
            onUsageEntered(true)
        }

        // 🧪 test panel — only exists in the .test build, hidden otherwise
        val btnTest = findViewById<Button>(R.id.btnTest)
        if (TestMode.active) btnTest.setOnClickListener { showTestPanel() }
        else btnTest.visibility = View.GONE

        swMonitor.setOnCheckedChangeListener { _, checked ->
            if (suppressSw) return@setOnCheckedChangeListener
            if (checked) beginEnable()
            else guarded(tr(T.turnOff)) { disableEverything() }
        }

        applyTexts()

        // entrance-once (spec-007): staggered slide-fade on create; the 1 s tick
        // never replays it. Element type pinned to View — mixed-type inference
        // is the v1.3.5 launch crash (spec-009), never again.
        glass.entrance(listOf(
            findViewById<View>(R.id.headerRow),
            findViewById<View>(R.id.cardHero),
            btnUnlock as View))
    }

    override fun onResume() {
        super.onResume()
        glass.resumed = true
        refreshUi(); uiTick.run()
        glass.startHalo(heroHalo)
    }
    override fun onPause() {
        super.onPause()
        glass.resumed = false
        uiHandler.removeCallbacks(uiTick); glass.stopHalo()
    }
    override fun onDestroy() {
        super.onDestroy()
        if (::glass.isInitialized) glass.shutdown()
    }

    private fun immediateReevaluate() {
        ContextCompat.startForegroundService(this,
            Intent(this, WatchdogService::class.java))
        refreshUi()
    }

    // ================= tabs (spec-010) =================

    private fun showTab(tab: View) {
        for (t in listOf(tabStatus, tabUsage, tabParent)) {
            if (t !== tab) t.visibility = View.GONE
        }
        if (tab.visibility == View.VISIBLE) return
        if (glass.animationsEnabled()) {
            tab.alpha = 0f
            tab.visibility = View.VISIBLE
            tab.animate().alpha(1f).setDuration(160)
                .withEndAction { tab.alpha = 1f }.start()
        } else {
            tab.visibility = View.VISIBLE
        }
    }

    /** PIN gate for the Parent tab — same rules as every guarded() action:
     *  no PIN + unarmed + not owner -> straight in; PIN set -> ask; the
     *  nav item never selects until the gate passes. */
    private fun maybeGateParent() {
        if (!Prefs.pinSet(this)) {
            if (!OwnerEnforcer.isDeviceOwner(this) && !Prefs.monitoring(this)) {
                switchToParent(); return
            }
            askNewPinDouble(tr(T.parentGate)) { switchToParent() }
            return
        }
        askPin("${tr(T.parentGate)} — ${if (fa) "رمز را وارد کن" else "enter PIN"}") {
            switchToParent()
        }
    }

    private fun switchToParent() {
        parentAuthed = true
        suppressNav = true   // armed BEFORE the programmatic selection (spec-009 lesson)
        bottomNav.selectedItemId = R.id.nav_parent
        suppressNav = false
        showTab(tabParent)
    }

    // ================= usage history (spec-010, cosmetic) =================

    /** Renders the cache, then refreshes in the background when stale. */
    private fun onUsageEntered(manual: Boolean) {
        renderHistory()
        if (!CloudLink.paired(this)) return
        val age = System.currentTimeMillis() - Prefs.historyCacheAt(this)
        if (manual || (!historyFetchInFlight &&
                (Prefs.historyCacheAt(this) == 0L || age > 5 * 60_000L))) {
            historyFetchInFlight = true
            CloudLink.fetchHistory(this) { ok, _ ->
                historyFetchInFlight = false
                if (ok) renderHistory()
            }
        }
    }

    private fun renderHistory() {
        val tz = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        val todayDay = (System.currentTimeMillis() + tz * 60_000L).floorDiv(86_400_000L)
        val arr = try { JSONArray(Prefs.historyCache(this)) } catch (_: Exception) { null }
        val days = mutableListOf<HistoryBarView.Day>()
        var anyData = false
        if (arr != null) for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val day = o.optLong("day", Long.MIN_VALUE)
            if (day == Long.MIN_VALUE) continue
            val ub = if (o.has("used_bytes") && !o.isNull("used_bytes"))
                o.optLong("used_bytes") else null
            if (ub != null && ub > 0) anyData = true
            days.add(HistoryBarView.Day(
                day, ub, HistoryUi.weekdayLabel(day, tz, fa), day == todayDay))
        }
        if (days.isEmpty() || !anyData) {
            chartHistory.setData(emptyList(), 0L)
            tvHistoryNote.text = if (!CloudLink.paired(this)) tr(T.histPair)
            else tr(T.histEmpty)
            return
        }
        chartHistory.animationsEnabled = glass.animationsEnabled()
        chartHistory.setData(
            days, HistoryUi.dayBudget(Prefs.limitBytes(this), Prefs.monthlyReset(this)))
        val mins = (System.currentTimeMillis() - Prefs.historyCacheAt(this)) / 60_000
        tvHistoryNote.text = HistoryUi.updatedAgo(mins, fa)
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
        val usage      = mapOf("en" to "USAGE", "fa" to "مصرف")
        val rearm      = mapOf("en" to "Re-arms in %s ⏳", "fa" to "قفل مجدد در %s ⏳")
        val blocked    = mapOf("en" to "BLOCKED", "fa" to "قفل شده")
        val grace      = mapOf("en" to "FREE TIME", "fa" to "مهلت آزاد")
        val unlockedMsg= mapOf("en" to "Unlocked", "fa" to "آزاد شد")
        val protected_ = mapOf("en" to "Protected", "fa" to "محافظت فعال")
        val noLimit    = mapOf("en" to "no limit set", "fa" to "حدی تعیین نشده")
        val checklist  = mapOf("en" to "Checklist:", "fa" to "چک‌لیست:")
        val pinLbl     = mapOf("en" to "PIN", "fa" to "رمز")
        val usageAccLbl= mapOf("en" to "Usage access", "fa" to "Usage access")
        val ownerLbl   = mapOf("en" to "Device owner", "fa" to "Device Owner")
        val enforceLbl = mapOf("en" to "Enforce ON", "fa" to "نظارت روشن")
        val cloudLbl   = mapOf("en" to "Cloud linked", "fa" to "متصل به ابر")

        // spec-010
        val tabStatus  = mapOf("en" to "Status", "fa" to "وضعیت")
        val tabUsage   = mapOf("en" to "Usage", "fa" to "مصرف")
        val tabParent  = mapOf("en" to "Parent", "fa" to "والد")
        val parentGate = mapOf("en" to "Parent panel", "fa" to "پنل والد")
        val lblUsed    = mapOf("en" to "Used this period", "fa" to "مصرف این دوره")
        val lblLeft    = mapOf("en" to "Left", "fa" to "باقی‌مانده")
        val lblLimit   = mapOf("en" to "Limit", "fa" to "حد")
        val lblPeriod  = mapOf("en" to "Period ends", "fa" to "پایان دوره")
        val lblBattery = mapOf("en" to "Battery", "fa" to "باتری")
        val of         = mapOf("en" to "of", "fa" to "از")
        val dailyShort = mapOf("en" to "daily reset", "fa" to "ریست روزانه")
        val monthlyShort = mapOf("en" to "monthly reset", "fa" to "ریست ماهانه")
        val histTitle  = mapOf("en" to "Last 7 days", "fa" to "۷ روز اخیر")
        val histPair   = mapOf("en" to
            "☁ Pair this device (Parent tab → Cloud pairing) to see your 7-day history here.",
            "fa" to "☁ برای دیدن تاریخچه ۷ روزه، دستگاه را از تب والد به ابر وصل کن.")
        val histEmpty  = mapOf("en" to
            "No history yet — the device reports about every 10 minutes while paired.",
            "fa" to "هنوز تاریخی نیست — دستگاه تقریباً هر ۱۰ دقیقه گزارش می‌دهد.")
        val unlockDurCap = mapOf("en" to "⏱ Unlock duration", "fa" to "⏱ مدت باز شدن قفل")
        val chipHint  = mapOf("en" to
            "How long a PIN unlock lasts. ∞ = until the period ends.",
            "fa" to "مدت باز ماندن بعد از رمز. ∞ = تا پایان دوره.")
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

        // tabs
        bottomNav.menu.findItem(R.id.nav_status).title = tr(T.tabStatus)
        bottomNav.menu.findItem(R.id.nav_usage).title  = tr(T.tabUsage)
        bottomNav.menu.findItem(R.id.nav_parent).title = tr(T.tabParent)

        // usage tab labels
        findViewById<TextView>(R.id.tvLblUsed).text    = tr(T.lblUsed).uppercase()
        findViewById<TextView>(R.id.tvLblLeft).text    = tr(T.lblLeft).uppercase()
        findViewById<TextView>(R.id.tvLblLimit).text   = tr(T.lblLimit).uppercase()
        findViewById<TextView>(R.id.tvLblPeriod).text  = tr(T.lblPeriod).uppercase()
        findViewById<TextView>(R.id.tvLblBattery).text = tr(T.lblBattery).uppercase()
        findViewById<TextView>(R.id.tvHistoryTitle).text = tr(T.histTitle).uppercase()

        // parent tab
        findViewById<TextView>(R.id.tvUnlockDurCaption).text = tr(T.unlockDurCap).uppercase()
        findViewById<TextView>(R.id.tvChipHint).text = tr(T.chipHint)

        // chips relabel + re-render selection
        chips.forEachIndexed { i, btn ->
            val m = chipOptions[i]
            btn.text = if (m == 0) "∞"
            else if (fa) faDigits(m.toString()) + "د" else "${m}m"
        }
        renderChips()

        // history labels are language-dependent too
        if (::chartHistory.isInitialized) renderHistory()
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

    // ---- unlock-duration chips ----

    private fun applyChip(i: Int) {
        Prefs.setUnlockMinutes(this, chipOptions[i])
        renderChips()
    }

    private fun renderChips() {
        val cur = Prefs.unlockMinutes(this)
        chips.forEachIndexed { i, btn ->
            val on = chipOptions[i] == cur
            btn.setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip)
            btn.setTextColor(if (on) 0xFFFFFFFF.toInt() else 0xFFC9D2E8.toInt())
        }
    }

    private fun faDigits(s: String): String = buildString {
        for (ch in s) append(when (ch) {
            '0' -> '۰'; '1' -> '۱'; '2' -> '۲'; '3' -> '۳'; '4' -> '۴'
            '5' -> '۵'; '6' -> '۶'; '7' -> '۷'; '8' -> '۸'; '9' -> '۹'
            else -> ch })
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
        AlertDialog.Builder(this, R.style.ThemeOverlay_DataGuard_Glass)
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
        AlertDialog.Builder(this, R.style.ThemeOverlay_DataGuard_Glass)
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
                AlertDialog.Builder(this, R.style.ThemeOverlay_DataGuard_Glass)
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
        glass.toast(if (fa) "محافظ فعال شد 🛡️" else "Guard armed 🛡️", GuardStateUi.Chime.ARM)
        refreshUi()
    }

    private fun disableEverything() {
        Prefs.setMonitoring(this, false)
        WatchdogService.unlatch(this)
        Prefs.setGraceUntil(this, 0L)
        OwnerEnforcer.relax(this)
        OwnerEnforcer.trySilentWifiOn(this)
        toast(if (fa) "محافظ خاموش شد" else "Guard disabled")
        suppressNextTransition = true   // "Guard disabled" already says it — no contradictory hero toast
        refreshUi()
    }

    private fun unlockFlow() {
        val graceLeft = (Prefs.graceUntil(this) - AppClock.now()) / 1000
        if (graceLeft > 0) {
            // clicking during grace = lock again NOW
            Prefs.setGraceUntil(this, 0L)
            Logger.d(this, "grace cancelled by user -> re-arm immediately")
            immediateReevaluate()   // transition detector announces + chimes (spec-007)
            return
        }
        guarded(tr(T.unlock)) {
            // FIX: keep the latch and do NOT reset the counter — the grace window
            // merely suspends enforcement, so the lock re-arms the moment it
            // expires (or when the user cancels it with "lock again now").
            val mins = Prefs.unlockMinutes(this)
            val until = if (mins <= 0) endOfPeriod(this)
            else AppClock.now() + mins * 60_000L
            Prefs.setGraceUntil(this, until)
            Logger.d(this, "UNLOCK: grace ${mins}min (0=period), latch kept")
            immediateReevaluate()   // transition detector announces + chimes (spec-007)
        }
    }
    private fun endOfPeriod(c: android.content.Context): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = AppClock.now()
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
        AlertDialog.Builder(this, R.style.ThemeOverlay_DataGuard_Glass).setTitle("Device Owner")
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
            AlertDialog.Builder(this, R.style.ThemeOverlay_DataGuard_Glass)
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

        val dlg = AlertDialog.Builder(this, R.style.ThemeOverlay_DataGuard_Glass)
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
        AlertDialog.Builder(this, R.style.ThemeOverlay_DataGuard_Glass)
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

    // ================= test panel (.test builds only) =================

    private fun fmtTs(ms: Long): String =
        java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(ms))

    private fun showTestPanel() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_test, null)
        val tvState = v.findViewById<TextView>(R.id.tvTestState)
        val btnSim = v.findViewById<Button>(R.id.btnSimOffline)

        fun testLog(msg: String) = Logger.d(this, "TEST $msg")

        fun state() {
            val lim = Prefs.limitBytes(this)
            val used = LiveCounter.currentBytes()
            val last = Prefs.cloudLastSync(this)
            val realNow = System.currentTimeMillis()
            tvState.text = buildString {
                append("virtual : ").append(fmtTs(AppClock.now())).append('\n')
                append("real    : ").append(fmtTs(realNow)).append('\n')
                append("scale   : ").append(
                    String.format(java.util.Locale.US, "%.0fx", AppClock.scale)).append('\n')
                append("paired  : ").append(CloudLink.paired(this@MainActivity)).append('\n')
                append("syncAge : ").append(if (last <= 0) "-"
                    else "${(realNow - last) / 1000}s (real)").append('\n')
                append("tolLeft : ").append(if (last <= 0) "-"
                    else "${((Prefs.offlineTolMin(this@MainActivity) * 60_000L -
                        (realNow - last)) / 1000).coerceAtLeast(0)}s").append('\n')
                append("pollIn  : ").append("${CloudLink.nextPollInMs() / 1000}s").append('\n')
                append("simOff  : ").append(TestMode.simOffline).append('\n')
                append("latched : ").append(WatchdogService.latched).append(" (")
                    .append(Prefs.latchReason(this@MainActivity).ifEmpty { "-" }).append(")\n")
                append("usage   : ").append(humanize(used)).append(" / ")
                    .append(humanize(lim)).append('\n')
                append("period  : ").append(if (Prefs.monthlyReset(this@MainActivity))
                    "monthly" else "daily")
            }
            btnSim.text = "Sim offline: ${if (TestMode.simOffline) "ON" else "OFF"}"
        }

        // ---- time scale ----
        v.findViewById<Button>(R.id.btnTs1).setOnClickListener {
            AppClock.setScale(1.0); testLog("time scale -> 1x"); state() }
        v.findViewById<Button>(R.id.btnTs10).setOnClickListener {
            AppClock.setScale(10.0); testLog("time scale -> 10x"); state() }
        v.findViewById<Button>(R.id.btnTs60).setOnClickListener {
            AppClock.setScale(60.0); testLog("time scale -> 60x"); state() }
        v.findViewById<Button>(R.id.btnTs600).setOnClickListener {
            AppClock.setScale(600.0); testLog("time scale -> 600x"); state() }
        v.findViewById<Button>(R.id.btnTs3600).setOnClickListener {
            AppClock.setScale(3600.0); testLog("time scale -> 3600x"); state() }
        v.findViewById<Button>(R.id.btnJumpBack1h).setOnClickListener {
            AppClock.jump(-3_600_000L); testLog("virtual clock jumped -1h"); state() }
        v.findViewById<Button>(R.id.btnRollbackServer).setOnClickListener {
            if (TestMode.rollbackBelowServer(this)) {
                testLog("clock rolled below server time -> tamper latch expected")
                toast(if (fa) "ساعت به عقب برگشت — قفل در تیک بعدی"
                    else "Clock below server — latch on next tick")
            } else toast(if (fa) "اول به ابر وصل شو" else "Pair to cloud first")
            state()
        }
        v.findViewById<Button>(R.id.btnTsSnap).setOnClickListener {
            AppClock.snapToReal(); testLog("virtual clock snapped to real, 1x"); state() }

        // ---- usage injection ----
        v.findViewById<Button>(R.id.btnAdd100).setOnClickListener {
            TestMode.addUsageMb(100); testLog("usage +100MB"); state(); immediateReevaluate() }
        v.findViewById<Button>(R.id.btnAdd500).setOnClickListener {
            TestMode.addUsageMb(500); testLog("usage +500MB"); state(); immediateReevaluate() }
        v.findViewById<Button>(R.id.btnNearLimit).setOnClickListener {
            TestMode.setUsagePctOfLimit(this, 99)
            testLog("usage -> 99% of limit"); state(); immediateReevaluate() }
        v.findViewById<Button>(R.id.btnUsage0).setOnClickListener {
            TestMode.usageZero(); testLog("usage -> 0"); state(); immediateReevaluate() }

        // ---- cloud / fail-closed ----
        v.findViewById<Button>(R.id.btnForcePoll).setOnClickListener {
            CloudLink.forcePoll(this); testLog("poll forced"); state() }
        btnSim.setOnClickListener {
            TestMode.simOffline = !TestMode.simOffline
            testLog("simulated offline = ${TestMode.simOffline}"); state() }
        v.findViewById<Button>(R.id.btnBackdate).setOnClickListener {
            val last = Prefs.cloudLastSync(this)
            if (last <= 0) { toast(if (fa) "اول به ابر وصل شو" else "Pair to cloud first")
                return@setOnClickListener }
            Prefs.setCloudLastSync(this, last - 20 * 60_000L)
            testLog("last sync backdated -20min -> offline latch expected")
            toast(if (fa) "sync به ۲۰ دقیقه قبل برگشت — قفل در تیک بعدی"
                else "Sync backdated 20 min — latch on next tick")
            state()
        }
        v.findViewById<Button>(R.id.btnTol1).setOnClickListener {
            Prefs.setOfflineTolMin(this, 1)
            testLog("offline tolerance = 1 min (real)"); state() }
        v.findViewById<Button>(R.id.btnPoll15).setOnClickListener {
            Prefs.setPollIntervalSec(this, 15)
            testLog("poll interval = 15 s (real)"); state() }

        // ---- logs ----
        v.findViewById<Button>(R.id.btnShareLog).setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "DataGuard test log")
                putExtra(Intent.EXTRA_TEXT, Logger.readAll(this@MainActivity))
            }
            try { startActivity(Intent.createChooser(send, "Share test log")) }
            catch (_: Exception) {}
        }
        v.findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            Logger.clear(this); testLog("log cleared"); toast(if (fa) "پاک شد" else "Cleared") }

        val dlg = AlertDialog.Builder(this, R.style.ThemeOverlay_DataGuard_Glass)
            .setTitle("🧪 Test panel")
            .setView(v)
            .setPositiveButton(if (fa) "بستن" else "Close", null)
            .show()
        state()
        val refresher = object : Runnable {
            override fun run() {
                if (!dlg.isShowing) return
                state()
                uiHandler.postDelayed(this, 500)
            }
        }
        uiHandler.postDelayed(refresher, 500)
        dlg.setOnDismissListener { uiHandler.removeCallbacks(refresher) }
    }

    // ================= UI =================

    private fun fmt(sec: Long): String =
        String.format(java.util.Locale.US, "%d:%02d", sec / 60, sec % 60)

    private fun refreshUi() {
        val limit = Prefs.limitBytes(this)
        val used = LiveCounter.currentBytes()
        val graceLeft = (Prefs.graceUntil(this) - AppClock.now()) / 1000

        // big usage number (Usage tab) — counts up ONCE per launch (spec-010)
        if (!countedUp && used > 0 && glass.animationsEnabled()) {
            countedUp = true
            val a = ValueAnimator.ofFloat(0f, used.toFloat())
            a.duration = 600
            a.addUpdateListener { anim ->
                tvUsageBig.text = humanize((anim.animatedValue as Float).toLong())
            }
            a.start()
        } else {
            countedUp = true
            tvUsageBig.text = humanize(used)
        }
        tvUsageBigSub.text = if (limit > 0)
            "${tr(T.of)} ${humanize(limit)} • ${pct(used, limit)}% • " +
            tr(if (Prefs.monthlyReset(this)) T.monthlyShort else T.dailyShort)
        else tr(T.noLimit)
        tvUsageSub.text = if (limit > 0)
            "/ ${humanize(limit)} • ${pct(used, limit)}%"
        else tr(T.noLimit)

        // usage-tab stats
        tvStatLeft.text  = if (limit > 0) humanize((limit - used).coerceAtLeast(0)) else "—"
        tvStatLimit.text = if (limit > 0) humanize(limit) else "—"
        tvStatPeriod.text = fmtPeriodLeft(endOfPeriod(this) - AppClock.now())
        tvStatBattery.text = "${batteryPct()}%"

        // glass hero state (same truth sources + precedence as the legacy status line — Art. VIII)
        val state = GuardStateUi.stateOf(graceLeft, WatchdogService.latched)
        when (state) {
            GuardStateUi.S.GRACE -> {
                heroState.text = tr(T.grace)
                heroState.setTextColor(0xFFFFB300.toInt())
                heroDot.setBackgroundResource(R.drawable.dot_grace)
                heroHalo.setBackgroundResource(R.drawable.halo_grace)
            }
            GuardStateUi.S.BLOCKED -> {
                heroState.text = tr(T.blocked)
                heroState.setTextColor(0xFFFF5449.toInt())
                heroDot.setBackgroundResource(R.drawable.dot_latched)
                heroHalo.setBackgroundResource(R.drawable.halo_latched)
            }
            GuardStateUi.S.PROTECTED -> {
                heroState.text = tr(T.protected_)
                heroState.setTextColor(0xFF34D399.toInt())
                heroDot.setBackgroundResource(R.drawable.dot_protected)
                heroHalo.setBackgroundResource(R.drawable.halo_protected)
            }
        }

        // animated ring (Status tab) — same truth the flat bar drew
        ringUsage.animationsEnabled = glass.animationsEnabled()
        ringUsage.setUsage(used, limit, state)

        // blocked badge on the Status tab icon (spec-010 FR-101)
        try {
            val badge = bottomNav.getOrCreateBadge(R.id.nav_status)
            badge.backgroundColor = 0xFFFF5449.toInt()
            badge.isVisible = state == GuardStateUi.S.BLOCKED
        } catch (_: Exception) {}

        // unlock button + status line
        val statusLine = when (state) {
            GuardStateUi.S.GRACE -> {
                btnUnlock.text = tr(T.rearm).replace("%s", fmt(graceLeft))
                btnUnlock.setBackgroundResource(R.drawable.btn_unlock_amber)
                btnUnlock.setTextColor(0xFF2E1A00.toInt())
                "⏳ ${tr(T.grace)}"
            }
            GuardStateUi.S.BLOCKED -> {
                btnUnlock.text = tr(T.mapUnlock)
                btnUnlock.setBackgroundResource(R.drawable.btn_unlock)
                btnUnlock.setTextColor(0xFF06281A.toInt())
                "🔒 ${tr(T.blocked)}"
            }
            GuardStateUi.S.PROTECTED -> {
                btnUnlock.text = tr(T.mapUnlock)
                btnUnlock.setBackgroundResource(R.drawable.btn_unlock)
                btnUnlock.setTextColor(0xFF06281A.toInt())
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

        // state-change celebration (Art. VIII): fires ONLY on real transitions,
        // exactly once, detected from the same truth the status line renders.
        val prev = guardPrev
        if (GuardStateUi.isTransition(prev, state) && !suppressNextTransition) {
            val (en, faTxt) = GuardStateUi.label(prev!!, state)
            glass.toast(if (fa) faTxt else en, GuardStateUi.chime(prev, state))
        }
        suppressNextTransition = false
        guardPrev = state
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

    private fun fmtPeriodLeft(ms: Long): String {
        if (ms <= 0) return "—"
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        return when {
            h >= 24 -> "${h / 24}d ${h % 24}h"
            h > 0   -> "${h}h ${m}m"
            else    -> "${m}m"
        }
    }

    private fun batteryPct(): Int = try {
        getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    } catch (_: Exception) { 0 }

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

    private fun toast(s: String) = glass.toast(s)
}
