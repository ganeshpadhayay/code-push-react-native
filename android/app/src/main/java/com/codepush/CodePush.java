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

    private static String sAppVersion = null;

    private String mAssetsBundleFileName;

    // Helper classes.
    private CodePushUpdateManager mUpdateManager;

    private Context mContext;
    private final boolean mIsDebugMode;

    private static ReactInstanceHolder mReactInstanceHolder;
    private static CodePush mCurrentInstance;

    public CodePush(Context context, boolean isDebugMode) {
        mContext = context.getApplicationContext();

        mUpdateManager = new CodePushUpdateManager(context.getFilesDir().getAbsolutePath());
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

        clearDebugCacheIfNeeded(null);
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

    public String getAppVersion() {
        return sAppVersion;
    }

    public String getAssetsBundleFileName() {
        return mAssetsBundleFileName;
    }

    @Deprecated
    public static String getBundleUrl() {
        return getJSBundleFile();
    }

    @Deprecated
    public static String getBundleUrl(String assetsBundleFileName) {
        return getJSBundleFile(assetsBundleFileName);
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
                return binaryJsBundleUrl;
            }

            //get the appVersion of the current js bundle and if it is less than the current appVersion then do not use that
            JSONObject currentPackage = mUpdateManager.getCurrentPackage();
            String currentAppVersionInLocalBundle = currentPackage.optString("appVersion", null);
            if (currentAppVersionInLocalBundle == null || checkForUpdate(currentAppVersionInLocalBundle, sAppVersion))
                return binaryJsBundleUrl;

            return packageFilePath;
        } catch (Exception e) {
            //do nothing for now
        }
        return binaryJsBundleUrl;
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

    public void clearUpdates() {
        mUpdateManager.clearUpdates();
    }

    void invalidateCurrentInstance() {
        mCurrentInstance = null;
    }

    public static void setReactInstanceHolder(ReactInstanceHolder reactInstanceHolder) {
        mReactInstanceHolder = reactInstanceHolder;
    }

    public boolean checkForUpdate(String existingVersion, String newVersion) {
        if (existingVersion.isEmpty() || newVersion.isEmpty()) {
            return false;
        }

        existingVersion = existingVersion.replaceAll("\\.", "");
        newVersion = newVersion.replaceAll("\\.", "");

        int existingVersionLength = existingVersion.length();
        int newVersionLength = newVersion.length();

        StringBuilder versionBuilder = new StringBuilder();
        if (newVersionLength > existingVersionLength) {
            versionBuilder.append(existingVersion);
            for (int i = existingVersionLength; i < newVersionLength; i++) {
                versionBuilder.append("0");
            }
            existingVersion = versionBuilder.toString();
        } else if (existingVersionLength > newVersionLength) {
            versionBuilder.append(newVersion);
            for (int i = newVersionLength; i < existingVersionLength; i++) {
                versionBuilder.append("0");
            }
            newVersion = versionBuilder.toString();
        }

        return Integer.parseInt(newVersion) > Integer.parseInt(existingVersion);
    }
}
