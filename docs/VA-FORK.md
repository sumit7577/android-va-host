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

---

## LAUNCH REACHES THE STUB; THE SWAP IS THE LAST GAP (11-Nov-2026, evening)

`startActivity -> 0`. No hang, no crash. The framework resumes the stub:

```
ResumedActivity: dev.bello.vahost/com.lody.virtual.client.stub.StubActivity$C0
START u0 {typ=dev.bello.container.guest/dev.bello.container.guest.GuestActivity
          cmp=dev.bello.vahost/com.lody.virtual.client.stub.StubActivity$C0}
Task{ A=10450:com.lody.virtual.vt }
```

The real target rides in the intent's `typ` field while `cmp` is the stub — the
component-rewriting trick working end to end. The virtual filesystem is built
too:

```
virtual/data/app/dev.bello.container.guest/{base.apk, lib, package.ini}
virtual/data/app/system/{packages.ini, uid-list.ini}
```

**But the guest never runs.** The stub activity launches and immediately
finishes, repeatedly (`deliverResultForFinishActivity`, then another START).
No `GuestActivity`, no guest report file.

**Why: the ActivityInfo swap never happens.** `HCallbackStub` performs it by
intercepting `ActivityThread$H.LAUNCH_ACTIVITY`, which Android 9 removed in
favour of `EXECUTE_TRANSACTION` / `ClientTransaction`. We had to null-guard that
constant just to let `startup()` finish, so the hook is now inert and the stub
has nothing to become.

### Four more fixes this round

1. **`HCallbackStub.<clinit>` NPE** on `ActivityThread.H.LAUNCH_ACTIVITY.get()`
   — removed in Android 9. Null-guarded (`SCHEDULE_CRASH` already was).
   This was the cause of the earlier **binder hang**: startup() aborted in every
   virtual process, so the client never attached and the server never replied.
2. **Resilient stub injection.** `injectAll`/`injectInternal` now catch per
   stub and record failures (`InvocationStubManager.getInjectFailures()`)
   instead of one bad stub killing the engine. 48 construction sites routed
   through `safeAdd`. Current score on Android 15: **1 failure** —
   `WindowManagerStub.inject` (a `RefStaticObject` that no longer resolves).
3. **`ProviderHook` fixed argument offsets.** `start` was hard-coded to 1.
   `IContentProvider.call` is now
   `(AttributionSource, String authority, String method, String arg, Bundle extras)`,
   so the old offset read a String as a Bundle:
   `ClassCastException: String cannot be cast to Bundle`, thrown from
   `PhoneWindow.<init>` during `Activity.attach`. Now anchored on parameter
   **types** (first `Uri`, trailing `Bundle`), which is stable across versions.
4. **`SecurityException: Calling uid: 10450 doesn't match source uid: 10001`.**
   Android 12+ validates the `AttributionSource` against the real caller, and
   the engine's spoofed identity leaked into it. The host's genuine
   AttributionSource is now restored before the call is passed on.
   **Trade-off:** provider calls attribute to the host, not the guest. Correct
   per-guest attribution is unsolved.

### Next, and it is the known one

Implement the activity swap for `ClientTransaction`-era Android. Two options:

- intercept `EXECUTE_TRANSACTION` and rewrite `LaunchActivityItem`'s
  ActivityInfo — faithful to the design, but the item's shape changed in 14
  and again in 15, so it is version-specific; or
- do the swap in `AppInstrumentation` (the engine already injects it) using
  `Instrumentation.newActivity`, the technique M0 proved on this exact device.
  See `docs/M0_GuestInstrumentation.kt.reference`.

The second is the cheaper bet and reuses work already validated here.

---

## THE REAL FLOOR: native ART hooking (11-Nov-2026, night)

The ClientTransaction swap is implemented and **the hook fires correctly**:

```
HCallbackStub: EXECUTE_TRANSACTION id=159 obj=android.app.servertransaction.ClientTransaction
HCallbackStub: transaction items = android.app.servertransaction.LaunchActivityItem
HCallbackStub: stubIntent = Intent { typ=dev.bello.container.guest/.GuestActivity
                                     cmp=dev.bello.vahost/...StubActivity$C0 }
HCallbackStub: target=dev.bello.container.guest/.GuestActivity token=true bound=false
```

Implementation notes: `ActivityThread$H.EXECUTE_TRANSACTION` added to the mirror;
the item list field is probed (`mTransactionItems` / `mActivityCallbacks` /
`mCallbacks`) rather than assumed, and on this device it is found; the swap then
rewrites `LaunchActivityItem.mIntent` and `.mInfo` exactly as the old
LAUNCH_ACTIVITY path did.

It never gets to rewrite them, because `VClientImpl.bindApplication()` is reached
with `bound=false` and **the process dies inside it**:

```
Cmdline: dev.bello.container.guest        <-- process renamed to the GUEST
signal 11 (SIGSEGV), SEGV_MAPERR, fault addr 0x9, null pointer dereference
#00 libva++.so  measureNativeOffset(bool)+50
#01 libva++.so  hookAndroidVM(...)
#02 libva++.so  jni_nativeLaunchEngine(...)
```

Two things in that one tombstone:

1. **Identity spoofing works.** `bindApplication` got far enough to rename the
   process to `dev.bello.container.guest`. The engine really does take on the
   guest's identity.
2. **The native ART hook does not.** `measureNativeOffset()` probes ART's
   internal object layout by scanning for known offsets. Those offsets are 2017
   assumptions about ART; on Android 15's ART it reads garbage and dereferences
   null.

### What this means

Everything above the native layer is now either working or mechanically
fixable — build, boot, install, stub pool, component rewriting, the
ClientTransaction swap, provider hooks, identity. The remaining floor is
**native ART internals**, and that is not an API rename you can chase with
reflection. It is the part the original plan priced at months, and it is now
pinned to a specific function.

### Options from here

- **Replace the ART hooking** with a maintained library — LSPlant (LGPL-3.0) is
  exactly this, is still updated, and is what Katana themselves credit. This is
  the serious path: delete `measureNativeOffset`/`hookAndroidVM` and bridge to
  LSPlant + ShadowHook/ByteHook.
- **Try without it.** `VASettings.ENABLE_IO_REDIRECT = false` removes the
  IOUniformer half; `hookAndroidVM` is a separate call from
  `jni_nativeLaunchEngine`, so this may not avoid the crash. Cheap to test.
- **Accept the ceiling.** Without native hooking there is no sandbox: no path
  redirection, no native method interception. The Java layer alone cannot host
  a real app.

Still 32-bit only; arm64 remains blocked on the `*at` syscall rewrite, and that
work and this work are in the same file family.

---

## arm64 UNBLOCKED AT BUILD TIME (12-Nov-2026)

`libva++.so` now builds for **arm64-v8a** as well as armeabi-v7a. Zero errors.
The "4-8 week IOUniformer rewrite" collapsed into two compile-time changes,
once the Meta APK showed that a shipping container does no syscall redirection
at all.

### 1. IOUniformer compiled out on 64-bit

`Foundation/IOUniformer.cpp` is wrapped in
`#if defined(__aarch64__) || defined(__x86_64__)` with the seven public entry
points stubbed — `query`/`reverse` return the path unchanged, the rest no-op.
That removed all ~20 `__NR_chmod` / `__NR_unlink` / `__NR_rename` /
`__NR_stat64` / `__NR_access` errors at a stroke, instead of porting 43 call
sites onto the `*at` family.

**Consequence:** on 64-bit, a guest's raw native file access sees real paths.
Java-level redirection still applies. This is the same trade-off Mundo makes.

### 2. Substrate has no arm64 inline hooker

One error survived: `SubstrateHook.cpp:927: use of undeclared identifier
'SubstrateHookFunction'`. That function is defined only under `#ifdef __arm__`
and `#if defined(__i386__) || defined(__x86_64__)` — this 2012 CydiaSubstrate
derivative predates arm64 entirely. `MSHookFunction` is now a no-op on
`__aarch64__`.

This is exactly the gap Mundo fills with **Dobby**, whose string appears in
`libmundo.so`. Substrate is simply the wrong component to carry forward.

### Honest status

arm64 **builds**; it is not yet known to **run**. The ART-hooking crash
(`measureNativeOffset` -> `hookAndroidVM`) lives in `VMPatch`/`SymbolFinder`,
which still compile for both ABIs, so 64-bit will hit the same SIGSEGV until
LSPlant replaces that layer. What has changed is that arm64 is no longer a
multi-week porting wall — it is downstream of the same single fix as 32-bit.

`app/build.gradle` deliberately still pins `abiFilters "armeabi-v7a"`: 32-bit
is the better-tested configuration today. Flip it to include `arm64-v8a` once
LSPlant is in.

### Remaining native work, now a short list

| Component | VirtualApp (2017) | Replace with |
| --- | --- | --- |
| Java method hooking | `measureNativeOffset` / `hookAndroidVM` — **SIGSEGV on 15** | **LSPlant** (what Mundo links) |
| Native inline hooking | Substrate — **no arm64** | **Dobby** (Apache-2.0) or ShadowHook (MIT) |
| Path redirection | IOUniformer — no arm64 syscalls | **nothing** — Mundo ships without it |

---

## IT WORKS — guest app running inside the container (12-Nov-2026)

A non-installed apk is installed into the engine, launched into a stub slot, and
runs in its own process with a fully virtualised identity, on **Android 15**.

The guest's own report, written to its **redirected** data dir:

```
--- GUEST ACTIVITY RAN ---
GUEST RESOURCES OK — this string came from guest.apk

packageName  : dev.bello.container.guest
uid          : 10001
dataDir      : /data/user/0/dev.bello.vahost/virtual/data/user/0/dev.bello.container.guest
filesDir     : .../virtual/data/user/0/dev.bello.container.guest/files
sourceDir    : .../virtual/data/app/dev.bello.container.guest/base.apk
class loader : PathClassLoader[... /virtual/data/app/.../base.apk ...]
```

```
u0_a450 13651 dev.bello.vahost
u0_a450 13738 dev.bello.vahost:x
u0_a450 13793 dev.bello.container.guest     <- the guest, in its own process
```

`stub injection failures: 0`. Compare with M0, where `packageName` was still the
host's: identity, data dir, resources and class loader are now all the guest's.

### The four fixes that closed it

1. **`VMPatch` ArtMethod patching gated off on API >= 28.** `measureNativeOffset`
   discovers ArtMethod's layout by scanning forward from a jmethodID for the JNI
   pointer; on 15 that base is no longer valid and it SIGSEGVs at `fault addr
   0x9`. Its four patches — `getCallingUid`, `openDexFileNative`, camera setup,
   audio permission — are identity/permission **refinements**, not launch
   prerequisites, so skipping them removed the crash and cost nothing visible.
   **They remain inactive; restoring them is the LSPlant job.**
2. **`VMRuntime.setTargetSdkVersion` null-guarded** — gone from modern
   `dalvik.system.VMRuntime`; it only selects compat behaviours.
3. **Hidden-API exemptions applied in EVERY process, before `startup()`.** This
   was the big one. They had only ever been applied in the main process, so the
   virtual processes — the ones that actually need framework internals —
   resolved their mirrors against the restricted view.
   `LoadedApk.makeApplicationInner` exists at runtime (the framework's own stack
   traces show it) but `getDeclaredMethods()` **hides it**, so the mirror was
   null and the code fell through to the deprecated `makeApplication` wrapper,
   which returns NULL. With exemptions applied, both the 2-arg and private 3-arg
   forms become visible. This also took `:p0`'s stub injection failures from 1
   to 0.
4. **The masked exception.** `bindApplicationNoCheck`'s catch block dereferenced
   `mInitialApplication` — null exactly when `makeApplication` failed — so the
   real error was replaced by an NPE thrown from the error path. Fixed to log
   the true cause chain first.

### One loose end to understand

The call order is Inner3 -> Inner2 -> legacy. On this device the private 3-arg
`makeApplicationInner(boolean, Instrumentation, boolean)` throws
`InvocationTargetException`, and the 2-arg call immediately after **succeeds**.
That smells like the 3-arg attempt doing the construction and failing late,
leaving `mApplication` set, with the 2-arg then returning the cached instance.
It works, but it is accidental. Unwrap the InvocationTargetException and find
out what the 3-arg form actually throws before trusting this.

### Current honest state

| | |
| --- | --- |
| Guest installs | yes |
| Guest process with own identity/uid/dataDir | yes |
| Guest Activity runs, own resources + class loader | yes |
| Stub injection failures | 0 |
| `getCallingUid` / dex / camera / audio patches | **inactive** (need LSPlant) |
| Native inline hooking | **stubbed on arm64** (needs Dobby) |
| Path redirection | Java-level only, as Mundo does |
| arm64 | builds; untested at runtime |
| Guest with native code, Play Services, real apps | untested |

Next: unwrap the Inner3 exception; then LSPlant to restore the four patches;
then try a real third-party apk.

### Loose end closed (partly): the double call is load-bearing

Unwrapping the InvocationTargetException showed the 3-arg form failing inside
new AOSP code:

```
Inner3 threw NullPointerException: Attempt to read from null array
  at LoadedApk.registerAppInfoToArt(LoadedApk.java:1202)
  at LoadedApk.createOrUpdateClassLoaderLocked(LoadedApk.java:1105)
  at LoadedApk.getClassLoader(LoadedApk.java:1160)
  at LoadedApk.makeApplicationInner(LoadedApk.java:1491)
```

Defaulting ApplicationInfo's split arrays to empty turned that into
`ArrayIndexOutOfBoundsException: length=0; index=0`, so empty is as wrong as
null; that change was reverted.

The useful finding came from testing three call orderings
(Inner3->Inner2, Inner2->legacy, legacy->Inner2): **whichever method is called
first returns null, and whichever is called second succeeds.** The behaviour
tracks call ORDER, not the method — the first call constructs the Application
and fails to return it. Reading `LoadedApk.mApplication` back afterwards did
not recover the instance, so it is cached elsewhere or that mirror does not
resolve.

Conclusion: the retry is **load-bearing**, and is commented as such in
`VClientImpl` so nobody deletes it as redundant. Still unresolved: where the
first call loses the reference.
