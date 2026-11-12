package com.lody.virtual.client.core;

import android.os.Build;

import com.lody.virtual.client.hook.base.MethodInvocationProxy;
import com.lody.virtual.client.hook.base.MethodInvocationStub;
import com.lody.virtual.client.hook.delegate.AppInstrumentation;
import com.lody.virtual.client.hook.proxies.account.AccountManagerStub;
import com.lody.virtual.client.hook.proxies.alarm.AlarmManagerStub;
import com.lody.virtual.client.hook.proxies.am.ActivityManagerStub;
import com.lody.virtual.client.hook.proxies.am.ActivityTaskManagerStub;
import com.lody.virtual.client.hook.proxies.am.HCallbackStub;
import com.lody.virtual.client.hook.proxies.appops.AppOpsManagerStub;
import com.lody.virtual.client.hook.proxies.appwidget.AppWidgetManagerStub;
import com.lody.virtual.client.hook.proxies.audio.AudioManagerStub;
import com.lody.virtual.client.hook.proxies.backup.BackupManagerStub;
import com.lody.virtual.client.hook.proxies.bluetooth.BluetoothStub;
import com.lody.virtual.client.hook.proxies.clipboard.ClipBoardStub;
import com.lody.virtual.client.hook.proxies.connectivity.ConnectivityStub;
import com.lody.virtual.client.hook.proxies.content.ContentServiceStub;
import com.lody.virtual.client.hook.proxies.context_hub.ContextHubServiceStub;
import com.lody.virtual.client.hook.proxies.devicepolicy.DevicePolicyManagerStub;
import com.lody.virtual.client.hook.proxies.display.DisplayStub;
import com.lody.virtual.client.hook.proxies.dropbox.DropBoxManagerStub;
import com.lody.virtual.client.hook.proxies.fingerprint.FingerprintManagerStub;
import com.lody.virtual.client.hook.proxies.graphics.GraphicsStatsStub;
import com.lody.virtual.client.hook.proxies.imms.MmsStub;
import com.lody.virtual.client.hook.proxies.input.InputMethodManagerStub;
import com.lody.virtual.client.hook.proxies.isms.ISmsStub;
import com.lody.virtual.client.hook.proxies.isub.ISubStub;
import com.lody.virtual.client.hook.proxies.job.JobServiceStub;
import com.lody.virtual.client.hook.proxies.libcore.LibCoreStub;
import com.lody.virtual.client.hook.proxies.location.LocationManagerStub;
import com.lody.virtual.client.hook.proxies.media.router.MediaRouterServiceStub;
import com.lody.virtual.client.hook.proxies.media.session.SessionManagerStub;
import com.lody.virtual.client.hook.proxies.mount.MountServiceStub;
import com.lody.virtual.client.hook.proxies.network.NetworkManagementStub;
import com.lody.virtual.client.hook.proxies.notification.NotificationManagerStub;
import com.lody.virtual.client.hook.proxies.persistent_data_block.PersistentDataBlockServiceStub;
import com.lody.virtual.client.hook.proxies.phonesubinfo.PhoneSubInfoStub;
import com.lody.virtual.client.hook.proxies.pm.PackageManagerStub;
import com.lody.virtual.client.hook.proxies.power.PowerManagerStub;
import com.lody.virtual.client.hook.proxies.restriction.RestrictionStub;
import com.lody.virtual.client.hook.proxies.search.SearchManagerStub;
import com.lody.virtual.client.hook.proxies.shortcut.ShortcutServiceStub;
import com.lody.virtual.client.hook.proxies.telephony.TelephonyRegistryStub;
import com.lody.virtual.client.hook.proxies.telephony.TelephonyStub;
import com.lody.virtual.client.hook.proxies.usage.UsageStatsManagerStub;
import com.lody.virtual.client.hook.proxies.user.UserManagerStub;
import com.lody.virtual.client.hook.proxies.vibrator.VibratorStub;
import com.lody.virtual.client.hook.proxies.view.AutoFillManagerStub;
import com.lody.virtual.client.hook.proxies.wifi.WifiManagerStub;
import com.lody.virtual.client.hook.proxies.wifi_scanner.WifiScannerStub;
import com.lody.virtual.client.hook.proxies.window.WindowManagerStub;
import com.lody.virtual.client.interfaces.IInjector;

import java.util.HashMap;
import java.util.Map;

import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR2;
import static android.os.Build.VERSION_CODES.KITKAT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.LOLLIPOP_MR1;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;

/**
 * @author Lody
 *
 */
public final class InvocationStubManager {

    private static InvocationStubManager sInstance = new InvocationStubManager();
    private static boolean sInit;

	private Map<Class<?>, IInjector> mInjectors = new HashMap<>(13);


	/** PORT FIX: stubs that could not be built or injected on this Android version. */
	private static final java.util.List<String> sFailures =
			java.util.Collections.synchronizedList(new java.util.ArrayList<String>());

	public static java.util.List<String> getInjectFailures() {
		return new java.util.ArrayList<>(sFailures);
	}

	private static void recordFailure(String what, Throwable e) {
		Throwable root = e;
		while (root.getCause() != null) root = root.getCause();
		String at = root.getStackTrace().length > 0 ? String.valueOf(root.getStackTrace()[0]) : "?";
		sFailures.add(what + " -> " + root.getClass().getSimpleName()
				+ ": " + root.getMessage() + " @" + at);
	}

	interface StubFactory { IInjector make(); }

	/** Construct + register a stub, surviving a clinit/ctor that no longer resolves. */
	private void safeAdd(StubFactory f) {
		try {
			addInjector(f.make());
		} catch (Throwable e) {
			recordFailure("create", e);
		}
	}

	private InvocationStubManager() {
	}

	public static InvocationStubManager getInstance() {
		return sInstance;
	}

	void injectAll() throws Throwable {
		for (IInjector injector : mInjectors.values()) {
			// PORT FIX: one stub whose framework target has moved must not abort
			// the whole engine. Record and continue; see getInjectFailures().
			try {
				injector.inject();
			} catch (Throwable e) {
				recordFailure("inject " + injector.getClass().getSimpleName(), e);
			}
		}
		// XXX: Lazy inject the Instrumentation,
		addInjector(AppInstrumentation.getDefault());
	}

    /**
	 * @return if the InvocationStubManager has been initialized.
	 */
	public boolean isInit() {
		return sInit;
	}


	public void init() throws Throwable {
		if (isInit()) {
			throw new IllegalStateException("InvocationStubManager Has been initialized.");
		}
		injectInternal();
		sInit = true;

	}

	private void injectInternal() throws Throwable {
		if (VirtualCore.get().isMainProcess()) {
			return;
		}
		if (VirtualCore.get().isServerProcess()) {
			safeAdd(() -> new ActivityManagerStub());
			if (ActivityTaskManagerStub.isSupported()) {
				safeAdd(() -> new ActivityTaskManagerStub());
			}
			safeAdd(() -> new PackageManagerStub());
			return;
		}
		if (VirtualCore.get().isVAppProcess()) {
			safeAdd(() -> new LibCoreStub());
			safeAdd(() -> new ActivityManagerStub());
			// Android 10+ moved startActivity and friends off IActivityManager.
			if (ActivityTaskManagerStub.isSupported()) {
				safeAdd(() -> new ActivityTaskManagerStub());
			}
			safeAdd(() -> new PackageManagerStub());
			safeAdd(() -> HCallbackStub.getDefault());
			safeAdd(() -> new ISmsStub());
			safeAdd(() -> new ISubStub());
			safeAdd(() -> new DropBoxManagerStub());
			safeAdd(() -> new NotificationManagerStub());
			safeAdd(() -> new LocationManagerStub());
			safeAdd(() -> new WindowManagerStub());
			safeAdd(() -> new ClipBoardStub());
			safeAdd(() -> new MountServiceStub());
			safeAdd(() -> new BackupManagerStub());
			safeAdd(() -> new TelephonyStub());
			safeAdd(() -> new TelephonyRegistryStub());
			safeAdd(() -> new PhoneSubInfoStub());
			safeAdd(() -> new PowerManagerStub());
			safeAdd(() -> new AppWidgetManagerStub());
			safeAdd(() -> new AccountManagerStub());
			safeAdd(() -> new AudioManagerStub());
			safeAdd(() -> new SearchManagerStub());
			safeAdd(() -> new ContentServiceStub());
			safeAdd(() -> new ConnectivityStub());

			if (Build.VERSION.SDK_INT >= JELLY_BEAN_MR2) {
				safeAdd(() -> new VibratorStub());
				safeAdd(() -> new WifiManagerStub());
				safeAdd(() -> new BluetoothStub());
				safeAdd(() -> new ContextHubServiceStub());
			}
			if (Build.VERSION.SDK_INT >= JELLY_BEAN_MR1) {
				safeAdd(() -> new UserManagerStub());
			}

			if (Build.VERSION.SDK_INT >= JELLY_BEAN_MR1) {
				safeAdd(() -> new DisplayStub());
			}
			if (Build.VERSION.SDK_INT >= LOLLIPOP) {
				safeAdd(() -> new PersistentDataBlockServiceStub());
				safeAdd(() -> new InputMethodManagerStub());
				safeAdd(() -> new MmsStub());
				safeAdd(() -> new SessionManagerStub());
				safeAdd(() -> new JobServiceStub());
				safeAdd(() -> new RestrictionStub());
			}
			if (Build.VERSION.SDK_INT >= KITKAT) {
				safeAdd(() -> new AlarmManagerStub());
				safeAdd(() -> new AppOpsManagerStub());
				safeAdd(() -> new MediaRouterServiceStub());
			}
			if (Build.VERSION.SDK_INT >= LOLLIPOP_MR1) {
				safeAdd(() -> new GraphicsStatsStub());
				safeAdd(() -> new UsageStatsManagerStub());
			}
			if (Build.VERSION.SDK_INT >= M) {
				safeAdd(() -> new FingerprintManagerStub());
				safeAdd(() -> new NetworkManagementStub());
			}
			if (Build.VERSION.SDK_INT >= N) {
                safeAdd(() -> new WifiScannerStub());
                safeAdd(() -> new ShortcutServiceStub());
                safeAdd(() -> new DevicePolicyManagerStub());
            }
            if (Build.VERSION.SDK_INT >= 26) {
				safeAdd(() -> new AutoFillManagerStub());
			}
		}
	}

	private void addInjector(IInjector IInjector) {
		mInjectors.put(IInjector.getClass(), IInjector);
	}

	public <T extends IInjector> T findInjector(Class<T> clazz) {
		// noinspection unchecked
		return (T) mInjectors.get(clazz);
	}

	public <T extends IInjector> void checkEnv(Class<T> clazz) {
		IInjector IInjector = findInjector(clazz);
		if (IInjector != null && IInjector.isEnvBad()) {
			try {
				IInjector.inject();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	public <T extends IInjector, H extends MethodInvocationStub> H getInvocationStub(Class<T> injectorClass) {
		T injector = findInjector(injectorClass);
		if (injector != null && injector instanceof MethodInvocationProxy) {
			// noinspection unchecked
			return (H) ((MethodInvocationProxy) injector).getInvocationStub();
		}
		return null;
	}

}