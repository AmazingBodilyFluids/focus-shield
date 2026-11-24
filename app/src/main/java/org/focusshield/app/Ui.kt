package org.focusshield.app

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

/** The Threshold look: lacquer-dark surfaces with gold, sakura and jade accents. */
object Ui {
    val INK = Color.parseColor("#0E0F15")
    val LACQUER = Color.parseColor("#14151E")
    val SURFACE = Color.parseColor("#1B1D29")
    val RAISED = Color.parseColor("#232636")
    val LINE = Color.parseColor("#2E3144")
    val TEXT = Color.parseColor("#ECEBF3")
    val MUTED = Color.parseColor("#9A9CB2")
    val GOLD = Color.parseColor("#EAD6A6")
    val SAKURA = Color.parseColor("#F4BCCB")
    val JADE = Color.parseColor("#A3E3C8")
    val EMBER = Color.parseColor("#F2A58C")
    val BLOOD = Color.parseColor("#E3394F")
    val ON_GOLD = Color.parseColor("#1A1712")

    val DISPLAY: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val BODY: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    const val OGRE = "👹" // 👹, drawn with the phone's own emoji font

    fun dp(c: Context, v: Float) = v * c.resources.displayMetrics.density
    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun rounded(c: Context, fill: Int, radiusDp: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(c, radiusDp)
        if (stroke != null) setStroke(dp(c, 1), stroke)
    }

    fun card(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(c, SURFACE, 20f, LINE)
        val p = dp(c, 16)
        setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(c, 14) }
    }

    fun text(c: Context, s: CharSequence, sizeSp: Float = 14f, color: Int = TEXT, face: Typeface = BODY) =
        TextView(c).apply {
            text = s
            textSize = sizeSp
            setTextColor(color)
            typeface = face
            setLineSpacing(0f, 1.15f)
        }

    fun heading(c: Context, s: String) = text(c, s, 18f, TEXT, DISPLAY)

    fun muted(c: Context, s: CharSequence, sizeSp: Float = 12.5f) = text(c, s, sizeSp, MUTED)

    /** Rounded pill button. Primary ones are gold with dark text. */
    fun button(c: Context, label: String, primary: Boolean = false, onClick: () -> Unit) = TextView(c).apply {
        text = label
        textSize = 14f
        typeface = MEDIUM
        gravity = Gravity.CENTER
        setTextColor(if (primary) ON_GOLD else TEXT)
        background = rounded(c, if (primary) GOLD else RAISED, 999f, if (primary) GOLD else LINE)
        setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    fun tickbox(c: Context) = CheckBox(c).apply {
        buttonTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(SAKURA, MUTED)
        )
    }

    /** One settings row: title and optional description on the left, a control on the right. */
    fun row(c: Context, title: String, desc: String?, control: View?, divider: Boolean = true): LinearLayout =
        LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(c, 11), 0, dp(c, 11))
            if (divider) background = topLine(c)
            addView(LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(c, title, 14.5f, TEXT, MEDIUM))
                if (desc != null) addView(muted(c, desc, 12f))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(c, 12)
            })
            if (control != null) addView(control)
        }

    /** A hairline along the top edge, used between rows. */
    fun topLine(c: Context): android.graphics.drawable.Drawable {
        val line = GradientDrawable().apply { setColor(LINE) }
        val clear = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
        return android.graphics.drawable.LayerDrawable(arrayOf(clear, line)).apply {
            setLayerHeight(1, dp(c, 1))
            setLayerGravity(1, Gravity.TOP)
        }
    }

    fun shake(v: View) {
        v.animate().cancel()
        val a = ValueAnimator.ofFloat(0f, 1f).setDuration(420)
        a.addUpdateListener {
            val t = it.animatedValue as Float
            v.translationX = (Math.sin(t * Math.PI * 6) * dp(v.context, 7f) * (1 - t)).toFloat()
        }
        a.start()
        v.performHapticFeedback(HapticFeedbackConstants.REJECT.takeIf { android.os.Build.VERSION.SDK_INT >= 30 }
            ?: HapticFeedbackConstants.LONG_PRESS)
    }
}

/** A pill-shaped on/off switch in the Threshold colours. */
class PillSwitch(context: Context) : View(context) {
    var checked = false
        private set
    var onChange: ((Boolean) -> Unit)? = null
    private var pos = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            if (!isEnabled) return@setOnClickListener
            set(!checked, animate = true)
            onChange?.invoke(checked)
        }
    }

    fun set(on: Boolean, animate: Boolean = false) {
        checked = on
        contentDescription = if (on) "On" else "Off"
        val target = if (on) 1f else 0f
        if (!animate) {
            pos = target; invalidate(); return
        }
        ValueAnimator.ofFloat(pos, target).setDuration(180).apply {
            addUpdateListener { pos = it.animatedValue as Float; invalidate() }
        }.start()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.45f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(Ui.dp(context, 50), Ui.dp(context, 30))
    }

    override fun onDraw(canvas: Canvas) {
        val d = Ui.dp(context, 1f)
        val h = height.toFloat()
        rect.set(d, d, width - d, h - d)
        paint.style = Paint.Style.FILL
        paint.color = blend(Ui.RAISED, Color.parseColor("#3A3628"), pos)
        canvas.drawRoundRect(rect, h / 2, h / 2, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = d
        paint.color = blend(Ui.LINE, Ui.GOLD, pos)
        canvas.drawRoundRect(rect, h / 2, h / 2, paint)
        paint.style = Paint.Style.FILL
        paint.color = blend(Ui.MUTED, Ui.GOLD, pos)
        val r = h / 2 - 4 * d
        val x = h / 2 + pos * (width - h)
        canvas.drawCircle(x, h / 2, r, paint)
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )
}
