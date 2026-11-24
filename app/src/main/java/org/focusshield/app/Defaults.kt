package org.focusshield.app

object Defaults {
    val APPS = setOf(
        // Official YouTube apps
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.google.android.apps.youtube.kids",
        "com.google.android.youtube.tv",
        // ReVanced / Vanced builds
        "app.revanced.android.youtube",
        "app.revanced.android.apps.youtube.music",
        "app.rvx.android.youtube",
        "app.rvx.android.apps.youtube.music",
        "com.vanced.android.youtube",
        "com.vanced.android.apps.youtube.music",
        // Alternative front ends
        "org.schabi.newpipe",
        "org.polymorphicshade.newpipe",
        "org.polymorphicshade.tubular",
        "InfinityLoop1309.NewPipeEnhanced",
        "com.github.libretube",
        "com.github.lamarios.clipious",
        "com.futo.platformplayer",
        "free.rm.skytube.oss",
        "free.rm.skytube.extra",
        "com.kapp.youtube.final",
    )

    /** What the Guardian says on a block screen. One is picked at random each time. */
    val BLOCK_LINES = listOf(
        "Hehe, hoho, N-O!",
        "Denied.",
        "This piggy went the wrong way.",
        "Refresh the page 20-ish times, maybe it'll work.",
        "Error 418: I'm an ogre.",
        "Nice try. The Guardian saw that.",
        "Request received. Request laughed at.",
        "Closed for your own good. Opening hours: never.",
        "Have you tried, hear me out, not watching that?",
        "Nope. Not today. Probably not tomorrow either.",
        "The Guardian ate this page. It tasted of wasted afternoons.",
        "Plug in your charger and think about what you've done.",
        "Your future self says thanks. Grudgingly.",
        "Loading\u2026 just kidding.",
        "This little piggy stays home.",
        "Zero videos found. Zero regrets.",
        "Go touch some grass. I'll guard the door.",
        "Knock knock. Who's there? Not you.",
    )

    val APP_KEYWORDS = setOf("youtube", "newpipe", "revanced", "tubular", "libretube", "pipepipe")

    val SITES = setOf(
        "youtube.com",
        "youtu.be",
        "youtube-nocookie.com",
        "youtubekids.com",
        "invidious",
        "piped",
        "yewtu.be",
        "poketube.fun",
    )

    val BROWSERS = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "org.chromium.chrome", "com.brave.browser", "com.brave.browser_beta",
        "com.microsoft.emmx", "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
        "org.mozilla.focus", "org.mozilla.klar", "org.mozilla.fennec_fdroid",
        "io.github.forkmaintainers.iceraven", "us.spotco.fennec_dos",
        "com.duckduckgo.mobile.android", "com.opera.browser", "com.opera.mini.native",
        "com.opera.gx", "com.vivaldi.browser", "com.kiwibrowser.browser",
        "com.yandex.browser", "com.mi.globalbrowser", "com.android.browser",
        "org.bromite.bromite", "org.cromite.cromite", "app.vanadium.browser",
        "mark.via.gp", "mark.via", "com.UCMobile.intl", "com.ecosia.android",
        "com.huawei.browser", "org.torproject.torbrowser", "com.cookiegames.smartcookie",
    )

    /** Address-bar view ids used by the browsers above (the part after ":id/"). */
    val URL_BAR_IDS = listOf(
        "url_bar", "mozac_browser_toolbar_url_view", "location_bar_edit_text", "url_field",
        "omnibarTextInput", "url", "addressbar_url", "url_edit_text", "search_box",
        "display_url", "edit_text", "address_bar_edit_text", "toolbar_url",
    )

    val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.settings.intelligence",
        "com.samsung.android.lool",
        "com.miui.securitycenter",
        "com.android.vpndialogs",
    )

    val INSTALLER_PACKAGES = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.packageinstaller",
    )
}
