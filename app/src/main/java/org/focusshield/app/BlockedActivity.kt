package org.focusshield.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class BlockedActivity : Activity() {

    private lateinit var message: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        message = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        val home = Button(this).apply {
            text = "Back to home screen"
            setOnClickListener { goHome() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#1B5E20"))
            addView(TextView(context).apply {
                text = "Blocked"
                textSize = 34f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            })
            addView(message)
            addView(home)
        })
        show(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        show(intent)
    }

    private fun show(intent: Intent?) {
        val what = intent?.getStringExtra(EXTRA_WHAT) ?: "This"
        message.text = "\n$what is blocked right now.\n\nTo unlock, open Focus Shield and meet your unlock conditions.\n"
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = goHome()

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_WHAT = "what"
    }
}
