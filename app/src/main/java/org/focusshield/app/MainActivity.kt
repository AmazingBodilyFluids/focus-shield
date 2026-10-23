package org.focusshield.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var serviceStatus: TextView
    private lateinit var serviceButton: Button
    private lateinit var lockStatus: TextView
    private lateinit var conditions: TextView
    private lateinit var unlockButton: Button
    private lateinit var lockButton: Button

    private lateinit var appsField: EditText
    private lateinit var pickAppsButton: Button
    private lateinit var keywordsField: EditText
    private lateinit var sitesField: EditText
    private lateinit var browsersField: EditText
    private lateinit var chargerBox: CheckBox
    private lateinit var batteryField: EditText
    private lateinit var waitField: EditText
    private lateinit var unlockMinutesField: EditText
    private lateinit var guardBox: CheckBox
    private lateinit var saveButton: Button
    private val editable = ArrayList<View>()

    /** When the user started an unlock that needs the conditions held for a while. */
    private var holdStartedAt: Long? = null
    private var wasUnlocked: Boolean? = null

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        buildUi()
        loadFields()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        holdStartedAt = null
    }

    // ---- UI ----

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        col.addView(header("Blocking service"))
        serviceStatus = text("")
        col.addView(serviceStatus)
        serviceButton = button("Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        col.addView(serviceButton)
        col.addView(text(
            "If Android says the setting is restricted: open App info for Focus Shield, tap the ⋮ menu, " +
                "choose \"Allow restricted settings\", then try again.", small = true
        ))

        col.addView(header("Lock"))
        lockStatus = text("").apply { textSize = 18f; setTypeface(typeface, Typeface.BOLD) }
        col.addView(lockStatus)
        conditions = text("")
        col.addView(conditions)
        unlockButton = button("Unlock") { onUnlockPressed() }
        col.addView(unlockButton)
        lockButton = button("Lock now") {
            prefs.lockNow()
            holdStartedAt = null
            refresh()
        }
        col.addView(lockButton)

        col.addView(header("Blocked apps (one package name per line)"))
        appsField = multiline()
        col.addView(appsField)
        pickAppsButton = button("Choose from installed apps") { pickApps() }
        col.addView(pickAppsButton)

        col.addView(header("App name keywords"))
        col.addView(text("Any app whose name or package contains one of these is blocked too, so renamed or patched YouTube apps are caught.", small = true))
        keywordsField = multiline()
        col.addView(keywordsField)

        col.addView(header("Blocked websites"))
        col.addView(text("\"youtube.com\" blocks that site and its subdomains. A word without a dot, like \"invidious\", blocks any site with that word in its address.", small = true))
        sitesField = multiline()
        col.addView(sitesField)

        col.addView(header("Browsers to watch (package names)"))
        browsersField = multiline()
        col.addView(browsersField)

        col.addView(header("Unlock rules"))
        chargerBox = CheckBox(this).apply { text = "Charger must be connected" }
        col.addView(chargerBox)
        col.addView(text("Battery must be at least (%, 0 = no requirement)"))
        batteryField = number()
        col.addView(batteryField)
        col.addView(text("Keep the conditions met with this screen open for (minutes, 0 = instant)"))
        waitField = number()
        col.addView(waitField)
        col.addView(text("An unlock lasts (minutes), then blocking turns itself back on"))
        unlockMinutesField = number()
        col.addView(unlockMinutesField)
        guardBox = CheckBox(this).apply { text = "While locked, stop Focus Shield being switched off or uninstalled" }
        col.addView(guardBox)

        saveButton = button("Save") { save() }
        col.addView(saveButton)

        editable.addAll(listOf(
            appsField, pickAppsButton, keywordsField, sitesField, browsersField,
            chargerBox, batteryField, waitField, unlockMinutesField, guardBox, saveButton,
        ))

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun header(s: String) = TextView(this).apply {
        text = s
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(4))
    }

    private fun text(s: String, small: Boolean = false) = TextView(this).apply {
        text = s
        textSize = if (small) 13f else 15f
        if (small) setTextColor(Color.GRAY)
    }

    private fun button(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s
        setOnClickListener { onClick() }
    }

    private fun multiline() = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        minLines = 3
        maxLines = 10
        textSize = 14f
        setTypeface(Typeface.MONOSPACE)
    }

    private fun number() = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }

    // ---- State ----

    private fun loadFields() {
        appsField.setText(prefs.blockedApps.sorted().joinToString("\n"))
        keywordsField.setText(prefs.appKeywords.sorted().joinToString("\n"))
        sitesField.setText(prefs.blockedSites.sorted().joinToString("\n"))
        browsersField.setText(prefs.browsers.sorted().joinToString("\n"))
        chargerBox.isChecked = prefs.requireCharger
        batteryField.setText(prefs.minBattery.toString())
        waitField.setText(prefs.waitMinutes.toString())
        unlockMinutesField.setText(prefs.unlockMinutes.toString())
        guardBox.isChecked = prefs.guardSettings
    }

    private fun lines(f: EditText) =
        f.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun save() {
        if (!prefs.isUnlocked) {
            toast("Locked. Unlock first.")
            return
        }
        prefs.blockedApps = lines(appsField)
        prefs.appKeywords = lines(keywordsField)
        prefs.blockedSites = lines(sitesField)
        prefs.browsers = lines(browsersField)
        prefs.requireCharger = chargerBox.isChecked
        prefs.minBattery = batteryField.text.toString().toIntOrNull() ?: 0
        prefs.waitMinutes = waitField.text.toString().toIntOrNull() ?: 0
        prefs.unlockMinutes = unlockMinutesField.text.toString().toIntOrNull() ?: 15
        prefs.guardSettings = guardBox.isChecked
        loadFields()
        toast("Saved")
    }

    private fun conditionsMet(): Pair<Boolean, String> {
        val b = readBattery(this)
        val sb = StringBuilder()
        var ok = true
        if (prefs.requireCharger) {
            sb.append(if (b.pluggedIn) "✓ Charger connected\n" else "✗ Charger not connected\n")
            ok = ok && b.pluggedIn
        }
        if (prefs.minBattery > 0) {
            val good = b.percent >= prefs.minBattery
            sb.append(if (good) "✓" else "✗").append(" Battery ${b.percent}% (needs ${prefs.minBattery}%)\n")
            ok = ok && good
        }
        if (prefs.waitMinutes > 0) sb.append("• Then hold them for ${prefs.waitMinutes} min with this screen open\n")
        if (sb.isEmpty()) sb.append("No unlock conditions set.\n")
        return ok to sb.toString().trimEnd()
    }

    private fun onUnlockPressed() {
        val (ok, _) = conditionsMet()
        if (!ok) {
            toast("Unlock conditions aren't met yet")
            return
        }
        if (prefs.waitMinutes == 0) {
            prefs.unlock()
        } else {
            holdStartedAt = SystemClock.elapsedRealtime()
        }
        refresh()
    }

    private fun refresh() {
        val on = isServiceEnabled()
        serviceStatus.text = if (on) "✓ Running" else "✗ Off. Blocking won't work until you turn on Focus Shield in Accessibility settings."
        serviceStatus.setTextColor(if (on) Color.parseColor("#2E7D32") else Color.RED)

        val unlocked = prefs.isUnlocked
        val (ok, summary) = conditionsMet()

        val hold = holdStartedAt
        var holdLine = ""
        if (!unlocked && hold != null) {
            if (!ok) {
                holdStartedAt = null
                holdLine = "\n\nConditions broke, so the countdown was reset."
            } else {
                val left = prefs.waitMinutes * 60_000L - (SystemClock.elapsedRealtime() - hold)
                if (left <= 0) {
                    holdStartedAt = null
                    prefs.unlock()
                } else {
                    holdLine = "\n\nUnlocking in ${fmt(left)}. Keep the conditions met and this screen open."
                }
            }
        }

        val nowUnlocked = prefs.isUnlocked
        if (nowUnlocked) {
            val left = prefs.unlockedUntil - System.currentTimeMillis()
            lockStatus.text = "Unlocked for ${fmt(left)}. Blocking is paused."
            lockStatus.setTextColor(Color.parseColor("#E65100"))
            conditions.text = "You can edit your lists and rules now. Remember to save."
        } else {
            lockStatus.text = "Locked. Blocking is on."
            lockStatus.setTextColor(Color.parseColor("#2E7D32"))
            conditions.text = "To unlock:\n$summary$holdLine"
        }
        unlockButton.visibility = if (nowUnlocked) View.GONE else View.VISIBLE
        unlockButton.isEnabled = ok && holdStartedAt == null
        lockButton.visibility = if (nowUnlocked) View.VISIBLE else View.GONE

        if (wasUnlocked != nowUnlocked) {
            editable.forEach { it.isEnabled = nowUnlocked }
            if (!nowUnlocked) loadFields() // throw away unsaved edits when the lock closes
            wasUnlocked = nowUnlocked
        }
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, BlockerService::class.java)
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == me
        }
    }

    private fun pickApps() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val current = lines(appsField).toMutableSet()
        val checked = BooleanArray(apps.size) { apps[it].first in current }
        AlertDialog.Builder(this)
            .setTitle("Block these apps")
            .setMultiChoiceItems(apps.map { "${it.second}\n${it.first}" }.toTypedArray(), checked) { _, i, c ->
                checked[i] = c
            }
            .setPositiveButton("OK") { _, _ ->
                apps.forEachIndexed { i, a -> if (checked[i]) current.add(a.first) else current.remove(a.first) }
                appsField.setText(current.sorted().joinToString("\n"))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
