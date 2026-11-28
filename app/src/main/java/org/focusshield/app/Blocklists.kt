package org.focusshield.app

/**
 * Known blocklists: ready-made packs of well-known apps and sites, added as a whole from the
 * Known blocklists card. Only the ids of the packs that are on are stored ([Prefs.blocklists]),
 * so a new build's lists replace the old ones on the phone.
 *
 * Site entries follow the same rules as the Websites list: "example.com" covers the domain and
 * its subdomains, and a word without a dot matches any address containing it. Keep the words
 * specific: "bet" would also block alphabet.com, and "gambl" would block the help sites.
 */
object Blocklists {

    class Pack(
        val id: String,
        val name: String,
        /** One line for the picker: what is in it. */
        val about: String,
        val apps: Set<String> = emptySet(),
        /** Matched against app names and package names, like the Keywords list. */
        val appKeywords: Set<String> = emptySet(),
        val sites: Set<String> = emptySet(),
        /**
         * Sites blocked in browsers but left alone by the DNS filter, because other apps need the
         * domain to work (Messenger talks to facebook.com, BBC Sounds to bbc.co.uk).
         */
        val pageOnlySites: Set<String> = emptySet(),
    ) {
        val counts: String
            get() = (sites.size + pageOnlySites.size).toString() + " sites and words · " + apps.size + " apps · " +
                appKeywords.size + " app keywords"
    }

    class Merged(
        val apps: Set<String>,
        val appKeywords: Set<String>,
        val sites: Set<String>,
        val pageOnlySites: Set<String>,
    )

    val ALL: List<Pack> = listOf(
        Pack(
            id = "video",
            name = "Video",
            about = "YouTube, TikTok, Twitch, Netflix and other video and streaming",
            apps = Defaults.APPS + setOf(
                "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go",
                "com.ss.android.ugc.aweme", "tv.twitch.android.app", "com.netflix.mediaclient",
                "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient", "com.hulu.plus",
                "com.wbd.stream", "com.hbo.hbonow", "com.vimeo.android.videoapp",
                "com.dailymotion.dailymotion", "com.kick.mobile", "com.rumble.battles",
                "tv.danmaku.bili", "com.bilibili.app.in", "com.crunchyroll.crunchyroid", "com.cbs.app",
                "com.peacocktv.peacockandroid", "com.tubitv", "tv.pluto.android", "video.like",
                "com.kwai.video", "com.viki.android", "com.iqiyi.i18n", "in.startv.hotstar",
            ),
            appKeywords = Defaults.APP_KEYWORDS + setOf("tiktok", "twitch"),
            sites = Defaults.SITES + setOf(
                "tiktok.com", "douyin.com", "twitch.tv", "kick.com", "netflix.com", "disneyplus.com",
                "primevideo.com", "hulu.com", "max.com", "hbomax.com", "tv.apple.com",
                "paramountplus.com", "peacocktv.com", "tubitv.com", "pluto.tv", "crunchyroll.com",
                "vimeo.com", "dailymotion.com", "rumble.com", "odysee.com", "bitchute.com",
                "bilibili.com", "bilibili.tv", "nicovideo.jp", "rutube.ru", "coub.com", "streamable.com",
                "likee.video", "kwai.com", "viki.com", "iq.com", "iqiyi.com", "youku.com", "wetv.vip",
                "hotstar.com", "zee5.com", "sonyliv.com", "jiocinema.com", "viu.com", "crackle.com",
            ),
        ),
        Pack(
            id = "social",
            name = "Social",
            about = "Facebook, Instagram, X, Reddit, Snapchat and other feeds. Messengers are left alone.",
            apps = setOf(
                "com.facebook.katana", "com.facebook.lite", "com.instagram.android", "com.instagram.lite",
                "com.instagram.barcelona", "com.twitter.android", "com.twitter.android.lite",
                "com.reddit.frontpage", "com.snapchat.android", "com.pinterest", "com.tumblr",
                "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go",
                "org.joinmastodon.android", "com.keylesspalace.tusky", "xyz.blueskyweb.app",
                "com.vkontakte.android", "ru.ok.android", "com.sina.weibo", "com.xingin.xhs",
                "com.bereal.ft", "com.quora.android", "com.ninegag.android.app", "com.imgur.mobile",
                "com.laurencedawson.reddit_sync", "com.andrewshu.android.reddit",
                "ml.docilealligator.infinityforreddit", "free.reddit.news", "com.rubenmayayo.reddit",
                "org.quantumbadger.redreader",
            ),
            appKeywords = setOf("instagram", "tiktok", "reddit", "twitter", "snapchat", "pinterest", "tumblr"),
            sites = setOf(
                "instagram.com", "threads.net", "threads.com", "twitter.com", "x.com", "xcancel.com",
                "nitter", "reddit.com", "redd.it", "libreddit", "redlib", "snapchat.com", "pinterest",
                "tumblr.com", "tiktok.com", "ok.ru", "weibo.com", "xiaohongshu.com", "bsky.app",
                "mastodon", "mstdn.social", "truthsocial.com", "gab.com", "quora.com", "9gag.com",
                "imgur.com", "ifunny.co", "bereal.com", "4chan.org", "4channel.org", "lemmy.world",
                "lemmy.ml",
            ),
            pageOnlySites = setOf("facebook.com", "fb.com", "fb.watch", "vk.com"),
        ),
        Pack(
            id = "porn",
            name = "Porn",
            about = "Adult sites, cams and hentai, plus any address containing words like \"porn\" or \"xxx\"",
            appKeywords = setOf("porn", "hentai", "xvideos", "xnxx", "xhamster", "onlyfans", "nsfw", "rule34"),
            sites = setOf(
                // Words: these also catch mirrors and the many sites not listed by name.
                "porn", "xxx", "hentai", "nsfw", "milf", "camgirl", "sexcam", "livesex", "rule34",
                "erotic", "nude", "boobs", "pussy", "xvideos", "xnxx", "xhamster", "redtube",
                "spankbang", "chaturbate", "stripchat", "bongacams", "livejasmin", "onlyfans", "fansly",
                "brazzers", "motherless", "redgifs", "missav", "javhd", "hanime",
                // Sites whose address has none of those words.
                "tube8.com", "beeg.com", "txxx.com", "sex.com", "youjizz.com", "drtuber.com",
                "tnaflix.com", "4tube.com", "fuq.com", "thumbzilla.com", "sxyprn.com", "daftsex.com",
                "erome.com", "imagefap.com", "fapello.com", "scrolller.com", "coomer.su", "coomer.party",
                "simpcity.su", "e621.net", "gelbooru.com", "danbooru.donmai.us", "exhentai.org",
                "hitomi.la", "tsumino.com", "fakku.net", "luscious.net", "8muses.com", "nutaku.net",
                "f95zone.to", "literotica.com", "cam4.com", "myfreecams.com", "camsoda.com",
                "manyvids.com", "clips4sale.com", "bangbros.com", "realitykings.com",
                "naughtyamerica.com", "jable.tv", "javlibrary.com", "supjav.com",
                "adultfriendfinder.com", "fikfap.com",
            ),
        ),
        Pack(
            id = "gambling",
            name = "Gambling",
            about = "Betting, casino, poker and lottery sites and apps",
            apps = setOf("com.draftkings.sportsbook", "com.fanduel.sportsbook"),
            appKeywords = setOf(
                "casino", "poker", "slots", "betting", "sportsbook", "roulette", "jackpot", "blackjack",
                "bingo", "lottery", "bet365", "1xbet", "draftkings", "fanduel", "betway", "betfair",
                "pokerstars",
            ),
            sites = setOf(
                // Words.
                "casino", "poker", "betting", "sportsbook", "roulette", "jackpot", "blackjack", "slots",
                "bookmaker", "lottery", "bet365", "1xbet",
                // Sites whose address has none of those words.
                "betfair.com", "williamhill.com", "paddypower.com", "ladbrokes.com", "ladbrokes.com.au",
                "coral.co.uk", "skybet.com", "betway.com", "888.com", "888sport.com", "unibet.com",
                "bwin.com", "betfred.com", "betvictor.com", "draftkings.com", "fanduel.com",
                "betmgm.com", "pointsbet.com", "betrivers.com", "bovada.lv", "stake.com", "stake.us",
                "roobet.com", "22bet.com", "pinnacle.com", "betsson.com", "leovegas.com", "mrgreen.com",
                "casumo.com", "bet-at-home.com", "tipico.de", "tipico.com", "betano.com",
                "sportingbet.com", "bodog.eu", "betonline.ag", "mybookie.ag", "sportsbet.com.au",
                "tab.com.au", "neds.com.au", "parimatch.com", "melbet.com", "mostbet.com",
                "dafabet.com", "sbobet.com", "bc.game", "rollbit.com", "csgoempire.com", "duelbits.com",
                "tipsport.cz", "fortuna.cz", "sazka.cz", "lottoland.com", "tombola.co.uk",
                "smarkets.com", "prizepicks.com", "underdogfantasy.com", "polymarket.com", "kalshi.com",
            ),
        ),
        Pack(
            id = "news",
            name = "News & tabloids",
            about = "News sites, tabloids, gossip and tech news, plus anything with \"news\" in its name",
            apps = setOf(
                "com.google.android.apps.magazines", "bbc.mobile.news.ww", "bbc.mobile.news.uk",
                "com.cnn.mobile.android.phone", "com.foxnews.android", "com.guardian",
                "com.nytimes.android", "com.washingtonpost.android", "com.dailymail.online",
                "com.buzzfeed.android", "com.huffingtonpost.android", "flipboard.app",
                "com.particlenews.newsbreak", "jp.gocro.smartnews.android",
                "com.microsoft.amp.apps.bingnews", "com.thomsonreuters.reuters", "mnn.Android",
                "wsj.reader_sp", "com.bloomberg.android.plus", "com.usatoday.android.news",
                "com.zumobi.msnbc", "com.abc.abcnews", "com.bskyb.skynews.android",
                "io.github.hidroh.materialistic",
            ),
            appKeywords = setOf("news", "tabloid", "dailymail", "buzzfeed", "flipboard"),
            sites = setOf(
                // Words. "news" alone covers foxnews, nbcnews, news.google.com, news.ycombinator.com...
                "news", "tabloid",
                // Tabloids and gossip.
                "dailymail.co.uk", "thesun.co.uk", "the-sun.com", "mirror.co.uk", "express.co.uk",
                "dailystar.co.uk", "metro.co.uk", "nypost.com", "pagesix.com", "tmz.com",
                "buzzfeed.com", "huffpost.com", "ladbible.com", "unilad.com", "perezhilton.com",
                "eonline.com", "people.com", "usmagazine.com", "radaronline.com", "bild.de", "blesk.cz",
                // Newspapers and broadcasters.
                "cnn.com", "msnbc.com", "nytimes.com", "washingtonpost.com", "wsj.com", "usatoday.com",
                "reuters.com", "bloomberg.com", "theguardian.com", "independent.co.uk",
                "telegraph.co.uk", "standard.co.uk", "politico.com", "thehill.com", "axios.com",
                "vox.com", "vice.com", "time.com", "breitbart.com", "drudgereport.com", "msn.com",
                "aljazeera.com", "dw.com", "rt.com", "france24.com", "spiegel.de", "welt.de",
                "focus.de", "lemonde.fr", "lefigaro.fr", "elpais.com", "elmundo.es", "corriere.it",
                "repubblica.it", "g1.globo.com", "ndtv.com", "indiatimes.com", "hindustantimes.com",
                "indianexpress.com", "scmp.com", "straitstimes.com", "smh.com.au", "stuff.co.nz",
                "nzherald.co.nz", "irishtimes.com", "independent.ie", "thejournal.ie", "novinky.cz",
                "idnes.cz", "aktualne.cz", "seznamzpravy.cz",
                // Tech news.
                "theverge.com", "techcrunch.com", "arstechnica.com", "engadget.com", "gizmodo.com",
                "kotaku.com", "mashable.com", "wired.com", "slashdot.org", "lobste.rs",
            ),
            pageOnlySites = setOf("bbc.co.uk", "bbc.com", "abc.net.au", "cbc.ca"),
        ),
    )

    @Volatile
    private var memo: Pair<Set<String>, Merged>? = null

    /** Everything in the packs with these ids, merged. The last answer is kept, as it is asked for constantly. */
    fun merged(ids: Set<String>): Merged {
        memo?.let { if (it.first == ids) return it.second }
        val on = ALL.filter { it.id in ids }
        val m = Merged(
            on.flatMapTo(HashSet<String>()) { it.apps },
            on.flatMapTo(HashSet<String>()) { it.appKeywords },
            on.flatMapTo(HashSet<String>()) { it.sites },
            on.flatMapTo(HashSet<String>()) { it.pageOnlySites },
        )
        memo = ids to m
        return m
    }
}
