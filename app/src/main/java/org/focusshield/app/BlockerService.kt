package org.focusshield.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BlockerService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private val labelCache = HashMap<String, String>()
    private var lastCheckAt = 0L
    private var lastBlockAt = 0L
    private var browserCache: Set<String> = emptySet()
    private var browserCacheAt = 0L
    private var lastDnsCheckAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        prefs = Prefs(this)
        DnsVpnService.ensureRunning(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::prefs.isInitialized) prefs = Prefs(this)
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || prefs.isUnlocked) return

        val now = SystemClock.uptimeMillis()
        if (now - lastBlockAt < 1500) return
        if (now - lastDnsCheckAt > 60_000) {
            // Restart the DNS filter if Android stopped it.
            lastDnsCheckAt = now
            DnsVpnService.ensureRunning(this)
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && isBlockedApp(pkg)) {
            block(appLabel(pkg))
            return
        }

        // Content-change events arrive in bursts; checking a few times a second is plenty.
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && now - lastCheckAt < 400) return
        lastCheckAt = now

        when {
            prefs.guardSettings && pkg in Defaults.INSTALLER_PACKAGES -> checkInstaller()
            prefs.guardSettings && pkg in Defaults.SETTINGS_PACKAGES -> checkSettings()
            pkg in prefs.browsers || pkg in installedBrowsers() -> checkBrowser(pkg)
            pkg !in neverBlock -> checkInAppBrowser(pkg)
        }
    }

    override fun onInterrupt() {}

    /** Home screens and system UI are never blocked, so a too-broad keyword can't trap the phone. */
    private val neverBlock: Set<String> by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }.toSet() +
            setOf("com.android.systemui", "android", "com.android.phone", "com.android.dialer", "com.google.android.dialer")
    }

    /** Every installed app that can open a web link, so new or unusual browsers are covered too. */
    private fun installedBrowsers(): Set<String> {
        val now = SystemClock.uptimeMillis()
        if (browserCacheAt == 0L || now - browserCacheAt > 5 * 60_000L) {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
            browserCache = packageManager.queryIntentActivities(view, PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }.toSet() - packageName
            browserCacheAt = now
        }
        return browserCache
    }

    /**
     * Apps with a built-in browser (Telegram, Reddit, the Google app...) usually print the
     * site's address in their own toolbar above the page. Only checked when a web page is on
     * screen, and the page content itself is skipped, so a link in a chat doesn't count.
     */
    private fun checkInAppBrowser(pkg: String) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        var hasWeb = false
        val hosts = ArrayList<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 400) {
            val n = queue.removeFirst()
            visited++
            val cls = n.className?.toString() ?: ""
            if (cls.contains("WebView") || cls.contains("GeckoView")) {
                hasWeb = true
                continue
            }
            val text = nodeText(n)
            if (text != null && !text.contains('@')) {
                hostOf(text)?.takeIf { looksLikeHost(it) }?.let { hosts.add(it) }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        if (!hasWeb) return
        val sites = prefs.blockedSites
        for (h in hosts) {
            val hit = siteMatches(h, sites) ?: continue
            prefs.recordLastAddress(pkg, h)
            lastBlockAt = SystemClock.uptimeMillis()
            performGlobalAction(GLOBAL_ACTION_BACK)
            showBlocked(hit)
            return
        }
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
        blockTab(bar, hit)
    }

    /**
     * Sends the tab to about:blank by typing into the address bar, then shows the Blocked
     * screen. Closing the whole browser instead would loop: reopening it restores the
     * blocked tab, which closes the browser again before the tab can be closed.
     */
    private fun blockTab(bar: AccessibilityNodeInfo, what: String) {
        lastBlockAt = SystemClock.uptimeMillis()
        if (Build.VERSION.SDK_INT < 30 || (!bar.isEditable && !clickNodeOrParent(bar))) {
            block(what)
            return
        }
        handler.postDelayed({
            val input = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            // Some browsers keep the address bar editable in place; others open a separate edit box.
            val sent = typeBlank(input) || typeBlank(bar)
            lastBlockAt = SystemClock.uptimeMillis()
            if (sent) handler.postDelayed({ showBlocked(what) }, 600) else block(what)
        }, 400)
    }

    private fun typeBlank(input: AccessibilityNodeInfo?): Boolean {
        if (input == null || !input.isEditable) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "about:blank")
        }
        return input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) &&
            input.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 5) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            depth++
        }
        return false
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
        val ownPage = listOf(
            "use $name", "$name shortcut", "stop $name", "turn off $name",
            // VPN details page and the disconnect dialog for the DNS filter
            "forget vpn", "always-on vpn", "disconnect",
        )
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
        showBlocked(what)
    }

    private fun showBlocked(what: String) {
        startActivity(
            Intent(this, BlockedActivity::class.java)
                .putExtra(BlockedActivity.EXTRA_WHAT, what)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }
}
