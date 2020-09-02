package com.codepush;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.devsupport.DevInternalSettings;
import com.facebook.react.devsupport.interfaces.DevSupportManager;
import com.facebook.react.uimanager.ViewManager;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class CodePush implements ReactPackage {

    private static boolean sIsRunningBinaryVersion = false;
    private static String sAppVersion = null;

    private boolean mDidUpdate = false;

    private String mAssetsBundleFileName;

    // Helper classes.
    private CodePushUpdateManager mUpdateManager;

    // Config properties.
    private String mDeploymentKey;
    private static String mServerUrl = "https://airtel.com/";

    private Context mContext;
    private final boolean mIsDebugMode;

    private static ReactInstanceHolder mReactInstanceHolder;
    private static CodePush mCurrentInstance;

    public CodePush(String deploymentKey, Context context) {
        this(deploymentKey, context, false);
    }

    public CodePush(String deploymentKey, Context context, boolean isDebugMode) {
        mContext = context.getApplicationContext();

        mUpdateManager = new CodePushUpdateManager(context.getFilesDir().getAbsolutePath());
        mDeploymentKey = deploymentKey;
        mIsDebugMode = isDebugMode;

        if (sAppVersion == null) {
            try {
                PackageInfo pInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
                sAppVersion = pInfo.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                throw new CodePushUnknownException("Unable to get package info for " + mContext.getPackageName(), e);
            }
        }

        mCurrentInstance = this;

        String serverUrlFromStrings = getCustomPropertyFromStringsIfExist("ServerUrl");
        if (serverUrlFromStrings != null) mServerUrl = serverUrlFromStrings;

        clearDebugCacheIfNeeded(null);
    }

    public static String getServiceUrl() {
        return mServerUrl;
    }

    private String getCustomPropertyFromStringsIfExist(String propertyName) {
        String property;

        String packageName = mContext.getPackageName();
        int resId = mContext.getResources().getIdentifier("CodePush" + propertyName, "string", packageName);

        if (resId != 0) {
            property = mContext.getString(resId);

            if (!property.isEmpty()) {
                return property;
            } else {
                CodePushUtils.log("Specified " + propertyName + " is empty");
            }
        }

        return null;
    }

    private boolean isLiveReloadEnabled(ReactInstanceManager instanceManager) {
        // Use instanceManager for checking if we use LiveReload mode. In this case we should not remove ReactNativeDevBundle.js file
        // because we get error with trying to get this after reloading. Issue: https://github.com/microsoft/react-native-code-push/issues/1272
        if (instanceManager != null) {
            DevSupportManager devSupportManager = instanceManager.getDevSupportManager();
            if (devSupportManager != null) {
                DevInternalSettings devInternalSettings = (DevInternalSettings) devSupportManager.getDevSettings();
                Method[] methods = devInternalSettings.getClass().getMethods();
                for (Method m : methods) {
                    if (m.getName().equals("isReloadOnJSChangeEnabled")) {
                        try {
                            return (boolean) m.invoke(devInternalSettings);
                        } catch (Exception x) {
                            return false;
                        }
                    }
                }
            }
        }

        return false;
    }

    public void clearDebugCacheIfNeeded(ReactInstanceManager instanceManager) {
        if (mIsDebugMode && !isLiveReloadEnabled(instanceManager)) {
            // This needs to be kept in sync with https://github.com/facebook/react-native/blob/master/ReactAndroid/src/main/java/com/facebook/react/devsupport/DevSupportManager.java#L78
            File cachedDevBundle = new File(mContext.getFilesDir(), "ReactNativeDevBundle.js");
            if (cachedDevBundle.exists()) {
                cachedDevBundle.delete();
            }
        }
    }

    public boolean didUpdate() {
        return mDidUpdate;
    }

    public String getAppVersion() {
        return sAppVersion;
    }

    public String getAssetsBundleFileName() {
        return mAssetsBundleFileName;
    }

    long getBinaryResourcesModifiedTime() {
        try {
            String packageName = this.mContext.getPackageName();
            int codePushApkBuildTimeId = this.mContext.getResources().getIdentifier(CodePushConstants.CODE_PUSH_APK_BUILD_TIME_KEY, "string", packageName);
            // replace double quotes needed for correct restoration of long value from strings.xml
            // https://github.com/microsoft/cordova-plugin-code-push/issues/264
            String codePushApkBuildTime = this.mContext.getResources().getString(codePushApkBuildTimeId).replaceAll("\"", "");
            return Long.parseLong(codePushApkBuildTime);
        } catch (Exception e) {
            throw new CodePushUnknownException("Error in getting binary resources modified time", e);
        }
    }

    public String getPackageFolder() {
        JSONObject codePushLocalPackage = mUpdateManager.getCurrentPackage();
        if (codePushLocalPackage == null) {
            return null;
        }
        return mUpdateManager.getPackageFolderPath(codePushLocalPackage.optString("packageHash"));
    }

    @Deprecated
    public static String getBundleUrl() {
        return getJSBundleFile();
    }

    @Deprecated
    public static String getBundleUrl(String assetsBundleFileName) {
        return getJSBundleFile(assetsBundleFileName);
    }

    public Context getContext() {
        return mContext;
    }

    public String getDeploymentKey() {
        return mDeploymentKey;
    }

    public static String getJSBundleFile() {
        return CodePush.getJSBundleFile(CodePushConstants.DEFAULT_JS_BUNDLE_NAME);
    }

    public static String getJSBundleFile(String assetsBundleFileName) {
        if (mCurrentInstance == null) {
            throw new CodePushNotInitializedException("A CodePush instance has not been created yet. Have you added it to your app's list of ReactPackages?");
        }

        return mCurrentInstance.getJSBundleFileInternal(assetsBundleFileName);
    }

    public String getJSBundleFileInternal(String assetsBundleFileName) {
        this.mAssetsBundleFileName = assetsBundleFileName;
        String binaryJsBundleUrl = CodePushConstants.ASSETS_BUNDLE_PREFIX + assetsBundleFileName;

        try {
            String packageFilePath = null;
            try {
                packageFilePath = mUpdateManager.getCurrentPackageBundlePath(this.mAssetsBundleFileName);
            } catch (CodePushMalformedDataException e) {
                // We need to recover the app in case 'codepush.json' is corrupted
                CodePushUtils.log(e.getMessage());
                clearUpdates();
            }

            if (packageFilePath == null) {
                // There has not been any downloaded updates.
                CodePushUtils.logBundleUrl(binaryJsBundleUrl);
                sIsRunningBinaryVersion = true;
                return binaryJsBundleUrl;
            }

            JSONObject packageMetadata = this.mUpdateManager.getCurrentPackage();
            if (isPackageBundleLatest(packageMetadata)) {
                CodePushUtils.logBundleUrl(packageFilePath);
                sIsRunningBinaryVersion = false;
                return packageFilePath;
            } else {
                // The binary version is newer.
                this.mDidUpdate = false;
                if (!this.mIsDebugMode || hasBinaryVersionChanged(packageMetadata)) {
                    this.clearUpdates();
                }

                CodePushUtils.logBundleUrl(binaryJsBundleUrl);
                sIsRunningBinaryVersion = true;
                return binaryJsBundleUrl;
            }
        } catch (Exception e) {
            //do nothing for now
        }
        return binaryJsBundleUrl;
    }

    public String getServerUrl() {
        return mServerUrl;
    }

    void invalidateCurrentInstance() {
        mCurrentInstance = null;
    }

    boolean isDebugMode() {
        return mIsDebugMode;
    }

    boolean isRunningBinaryVersion() {
        return sIsRunningBinaryVersion;
    }

    private boolean isPackageBundleLatest(JSONObject packageMetadata) {
        try {
            Long binaryModifiedDateDuringPackageInstall = null;
            String binaryModifiedDateDuringPackageInstallString = packageMetadata.optString(CodePushConstants.BINARY_MODIFIED_TIME_KEY, null);
            if (binaryModifiedDateDuringPackageInstallString != null) {
                binaryModifiedDateDuringPackageInstall = Long.parseLong(binaryModifiedDateDuringPackageInstallString);
            }
            String packageAppVersion = packageMetadata.optString("appVersion", null);
            long binaryResourcesModifiedTime = this.getBinaryResourcesModifiedTime();
            return binaryModifiedDateDuringPackageInstall != null &&
                    binaryModifiedDateDuringPackageInstall == binaryResourcesModifiedTime &&
                    (sAppVersion.equals(packageAppVersion));
        } catch (NumberFormatException e) {
            throw new CodePushUnknownException("Error in reading binary modified date from package metadata", e);
        }
    }

    private boolean hasBinaryVersionChanged(JSONObject packageMetadata) {
        String packageAppVersion = packageMetadata.optString("appVersion", null);
        return !sAppVersion.equals(packageAppVersion);
    }


    public static void overrideAppVersion(String appVersionOverride) {
        sAppVersion = appVersionOverride;
    }

    public void clearUpdates() {
        mUpdateManager.clearUpdates();
    }

    public static void setReactInstanceHolder(ReactInstanceHolder reactInstanceHolder) {
        mReactInstanceHolder = reactInstanceHolder;
    }

    static ReactInstanceManager getReactInstanceManager() {
        if (mReactInstanceHolder == null) {
            return null;
        }
        return mReactInstanceHolder.getReactInstanceManager();
    }

    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactApplicationContext) {
        CodePushNativeModule codePushModule = new CodePushNativeModule(reactApplicationContext, this, mUpdateManager);
        List<NativeModule> nativeModules = new ArrayList<>();
        nativeModules.add(codePushModule);
        return nativeModules;
    }

    // Deprecated in RN v0.47.
    public List<Class<? extends JavaScriptModule>> createJSModules() {
        return new ArrayList<>();
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactApplicationContext) {
        return new ArrayList<>();
    }
}

class CodePushBuilder {

    private String mDeploymentKey;
    private Context mContext;
    private boolean mIsDebugMode;
    private String mServerUrl;

    public CodePushBuilder(String deploymentKey, Context context) {
        this.mDeploymentKey = deploymentKey;
        this.mContext = context;
        this.mServerUrl = CodePush.getServiceUrl();
    }

    public CodePushBuilder setIsDebugMode(boolean isDebugMode) {
        this.mIsDebugMode = isDebugMode;
        return this;
    }

    public CodePush build() {
        return new CodePush(this.mDeploymentKey, this.mContext, this.mIsDebugMode);
    }
}
