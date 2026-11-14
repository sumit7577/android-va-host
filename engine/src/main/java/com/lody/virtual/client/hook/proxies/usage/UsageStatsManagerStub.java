package com.lody.virtual.client.hook.proxies.usage;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

import com.lody.virtual.client.hook.base.BinderInvocationProxy;
import com.lody.virtual.client.hook.base.ReplaceLastPkgMethodProxy;
import com.lody.virtual.client.hook.base.ResultStaticMethodProxy;

import mirror.android.app.IUsageStatsManager;

/**
 * Created by caokai on 2017/9/8.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
public class UsageStatsManagerStub extends BinderInvocationProxy {

    public UsageStatsManagerStub() {
        super(IUsageStatsManager.Stub.asInterface, Context.USAGE_STATS_SERVICE);
    }
    @Override
    protected void onBindMethods() {
        super.onBindMethods();
        addMethodProxy(new ReplaceLastPkgMethodProxy("queryUsageStats"));
        addMethodProxy(new ReplaceLastPkgMethodProxy("queryConfigurations"));
        addMethodProxy(new ReplaceLastPkgMethodProxy("queryEvents"));
        // PORT FIX: getAppStandbyBucket needs GET_USAGE_STATS, which the HOST
        // does not hold either -- so rewriting the package cannot help, and
        // unproxied it threw:
        //   SecurityException: <guest> from uid N not allowed to perform
        //   GET_USAGE_STATS
        // killing apps that check their own bucket on startup. Answer with
        // ACTIVE (10) instead: a guest that is being launched genuinely is
        // active, so it is the truthful answer as well as the safe one.
        addMethodProxy(new ResultStaticMethodProxy("getAppStandbyBucket", 10));
        addMethodProxy(new ResultStaticMethodProxy("isAppInactive", false));
    }

}
