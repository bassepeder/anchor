# Anchor

Turn your smartphone back into what it should have been from the start: a phone. A tool. Not a doomscrolling or adult-content machine.

Anchor is a device-owner app for Android. It can't be uninstalled or switched off in Settings, and it follows one rule everywhere:

> **Stricter is instant. Looser needs an unlock.**

An unlock means requesting it in the app, then restarting the phone a set number of times (3 by default), each restart at least a set time after the last (12 hours by default). The unlock then stays open for a short window (30 minutes by default). By then, the urge has usually passed.

## Features

**Setup wizard**

The first launch walks through six short screens:

1. Welcome.
2. Pick a preset.
3. Fine-tune the categories.
4. Choose how hard undoing should be.
5. Choose the DNS filter (Anchor's own, or your own, such as AdGuard).
6. A summary. If Anchor isn't device owner yet, this screen shows the adb command with a Copy button and updates by itself once the command has run.

| Preset | Blocks | Unlock |
|---|---|---|
| Essentials | Adult content, bypass tools | Gentle: 2 restarts, 6 h apart |
| **Balanced** (recommended) | + social feeds, short videos | Firm: 3 restarts, 12 h apart |
| Focused | + gambling, dating, strict YouTube | Firm |
| Just a phone | + video and streaming, apps frozen | Iron: 5 restarts, 24 h apart, open 10 min |

Until Anchor is turned on, you can run the wizard again from Rules.

**Sites**
- Categories: Adult content and Bypass tools (proxies, Tor, translation proxies) are always on. Social feeds and Short videos are on by default. Video and streaming, Gambling and Dating are optional.
- Your own blocked sites, including paths like `youtube.com/shorts`.
- An "Always allowed" list for sites blocked by mistake.
- A large, maintained adult-site list ([HaGeZi NSFW](https://github.com/hagezi/dns-blocklists)) that is downloaded and refreshed weekly, plus Cloudflare for Families as the upstream DNS.

**Apps**
- Hide any app with one tap. Hidden apps stay installed with their data.
- Every browser except Chrome is hidden automatically, including ones installed later.
- Apps in active categories (Instagram, TikTok, Tinder, …) are hidden automatically.
- The phone, SMS, home screen and Settings apps can never be hidden.

**Chrome, locked down with managed policies**
- Category and personal blocklists, no incognito, Chrome's own secure DNS off, SafeSearch, YouTube restricted mode, Google's adult-site filter, and no article feed on the new tab page. Check it on the phone at `chrome://policy`.

**DNS filter** (optional, on by default)
- An on-device always-on VPN that only handles DNS. Nothing else is routed through it.
- Blocks the lists for every app, not just Chrome.
- Forces SafeSearch on Google, Bing and DuckDuckGo, and YouTube restricted mode, for every app.
- Catches apps that try to use Google, Cloudflare, Quad9, NextDNS or AdGuard DNS directly.
- **Using AdGuard or another DNS filter instead?** Turn Anchor's filter off in Rules. Anchor then frees the VPN slot (so the AdGuard app can use it) and can lock Private DNS to your own hostname, such as `xxxxxxxx.d.adguard-dns.com`, so it can't be switched off in Settings. Chrome stays locked down either way.

**Rules**
- Restarts needed (1–5), time between them (1–48 hours), and how long an unlock stays open (10–60 minutes).
- Strict YouTube.
- Freeze apps: nothing can be installed, not even from the Play Store.
- Every stricter choice asks for confirmation first, since undoing it needs an unlock.

**Always enforced**
- VPN, Private DNS, date and time, extra users, Secure Folder, app cloning, private space and factory reset are locked in Settings.
- Release builds also turn off safe mode, USB debugging and sideloading.

## Build

With the Android SDK installed (`ANDROID_HOME` set), from this folder:

```bash
./gradlew assembleDebug
```

Or open the folder in Android Studio. GitHub Actions builds and lints every push to `main` and every pull request, and publishes the debug APK as the `anchor-debug-apk` artifact of each run.

- **Debug** build: for testing. adb, safe mode and sideloading keep working, the time between restarts counts in minutes instead of hours, and you can remove the app with adb.
- **Release** build: the real thing. It's signed with your debug key, so **back up `~/.android/debug.keystore`**. Updates must be signed with that same key.

## Install

1. Turn on USB debugging: Settings › About phone › Software information › tap Build number 7 times, then Developer options › USB debugging.
2. Settings › Accounts and backup › Manage accounts: remove **every** account. Remove Secure Folder if you have one. Android refuses to set a device owner while accounts exist.
3. Install the app and make it device owner:

```bash
adb install -t app/build/outputs/apk/debug/app-debug.apk
```

```bash
adb shell dpm set-device-owner app.anchor/.AdminReceiver
```

4. Add your accounts back.
5. Open Anchor and go through the setup wizard. Its last screen notices that Anchor is now device owner and offers **Turn on protection**.

You can also open the app first, go through the wizard, and run the adb command while its last screen is open.

For the release build, install `app-release.apk` (without `-t`) and run the same `dpm` command.

Remove a debug build with:

```bash
adb shell dpm remove-active-admin app.anchor/.AdminReceiver
```

A release build can only be removed through an unlock (Home › Turn Anchor off) or a factory reset from recovery mode.

## Test before going strict

On the debug build, check that:

- Blocked sites fail in Chrome and in other apps.
- `chrome://policy` lists the policies, and incognito is gone.
- Google image search is SafeSearch-filtered in the Google app too.
- Samsung Internet is hidden, and a newly installed browser gets hidden.
- Banking apps work with the filter on.
- The adult-site list downloads (Sites tab).
- The unlock flow works.
- With the filter off, AdGuard works and the Private DNS lock holds.

## Editing the built-in lists

Categories live in `app/src/main/assets/categories.txt`. The format is documented at the top of that file.

## Limits

- Factory reset from recovery mode (hardware buttons) can't be blocked. It's the emergency exit if something breaks.
- Shorts and Reels inside the YouTube or Instagram apps can't be blocked. Hide those apps instead.
- With the DNS filter on and in lockdown mode, the phone has no internet if the filter crashes, until it restarts.
- Freeze apps can stop app updates too.
