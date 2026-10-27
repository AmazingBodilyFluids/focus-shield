package org.focusshield.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.VpnService
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
    private lateinit var lastAddress: TextView
    private lateinit var dnsStatus: TextView
    private lateinit var dnsButton: Button
    private lateinit var dnsBox: CheckBox
    private lateinit var lockStatus: TextView
    private lateinit var conditions: TextView
    private lateinit var unlockButton: Button
    private lateinit var lockButton: Button

    private lateinit var appsList: ListSection
    private lateinit var keywordsList: ListSection
    private lateinit var sitesList: ListSection
    private lateinit var browsersList: ListSection
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

        lastAddress = text("", small = true)
        col.addView(lastAddress)

        col.addView(header("DNS filter"))
        col.addView(text(
            "Stops the blocked websites loading in every browser and app, including ones Focus Shield " +
                "can't see. Android shows it as a VPN, but only website name lookups go through it and " +
                "nothing leaves your phone except normal lookups. Tip: in Settings → VPN → Focus Shield, " +
                "turn on \"Always-on VPN\" (leave \"Block connections without VPN\" off).", small = true
        ))
        dnsStatus = text("")
        col.addView(dnsStatus)
        dnsButton = button("Turn on DNS filter") { startDnsFilter() }
        col.addView(dnsButton)

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

        appsList = ListSection(
            "Blocked apps", null, "Package name, e.g. com.example.app", lowercase = false,
            get = { prefs.blockedApps }, set = { prefs.blockedApps = it },
        )
        appsList.addTo(col)
        col.addView(button("Choose from installed apps") { pickApps() })

        keywordsList = ListSection(
            "App name keywords",
            "Any app whose name or package contains one of these is blocked too, so renamed or patched YouTube apps are caught.",
            "Keyword, e.g. tube", lowercase = true,
            get = { prefs.appKeywords }, set = { prefs.appKeywords = it },
        )
        keywordsList.addTo(col)

        sitesList = ListSection(
            "Blocked websites",
            "\"youtube.com\" blocks that site and its subdomains. A word without a dot, like \"invidious\", blocks any site with that word in its address.",
            "Website, e.g. 9gag.com", lowercase = true,
            get = { prefs.blockedSites }, set = { prefs.blockedSites = it },
        )
        sitesList.addTo(col)

        browsersList = ListSection(
            "Extra browsers to watch",
            "Installed browsers are found automatically. Add a package name here only if one is missed.",
            "Package name", lowercase = false,
            get = { prefs.browsers }, set = { prefs.browsers = it },
        )
        browsersList.addTo(col)

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
        dnsBox = CheckBox(this).apply { text = "Use the DNS filter" }
        col.addView(dnsBox)

        saveButton = button("Save unlock rules") { save() }
        col.addView(saveButton)

        // Block lists can always be added to; only the unlock rules need an unlock to change.
        editable.addAll(listOf(chargerBox, batteryField, waitField, unlockMinutesField, guardBox, dnsBox, saveButton))

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

    /**
     * One block list: its items as read-only rows plus an Add box. Adding always works;
     * the Remove buttons only exist while unlocked (and Prefs refuses removals when locked).
     */
    private inner class ListSection(
        private val title: String,
        private val hint: String?,
        private val inputHint: String,
        private val lowercase: Boolean,
        private val get: () -> Set<String>,
        private val set: (Set<String>) -> Unit,
    ) {
        private val rows = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        private val lockNote = text("", small = true)
        private val input = EditText(this@MainActivity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = true
            this.hint = inputHint
            textSize = 14f
        }

        fun addTo(col: LinearLayout) {
            col.addView(header(title))
            hint?.let { col.addView(text(it, small = true)) }
            col.addView(lockNote)
            col.addView(rows)
            col.addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(button("Add") { add() })
            })
        }

        private fun add() {
            val typed = input.text.toString().split(',', ' ', '\n')
                .map { it.trim().let { t -> if (lowercase) t.lowercase() else t } }
                .filter { it.isNotEmpty() }
                .toSet()
            if (typed.isEmpty()) return
            val new = typed - get()
            set(get() + new)
            input.setText("")
            render()
            toast(if (new.isEmpty()) "Already on the list" else "Added: " + new.joinToString(", "))
        }

        private fun remove(item: String) {
            if (!prefs.isUnlocked) {
                toast("Locked: items can't be removed until you unlock")
            } else {
                set(get() - item)
            }
            render()
        }

        fun render() {
            val unlocked = prefs.isUnlocked
            lockNote.text = if (unlocked) "Unlocked: you can add and remove." else "🔒 Locked: you can add, but not remove."
            rows.removeAllViews()
            for (item in get().sortedBy { it.lowercase() }) {
                rows.addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(2), 0, dp(2))
                    addView(TextView(context).apply {
                        text = item
                        textSize = 14f
                        setTypeface(Typeface.MONOSPACE)
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    if (unlocked) addView(button("Remove") { remove(item) })
                })
            }
            if (get().isEmpty()) rows.addView(text("(empty)", small = true))
        }
    }

    private fun number() = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER }

    // ---- State ----

    private fun loadFields() {
        listOf(appsList, keywordsList, sitesList, browsersList).forEach { it.render() }
        chargerBox.isChecked = prefs.requireCharger
        batteryField.setText(prefs.minBattery.toString())
        waitField.setText(prefs.waitMinutes.toString())
        unlockMinutesField.setText(prefs.unlockMinutes.toString())
        guardBox.isChecked = prefs.guardSettings
        dnsBox.isChecked = prefs.dnsFilter
    }

    private fun save() {
        if (!prefs.isUnlocked) {
            toast("Locked. Unlock first to change the unlock rules.")
            return
        }
        prefs.requireCharger = chargerBox.isChecked
        prefs.minBattery = batteryField.text.toString().toIntOrNull() ?: 0
        prefs.waitMinutes = waitField.text.toString().toIntOrNull() ?: 0
        prefs.unlockMinutes = unlockMinutesField.text.toString().toIntOrNull() ?: 15
        prefs.guardSettings = guardBox.isChecked
        prefs.dnsFilter = dnsBox.isChecked
        if (prefs.dnsFilter) DnsVpnService.ensureRunning(this) else DnsVpnService.stop(this)
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

        val dnsOn = DnsVpnService.running
        dnsStatus.text = when {
            !prefs.dnsFilter -> "Switched off in the unlock rules."
            dnsOn -> "✓ Running"
            else -> "✗ Not running. Tap the button below and allow the connection request."
        }
        dnsStatus.setTextColor(if (dnsOn || !prefs.dnsFilter) Color.parseColor("#2E7D32") else Color.RED)
        dnsButton.visibility = if (prefs.dnsFilter && !dnsOn) View.VISIBLE else View.GONE

        val seen = prefs.lastAddress
        lastAddress.text = "Last website address read from a browser: " + seen.ifEmpty { "none yet" }

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
            conditions.text = "You can add and remove list items, and change the unlock rules (remember to save them)."
        } else {
            lockStatus.text = "Locked. Blocking is on."
            lockStatus.setTextColor(Color.parseColor("#2E7D32"))
            conditions.text = "You can still add to the block lists below. Removing items or changing the unlock rules needs an unlock.\n\nTo unlock:\n$summary$holdLine"
        }
        unlockButton.visibility = if (nowUnlocked) View.GONE else View.VISIBLE
        unlockButton.isEnabled = ok && holdStartedAt == null
        lockButton.visibility = if (nowUnlocked) View.VISIBLE else View.GONE

        if (wasUnlocked != nowUnlocked) {
            editable.forEach { it.isEnabled = nowUnlocked }
            loadFields() // shows or hides the Remove buttons, and drops unsaved rule edits
            wasUnlocked = nowUnlocked
        }
    }

    /** Turning the filter on only makes blocking stricter, so it's allowed while locked. */
    private fun startDnsFilter() {
        val consent = VpnService.prepare(this)
        if (consent != null) {
            startActivityForResult(consent, REQ_VPN)
        } else {
            DnsVpnService.ensureRunning(this)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) DnsVpnService.ensureRunning(this)
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
        val unlocked = prefs.isUnlocked
        val current = prefs.blockedApps
        val apps = packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { it.first != packageName }
            // While locked only unblocked apps are offered, since nothing can be taken off.
            .filter { unlocked || it.first !in current }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val checked = BooleanArray(apps.size) { apps[it].first in current }
        AlertDialog.Builder(this)
            .setTitle(if (unlocked) "Block these apps" else "Add apps to block")
            .setMultiChoiceItems(apps.map { "${it.second}\n${it.first}" }.toTypedArray(), checked) { _, i, c ->
                checked[i] = c
            }
            .setPositiveButton("OK") { _, _ ->
                val chosen = apps.filterIndexed { i, _ -> checked[i] }.map { it.first }.toSet()
                prefs.blockedApps = if (unlocked) {
                    current - apps.map { it.first }.toSet() + chosen
                } else {
                    current + chosen
                }
                appsList.render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_VPN = 1
    }
}
