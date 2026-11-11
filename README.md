# va-host — Android app container

One Gradle project, three modules. Everything lives here; there are no sibling
projects any more.

| Module | What it is |
| --- | --- |
| `engine/` | **The engine.** Forked from `asLody/VirtualApp`'s `lib`, ported to API 37. Almost all work lands here. |
| `app/` | The shell. Boots the engine and hosts the UI. `dev.bello.vahost`. |
| `guest/` | Dependency-free test app, built and embedded at `assets/guest.apk` for installing into the container. |

## Build and run

```sh
cd ~/code/bello/container/va-host
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
GRADLE=$(ls -d ~/.gradle/wrapper/dists/gradle-9.8.1-bin/*/gradle-9.8.1/bin/gradle | head -1)
$GRADLE :app:assembleDebug --no-daemon

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop dev.bello.vahost
adb shell am start -n dev.bello.vahost/.StatusActivity
adb exec-out run-as dev.bello.vahost cat files/va-report.txt
```

`:app` depends on `project(':engine')` directly — no aar is copied any more, so
the engine can never be stale relative to the shell. The guest apk is rebuilt
and re-embedded automatically.

**Read the report file, not logcat.** MIUI silently drops this app's `Log.i`
while printing thousands of system lines.

## Hard constraints

- **32-bit only.** `abiFilters armeabi-v7a`. arm64 is blocked on the
  `IOUniformer` syscall rewrite — see `docs/ENGINE-PLAN.md` item 7.
- **`targetSdk 24`,** squeezed from both ends: Android 15 refuses to install
  below 24, and hidden-API enforcement is keyed to targetSdk so lower is more
  lenient. Every bump is a milestone, not a chore.
- **compileSdk 37, AGP 9.3.2, Gradle 9.8.1, JDK 21, NDK 27.1.12297006.**

## Current state

Engine boots: `startup() OK`, `initialize -> onServerProcess`, `:x` server
process alive and stable, `installed apps: 0`. Nothing has been installed or
launched into the container yet.

Next: `VirtualCore.get().installPackage(...)` with the embedded guest, then
launch it. That is the first real exercise of the 369 method proxies.

## docs/

| File | |
| --- | --- |
| `ENGINE-PLAN.md` | The build plan, the 8 engine pieces, milestones, and the M0 result |
| `VA-FORK.md` | Every change made to port VirtualApp, and the two runtime fixes |
| `BUILD.md` | The original Katana SPatch launcher-UI clone notes |
| `m0-host-reference/` | Our clean-room M0 host sources, kept for the technique |
| `M0_GuestInstrumentation.kt.reference` | The `Instrumentation.newActivity` swap — the fallback if VirtualApp's ClientTransaction interception proves too stale |
| `spatch-launcher-ui-local-changes.patch` | Our port of the SPatch launcher UI, for when a real front end is wanted. Re-clone `Katana-Official/SPatch-Update` and apply. |

Deleted on purpose (all re-obtainable): `va-fork`, `m0-spike`, `virtualapp`,
`SPatch-Update`.
