package org.focusshield.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BlockerService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private val labelCache = HashMap<String, String>()
    private var lastCheckAt = 0L
    private var lastBlockAt = 0L

    override fun onServiceConnected() {
        prefs = Prefs(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::prefs.isInitialized) prefs = Prefs(this)
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || prefs.isUnlocked) return

        val now = SystemClock.uptimeMillis()
        if (now - lastBlockAt < 1500) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && isBlockedApp(pkg)) {
            block(appLabel(pkg))
            return
        }

        // Content-change events arrive in bursts; checking a few times a second is plenty.
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && now - lastCheckAt < 400) return
        lastCheckAt = now

        when {
            pkg in prefs.browsers -> checkBrowser(pkg)
            prefs.guardSettings && pkg in Defaults.INSTALLER_PACKAGES -> checkInstaller()
            prefs.guardSettings && pkg in Defaults.SETTINGS_PACKAGES -> checkSettings()
        }
    }

    override fun onInterrupt() {}

    private fun isBlockedApp(pkg: String): Boolean {
        if (pkg in prefs.blockedApps) return true
        val keywords = prefs.appKeywords
        if (keywords.isEmpty()) return false
        val p = pkg.lowercase()
        val label = appLabel(pkg).lowercase()
        return keywords.any { k ->
            val kw = k.trim().lowercase()
            kw.isNotEmpty() && (p.contains(kw) || label.contains(kw))
        }
    }

    private fun appLabel(pkg: String): String = labelCache.getOrPut(pkg) {
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private fun checkBrowser(pkg: String) {
        val root = rootInActiveWindow ?: return
        val bar = findUrlBar(root, pkg) ?: return
        // While the address bar has focus the user is still typing, so it isn't a loaded page yet.
        if (bar.isFocused) return
        val host = hostOf(bar.text?.toString() ?: return) ?: return
        val hit = siteMatches(host, prefs.blockedSites) ?: return
        block(hit)
    }

    private fun findUrlBar(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        for (id in Defaults.URL_BAR_IDS) {
            val found = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            val withText = found.firstOrNull { !it.text.isNullOrBlank() }
            if (withText != null) return withText
        }
        return null
    }

    private fun checkInstaller() {
        val root = rootInActiveWindow ?: return
        val texts = collectTexts(root)
        if (texts.any { it.contains(getString(R.string.app_name), ignoreCase = true) }) {
            block(getString(R.string.blocked_reason_protect))
        }
    }

    private fun checkSettings() {
        val root = rootInActiveWindow ?: return
        val texts = collectTexts(root)
        val name = getString(R.string.app_name)
        if (texts.none { it.contains(name, ignoreCase = true) }) return
        // Only screens that can switch us off: the accessibility toggle page and App info.
        val dangerous = listOf("uninstall", "force stop", "clear storage", "clear data", "disable", "turn off")
        if (hasSwitch(root, 0) || texts.any { t -> dangerous.any { t.contains(it, ignoreCase = true) } }) {
            block(getString(R.string.blocked_reason_protect))
        }
    }

    private fun collectTexts(root: AccessibilityNodeInfo): List<String> {
        val out = ArrayList<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 600) {
            val n = queue.removeFirst()
            visited++
            n.text?.let { out.add(it.toString()) }
            n.contentDescription?.let { out.add(it.toString()) }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return out
    }

    private fun hasSwitch(n: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 30) return false
        val cls = n.className?.toString() ?: ""
        if (cls.endsWith("Switch") || cls.endsWith("SwitchCompat") || cls.endsWith("ToggleButton")) return true
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            if (hasSwitch(c, depth + 1)) return true
        }
        return false
    }

    private fun block(what: String) {
        lastBlockAt = SystemClock.uptimeMillis()
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockedActivity::class.java)
                .putExtra(BlockedActivity.EXTRA_WHAT, what)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }
}
