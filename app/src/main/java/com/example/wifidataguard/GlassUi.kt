package com.example.wifidataguard

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Glass super-UI runtime (spec-007): in-app glass toast stack with system-toast
 * fallback, two-tone chime (dashboard parity), vibration, entrance-once
 * animation and the breathing status halo. Purely cosmetic — no enforcement,
 * cloud or timing logic lives here (Art. II/VIII/IX).
 */
class GlassUi(private val activity: Activity) {

    private val handler = Handler(Looper.getMainLooper())
    private val audioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "dg-chime").apply { isDaemon = true }
    }
    private var stack: LinearLayout? = null
    private var haloAnimator: ObjectAnimator? = null

    /** Set by MainActivity onResume/onPause — decides in-app vs system toast. */
    @Volatile var resumed = false

    // ---------- reduce-motion (developer options: animator duration scale 0) ----------

    fun animationsEnabled(): Boolean = try {
        Settings.Global.getFloat(
            activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    } catch (_: Exception) { true }

    // ---------- entrance (once, on create; never on the 1 s refresh tick) ----------

    fun entrance(views: List<View>) {
        if (views.isEmpty() || !animationsEnabled()) return
        for (v in views) { v.alpha = 0f; v.translationY = dpF(28f) }
        views.forEachIndexed { i, v ->
            v.postDelayed({
                v.animate().alpha(1f).translationY(0f).setDuration(300)
                    .withEndAction { v.alpha = 1f; v.translationY = 0f }
                    .start()
            }, 60L * i)
        }
        // safety sweep: never leave a view at alpha 0 even if an animation is interrupted
        handler.postDelayed({
            for (v in views) { v.alpha = 1f; v.translationY = 0f }
        }, 60L * views.size + 450)
    }

    // ---------- breathing halo ----------

    fun startHalo(halo: View?) {
        stopHalo()
        if (halo == null) return
        if (!animationsEnabled()) { halo.alpha = 0.35f; return }
        halo.alpha = 0.55f
        val a = ObjectAnimator.ofPropertyValuesHolder(halo,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.9f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.9f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.55f, 0f))
        a.repeatCount = ObjectAnimator.INFINITE
        a.duration = 1600
        a.start()
        haloAnimator = a
    }

    fun stopHalo() {
        try { haloAnimator?.cancel() } catch (_: Exception) {}
        haloAnimator = null
    }

    // ---------- toasts ----------

    /** Show a glass toast. [chime] != null => real state transition: sound + vibrate. */
    fun toast(msg: String, chime: GuardStateUi.Chime? = null) {
        if (chime != null) { playChime(chime); vibrate(chime) }
        if (resumed) showInApp(msg) else systemToast(msg)
    }

    private fun systemToast(msg: String) {
        try {
            val t = Toast.makeText(activity, msg, Toast.LENGTH_LONG)
            try { t.view = buildToastView(msg) } catch (_: Exception) {}
            t.show()
        } catch (_: Exception) {}
    }

    private fun showInApp(msg: String) {
        val s = stack()
        if (s.childCount >= 3) s.removeViewAt(0)
        val v = buildToastView(msg)
        v.alpha = 0f
        v.translationY = dpF(40f)
        s.addView(v)
        v.setOnClickListener { dismiss(s, v) }
        if (animationsEnabled()) {
            v.animate().alpha(1f).translationY(0f).setDuration(260)
                .withEndAction { v.alpha = 1f; v.translationY = 0f }.start()
        } else { v.alpha = 1f; v.translationY = 0f }
        handler.postDelayed({ dismiss(s, v) }, 3500)
    }

    private fun dismiss(s: LinearLayout, v: View) {
        try {
            if (animationsEnabled()) {
                v.animate().alpha(0f).setDuration(200)
                    .withEndAction { try { s.removeView(v) } catch (_: Exception) {} }
                    .start()
            } else s.removeView(v)
        } catch (_: Exception) {}
    }

    private fun stack(): LinearLayout {
        stack?.let { return it }
        val s = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(26)
            leftMargin = dp(20)
            rightMargin = dp(20)
        }
        activity.addContentView(s, params)
        stack = s
        return s
    }

    private fun buildToastView(msg: String): View {
        val padH = dp(16)
        val padV = dp(12)
        return TextView(activity).apply {
            text = msg
            textSize = 14f
            setTextColor(0xFFE8ECF8.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, padV, padH, padV)
            background = ContextCompat.getDrawable(context, R.drawable.bg_toast)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
    }

    // ---------- chime (AudioTrack, no assets, no permissions) ----------

    private fun playChime(kind: GuardStateUi.Chime) {
        try {
            val am = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        } catch (_: Exception) { }
        audioExecutor.execute {
            try {
                val sr = 44100
                // Same tone pairs as the dashboard (spec-006): lock falling,
                // unlock rising, arm = one soft note.
                val notes: List<Pair<Float, Int>> = when (kind) {
                    GuardStateUi.Chime.LOCK -> listOf(330f to 150, 220f to 170)
                    GuardStateUi.Chime.UNLOCK -> listOf(440f to 150, 660f to 170)
                    GuardStateUi.Chime.ARM -> listOf(440f to 200)
                }
                val gapMs = 25
                val totalMs = notes.sumOf { it.second } + gapMs * (notes.size - 1)
                val n = sr * totalMs / 1000
                val pcm = ShortArray(n)
                var i = 0
                for ((idx, note) in notes.withIndex()) {
                    if (idx > 0) i += sr * gapMs / 1000
                    val (freq, ms) = note
                    val len = sr * ms / 1000
                    for (k in 0 until len) {
                        if (i >= n) break
                        val t = k.toFloat() / len
                        val env = when {
                            t < 0.1f -> t / 0.1f
                            t > 0.85f -> (1f - t) / 0.15f
                            else -> 1f
                        }.coerceIn(0f, 1f)
                        val sample = sin(2.0 * PI * freq * k / sr) * 0.28 * env
                        pcm[i++] = (sample * Short.MAX_VALUE).toInt().toShort()
                    }
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sr)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                handler.postDelayed({
                    try { track.release() } catch (_: Exception) {}
                }, (totalMs + 250).toLong())
            } catch (_: Exception) { }
        }
    }

    // ---------- vibration ----------

    private fun vibrate(kind: GuardStateUi.Chime) {
        try {
            val vib: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val effect = if (kind == GuardStateUi.Chime.LOCK)
                VibrationEffect.createWaveform(longArrayOf(0, 60, 50, 60), -1)
            else
                VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
            vib.vibrate(effect)
        } catch (_: Exception) { }
    }

    // ---------- lifecycle ----------

    fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        stopHalo()
        try { audioExecutor.shutdownNow() } catch (_: Exception) {}
    }

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()
    private fun dpF(v: Float): Float = v * activity.resources.displayMetrics.density
}
