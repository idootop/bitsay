<div align="center">

<img src="docs/images/logo.svg" width="96" alt="BitSay">

<h1>BitSay</h1>

<strong>Notes &amp; todos, always in sight.</strong>

<br>
<br>

<a href="https://github.com/idootop/bitsay/releases/latest"><b>Download</b></a> &nbsp;·&nbsp; <a href="README.zh-CN.md">中文</a>

</div>

---

## Why BitSay

**📌 Jot it down without opening anything.**<br>The widget *is* the app. Write a note, tick a todo or search your list straight from the home screen — no launch, no splash, no waiting.

**⚡ Ideas don't wait.**<br>Tap `+` and start typing. What you write is saved as you write it, so a half-finished thought survives a phone call, a locked screen or a dead battery.

**👀 Keep what matters in front of you.**<br>Pin the list you actually need — today's todos, that one thing you keep forgetting — somewhere you already look a hundred times a day. Out of sight really is out of mind; this fixes that.

**🔒 Nothing gets lost, and nothing gets out.**<br>Everything autosaves. One tap exports a complete backup, one tap restores it on a new phone. The app requests **no permissions at all** and contains no network code — your notes sit in a local database and stay there.

**🪶 Tiny and quiet.**<br>**2 MB**, no ads, no account, no sign-up, no telemetry. Free and open source under MIT.

**📱 Looks right on whatever you're holding.**<br>Light and dark, English and 中文, and layouts that adapt from a phone to a tablet or an unfolded foldable — on a wide screen the list stays put and the editor opens beside it, instead of one covering the other.

## Screenshots

<p align="center">
  <img src="docs/images/shot-widget.png" width="150" alt="Home screen widget">
  <img src="docs/images/shot-notes.png" width="150" alt="Notes">
  <img src="docs/images/shot-todos.png" width="150" alt="Todos">
  <img src="docs/images/shot-editor.png" width="150" alt="Editor in dark mode">
</p>

<p align="center"><sub>Home screen widget &nbsp;·&nbsp; Notes &nbsp;·&nbsp; Todos &nbsp;·&nbsp; Editor (dark)</sub></p>

<p align="center">
  <img src="docs/images/shot-tablet.png" width="700" alt="Tablet and foldable layout">
</p>

## Install

Download the latest `Bitsay-x.y.z.apk` from [**Releases**](https://github.com/idootop/bitsay/releases/latest) and open it on your phone. Android will ask you to allow installing from this source — that is the only prompt you will ever see, because the app asks for nothing else.

Each release also lists the APK's SHA-256 and its signing certificate fingerprint, so you can verify what you downloaded if you want to.

## Requirements

**Android 12 (API 31) or newer.**

Not an arbitrary floor: the widget is built on `RemoteViews.RemoteCollectionItems`, `targetCellWidth` and `previewLayout`, which all arrived in API 31 — and are still the non-deprecated collection APIs on Android 17.

The app requests **no permissions at all** — no network, no storage, no notifications.

## License

MIT License © 2026-PRESENT [Del Wang](https://github.com/idootop)
