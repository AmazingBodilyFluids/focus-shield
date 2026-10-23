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

    /** Home screens and system UI are never blocked, so a too-broad keyword can't trap the phone. */
    private val neverBlock: Set<String> by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }.toSet() +
            setOf("com.android.systemui", "android", "com.android.phone", "com.android.dialer", "com.google.android.dialer")
    }

    private fun isBlockedApp(pkg: String): Boolean {
        if (pkg in neverBlock) return false
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
        val host = hostOf(nodeText(bar) ?: return) ?: return
        prefs.recordLastAddress(pkg, host)
        val hit = siteMatches(host, prefs.blockedSites) ?: return
        block(hit)
    }

    private fun nodeText(n: AccessibilityNodeInfo): String? =
        n.text?.toString()?.takeIf { it.isNotBlank() }
            ?: n.contentDescription?.toString()?.takeIf { it.isNotBlank() }

    private fun findUrlBar(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        for (id in Defaults.URL_BAR_IDS) {
            val found = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            val withText = found.firstOrNull { nodeText(it) != null }
            if (withText != null) return withText
        }
        return findUrlBarByScan(root)
    }

    /**
     * Fallback for browsers whose toolbar has no known view id (newer Firefox builds draw it
     * with Jetpack Compose). Walks the browser's own UI, skipping the web page itself, and
     * prefers a node whose id mentions the address bar, then any text that looks like a URL.
     */
    private fun findUrlBarByScan(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val hints = listOf("url", "address", "omnibox", "location", "toolbar")
        var looksLikeUrl: AccessibilityNodeInfo? = null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 400) {
            val n = queue.removeFirst()
            visited++
            val cls = n.className?.toString() ?: ""
            if (cls.contains("WebView") || cls.contains("GeckoView")) continue
            val text = nodeText(n)
            if (text != null) {
                val id = n.viewIdResourceName?.lowercase() ?: ""
                if (hints.any { id.contains(it) } && hostOf(text) != null) return n
                if (looksLikeUrl == null && hostOf(text) != null) looksLikeUrl = n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return looksLikeUrl
    }

    private fun checkInstaller() {
        val root = rootInActiveWindow ?: return
        val texts = collectTexts(root)
        val name = getString(R.string.app_name)
        // Installing an update over the top is fine; only the uninstall prompt is stopped.
        if (texts.any { it.contains(name, ignoreCase = true) } &&
            texts.any { it.contains("uninstall", ignoreCase = true) }
        ) {
            block(getString(R.string.blocked_reason_protect))
        }
    }

    private fun checkSettings() {
        val root = rootInActiveWindow ?: return
        val texts = collectTexts(root).map { it.lowercase() }
        val name = getString(R.string.app_name).lowercase()
        if (texts.none { it.contains(name) }) return
        // Only screens that can switch Focus Shield off: its own accessibility page (which has
        // "Use Focus Shield" / "Focus Shield shortcut"), the stop/turn-off confirmation, and
        // its App info page. Lists that merely mention it, like the Accessibility menu, stay usable.
        val ownPage = listOf("use $name", "$name shortcut", "stop $name", "turn off $name")
        val appInfo = listOf("uninstall", "force stop", "clear storage", "clear data")
        if (texts.any { t -> ownPage.any { t.contains(it) } } ||
            texts.any { t -> appInfo.any { t.contains(it) } }
        ) {
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
