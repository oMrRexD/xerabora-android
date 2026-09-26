<p align="center">
  <img src="../docs/icon.png" alt="xeRAbora" width="128">
</p>

# xeRAbora for Android

[xeRAbora](https://github.com/hacan359/xerabora), the RetroAchievements client for a real
PlayStation 2, running entirely on an Android phone. No PC: the PS2 finds the phone on the Wi-Fi,
and the phone talks to RetroAchievements, unlocks the achievements and shows the client's own page.

*Português: [android/README.md](../android/README.md).*

## Install

1. Download the APK from the [latest release](https://github.com/oMrRexD/xerabora-android/releases/latest)
   and install it.
2. Open it and allow notifications and running in the background: that is what keeps the client
   alive with the screen off in the middle of a game.
3. On the page's **SETTINGS** tab, sign in and paste your Web API key, as on the PC.
4. On the PS2, with the phone on the **same Wi-Fi**: **RA: test PC connection** shows the phone's
   address. Then **RA: check game support**, then the game.

The console side is upstream's: `OPL-RA.ELF` from the
[xeRAbora releases](https://github.com/hacan359/xerabora/releases), or a loader that carries the same
agent. Updates to the app are offered by the app itself.

## What the phone adds

- A status notification: the console, the running game with its icon, achievements, points,
  session time and rich presence.
- A notification for every unlock, with its badge, and one when a game is mastered.
- Pop-ups for "RA: test PC connection", "RA: check game support" and a game starting.
- Everything else is the client's own page, the same one the PC shows.

## How it follows upstream

This fork only adds files: [`android/`](../android), the `android` and `sync-upstream` workflows and
this page. Nothing upstream is edited, so a daily workflow merges
[hacan359/xerabora](https://github.com/hacan359/xerabora) without conflicts and publishes a new APK
when there is something new. How the port works, file by file, is in
[android/README.md](../android/README.md); upstream's own documentation is its
[README](../README.md).

## Credits

xeRAbora by [hacan359](https://github.com/hacan359) (MIT). [rcheevos](https://github.com/RetroAchievements/rcheevos)
by RetroAchievements (MIT). Android port by MrRexD.
