package org.focusshield.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var badge: TextView
    private lateinit var guardian: GuardianView
    private lateinit var chips: LinearLayout

    private lateinit var svcDot: View
    private lateinit var svcText: TextView
    private lateinit var svcButton: TextView
    private lateinit var restrictNote: View
    private lateinit var appInfoButton: TextView
    private lateinit var dnsDot: View
    private lateinit var dnsText: TextView
    private lateinit var dnsButton: TextView
    private lateinit var lastAddress: TextView

    private lateinit var rulesTag: TextView
    private lateinit var chargerSwitch: PillSwitch
    private lateinit var guardSwitch: PillSwitch
    private lateinit var dnsSwitch: PillSwitch
    private lateinit var batteryStepper: Stepper
    private lateinit var holdStepper: Stepper
    private lateinit var windowStepper: Stepper
    private lateinit var saveButton: TextView

    private lateinit var packsTag: TextView
    private lateinit var packRows: LinearLayout
    private lateinit var lists: BlockLists
    private lateinit var linesTag: TextView
    private lateinit var customBox: LinearLayout

    /** Unsaved edits to the unlock rules. */
    private var draftCharger = true
    private var draftGuard = true
    private var draftDns = true

    /** When the Guardian was moved and the conditions started being held. */
    private var holdStartedAt: Long? = null
    private var wasUnlocked: Boolean? = null

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        buildUi()
        loadRules()
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

    private fun dp(v: Int) = Ui.dp(this, v)

    // ---------------------------------------------------------------- layout

    private fun buildUi() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(40))
        }

        badge = Ui.text(this, "Locked", 12f, Ui.SAKURA, Ui.MEDIUM).apply {
            setPadding(dp(12), dp(5), dp(12), dp(5))
        }
        col.addView(LinearLayout(this).apply {
            gravity = Gravity.END
            addView(badge)
        })

        guardian = GuardianView(this).apply {
            canMove = { unmetReason() }
            onRefused = { toast("The Guardian won't move: $it") }
            onTooGentle = { toast("Too gentle. Slide further.") }
            onStroke = { refresh() }
            onReleased = { guardianMoved() }
            onReturned = { lockNow() }
        }
        col.addView(guardian, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })

        chips = LinearLayout(this).apply { gravity = Gravity.CENTER }
        col.addView(chips, centred(dp(12)))

        col.addView(protectionCard(), spaced(dp(22)))
        col.addView(rulesCard())
        col.addView(packsCard())
        lists = BlockLists()
        col.addView(lists.card)
        col.addView(linesCard())

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Ui.LACQUER)
            addView(col)
        })
    }

    private fun centred(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = top }

    private fun spaced(top: Int) = centred(top)

    private fun dot() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).apply { rightMargin = dp(12) }
    }

    private fun setDot(v: View, ok: Boolean) {
        v.background = Ui.rounded(this, if (ok) Ui.JADE else Ui.EMBER, 99f)
    }

    private fun statusRow(title: String, dotView: View, desc: TextView, action: View?, divider: Boolean) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(11), 0, dp(11))
            if (divider) background = Ui.topLine(context)
            addView(dotView)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(Ui.text(context, title, 14.5f, Ui.TEXT, Ui.MEDIUM))
                addView(desc)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(10) })
            if (action != null) addView(action)
        }

    private fun note(text: String) = Ui.muted(this, text, 12f).apply {
        background = Ui.rounded(context, Ui.RAISED, 12f)
        setPadding(dp(11), dp(9), dp(11), dp(9))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        }
    }

    private fun protectionCard(): View {
        val card = Ui.card(this)
        card.addView(Ui.heading(this, "Protection"))
        svcDot = dot()
        svcText = Ui.muted(this, "", 12f)
        svcButton = Ui.button(this, "Open settings") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        card.addView(statusRow("Blocking service", svcDot, svcText, svcButton, false))
        dnsDot = dot()
        dnsText = Ui.muted(this, "", 12f)
        dnsButton = Ui.button(this, "Turn on") { startDnsFilter() }
        card.addView(statusRow("DNS filter", dnsDot, dnsText, dnsButton, true))
        lastAddress = Ui.text(this, "", 12f, Ui.MUTED).apply { typeface = android.graphics.Typeface.MONOSPACE }
        card.addView(statusRow("Last address read", dot().apply { background = Ui.rounded(context, Ui.MUTED, 99f) }, lastAddress, null, true))
        restrictNote = note(
            "If Android refuses with \"App was denied access\" or \"Restricted setting\":\n" +
                "1. Tap Open App info below.\n" +
                "2. Tap ⋮ in the top right corner and choose \"Allow restricted settings\", then confirm with your PIN or fingerprint.\n" +
                "3. Come back here, tap Open settings and turn on Threshold.\n" +
                "The ⋮ option only appears after Android has refused once, and it can't be used during a phone call."
        )
        card.addView(restrictNote)
        appInfoButton = Ui.button(this, "Open App info") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        card.addView(appInfoButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        card.addView(note(
            "The DNS filter shows as a VPN, but only website name lookups go through it. Tip: in " +
                "Settings → VPN → Threshold, turn on \"Always-on VPN\" (leave \"Block connections without VPN\" off)."
        ))
        return card
    }

    private fun cardHeader(card: LinearLayout, title: String): TextView {
        val tag = Ui.text(this, "", 11f, Ui.MUTED, Ui.MEDIUM).apply { letterSpacing = 0.08f }
        card.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.heading(context, title), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(tag)
        })
        return tag
    }

    private fun rulesCard(): View {
        val card = Ui.card(this)
        rulesTag = cardHeader(card, "Unlock rules")
        card.addView(Ui.muted(this, "What it takes before the Guardian can be moved. Changing these needs an unlock."))

        chargerSwitch = PillSwitch(this).apply { onChange = { draftCharger = it; renderSave() } }
        card.addView(Ui.row(this, "Charger must be connected", null, chargerSwitch, divider = false), rowParams())

        batteryStepper = Stepper(listOf(0, 10, 20, 30, 40, 50, 60, 70, 75, 80, 85, 90, 95, 100), "%") { renderSave() }
        card.addView(Ui.row(this, "Minimum battery", "0% means no requirement", batteryStepper.view))

        holdStepper = Stepper(listOf(0, 1, 2, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240), " min") { renderSave() }
        card.addView(Ui.row(this, "Hold conditions for", "With this screen open, after the Guardian is moved. 0 = instant", holdStepper.view))

        windowStepper = Stepper(listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240), " min") { renderSave() }
        card.addView(Ui.row(this, "Unlock lasts", "Then blocking turns itself back on", windowStepper.view))

        guardSwitch = PillSwitch(this).apply { onChange = { draftGuard = it; renderSave() } }
        card.addView(Ui.row(this, "Guard against switch-off", "While locked, stop Threshold being turned off or uninstalled", guardSwitch))

        dnsSwitch = PillSwitch(this).apply { onChange = { draftDns = it; renderSave() } }
        card.addView(Ui.row(this, "Use the DNS filter", "Blocks listed sites in every browser and app", dnsSwitch))

        saveButton = Ui.button(this, "Save unlock rules", primary = true) { save() }
        card.addView(saveButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })
        return card
    }

    private fun rowParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(6)
    }

    /** −/+ control that steps through fixed values; an off-list saved value is kept as its own stop. */
    private inner class Stepper(base: List<Int>, private val unit: String, private val changed: () -> Unit) {
        private var values = base
        var value = base.first()
            private set
        private val out = Ui.text(this@MainActivity, "", 13f, Ui.TEXT, Ui.MEDIUM).apply {
            gravity = Gravity.CENTER
            minWidth = dp(60)
        }
        private val minus = arrow("−", -1)
        private val plus = arrow("+", 1)
        val view = LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(context, Ui.RAISED, 999f, Ui.LINE)
            setPadding(dp(2), dp(2), dp(2), dp(2))
            addView(minus); addView(out); addView(plus)
        }

        private fun arrow(s: String, dir: Int) = Ui.text(this@MainActivity, s, 18f, Ui.GOLD, Ui.MEDIUM).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(30))
            isClickable = true
            setOnClickListener { step(dir) }
        }

        fun set(v: Int) {
            if (v !in values) values = (values + v).sorted()
            value = v
            render()
        }

        private fun step(dir: Int) {
            if (!prefs.isUnlocked) {
                toast("Locked. Move the Guardian first to change the unlock rules."); return
            }
            val i = values.indexOf(value) + dir
            if (i in values.indices) {
                value = values[i]; render(); changed()
            }
        }

        fun render() {
            out.text = "$value$unit"
            val open = prefs.isUnlocked
            val i = values.indexOf(value)
            minus.alpha = if (open && i > 0) 1f else 0.35f
            plus.alpha = if (open && i < values.size - 1) 1f else 0.35f
        }
    }

    // ---------------------------------------------------------------- block lists

    private inner class ListSpec(
        val name: String,
        val hint: String,
        val inputHint: String,
        val lowercase: Boolean,
        val get: () -> Set<String>,
        val set: (Set<String>) -> Unit,
    )

    private inner class BlockLists {
        val specs = listOf(
            ListSpec("Apps", "Apps that are closed the moment they open.", "Package name, e.g. com.example.app", false,
                { prefs.blockedApps }, { prefs.blockedApps = it }),
            ListSpec("Keywords", "Any app whose name or package contains one of these is blocked too, so renamed or patched YouTube apps are caught.",
                "Keyword, e.g. tube", true, { prefs.appKeywords }, { prefs.appKeywords = it }),
            ListSpec("Websites", "\"youtube.com\" blocks that site and its subdomains. A word without a dot, like \"invidious\", blocks any address containing it.",
                "Website, e.g. 9gag.com", true, { prefs.blockedSites }, { prefs.blockedSites = it }),
            ListSpec("Browsers", "Installed browsers are found automatically. Add a package name only if one is missed.",
                "Package name", false, { prefs.browsers }, { prefs.browsers = it }),
        )
        var current = 0
        val card = Ui.card(this@MainActivity)
        private val tag: TextView = cardHeader(card, "Block lists")
        private val tabs = LinearLayout(this@MainActivity).apply {
            background = Ui.rounded(context, Ui.RAISED, 14f)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        private val hintText = Ui.muted(this@MainActivity, "")
        private val lockNote = note("")
        private val rows = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        private val input = textInput("")
        private val pick = Ui.button(this@MainActivity, "Choose from installed apps") { pickApps() }
        private val labels = HashMap<String, String>()

        init {
            card.addView(tabs, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
            card.addView(hintText, rowParams().apply { topMargin = dp(10) })
            card.addView(lockNote)
            card.addView(rows, rowParams())
            card.addView(addRow(input) { add() })
            card.addView(pick, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        }

        private fun label(pkg: String): String? = labels.getOrPut(pkg) {
            try {
                packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
            } catch (e: Exception) {
                ""
            }
        }.ifEmpty { null }

        fun render() {
            val open = prefs.isUnlocked
            val spec = specs[current]
            tag.text = if (open) "ADD & REMOVE" else "ADD ONLY"
            tabs.removeAllViews()
            specs.forEachIndexed { i, s ->
                val sel = i == current
                tabs.addView(Ui.text(this@MainActivity, "${s.name}\n${s.get().size}", 12f, if (sel) Ui.GOLD else Ui.MUTED, Ui.MEDIUM).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6), 0, dp(6))
                    background = if (sel) Ui.rounded(context, Ui.SURFACE, 10f) else null
                    isClickable = true
                    setOnClickListener { current = i; render() }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            hintText.text = spec.hint
            lockNote.text = if (open) "✓ Unlocked: untick an item to remove it."
            else "🔒 Locked: you can add, but not remove. Ticks stay on until the Guardian is moved."
            input.hint = spec.inputHint
            pick.visibility = if (current == 0) View.VISIBLE else View.GONE

            rows.removeAllViews()
            val items = spec.get().sortedBy { (if (current == 0) label(it) ?: it else it).lowercase() }
            if (items.isEmpty()) rows.addView(Ui.muted(this@MainActivity, "Nothing on this list yet."))
            items.forEachIndexed { i, item ->
                val name = if (current == 0) label(item) else null
                val box = Ui.tickbox(this@MainActivity).apply { isChecked = true }
                val row = LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                    if (i > 0) background = Ui.topLine(context)
                    addView(box)
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        if (name != null) addView(Ui.text(context, name, 14f, Ui.TEXT, Ui.MEDIUM))
                        addView(Ui.text(context, item, if (name != null) 11.5f else 13f, if (name != null) Ui.MUTED else Ui.TEXT).apply {
                            typeface = android.graphics.Typeface.MONOSPACE
                        })
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                }
                box.setOnCheckedChangeListener { _, checked ->
                    if (checked) return@setOnCheckedChangeListener
                    if (!prefs.isUnlocked) {
                        box.isChecked = true
                        Ui.shake(row)
                        toast("Locked: items can't be removed until you unlock")
                    } else {
                        spec.set(spec.get() - item)
                        toast("Removed ${name ?: item}")
                        render()
                    }
                }
                rows.addView(row)
            }
        }

        private fun add() {
            val spec = specs[current]
            val typed = input.text.toString().split(',', ' ', '\n')
                .map { it.trim().let { t -> if (spec.lowercase) t.lowercase() else t } }
                .filter { it.isNotEmpty() }
                .toSet()
            if (typed.isEmpty()) return
            val new = typed - spec.get()
            spec.set(spec.get() + new)
            input.setText("")
            render()
            toast(if (new.isEmpty()) "Already on the list" else "Added: " + new.joinToString(", "))
        }
    }

    private fun textInput(hintText: String) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        isSingleLine = true
        hint = hintText
        textSize = 14f
        setTextColor(Ui.TEXT)
        setHintTextColor(Ui.MUTED)
        background = Ui.rounded(context, Ui.RAISED, 999f, Ui.LINE)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        imeOptions = EditorInfo.IME_ACTION_DONE
    }

    private fun addRow(input: EditText, onAdd: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        addView(Ui.button(context, "Add", primary = true) { onAdd() })
        input.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { onAdd(); true } else false
        }
    }

    // ---------------------------------------------------------------- known blocklists

    private fun packsCard(): View {
        val card = Ui.card(this)
        packsTag = cardHeader(card, "Known blocklists")
        card.addView(Ui.muted(this, "Ready-made lists of well-known apps and sites, blocked on top of your own lists. Adding works any time; removing needs an unlock."))
        packRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(packRows, rowParams())
        card.addView(Ui.button(this, "Add known blocklist", primary = true) { pickPacks() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        renderPacks()
        return card
    }

    private fun renderPacks() {
        val on = prefs.blocklists
        val added = Blocklists.ALL.filter { it.id in on }
        packsTag.text = "${added.size} OF ${Blocklists.ALL.size} ON"
        packRows.removeAllViews()
        if (added.isEmpty()) packRows.addView(Ui.muted(this, "None added yet."))
        added.forEachIndexed { i, pack ->
            val box = Ui.tickbox(this).apply { isChecked = true }
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
                if (i > 0) background = Ui.topLine(context)
                addView(box)
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Ui.text(context, pack.name, 14f, Ui.TEXT, Ui.MEDIUM))
                    addView(Ui.muted(context, pack.counts, 11.5f))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            box.setOnCheckedChangeListener { _, checked ->
                if (checked) return@setOnCheckedChangeListener
                if (!prefs.isUnlocked) {
                    box.isChecked = true
                    Ui.shake(row)
                    toast("Locked: a blocklist can't be removed until you unlock")
                } else {
                    prefs.blocklists = prefs.blocklists - pack.id
                    toast("Removed ${pack.name}")
                    renderPacks()
                }
            }
            packRows.addView(row)
        }
    }

    /** Offers the known blocklists that aren't on yet. Nothing is added until "Add selected" is tapped. */
    private fun pickPacks() {
        val on = prefs.blocklists
        val choices = Blocklists.ALL.filter { it.id !in on }
        if (choices.isEmpty()) {
            toast("All known blocklists are already on"); return
        }
        val checked = BooleanArray(choices.size)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Add known blocklist")
            .setMultiChoiceItems(choices.map { "${it.name}\n${it.about}" }.toTypedArray(), checked) { _, i, c ->
                checked[i] = c
            }
            .setPositiveButton("Add selected") { _, _ ->
                val chosen = choices.filterIndexed { i, _ -> checked[i] }
                if (chosen.isNotEmpty()) {
                    prefs.blocklists = prefs.blocklists + chosen.map { it.id }
                    renderPacks()
                    toast("Added: " + chosen.joinToString(", ") { it.name })
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------- block screens

    private fun linesCard(): View {
        val card = Ui.card(this)
        linesTag = cardHeader(card, "Block screens")
        card.addView(Ui.muted(this, "The Guardian picks a taunt at random whenever something blocked is opened."))
        val appSwitch = PillSwitch(this)
        val customSwitch = PillSwitch(this)
        appSwitch.set(prefs.appTaunts)
        customSwitch.set(prefs.customTaunts)
        appSwitch.onChange = { on ->
            if (!on && !prefs.customTaunts) { appSwitch.set(true, animate = true); toast("Keep one kind of taunt on") }
            else { prefs.appTaunts = on; renderLines() }
        }
        customSwitch.onChange = { on ->
            if (!on && !prefs.appTaunts) { customSwitch.set(true, animate = true); toast("Keep one kind of taunt on") }
            else { prefs.customTaunts = on; renderLines() }
        }
        card.addView(Ui.row(this, "App taunts", "Threshold's own lines", appSwitch, divider = false), rowParams())
        card.addView(Ui.row(this, "Custom taunts", "Your own lines. Tap one to edit it.", customSwitch))
        customBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(customBox, rowParams())
        val input = textInput("Write your own taunt")
        card.addView(addRow(input) {
            val t = input.text.toString().trim()
            if (t in Defaults.BLOCK_LINES) {
                input.setText("")
                toast("The Guardian already says that one")
            } else if (t.isNotEmpty()) {
                if (t !in prefs.customLines) prefs.customLines = listOf(t) + prefs.customLines
                input.setText("")
                renderLines()
                toast("Added to the Guardian's repertoire")
            }
        })
        card.addView(Ui.button(this, "Preview a block screen") {
            startActivity(Intent(this, BlockedActivity::class.java)
                .putExtra(BlockedActivity.EXTRA_WHAT, "youtube.com")
                .putExtra(BlockedActivity.EXTRA_PREVIEW, true))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        renderLines()
        return card
    }

    /** The user's own taunts, listed under the Custom taunts switch. The app's own are never listed. */
    private fun renderLines() {
        val mine = prefs.customLines
        linesTag.text = if (mine.size == 1) "1 CUSTOM" else "${mine.size} CUSTOM"
        customBox.removeAllViews()
        if (mine.isEmpty()) customBox.addView(Ui.muted(this, "None yet. Write one below."))
        mine.forEachIndexed { i, line ->
            customBox.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                if (i > 0) background = Ui.topLine(context)
                addView(Ui.text(context, line, 14.5f, Ui.TEXT, Ui.DISPLAY).apply {
                    setPadding(0, dp(11), dp(8), dp(11))
                    contentDescription = "Edit taunt: $line"
                    isClickable = true
                    setOnClickListener { editLine(line) }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(Ui.text(context, "×", 20f, Ui.MUTED).apply {
                    gravity = Gravity.CENTER
                    contentDescription = "Delete taunt: $line"
                    isClickable = true
                    setOnClickListener { deleteLine(line) }
                }, LinearLayout.LayoutParams(dp(44), dp(44)))
            })
        }
    }

    private fun editLine(old: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(old)
            setSelection(old.length)
            setTextColor(Ui.TEXT)
        }
        val holder = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Edit taunt")
            .setView(holder)
            .setPositiveButton("Save") { _, _ ->
                val t = input.text.toString().replace('\n', ' ').trim()
                val lines = prefs.customLines
                when {
                    t.isEmpty() || t == old -> {}
                    t in Defaults.BLOCK_LINES -> toast("The Guardian already says that one")
                    t in lines -> toast("You already have that one")
                    else -> {
                        prefs.customLines = lines.map { if (it == old) t else it }
                        renderLines()
                        toast("Saved")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteLine(line: String) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Delete this taunt?")
            .setMessage(line)
            .setPositiveButton("Delete") { _, _ ->
                prefs.customLines = prefs.customLines - line
                renderLines()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------------------- state

    private fun loadRules() {
        draftCharger = prefs.requireCharger
        draftGuard = prefs.guardSettings
        draftDns = prefs.dnsFilter
        chargerSwitch.set(draftCharger)
        guardSwitch.set(draftGuard)
        dnsSwitch.set(draftDns)
        batteryStepper.set(prefs.minBattery)
        holdStepper.set(prefs.waitMinutes)
        windowStepper.set(prefs.unlockMinutes)
        renderSave()
    }

    private fun dirty() = draftCharger != prefs.requireCharger || draftGuard != prefs.guardSettings ||
        draftDns != prefs.dnsFilter || batteryStepper.value != prefs.minBattery ||
        holdStepper.value != prefs.waitMinutes || windowStepper.value != prefs.unlockMinutes

    private fun renderSave() {
        val open = prefs.isUnlocked
        listOf(chargerSwitch, guardSwitch, dnsSwitch).forEach { it.isEnabled = open }
        listOf(batteryStepper, holdStepper, windowStepper).forEach { it.render() }
        rulesTag.text = if (open) "EDITABLE" else "🔒 LOCKED"
        val d = dirty()
        saveButton.text = if (d) "Save unlock rules" else "Unlock rules saved"
        saveButton.alpha = if (open && d) 1f else 0.45f
    }

    private fun save() {
        if (!prefs.isUnlocked) {
            toast("Locked. Move the Guardian first to change the unlock rules.")
            return
        }
        if (!dirty()) return
        prefs.requireCharger = draftCharger
        prefs.minBattery = batteryStepper.value
        prefs.waitMinutes = holdStepper.value
        prefs.unlockMinutes = windowStepper.value
        prefs.guardSettings = draftGuard
        prefs.dnsFilter = draftDns
        if (prefs.dnsFilter) DnsVpnService.ensureRunning(this) else DnsVpnService.stop(this)
        loadRules()
        toast("Saved")
    }

    private data class Cond(val ok: Boolean, val label: String)

    private fun conditions(): List<Cond> {
        val b = readBattery(this)
        val out = ArrayList<Cond>()
        if (prefs.requireCharger) out.add(Cond(b.pluggedIn, if (b.pluggedIn) "Charger connected" else "Charger not connected"))
        if (prefs.minBattery > 0) out.add(Cond(b.percent >= prefs.minBattery, "Battery ${b.percent}% of ${prefs.minBattery}%"))
        return out
    }

    private fun unmetReason(): String? =
        conditions().filter { !it.ok }.joinToString(", ") { it.label.lowercase() }.ifEmpty { null }

    private fun guardianMoved() {
        if (unmetReason() != null) {
            refresh(); return
        }
        if (prefs.waitMinutes == 0) {
            prefs.unlock()
            toast("Unlocked for ${prefs.unlockMinutes} min")
        } else {
            holdStartedAt = SystemClock.elapsedRealtime()
        }
        refresh()
    }

    private fun lockNow() {
        prefs.lockNow()
        holdStartedAt = null
        toast("Guardian returned. Blocking is on.")
        refresh()
    }

    private fun refresh() {
        // Protection
        val on = isServiceEnabled()
        setDot(svcDot, on)
        svcText.text = if (on) "Running" else "Off. Blocking won't work until you turn on Threshold in Accessibility settings."
        svcButton.visibility = if (on) View.GONE else View.VISIBLE
        restrictNote.visibility = if (on) View.GONE else View.VISIBLE
        appInfoButton.visibility = restrictNote.visibility
        val dnsOn = DnsVpnService.running
        setDot(dnsDot, dnsOn || !prefs.dnsFilter)
        dnsText.text = when {
            !prefs.dnsFilter -> "Switched off in the unlock rules"
            dnsOn -> "Running. Lookups for blocked sites fail in every app."
            else -> "Not running. Tap Turn on and allow the connection request."
        }
        dnsButton.visibility = if (prefs.dnsFilter && !dnsOn) View.VISIBLE else View.GONE
        lastAddress.text = prefs.lastAddress.ifEmpty { "none yet" }

        // Hold countdown after the Guardian was moved
        val conds = conditions()
        val met = conds.all { it.ok }
        val hold = holdStartedAt
        var holdLeft = 0L
        if (!prefs.isUnlocked && hold != null) {
            if (!met) {
                holdStartedAt = null
                toast("Conditions broke. The Guardian is back.")
            } else {
                holdLeft = prefs.waitMinutes * 60_000L - (SystemClock.elapsedRealtime() - hold)
                if (holdLeft <= 0) {
                    holdStartedAt = null
                    prefs.unlock()
                    toast("Unlocked for ${prefs.unlockMinutes} min")
                }
            }
        }

        val unlocked = prefs.isUnlocked
        when {
            unlocked -> {
                val left = prefs.unlockedUntil - System.currentTimeMillis()
                val total = prefs.unlockMinutes * 60_000f
                guardian.show(GuardianView.Phase.UNLOCKED, (left / total), fmt(left), "Blocking paused")
                setBadge("Unlocked " + fmt(left), Ui.GOLD)
            }
            holdStartedAt != null -> {
                val total = prefs.waitMinutes * 60_000f
                guardian.show(GuardianView.Phase.HOLDING, holdLeft / total, fmt(holdLeft), "Hold the conditions")
                setBadge("Holding", Ui.SAKURA)
            }
            else -> {
                guardian.show(GuardianView.Phase.LOCKED, 0f, "", "")
                setBadge("Locked", Ui.SAKURA)
            }
        }
        chips.removeAllViews()
        if (conds.isEmpty()) chips.addView(chip("No unlock conditions set", Ui.MUTED))
        conds.forEach { chips.addView(chip((if (it.ok) "✓ " else "✗ ") + it.label, if (it.ok) Ui.JADE else Ui.EMBER)) }

        if (wasUnlocked != unlocked) {
            wasUnlocked = unlocked
            loadRules() // drops unsaved rule edits and enables or disables the controls
            lists.render()
        }
    }

    private fun chip(s: String, color: Int) = Ui.text(this, s, 12.5f, color, Ui.MEDIUM).apply {
        background = Ui.rounded(context, Ui.RAISED, 999f, Color.argb(90, Color.red(color), Color.green(color), Color.blue(color)))
        setPadding(dp(11), dp(6), dp(11), dp(6))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(4); rightMargin = dp(4)
        }
    }

    private fun setBadge(s: String, color: Int) {
        badge.text = s
        badge.setTextColor(color)
        badge.background = Ui.rounded(this, Color.TRANSPARENT, 999f, color)
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
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(if (unlocked) "Block these apps" else "Add apps to block")
            .setMultiChoiceItems(apps.map { "${it.second}\n${it.first}" }.toTypedArray(), checked) { _, i, c ->
                checked[i] = c
            }
            .setPositiveButton("Block selected") { _, _ ->
                val chosen = apps.filterIndexed { i, _ -> checked[i] }.map { it.first }.toSet()
                prefs.blockedApps = if (unlocked) {
                    current - apps.map { it.first }.toSet() + chosen
                } else {
                    current + chosen
                }
                lists.render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_VPN = 1
    }
}
