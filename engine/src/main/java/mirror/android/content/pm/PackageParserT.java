package mirror.android.content.pm;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;

import mirror.MethodReflectParams;
import mirror.RefClass;
import mirror.RefStaticMethod;

/**
 * PORT FIX (Android 13+): identical to {@link PackageParserMarshmallow} except
 * that the user-state parameter is now the interface
 * android.content.pm.pkg.FrameworkPackageUserState. Same arities, so only the
 * declared parameter type changes.
 */
public class PackageParserT {
    public static Class<?> TYPE = RefClass.load(PackageParserT.class, "android.content.pm.PackageParser");

    @MethodReflectParams({"android.content.pm.PackageParser$Activity", "int", "android.content.pm.pkg.FrameworkPackageUserState", "int"})
    public static RefStaticMethod<ActivityInfo> generateActivityInfo;

    @MethodReflectParams({"android.content.pm.PackageParser$Package", "int", "android.content.pm.pkg.FrameworkPackageUserState"})
    public static RefStaticMethod<ApplicationInfo> generateApplicationInfo;

    @MethodReflectParams({"android.content.pm.PackageParser$Package", "[I", "int", "long", "long", "java.util.Set", "android.content.pm.pkg.FrameworkPackageUserState"})
    public static RefStaticMethod<PackageInfo> generatePackageInfo;

    @MethodReflectParams({"android.content.pm.PackageParser$Provider", "int", "android.content.pm.pkg.FrameworkPackageUserState", "int"})
    public static RefStaticMethod<ProviderInfo> generateProviderInfo;

    @MethodReflectParams({"android.content.pm.PackageParser$Service", "int", "android.content.pm.pkg.FrameworkPackageUserState", "int"})
    public static RefStaticMethod<ServiceInfo> generateServiceInfo;
}
