# SPatch-Update (Metaverse Engine Launcher UI) — local build notes

Clone: `SPatch-Update/` @ upstream master, `https://github.com/Katana-Official/SPatch-Update`
Built: 2026-11-11 · macOS arm64

## Build command (works)

```sh
cd ~/code/bello/container/SPatch-Update
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
GRADLE=$(ls -d ~/.gradle/wrapper/dists/gradle-9.8.1-bin/*/gradle-9.8.1/bin/gradle | head -1)
$GRADLE :app:assembleStubDebug --no-daemon
```

Outputs (5 APKs, ~1.8 MB each, identical — no native libs in the tree):

```
app/build/outputs/apk/stub/debug/app-stub-{universal,arm64-v8a,armeabi-v7a,x86,x86_64}-debug.apk
```

`package=net_62v.launcher`, label **Meta Launcher**, launchable `com.android.launcher2.Launcher`,
compileSdk 37 / targetSdk 37 (platformBuildVersionName 17).

Install (device `adb devices` must be listed):
`adb install -r app/build/outputs/apk/stub/debug/app-stub-universal-debug.apk`
It only becomes the home screen if you pick it in the HOME chooser.

## Toolchain required

| Need | Status on this machine |
| --- | --- |
| JDK 21 | `/opt/homebrew/opt/openjdk@21` (21.0.10). `java` on PATH is 17 — must override `JAVA_HOME` |
| Gradle 9.x | 9.8.1 from the wrapper dist cache. Brew `gradle` is 8.13 = **too old for AGP 9.3.2** |
| Android platform 37 | installed as `platforms/android-37.0` (+ `build-tools;37.0.0`) |
| Gradle wrapper | **repo has none** — `gradlew` and `gradle/` are in `.gitignore` |

`$JAVA_HOME` in the shell profile points at a deleted Gradle-provisioned JDK 17, which breaks
bare `gradle`/`sdkmanager`. Always set `JAVA_HOME` explicitly.

## Two flavors (`engine` dimension)

- **`stub`** — compiles against `app/src/stub/kotlin/net_62v/external/MundoStubs.kt`, fork-local
  no-op shims. Builds and installs with no SDK. Launcher UI runs; nothing is virtualized.
  Every shim logs under tag `MetaStub` (so `adb logcat | grep -iE "mundo|62v|meta"` shows the
  bridge sequence, which is task T5).
- **`real`** — excludes the shims, expects the vendor `.aar` in `app/libs/`. Currently fails with
  exactly the 4 errors upstream predicts (`Unresolved reference 'net_62v'` ×2, `MetaCore`,
  `MetaPackageManager`). This is the proof the engine is external.

Drop the aar in `app/libs/` and `assembleRealDebug` becomes the real build. `fileTree(include:
['*.aar','*.jar'])` means **the filename does not matter** — any `.aar` there is picked up.

## Fork changes (vendor originals kept as `*.orig`)

The published tree **cannot configure** under the AGP version it pins. Fixes applied:

1. root `build.gradle`: `rootProject.buildDir` → `layout.buildDirectory` (`Project.buildDir`
   removed in Gradle 9); added `google()` + `mavenCentral()` ahead of the huaweicloud mirror.
2. `app/build.gradle`: `packagingOptions` → `packaging`; `minSdkVersion` → `minSdk`;
   `kotlinOptions { jvmTarget }` **deleted** (gone in AGP 9's built-in Kotlin — jvmTarget now
   follows `compileOptions`); `splits` and `ndk { abiFilters }` lifted out of `defaultConfig`,
   where the vendor had nested them (plus a stray nested `android { }`).
3. `debug` buildType: vendor shipped `debuggable false` + minify + shrink, i.e. release-equivalent.
   Flipped to `debuggable true`, minify/shrink off, so the bridge can be instrumented. `release`
   untouched.
4. Added the `engine` flavor dimension + the stub source set.
5. `local.properties` with `sdk.dir`.

Do **not** add the Kotlin Gradle plugin: AGP 9 has built-in Kotlin support and
`org.jetbrains.kotlin.android` is now a hard error. The vendor tree is right to have none.

## Corrections to the reference write-up

Verified **true**: AGP 9.3.2 (really exists), compileSdk/targetSdk 37, minSdk 21, Java 21,
release-equivalent `debug`, 4-ABI splits + `universalApk`, `useLegacyPackaging`, local-aar
`fileTree`, `dalvik-dx:14.0.0_r21` (resolves), huaweicloud+jitpack repos, `android_common.jar`
138 520 bytes (pure `com/android/...`, no engine), CC-BY-4.0, `<original-package>` trick,
stock `LauncherApplication` with no engine init, **zero** STUB components in the manifest
(2 activities, 1 provider, 0 services), and §8's "fails without the aar".

Wrong or incomplete:

- **"`LauncherLoader.kt` is the ONLY file that talks to the SDK" — false.** Four files import
  `net_62v.external.*`: `LauncherLoader.kt`, `Launcher.java` (118-119), `LauncherModel.java` (66),
  `AppsCustomizePagedView.java` (62).
- **The API surface is 6 members, not 3.** Actual, from the call sites:
  - `MetaCore.registerCoreCallback(() -> Unit)`
  - `MetaPackageManager.getLaunchIntentForPackage(Intent): Intent?`
  - `MetaPackageManager.acquireObtainAppSplash()` — `Launcher.java:438`, `LauncherModel.java:2123`
  - `MetaPackageManager.isInnerAppInstalled(String): Boolean` — `Launcher.java:2133`
  - `MetaPackageManager.uninstallAppFully(String)` — `Launcher.java:2135`
  - `MetaActivityManager.obtainSplashLaunchIntent(int partition, String pkg, Context): Intent?`
    — `Launcher.java:2029`, `AppsCustomizePagedView.java:528`
  `MetaActivityManager.launchApp(...)` from the write-up's table appears only in README samples
  (lines 40, 46), **not** in the code.
- **`./gradlew :app:assembleDebug` cannot work** — there is no wrapper in the repo, and the vendor
  build files don't configure under AGP 9.3.2 anyway (see fork changes).
- 79 `.java` files + 1 `.kt`, not "~60". `namespace` is `com.android.launcher` (R class
  `com.android.launcher.R`), while code package and `<original-package>` are `com.android.launcher2`.
- `compileSdk 37` resolves against the minor-versioned platform dir `android-37.0`.

## §10 open verifications — status

1. **Done.** `Launcher.java:436` — `LauncherLoader.instance().waitForAndGetInitialService(() -> {
   MetaPackageManager.acquireObtainAppSplash(); return null; })`, at the end of the
   `unlockScreenOrientation(true)` block. Non-system app taps go through
   `MetaActivityManager.obtainSplashLaunchIntent(0, pkg, ctx)` and are **skipped when it returns
   null**, so with no engine the launcher silently does nothing for hosted apps.
2. **Answered differently.** No specific filename is expected — the `fileTree` glob takes any
   `*.aar`/`*.jar` in `app/libs/`.
3. **Blocked** — needs the aar (ProGuard mapping for the SDK).
4. **Partly done.** Repo manifest has 0 STUB components, confirming they arrive via the aar's
   merged manifest. The 24/5 counts can't be cross-checked until the aar is in hand.

## Not done / blocked

- T2 vendor contact, T6 first guest, T7 BattleRush — all need the SDK aar; the launcher cannot
  host anything without it.
- T1 lane choice (rebrand vs own engine) is still yours to make.
- T4 fork hygiene: `applicationId` is still the vendor's `net_62v.launcher`, label still
  "Meta Launcher" — deliberately unchanged so this clone stays comparable to upstream.
