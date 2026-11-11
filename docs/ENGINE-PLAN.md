# Path B — building the container engine yourself

Plan for replacing the Mundo SDK with your own engine, behind the same
`net_62v.external.*` façade the launcher UI already calls.

The façade is already written: `app/src/stub/kotlin/net_62v/external/MundoStubs.kt`.
Today every method is a logged no-op. **Path B = making those six methods real.**
The UI never changes.

---

## 0. Before writing any code — three decisions

### 0.1 Licence check (blocks everything)

Nobody writes this from zero. You fork a VirtualApp-lineage engine. The question
is which, and what it obliges you to publish.

| Candidate | Check |
| --- | --- |
| VirtualApp (asLody) | historically GPL-3.0 + separate paid commercial licence |
| BlackBox / NewBlackbox | historically GPL-3.0 |
| Your own from scratch | no obligation, no chance |

**Unverified — verify before committing.** If GPL-3.0 holds, a closed commercial
product built on a fork requires full source release of the derivative work. That
is very likely *why* Katana sells a closed binary and credits only the MIT/Apache
hook libraries.

Hook libraries are fine either way: LSPlant **LGPL-3.0**, ShadowHook **MIT**,
ByteHook **MIT**, dexmaker **Apache-2.0**, AndroidHiddenApiBypass **Apache-2.0**.

### 0.2 Scope — general vs single-target

A container that runs *any* APK is a team-years product. A container that runs
*one known APK* you control is a few months. Decide which you are building. Most
of the cost below is generality.

### 0.3 Rooted or not

If your users are rooted, **you do not need this engine at all** — LSPosed/Zygisk
gives Java (LSPlant) and native hooking today, open source. The container exists
to hook *without* root. Only build it if unrooted distribution is the product.

---

## 1. Architecture

Two sides in one APK:

**Server side** — your own reimplementations of the framework services, holding
all truth about guests: installed guest list, parsed manifests, running process
records, fake uids, per-partition data dirs. Lives in a dedicated process, reached
over Binder.

**Client side** — code injected into every stub process at startup that makes the
guest believe it is running as itself.

**Stub processes** — `:p0 … :pN` declared in your manifest. One guest process
occupies one stub process. `STUB1..24` activities = launch-mode × count pooling;
stub providers host guest content providers.

### Bootstrap order (get this wrong and nothing else matters)

1. **ContentProvider.onCreate** — fires *before* `Application.onCreate`. This is
   your entry point (Mundo's `MundoSupervisorProvider`).
2. **HiddenApiBypass** — must run before any `@hide` reflection. Free, drop-in.
3. **ServiceManager cache poisoning** — replace cached `IBinder`s with dynamic
   proxies so every later framework call routes through you.
4. **`ActivityManager.IActivityManagerSingleton` / `ActivityTaskManager`** hooks.
5. **`Instrumentation.execStartActivity`** — rewrite the outgoing Intent's
   component to a free stub, stash the real one as an extra.
6. **`ActivityThread.mH` Handler.Callback** — intercept
   `LAUNCH_ACTIVITY`/`EXECUTE_TRANSACTION`, swap the stub `ActivityInfo` back to
   the real one before the activity is instantiated.
7. **LoadedApk construction** — guest `ClassLoader`, guest `Resources`
   (`AssetManager.addAssetPath`), patch `ContextImpl` paths and `ApplicationInfo`.
8. **Native IO hooks** — must be installed before any guest `.so` loads.

---

## 2. Work breakdown

| # | Piece | Effort | Notes |
| --- | --- | --- | --- |
| 1 | Hidden-API bypass | **hours** | AndroidHiddenApiBypass, Apache-2.0 |
| 2 | Stub pool + manifest codegen | **days** | Gradle task emitting `STUB1..N` + `:pN`; few hundred lines |
| 3 | Guest install pipeline | **1–2 wk** | parse APK, copy, extract `lib/<abi>`, make data dirs, record in server |
| 4 | Intent interception / component rewrite | **2–3 wk** | Instrumentation + mH + AMS proxy |
| 5 | **LoadedApk / ClassLoader / Resources** | **3–5 wk** | the first genuinely hard one; real framework internals |
| 6 | **Binder proxies, ~30–40 services** | **8 wk+, never done** | the thousands of classes — see §3 |
| 7 | **Native path redirection** | **4–8 wk** | libc hooks; Java-level is not enough — see §4 |
| 8 | `dlopen` / native lib loading, 32-v-64 | **2–4 wk** | stub process ABI must match guest |
| 9 | Signature spoofing | **1–2 wk** | works for naive checks only; dead against attestation |
| 10 | Partitions (clones) | **1–2 wk** | a `userId` dimension over data dirs + process pools |

Solo, realistically: **6–12 months to something usable for a narrow app set.**
General-purpose is not a solo project.

---

## 3. The system services (item 6 — the bulk)

Each needs per-method identity faking: caller package, uid, pid, data paths.

- **IActivityManager / IActivityTaskManager** — `startActivity`, `startService`,
  `bindService`, `registerReceiver`, `broadcastIntent`, `getContentProvider`
- **IPackageManager** — `getPackageInfo`, `getApplicationInfo`,
  `queryIntentActivities`, `resolveActivity`, `getInstalledPackages`,
  `checkPermission`, `getPackageUid`, signatures
- **IContentProvider** — authority rewrite to stub provider, then unwrap
- **INotificationManager** — channels and package attribution, or guests crash
- **IAlarmManager / IJobScheduler** — **PendingIntent is the classic trap**: the
  system resolves them with the *host's* identity
- **IAppOps, IUserManager, IAccountManager, IClipboard, ITelephony,
  IPhoneSubInfo, ILocationManager, IWindowManager**

---

## 4. Path redirection (item 7)

Guest believes `/data/data/<guest.pkg>`; reality is
`/data/data/<host>/virtual/<userId>/<guest.pkg>`.

Java-side patching of `ContextImpl` covers Java APIs only. Guest **native** code
calls libc directly, so you need PLT (ByteHook) or inline (ShadowHook) hooks on:
`open`, `openat`, `stat`, `lstat`, `access`, `chmod`, `rename`, `unlink`,
`mkdir`, `rmdir`, `readlink`, `execve`, `opendir`. This is VirtualApp's
`IOUniformer`. Miss one and guests corrupt host state or crash unpredictably.

---

## 5. What modern Android does to you

This is where VirtualApp-era designs rot. Budget for all of it:

| API | Obstacle |
| --- | --- |
| 28+ | hidden-API blocklist → HiddenApiBypass |
| 29+ | no executing files from writable app home (W^X) — hits guest dex/so loading |
| 30+ | package visibility `<queries>`; `/proc` restrictions |
| 31+ | PendingIntent mutability flags mandatory; `android:exported` mandatory |
| 33+ | notification permission |
| 34+ | foreground-service types; **background activity launch restrictions** |
| 35/36 | further BAL hardening |
| all | SELinux; app data isolation; Play Integrity defeats signature spoofing |

**Every Android release breaks something.** The maintenance tax is permanent and
is the real reason vendors sell SDK subscriptions.

---

## 6. Milestones

**M0 — Go/no-go spike (2 weeks).** Host app loads a hello-world guest APK's
Activity through one stub, with guest ClassLoader + Resources. No service faking,
no IO redirection. This proves item 5, the hardest early piece.
**Kill criterion: if M0 isn't working in 2 weeks, abandon Path B and license.**

**M1 — Core boot (3–5 wk).** Provider-based init, HiddenApiBypass, stub pool,
server skeleton, guest install pipeline. (items 1–3)

**M2 — Activity path (4–6 wk).** Full rewrite/swap, launch modes, back stack.
(item 4) → *the six façade methods can now be implemented for real.*

**M3 — Services, providers, receivers (4–6 wk).** (part of item 6)

**M4 — Native IO + lib loading (4–8 wk).** (items 7–8) → *first real-world app
has a chance of working.*

**M5 — Service breadth (8 wk+).** Notifications, alarms, jobs, accounts.

**M6 — Compat.** Per-app and per-OEM quirks. MIUI will be its own tax; your test
device is MIUI, which is both convenient and misleading — test on stock too.

---

## 7. Test harness (build in M0, not later)

A guest that reports what it sees, and a checklist:

- own package name, uid, data dir, cache dir
- `getPackageInfo` on itself; signature check
- resources + assets load; theme applies
- start a second activity; receive a broadcast; bind a service
- write a file, read it back, confirm it landed in the redirected path
- load a `.so` and call into it
- survive process death and relaunch

Keep a per-Android-version result matrix from day one. It is the only honest
measure of progress.

---

## 8. Recommendation

The sequence that wastes the least time:

1. Verify the GPL-3.0 question (§0.1). If it blocks commercial use, Path B is
   mostly dead and the answer is a licence.
2. Ask Katana for the **free-tier aar** regardless — `assembleRealDebug` is
   wired, so it costs an email to find out whether their engine even works on
   your targets.
3. Run M0 in parallel. Two weeks, strict kill criterion.
4. Decide with data instead of estimates.

And if root is acceptable for your use: build an LSPosed module instead and skip
this entire document.

---

## M0 RESULT — PASSED (11-Nov-2026)

Run on a real device, Android 15 / API 35 (MIUI, arm64). Code in `m0-spike/`.

```
newActivity<- className=...host.StubActivity extra=...guest.GuestActivity ready=true
  loaded dev.bello.container.guest.GuestActivity
    definedBy=DexClassLoader[... /files/virtual/0/dev.bello.container.guest/base.apk]  same=true
callActivityOnCreate<- dev.bello.container.guest.GuestActivity
--- GUEST ACTIVITY RAN ---
GUEST RESOURCES OK — this string came from guest.apk
class loader : DexClassLoader[... base.apk]
packageName  : dev.bello.container.host      <-- still the host's, as expected
dataDir      : /data/user/0/dev.bello.container.host
```

**Proven:** an Activity from a *non-installed* apk is constructed by the system
into a stub slot, defined by our own DexClassLoader, and resolves its own
resources. That is item 5 of §2 — the first hard piece — working on a current
Android.

**Not proven, by design:** identity (packageName/uid/dataDir are still the
host's), services, providers, IO redirection, native loading. Items 6–8 are
untouched and remain the real walls.

### Things learned that the plan did not predict

1. **compileSdk 37 required** — HiddenApiBypass 6.2 refuses to compile against 36.
2. **MIUI silently drops app logcat.** `Log.i` from the app never appears, while
   4800 lines of system logs do. The spike writes to
   `files/m0-report.txt`, read with `adb exec-out run-as <pkg> cat`. Use a file
   channel for every later milestone; do not trust logcat on this device.
3. **AGP 9 rejects Providers in the SourceSet API** — generated asset dirs need
   the Variant API or a static dir. The spike uses a static dir.
4. **`setReadOnly()` is a trap for iteration.** W^X forces it, but it then makes
   a stale guest apk unoverwritable and the engine silently keeps running the
   old guest. `install()` now always deletes and re-copies.
5. **Instrumentation.newActivity was the right hook choice.** It fired first
   try on API 35, with no ClientTransaction version-sniffing.

### Next: M1

Guest install pipeline proper (multiple guests, manifest parse beyond the first
activity, lib extraction), stub pool codegen, and the server-side record of
installed guests. Then M2 swaps the ActivityInfo so theme/launchMode stop coming
from the stub.
