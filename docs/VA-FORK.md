# VirtualApp fork — port status (11-Nov-2026)

`va-fork/` — copy of `asLody/VirtualApp`'s inner `VirtualApp/` tree, `:lib` only.
Personal use on the owner's own device; the upstream grants no licence (see
ENGINE-PLAN.md §0.1).

## Status: `:lib` BUILDS — `lib-debug.aar` 1.5 MB with `libva++.so` (armeabi-v7a)

Toolchain: AGP 9.3.2 / Gradle 9.8.1 / JDK 21 / compileSdk 37 / NDK 27.1.12297006.

```sh
cd ~/code/bello/container/va-fork
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
GRADLE=$(ls -d ~/.gradle/wrapper/dists/gradle-9.8.1-bin/*/gradle-9.8.1/bin/gradle | head -1)
$GRADLE :lib:assembleDebug --no-daemon
```

## Everything that had to change

**Build files**
- root: AGP 3.0.1 -> 9.3.2; jcenter (dead since 2021) -> google + mavenCentral
- `settings.gradle`: `:lib` only (`:app` is a demo UI, needs its own port)
- `lib`: `compileSdk 26->37`, `minSdk 14->21`, `targetSdk 22->35`, added
  `namespace`, dropped `buildToolsVersion`, `compile` -> `implementation`
- **`buildFeatures { aidl true }`** — AGP 8+ disabled AIDL by default and this
  tree has 47 `.aidl` files. This single line fixed **186 of 200** errors.
- `getDefaultProguardFile('proguard-android.txt')` is rejected by AGP 9 ->
  `proguard-android-optimize.txt`
- `ndkVersion 27.1.12297006` — **NDK 21 cannot run on Apple Silicon**
  ("Unknown host CPU architecture: arm64")

**Native makefiles**
- `APP_PLATFORM android-14 -> android-21`
- `APP_STL gnustl_static -> c++_static` (gnustl removed in NDK r18)
- `fb/Android.mk`: dropped `-Werror`, added `-Wno-vla-cxx-extension`
  (fbjni uses C++ VLAs, an error under clang 18)

**Source — 9 edits in 553 Java files + 3 in C++**
- 5x `? (List) ParceledListSlice.getList.call(...)` — stricter javac rejects the
  conditional's mixed types (`MethodProxies.java`, `ActivityManagerStub.java`)
- `Context.FINGERPRINT_SERVICE` -> `"fingerprint"` — the constant was removed
  from the public SDK
- 3x `abs(im)` -> `abs((int)(im))` in `SubstrateARM.hpp` / `SubstrateHook.cpp`

That is roughly 15 edits total. The Java engine is far more portable than
expected — most of it is reflection through `mirror.*` wrappers, so the SDK
moving under it barely matters at compile time.

## HARD BLOCKER: arm64

Built **armeabi-v7a only**. With `arm64-v8a` enabled, `IOUniformer.cpp` fails
with ~20 errors of the form:

```
error: use of undeclared identifier '__NR_chmod'    (also __NR_unlink, __NR_rename,
__NR_link, __NR_symlink, __NR_stat64, __NR_fstat64, __NR_fstatat64, __NR_access,
__NR_mknod, __NR_utimes, __NR_chown, __NR_rmdir, __NR_statfs64 ...)
```

These syscalls **do not exist on AArch64**. The arm64 kernel ABI dropped the
legacy forms and keeps only the `*at` family — `fchmodat`, `unlinkat`,
`renameat`, `newfstatat`, `faccessat`, `openat`. The IO redirection layer hooks
the 32-bit syscall table, so arm64 support is a redesign of that layer, not a
port. This is exactly item 7 of ENGINE-PLAN.md (4–8 weeks).

Practical consequence: as built it can only host **32-bit guests**, on a device
that still has 32-bit support. Most current apps ship arm64-only.

## Not yet known

Compiling is not running. Still untested:
- hidden-API blocklist (API 28+) — this code predates it entirely and reflects
  into framework internals everywhere. M0 proved `HiddenApiBypass` works here,
  so wiring it in is the obvious first runtime fix.
- `ClientTransaction` / `ActivityThread` changes across Android 9->15
- package visibility (11+), scoped storage, BAL (14+), PendingIntent flags (31+)
- the ~40 service proxies reference framework methods whose signatures moved;
  expect silent no-ops rather than clean failures

## Next step

Wire `lib-debug.aar` into the M0 host (which already boots on API 35 and has
HiddenApiBypass working) and call VirtualApp's init. That reuses a known-good
shell instead of porting the `:app` demo with its support-library and Tencent
SDK baggage.

---

## RUNTIME: engine boots on Android 15 (11-Nov-2026)

Host: `va-host/` — a minimal app that depends on `lib-debug.aar` and does
VirtualApp's own init (`VirtualCore.startup()` in `attachBaseContext`,
`initialize()` in `onCreate`). M0's host was not reused: VirtualApp needs its
own Application lifecycle.

```
== VA boot 35 / [arm64-v8a, armeabi-v7a, armeabi] ==
process=20092 64bit=false
startup() OK
initialize -> onServerProcess
initialize() OK
isStartup       : true
isEngineLaunched: false
installed apps  : 0
```

```
u0_a450 20031 dev.bello.vahost        <- main
u0_a450 20092 dev.bello.vahost:x      <- engine server, alive, no crash loop
```

Manifest merge worked unmodified: 255 components registered, including
`dev.bello.vahost.virtual.service.BinderProvider` and the `virtual_stub_0..N`
provider pool, all namespaced to the host via `${applicationId}`.

### `targetSdk` matters and is constrained from both ends

- Android 15 **refuses to install** below targetSdk 24
  (`INSTALL_FAILED_VERIFICATION_FAILURE`), so 23 is not available.
- Hidden-API enforcement is keyed to targetSdk, so **24 is the sweet spot**: the
  lowest installable, hence the most lenient. The engine's reflection survives
  at 24 without even needing HiddenApiBypass.
- Raising targetSdk later will re-break this. Treat every bump as a milestone.

### Two runtime fixes were needed

1. **`Long cannot be cast to Integer`** — crash loop in `:x` at
   `BinderProvider.onCreate` -> `VActivityManagerService.onCreate` ->
   `MethodProxies$GetPackageInfo`. `IPackageManager.getPackageInfo`'s flags
   parameter became `long` in Android 13; the hook still did `(int) args[1]`,
   which compiles to an `Integer` unbox. Fixed mechanically across **47 sites**:
   `(int) args[N]` -> `((Number) args[N]).intValue()`.
   Expect the same class of break in every proxy as more of the API surface is
   exercised. This is the main ongoing tax.
2. **`CannotPostForegroundServiceNotificationException`** — `DaemonService`
   called `startForeground(id, new Notification())`: no channel, no small icon.
   Channels are mandatory since O and an invalid FGS notification now kills the
   process. Added a channelled builder with a fallback below O.

### Where this actually stands

Booting is not hosting. `installed apps: 0` and `isEngineLaunched: false` —
nothing has been installed or launched yet. The service proxies have barely been
touched; the `getPackageInfo` break was found by the engine calling **its own**
PackageManager hook once, during startup. Installing and launching a real guest
will exercise dozens more.

Still true and unchanged: **32-bit only**. arm64 needs the `IOUniformer` rewrite
onto the `*at` syscalls (ENGINE-PLAN.md item 7).

### Next

`VirtualCore.get().installPackage(apkPath, flags)` with a small test apk, then
launch it. That is the first real exercise of the proxy layer.

---

## INSTALL WORKS; LAUNCH HANGS (11-Nov-2026, later)

```
installPackage -> success=true pkg=dev.bello.container.guest
installed=[dev.bello.container.guest]
getLaunchIntent -> Intent { MAIN/LAUNCHER cmp=dev.bello.container.guest/.GuestActivity }
```

Three processes alive: `dev.bello.vahost`, `:x` (server), `:p0` (virtual).
`:p0` is started by the framework **for `StubContentProvider$C0`** — the stub
provider pool is doing its job.

### Four more port fixes, all in the parse path

1. **`PackageParserCompat.<clinit>` NPE.** `static final sUserState =
   PackageUserState.ctor.newInstance()` — `android.content.pm.PackageUserState`
   no longer exists. Made lazy via `FrameworkUserStateCompat`.
2. **`PackageUserState` -> `FrameworkPackageUserState`.** The device reports
   `generateApplicationInfo(Package, int, FrameworkPackageUserState)`: the class
   became the interface `android.content.pm.pkg.FrameworkPackageUserState`, with
   no reachable implementation. Supplied one with a **dynamic Proxy** that
   answers "installed, unhidden, enabled" (`FrameworkUserStateCompat`). New
   mirror `PackageParserT` carries the renamed parameter type; `PackageParserCompat`
   gained an `API_LEVEL >= 33` branch in all five `generate*` methods.
3. **`collectCertificates` gone** in its old form. Skipped on 33+. The device
   does still expose `collectCertificates(Package, boolean)` and
   `collectCertificatesInternal(Package, boolean)`, so this is wireable when
   guests that self-verify matter.
4. **`NoSuchFieldError: mSignatures`.** The in-tree
   `android/content/pm/PackageParser.java` is only a **compile-time stub** — at
   runtime the framework's class wins, and `mSignatures` became `mSigningDetails`
   in Android 9. All seven direct field reads in `PackageParserEx`
   (`mSignatures`, `mAppMetaData`, `mPreferredOrder`, `mVersionName`,
   `mSharedUserId`, `mSharedUserLabel`, `mVersionCode`) now go through tolerant
   reflective `peek()`/`poke()` helpers.

   **This trap generalises:** anywhere the tree compiles against its own copy of
   a framework class, the compiler is happy and the device is not.

### Where it stops

`VActivityManager.startActivity()` never returns. Captured stack of the calling
thread:

```
at android.os.BinderProxy.transactNative(Native Method)
at com.lody.virtual.helper.ipcbus.IPCMethod.callRemote(IPCMethod.java:107)
at $Proxy7.startActivity(Unknown Source)
at com.lody.virtual.client.ipc.VActivityManager.startActivity(:58)
```

So the caller is blocked in a synchronous binder transaction into `:x`, and `:x`
never replies. `:p0` meanwhile boots (`TrafficStats init done`, `Miui Feature
Init done`) and then goes idle — it never receives the activity. The likely
cause is the VClient attach handshake: `:x` waits for the new virtual process to
report in, and that report never arrives.

**Confirmed working along the way:** hidden-API reflection in `:p0` is *allowed*
(`ActivityThread.mH`, `mBoundApplication`, `mInitialApplication`,
`mInstrumentation` — all logged as `allowed` at `TargetSdkVersion=24`). The
low-targetSdk strategy is doing exactly what it was chosen for.

### Debugging notes worth keeping

- Run engine calls **off the main thread with a watchdog** that dumps the
  worker's stack. A synchronous binder hang is otherwise indistinguishable from
  a silent freeze, and MIUI shows nothing.
- Give **each process its own report file**. `main` and `:x` both boot the
  engine and a shared file meant whichever started second truncated the other's
  output — that cost a cycle chasing "missing" log lines.
- MIUI's `SwipeUpClean` kills the host; a dead main process is not necessarily
  a crash.

### Next

Find why `:x` doesn't reply. Instrument `VActivityManagerService.startActivity`
and the VClient attach path, or dump `:x`'s threads on a timeout the same way.
