package org.focusshield.app

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** The Guardian's block screen: a big ogre, a cheeky line and a way home. */
class BlockedActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var ogre: ImageView
    private lateinit var line: TextView
    private lateinit var what: TextView
    private lateinit var count: TextView
    private var attempts = 0
    private var preview = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val dp = { v: Int -> Ui.dp(this, v) }

        ogre = ImageView(this).apply {
            setImageResource(R.drawable.ogre)
            contentDescription = "The Guardian"
        }
        line = Ui.text(this, "", 30f, Ui.TEXT, Ui.DISPLAY).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.05f)
        }
        what = Ui.muted(this, "", 14f).apply { gravity = Gravity.CENTER }
        count = Ui.muted(this, "", 12f).apply { gravity = Gravity.CENTER }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(40), dp(28), dp(40))
            addView(ogre, LinearLayout.LayoutParams(dp(172), dp(172)))
            addView(line, lp(dp(20)))
            addView(what, lp(dp(12)))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                addView(Ui.button(context, "Try again") { again() })
                addView(Ui.button(context, "Take me home", primary = true) { leave() }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { leftMargin = dp(10) })
            }, lp(dp(28)))
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_mark)
                setColorFilter(Ui.SAKURA)
            }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(8) })
            addView(Ui.text(context, "THRESHOLD", 12f, Ui.MUTED, Ui.MEDIUM).apply { letterSpacing = 0.16f })
        }

        setContentView(FrameLayout(this).apply {
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(0xFF2A1820.toInt(), Ui.LACQUER, Ui.INK)
                gradientRadius = resources.displayMetrics.heightPixels * 0.7f
                setGradientCenter(0.5f, 0.38f)
            }
            addView(header, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
                topMargin = dp(36)
            })
            addView(body, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            addView(count, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                bottomMargin = dp(32)
            })
        })
        show(intent)
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = top }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    private fun show(intent: Intent?) {
        preview = intent?.getBooleanExtra(EXTRA_PREVIEW, false) ?: false
        val thing = intent?.getStringExtra(EXTRA_WHAT) ?: "This"
        what.text = "$thing is behind the threshold."
        attempts = 0
        count.text = if (preview) "Preview. This is what a blocked app or site looks like." else ""
        line.text = prefs.randomLine()
        entrance()
    }

    private fun again() {
        attempts++
        line.text = prefs.randomLine(not = line.text.toString())
        count.text = "Attempt ${attempts + 1}. The Guardian is keeping count."
        entrance()
    }

    /** One of four ways the Guardian arrives: peeking up, spinning in, wagging, or stomping down. */
    private fun entrance() {
        val h = resources.displayMetrics.heightPixels.toFloat()
        ogre.animate().cancel()
        ogre.rotation = 0f; ogre.translationY = 0f; ogre.scaleX = 1f; ogre.scaleY = 1f
        when ((0..3).random()) {
            0 -> {
                ogre.translationY = h * 0.25f; ogre.rotation = -12f
                ogre.animate().translationY(0f).rotation(0f).setDuration(800).setInterpolator(OvershootInterpolator(1.6f)).start()
            }
            1 -> {
                ogre.scaleX = 0f; ogre.scaleY = 0f; ogre.rotation = -540f
                ogre.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(800).setInterpolator(OvershootInterpolator(1.2f)).start()
            }
            2 -> wobble(0)
            else -> {
                ogre.translationY = -h * 0.4f
                ogre.animate().translationY(0f).setDuration(700).setInterpolator(OvershootInterpolator(2f)).start()
            }
        }
        line.alpha = 0f; line.translationY = Ui.dp(this, 8f)
        line.animate().alpha(1f).translationY(0f).setDuration(350).start()
    }

    private fun wobble(step: Int) {
        val angles = floatArrayOf(-14f, 12f, -8f, 5f, 0f)
        if (step >= angles.size) return
        ogre.animate().rotation(angles[step]).setDuration(180).withEndAction { wobble(step + 1) }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = leave()

    private fun leave() {
        if (preview) {
            finish(); return
        }
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_WHAT = "what"
        const val EXTRA_PREVIEW = "preview"
    }
}
