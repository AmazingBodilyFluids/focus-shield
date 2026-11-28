package org.focusshield.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The Guardian of the Threshold: an ogre mask sitting in a ring. While locked it has to be
 * slid off its place with [NEEDED] separate long strokes, each one breaking a seal. Seals
 * re-form if the strokes stop. Moving it only works while [canMove] says the unlock
 * conditions are met.
 */
class GuardianView(context: Context) : View(context) {

    enum class Phase { LOCKED, HOLDING, UNLOCKED }

    /** Returns null when the Guardian may be moved, or the reason it may not. */
    var canMove: () -> String? = { null }
    var onRefused: (String) -> Unit = {}
    var onTooGentle: () -> Unit = {}
    var onStroke: (Int) -> Unit = {}
    var onReleased: () -> Unit = {}
    var onTapWhileOpen: () -> Unit = {}

    var phase = Phase.LOCKED
        private set
    var strokes = 0
        private set
    private var lastStroke = 0L

    /** 0..1 shown on the ring as an arc, and the big centre label while the threshold is open. */
    private var arcFraction = 0f
    private var centreText = ""
    private var centreCaption = ""

    // Ogre position relative to the centre, plus park amount (0 = centre, 1 = parked aside).
    private var ox = 0f
    private var oy = 0f
    private var rot = 0f
    private var park = 0f
    private var shakeT = 0f

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var travelled = 0f
    private var lastDx = 0f
    private var lastDy = 0f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    /** The Guardian's face: res/drawable-nodpi/ogre.png, so it looks the same on every phone. */
    private val ogre = BitmapFactory.decodeResource(resources, R.drawable.ogre)
    private val ogrePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ogreBox = RectF()
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val oval = RectF()
    private val dash = DashPathEffect(floatArrayOf(dp(2f), dp(6f)), 0f)

    private val minStroke = dp(105f)

    private val decay = object : Runnable {
        override fun run() {
            if (phase == Phase.LOCKED && strokes > 0 && !dragging) {
                val now = SystemClock.uptimeMillis()
                if (now - lastStroke > DECAY_MS) {
                    strokes--
                    lastStroke = now - DECAY_MS + 700
                    invalidate()
                }
            }
            postDelayed(this, 250)
        }
    }

    init {
        isClickable = true
        contentDescription = "Guardian. Slide it off the threshold with firm strokes to unlock."
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(decay)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(decay)
        super.onDetachedFromWindow()
    }

    private fun dp(v: Float) = Ui.dp(context, v)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val s = min(w, dp(320f).toInt())
        setMeasuredDimension(w, s)
    }

    /** Called by the screen on every tick with the current state. */
    fun show(newPhase: Phase, fraction: Float, text: String, caption: String) {
        val old = phase
        phase = newPhase
        arcFraction = fraction.coerceIn(0f, 1f)
        centreText = text
        centreCaption = caption
        if (newPhase == Phase.LOCKED && old != Phase.LOCKED) returnToCentre()
        if (newPhase != Phase.LOCKED && old == Phase.LOCKED && park < 1f) animatePark(1f)
        invalidate()
    }

    private fun returnToCentre() {
        strokes = 0
        animatePark(0f)
    }

    private fun animatePark(target: Float) {
        val sx = ox
        val sy = oy
        val sr = rot
        val start = park
        ValueAnimator.ofFloat(0f, 1f).setDuration(650).apply {
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                val t = it.animatedValue as Float
                park = start + (target - start) * t
                ox = sx * (1 - t); oy = sy * (1 - t); rot = sr * (1 - t)
                invalidate()
            }
        }.start()
    }

    private fun springBack() {
        val sx = ox
        val sy = oy
        val sr = rot
        ValueAnimator.ofFloat(1f, 0f).setDuration(550).apply {
            interpolator = OvershootInterpolator(2.2f)
            addUpdateListener {
                val t = it.animatedValue as Float
                ox = sx * t; oy = sy * t; rot = sr * t
                invalidate()
            }
        }.start()
    }

    private fun shake() {
        ValueAnimator.ofFloat(0f, 1f).setDuration(450).apply {
            addUpdateListener { shakeT = it.animatedValue as Float; invalidate() }
        }.start()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun ringRadius() = min(width, height) / 2f - dp(8f)
    private fun ogreSize() = ringRadius() * 1.18f

    private fun ogreCentre(): Pair<Float, Float> {
        val r = ringRadius()
        val cx = width / 2f + ox + park * r * 0.72f
        val cy = height / 2f + oy - park * r * 0.72f
        return cx to cy
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val (cx, cy) = ogreCentre()
                val reach = ogreSize() * (1 - 0.64f * park) * 0.6f + dp(16f)
                if (hypot(e.x - cx, e.y - cy) > reach) return false
                downX = e.x; downY = e.y; travelled = 0f
                parent?.requestDisallowInterceptTouchEvent(true)
                if (phase == Phase.LOCKED) {
                    val reason = canMove()
                    if (reason != null) {
                        shake(); onRefused(reason); return true
                    }
                    dragging = true
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX
                val dy = e.y - downY
                travelled = hypot(dx, dy)
                if (!dragging) return true
                val d = travelled.coerceAtLeast(1f)
                // Heavy resistance: dragging alone never gets it out of the ring.
                val pull = dp(74f) * (1 - exp(-d / dp(210f)))
                ox = dx / d * pull
                oy = dy / d * pull
                rot = dx / d * pull * 0.18f / Ui.dp(context, 1f)
                lastDx = dx; lastDy = dy
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (phase == Phase.UNLOCKED && travelled < dp(12f) && e.actionMasked == MotionEvent.ACTION_UP) {
                    performClick()
                    onTapWhileOpen()
                    return true
                }
                if (!dragging) return true
                dragging = false
                if (travelled >= minStroke && e.actionMasked == MotionEvent.ACTION_UP) {
                    strokes++
                    lastStroke = SystemClock.uptimeMillis()
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (strokes >= NEEDED) {
                        strokes = NEEDED
                        phase = Phase.HOLDING // the screen confirms the real phase on its next tick
                        animatePark(1f)
                        onReleased()
                        return true
                    }
                    onStroke(strokes)
                } else if (travelled > dp(12f)) {
                    onTooGentle()
                }
                springBack()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = ringRadius()

        // Halo behind the Guardian while it guards.
        if (park < 1f) {
            p.style = Paint.Style.FILL
            p.shader = RadialGradient(
                cx, cy, r * 0.75f,
                intArrayOf((0x38 * (1 - park)).toInt() shl 24 or (Ui.SAKURA and 0xFFFFFF), 0),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r * 0.75f, p)
            p.shader = null
        }

        // Outer ring and ticks.
        p.style = Paint.Style.STROKE
        p.color = Ui.LINE
        p.strokeWidth = dp(1f)
        canvas.drawCircle(cx, cy, r, p)
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0)
            val len = if (i % 5 == 0) dp(9f) else dp(4f)
            p.strokeWidth = if (i % 5 == 0) dp(1.5f) else dp(1f)
            canvas.drawLine(
                cx + (sin(a) * r).toFloat(), cy - (cos(a) * r).toFloat(),
                cx + (sin(a) * (r - len)).toFloat(), cy - (cos(a) * (r - len)).toFloat(), p
            )
        }
        p.strokeWidth = dp(1f)
        p.pathEffect = dash
        canvas.drawCircle(cx, cy, r * 0.81f, p)
        p.pathEffect = null

        // Countdown arc.
        val arcR = r * 0.905f
        if (phase != Phase.LOCKED) {
            p.color = if (phase == Phase.UNLOCKED) Ui.GOLD else Ui.SAKURA
            p.strokeWidth = dp(3f)
            p.strokeCap = Paint.Cap.ROUND
            oval.set(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
            canvas.drawArc(oval, -90f, 360f * arcFraction, false, p)
        }

        // Seals.
        for (i in 0 until NEEDED) {
            val a = 2 * Math.PI * i / NEEDED
            val bx = cx + (sin(a) * arcR).toFloat()
            val by = cy - (cos(a) * arcR).toFloat()
            p.style = Paint.Style.FILL
            p.color = if (i < strokes && phase == Phase.LOCKED) Ui.SAKURA else Ui.RAISED
            canvas.drawCircle(bx, by, dp(6f), p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(1.2f)
            p.color = Ui.SAKURA
            canvas.drawCircle(bx, by, dp(6f), p)
        }

        // Countdown text in the open threshold.
        if (park > 0.5f && centreText.isNotEmpty()) {
            val alpha = ((park - 0.5f) * 2 * 255).toInt().coerceIn(0, 255)
            label.typeface = Ui.DISPLAY
            label.textSize = r * 0.34f
            label.color = if (phase == Phase.UNLOCKED) Ui.GOLD else Ui.SAKURA
            label.alpha = alpha
            canvas.drawText(centreText, cx, cy + r * 0.08f, label)
            label.typeface = Ui.MEDIUM
            label.textSize = dp(11f)
            label.color = Ui.MUTED
            label.alpha = alpha
            canvas.drawText(centreCaption.uppercase(), cx, cy + r * 0.08f + dp(22f), label)
        }

        // The Guardian.
        val (gx, gy) = ogreCentre()
        val scale = 1f - 0.64f * park
        val shakeX = if (shakeT in 0.001f..0.999f) (sin(shakeT * Math.PI * 6) * dp(9f) * (1 - shakeT)).toFloat() else 0f
        canvas.save()
        canvas.translate(gx + shakeX, gy)
        canvas.rotate(rot + park * 18f + shakeX * 0.7f)
        canvas.scale(scale, scale)
        val half = ogreSize() * 0.58f
        ogreBox.set(-half, -half, half, half)
        canvas.drawBitmap(ogre, null, ogreBox, ogrePaint)
        canvas.restore()
    }

    companion object {
        const val NEEDED = 6
        private const val DECAY_MS = 2600L
    }
}
