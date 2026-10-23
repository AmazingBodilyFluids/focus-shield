# Focus Shield

A small, free Android app that blocks distracting apps and websites (YouTube and its front ends by default).

## What it does

- **Blocks apps** by package name, plus any app whose name or package contains a keyword
  (`youtube`, `newpipe`, `revanced`, …), so patched or renamed clients are caught.
- **Blocks websites** by reading the address bar of common browsers (Chrome, Firefox, Brave,
  Samsung Internet, Edge, DuckDuckGo, Opera, Vivaldi and more).
- **Lock with unlock conditions.** While locked you can add to the block lists but not remove from them, and the unlock rules are frozen. Unlocking pauses blocking
  for a set time (15 min by default). To unlock, all enabled
  conditions must be met:
  - charger connected,
  - battery at or above a level (80% by default),
  - optionally, those held for N minutes with the app open (unplugging resets the countdown).
- **Self-protection.** While locked, the Accessibility toggle page, App info page and the
  uninstall dialog for Focus Shield are closed as soon as they open.

The first 30 minutes after install are unlocked so you can set it up.

## Install

1. Download the newest `FocusShield-N.apk` from the repo's **Releases** page on your phone.
2. Open it and allow installing from your browser/files app when asked.
3. Open Focus Shield and tap **Open Accessibility settings**, then turn on Focus Shield.
   On Android 13+ this may be greyed out as a "restricted setting": go to
   Settings → Apps → Focus Shield → ⋮ → **Allow restricted settings**, then try again.
4. Edit your lists, set your unlock rules, tap **Save**, then **Lock now**.

New builds install over the old one and keep your settings.

## Limits

No phone-side blocker is unbreakable. Booting into safe mode disables it, and the
self-protection only recognises English Settings screens. Browsers not in the list are not
watched; add their package name under "Browsers to watch".

## Building

Every push to `main` builds a signed APK with GitHub Actions. Locally: install JDK 17 and the
Android SDK, then run `./gradlew assembleRelease`.
