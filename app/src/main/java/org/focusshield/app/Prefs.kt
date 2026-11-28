package org.focusshield.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager

/** All settings live in one SharedPreferences file. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("focusshield", Context.MODE_PRIVATE)

    init {
        if (!sp.contains(KEY_APPS)) {
            // First run: load the defaults and give a setup window so the lists can be edited.
            sp.edit()
                .putStringSet(KEY_APPS, Defaults.APPS)
                .putStringSet(KEY_KEYWORDS, Defaults.APP_KEYWORDS)
                .putStringSet(KEY_SITES, Defaults.SITES)
                .putStringSet(KEY_BROWSERS, Defaults.BROWSERS)
                .putLong(KEY_UNLOCKED_UNTIL, System.currentTimeMillis() + 30 * 60_000L)
                .apply()
        }
    }

    var blockedApps: Set<String>
        get() = sp.getStringSet(KEY_APPS, emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet(KEY_APPS, addOnlyWhileLocked(blockedApps, v)).apply()

    /** Matched against package names and app labels, so renamed/patched clients are caught too. */
    var appKeywords: Set<String>
        get() = sp.getStringSet(KEY_KEYWORDS, emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet(KEY_KEYWORDS, addOnlyWhileLocked(appKeywords, v)).apply()

    var blockedSites: Set<String>
        get() = sp.getStringSet(KEY_SITES, emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet(KEY_SITES, addOnlyWhileLocked(blockedSites, v)).apply()

    var browsers: Set<String>
        get() = sp.getStringSet(KEY_BROWSERS, emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet(KEY_BROWSERS, addOnlyWhileLocked(browsers, v)).apply()

    /** Ids of the known blocklists that are on (see [Blocklists]). Add-only while locked, like the lists. */
    var blocklists: Set<String>
        get() = sp.getStringSet(KEY_BLOCKLISTS, emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet(KEY_BLOCKLISTS, addOnlyWhileLocked(blocklists, v)).apply()

    /** True when [pkg] is on the user's Apps list or in a known blocklist that is on. */
    fun isAppListed(pkg: String): Boolean = pkg in blockedApps || pkg in Blocklists.merged(blocklists).apps

    /** The user's keywords plus those of the known blocklists that are on. */
    val allAppKeywords: Set<String>
        get() = appKeywords + Blocklists.merged(blocklists).appKeywords

    /**
     * The entry that blocks [host]: from the user's Websites list or a known blocklist that is on.
     * With [dns] the entries a blocklist marks as browser-only are skipped.
     */
    fun blockedSiteFor(host: String, dns: Boolean = false): String? {
        siteMatches(host, blockedSites)?.let { return it }
        val known = Blocklists.merged(blocklists)
        return siteMatches(host, known.sites) ?: if (dns) null else siteMatches(host, known.pageOnlySites)
    }

    var requireCharger: Boolean
        get() = sp.getBoolean(KEY_REQ_CHARGER, true)
        set(v) = sp.edit().putBoolean(KEY_REQ_CHARGER, v).apply()

    /** 0 means no battery requirement. */
    var minBattery: Int
        get() = sp.getInt(KEY_MIN_BATTERY, 80)
        set(v) = sp.edit().putInt(KEY_MIN_BATTERY, v.coerceIn(0, 100)).apply()

    /** Minutes the conditions must hold, with this screen open, before unlocking. */
    var waitMinutes: Int
        get() = sp.getInt(KEY_WAIT, 0)
        set(v) = sp.edit().putInt(KEY_WAIT, v.coerceIn(0, 240)).apply()

    /** How long an unlock lasts before blocking switches itself back on. */
    var unlockMinutes: Int
        get() = sp.getInt(KEY_UNLOCK_MINUTES, 15)
        set(v) = sp.edit().putInt(KEY_UNLOCK_MINUTES, v.coerceIn(1, 24 * 60)).apply()

    /** Stops the app being switched off or uninstalled through Settings while locked. */
    /** Also answer DNS lookups for blocked sites with "no such site", so they fail in every app. */
    var dnsFilter: Boolean
        get() = sp.getBoolean(KEY_DNS_FILTER, true)
        set(v) = sp.edit().putBoolean(KEY_DNS_FILTER, v).apply()

    var guardSettings: Boolean
        get() = sp.getBoolean(KEY_GUARD, true)
        set(v) = sp.edit().putBoolean(KEY_GUARD, v).apply()

    /** Lines the user wrote in the Block screens card, newest first. */
    var customLines: List<String>
        get() {
            sp.getString(KEY_CUSTOM_LINES, null)?.let { s -> return s.split(LINE_SEP).filter { it.isNotBlank() } }
            // Older builds kept the starters and the user's lines in one list: keep only the user's.
            val old = sp.getString(KEY_LINES, null)?.split(LINE_SEP)?.filter { it.isNotBlank() } ?: emptyList()
            val mine = old.filter { it !in Defaults.RETIRED_LINES && it !in Defaults.BLOCK_LINES }
            sp.edit().putString(KEY_CUSTOM_LINES, mine.joinToString(LINE_SEP)).remove(KEY_LINES).apply()
            return mine
        }
        set(v) = sp.edit().putString(KEY_CUSTOM_LINES, v.joinToString(LINE_SEP)).apply()

    /** Block screens card: use the app's built-in taunts. */
    var appTaunts: Boolean
        get() = sp.getBoolean(KEY_APP_TAUNTS, true)
        set(v) = sp.edit().putBoolean(KEY_APP_TAUNTS, v).apply()

    /** Block screens card: use the user's own taunts. */
    var customTaunts: Boolean
        get() = sp.getBoolean(KEY_CUSTOM_TAUNTS, true)
        set(v) = sp.edit().putBoolean(KEY_CUSTOM_TAUNTS, v).apply()

    /** A random line from the kinds switched on, avoiding [not] when there is a choice. Falls back to the app's own. */
    fun randomLine(not: String? = null): String {
        val on = ((if (customTaunts) customLines else emptyList()) + (if (appTaunts) Defaults.BLOCK_LINES else emptyList()))
            .distinct().ifEmpty { Defaults.BLOCK_LINES }
        val pool = on.filter { it != not }.ifEmpty { on }
        return pool.random()
    }

    var unlockedUntil: Long
        get() = sp.getLong(KEY_UNLOCKED_UNTIL, 0L)
        set(v) = sp.edit().putLong(KEY_UNLOCKED_UNTIL, v).apply()

    /** What the blocker last read from a browser's address bar, shown in the app for checking. */
    val lastAddress: String
        get() = sp.getString(KEY_LAST_ADDRESS, "") ?: ""

    fun recordLastAddress(browser: String, host: String) {
        val v = "$host  ($browser)"
        if (v != lastAddress) sp.edit().putString(KEY_LAST_ADDRESS, v).apply()
    }

    /** While locked a list can only grow: anything missing from the new value is kept. */
    private fun addOnlyWhileLocked(old: Set<String>, new: Set<String>): Set<String> =
        if (isUnlocked) new else old + new

    val isUnlocked: Boolean
        get() = System.currentTimeMillis() < unlockedUntil

    fun lockNow() {
        unlockedUntil = 0L
    }

    fun unlock() {
        unlockedUntil = System.currentTimeMillis() + unlockMinutes * 60_000L
    }

    companion object {
        private const val KEY_APPS = "apps"
        private const val KEY_KEYWORDS = "app_keywords"
        private const val KEY_SITES = "sites"
        private const val KEY_BROWSERS = "browsers"
        private const val KEY_BLOCKLISTS = "blocklists"
        private const val KEY_REQ_CHARGER = "require_charger"
        private const val KEY_MIN_BATTERY = "min_battery"
        private const val KEY_WAIT = "wait_minutes"
        private const val KEY_UNLOCK_MINUTES = "unlock_minutes"
        private const val KEY_GUARD = "guard_settings"
        private const val KEY_UNLOCKED_UNTIL = "unlocked_until"
        private const val KEY_LAST_ADDRESS = "last_address"
        private const val KEY_DNS_FILTER = "dns_filter"
        private const val KEY_LINES = "block_lines"
        private const val KEY_CUSTOM_LINES = "custom_block_lines"
        private const val KEY_APP_TAUNTS = "app_taunts"
        private const val KEY_CUSTOM_TAUNTS = "custom_taunts"
        private const val LINE_SEP = "\u001F"
    }
}

data class BatteryState(val percent: Int, val pluggedIn: Boolean)

fun readBattery(context: Context): BatteryState {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return BatteryState(0, false)
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val percent = if (level < 0 || scale <= 0) 0 else level * 100 / scale
    return BatteryState(percent, i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
}

/** Lowercased host of whatever is in a browser's address bar, or null if it isn't a URL. */
fun hostOf(urlText: String): String? {
    // Some browsers append extra words, e.g. "9gag.com, secure connection"; only the first token is the address.
    var s = urlText.trim().lowercase().split(' ', ',', '\n').first()
    if (s.isEmpty()) return null
    s = s.substringAfter("://")
    s = s.substringBefore('/').substringBefore('?').substringBefore('#')
    s = s.substringAfter('@').substringBefore(':')
    return if (s.contains('.')) s.removePrefix("www.") else null
}

/** True for things like "9gag.com" or "m.youtube.com", false for "v1.2" or plain words. */
fun looksLikeHost(host: String): Boolean {
    val tld = host.substringAfterLast('.', "")
    return tld.length >= 2 && tld.all { it in 'a'..'z' } && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
}

/**
 * Google Translate's proxy serves www.youtube.com as www-youtube-com.translate.goog,
 * which would otherwise slip past a domain entry.
 */
private fun unwrapProxy(host: String): String? {
    if (!host.endsWith(".translate.goog")) return null
    return host.removeSuffix(".translate.goog").replace("--", "\u0000").replace('-', '.').replace('\u0000', '-')
}

/** "youtube.com" matches the domain and its subdomains; an entry without a dot matches anywhere in the host. */
fun siteMatches(host: String, sites: Set<String>): String? {
    val hosts = listOfNotNull(host.removeSuffix(".").removePrefix("www."), unwrapProxy(host)?.removePrefix("www."))
    return sites.firstOrNull { raw ->
        val e = raw.trim().lowercase().removePrefix("www.")
        hosts.any { h ->
            when {
                e.isEmpty() -> false
                e.contains('.') -> h == e || h.endsWith(".$e")
                else -> h.contains(e)
            }
        }
    }
}
