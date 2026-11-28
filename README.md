# Threshold

A small, free Android app that blocks distracting apps and websites (YouTube and its front ends by default).

## What it does

- **Blocks apps** by package name, plus any app whose name or package contains a keyword
  (`youtube`, `newpipe`, `revanced`, …), so patched or renamed clients are caught.
- **Known blocklists.** Five ready-made lists (Video, Social, Porn, Gambling, News & tabloids) can
  be added with one button. Each covers well-known sites and apps of that kind and is blocked on
  top of your own lists; like the lists, they can be added any time but only removed while unlocked.
- **Blocks websites** in two layers:
  - a **DNS filter** (a local VPN that only carries name lookups) answers "no such site" for
    blocked domains, so they fail to load in every browser and app;
  - the accessibility service reads the address bar of **every installed browser** (found
    automatically) plus in-app browsers, and shows the Blocked screen. Google Translate proxy
    links (`www-youtube-com.translate.goog`) are unwrapped and matched too.
- **Lock with unlock conditions.** While locked each list shows its items read-only with an Add box: adding works, removing is impossible (no Remove buttons, and storage refuses removals), and the unlock rules are frozen. Unlocking pauses blocking
  for a set time (15 min by default). To unlock, all enabled
  conditions must be met, and then the **Guardian** (the 👹 ogre in the middle of the main
  screen) has to be slid off the threshold with 6 separate long strokes. Each stroke breaks a
  seal; pause for a few seconds and a seal re-forms. Drag the Guardian back onto its place to lock again early.
  The conditions are:
  - charger connected,
  - battery at or above a level (80% by default),
  - optionally, those held for N minutes with the app open (unplugging resets the countdown).
- **Block screens.** Blocked apps and browser tabs land on a screen where the Guardian says
  something cheeky, picked at random ("Hehe, hoho. NO.", "Loading… just kidding.", …).
  In the **Block screens** card you can add, edit and delete your own lines and switch the app's
  taunts and your custom taunts on or off.
- **Self-protection.** While locked, the Accessibility toggle page, App info page and the
  uninstall dialog for Threshold are closed as soon as they open.

The first 30 minutes after install are unlocked so you can set it up.

## Install

1. Download the newest `Threshold-N.apk` from the repo's **Releases** page on your phone.
2. Open it and allow installing from your browser/files app when asked.
3. Open Threshold and tap **Open Accessibility settings**, then turn on Threshold.
   On Android 13+ this may be refused ("App was denied access", or greyed out as a "restricted
   setting"): tap **Open App info** in Threshold, then ⋮ → **Allow restricted settings**, and try again.
   If Google Play Protect refuses to install the APK at all, pause "Scan apps with Play Protect"
   in the Play Store for the install, or install over USB with `adb install`.
4. Edit your lists, set your unlock rules, tap **Save unlock rules**, then drag the Guardian back onto its place to lock.

New builds install over the old one and keep your settings.

## Limits

No phone-side blocker is unbreakable. Booting into safe mode disables it, and the
self-protection only recognises English Settings screens.

The DNS filter can be bypassed by a browser using its own encrypted DNS (Firefox "DNS over
HTTPS" at Increased/Max protection, or Android "Private DNS" set to a hostname), by another
VPN app taking over the VPN slot, or by a web proxy/VPN site. The address-bar check still
catches most of those cases in browsers. Turn on Always-on VPN for Threshold, but leave
"Block connections without VPN" off, since only DNS goes through the tunnel.

## Building

Every push to `main` builds a signed APK with GitHub Actions and publishes it as a release.
Pull requests are built too (the APK is attached to the workflow run, no release). Locally: install JDK 17 and the
Android SDK, then run `./gradlew assembleRelease`.
