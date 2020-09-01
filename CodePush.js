import requestFetchAdapter from './request-fetch-adapter';
import {Platform} from 'react-native';
import log from './logging';
import hoistStatics from 'hoist-non-react-statics';

let NativeCodePush = require('react-native').NativeModules.CodePush;
const PackageMixins = require('./package-mixins')(NativeCodePush);

async function checkForUpdate() {
  /*
   * Before we ask the server if an update exists, we
   * need to retrieve three pieces of information from the
   * native side: deployment key, app version (e.g. 1.0.1)
   * and the hash of the currently running update (if there is one).
   * This allows the client to only receive updates which are targetted
   * for their specific deployment and version and which are actually
   * different from the CodePush update they have already installed.
   */
  const nativeConfig = await getConfiguration();
  console.log(`config: ${nativeConfig}`);
  console.log(nativeConfig);

  //make the network call to check if we have new updates
  const sdk = getPromisifiedSdk(requestFetchAdapter, nativeConfig);
  console.log(`sdk:`);
  console.log(sdk);

  // Use dynamically overridden getCurrentPackage() during tests.
  const localPackage = await module.exports.getCurrentPackage();
  console.log(`local package: ${localPackage}`);
  console.log(localPackage);

  /*
   * If the app has a previously installed update, and that update
   * was targetted at the same app version that is currently running,
   * then we want to use its package hash to determine whether a new
   * release has been made on the server. Otherwise, we only need
   * to send the app version to the server, since we are interested
   * in any updates for current binary version, regardless of hash.
   */
  let queryPackage;
  if (localPackage) {
    queryPackage = localPackage;
  } else {
    queryPackage = {appVersion: config.appVersion};
    if (Platform.OS === 'ios' && config.packageHash) {
      queryPackage.packageHash = config.packageHash;
    }
  }

  console.log(`query package: ${queryPackage}`);
  console.log(queryPackage);

  const update = await sdk.queryUpdateWithCurrentPackage(queryPackage);
  console.log(`update: ${update}`);
  console.log(update);

  /*
   * There are four cases where checkForUpdate will resolve to null:
   * ----------------------------------------------------------------
   * 1) The server said there isn't an update. This is the most common case.
   * 2) The server said there is an update but it requires a newer binary version.
   *    This would occur when end-users are running an older binary version than
   *    is available, and CodePush is making sure they don't get an update that
   *    potentially wouldn't be compatible with what they are running.
   * 3) The server said there is an update, but the update's hash is the same as
   *    the currently running update. This should _never_ happen, unless there is a
   *    bug in the server, but we're adding this check just to double-check that the
   *    client app is resilient to a potential issue with the update check.
   * 4) The server said there is an update, but the update's hash is the same as that
   *    of the binary's currently running version. This should only happen in Android -
   *    unlike iOS, we don't attach the binary's hash to the updateCheck request
   *    because we want to avoid having to install diff updates against the binary's
   *    version, which we can't do yet on Android.
   */
  if (
    !update ||
    update.updateAppVersion ||
    (localPackage && update.packageHash === localPackage.packageHash) ||
    ((!localPackage || localPackage._isDebugOnly) &&
      config.packageHash === update.packageHash)
  ) {
    if (update && update.updateAppVersion) {
      log(
        'An update is available but it is not targeting the binary version of your app.',
      );
      if (
        handleBinaryVersionMismatchCallback &&
        typeof handleBinaryVersionMismatchCallback === 'function'
      ) {
        handleBinaryVersionMismatchCallback(update);
      }
    }

    return null;
  } else {
    const remotePackage = {
      ...update,
      ...PackageMixins.remote(sdk.reportStatusDownload),
    };
    remotePackage.failedInstall = await NativeCodePush.isFailedUpdate(
      remotePackage.packageHash,
    );
    remotePackage.deploymentKey = deploymentKey || nativeConfig.deploymentKey;
    return remotePackage;
  }
}

const getConfiguration = (() => {
  let config;
  return async function getConfiguration() {
    if (config) {
      return config;
    } else {
      config = await NativeCodePush.getConfiguration();
      return config;
    }
  };
})();

async function getCurrentPackage() {
  return await getUpdateMetadata();
}

async function getUpdateMetadata() {
  let updateMetadata = await NativeCodePush.getUpdateMetadata(
    updateState || CodePush.UpdateState.RUNNING,
  );
  if (updateMetadata) {
    updateMetadata = {...PackageMixins.local, ...updateMetadata};
    updateMetadata.failedInstall = await NativeCodePush.isFailedUpdate(
      updateMetadata.packageHash,
    );
    updateMetadata.isFirstRun = await NativeCodePush.isFirstRun(
      updateMetadata.packageHash,
    );
  }
  return updateMetadata;
}

//need to write this
function getPromisifiedSdk(requestFetchAdapter, config) {
  return {name: 'Ganesh'};
}

let CodePush;

function codePushify(options = {}) {
  let React;
  let ReactNative = require('react-native');

  try {
    React = require('react');
  } catch (e) {}
  if (!React) {
    try {
      React = ReactNative.React;
    } catch (e) {}
    if (!React) {
      throw new Error("Unable to find the 'React' module.");
    }
  }

  if (!React.Component) {
    throw new Error(
      `Unable to find the "Component" class, please either:
1. Upgrade to a newer version of React Native that supports it, or
2. Call the codePush.sync API in your component instead of using the @codePush decorator`,
    );
  }

  var decorator = (RootComponent) => {
    const extended = class CodePushComponent extends React.Component {
      componentDidMount() {
        let rootComponentInstance = this.refs.rootComponent;

        let syncStatusCallback;
        if (
          rootComponentInstance &&
          rootComponentInstance.codePushStatusDidChange
        ) {
          syncStatusCallback = rootComponentInstance.codePushStatusDidChange;
          if (rootComponentInstance instanceof React.Component) {
            syncStatusCallback = syncStatusCallback.bind(rootComponentInstance);
          }
        }

        let downloadProgressCallback;
        if (
          rootComponentInstance &&
          rootComponentInstance.codePushDownloadDidProgress
        ) {
          downloadProgressCallback =
            rootComponentInstance.codePushDownloadDidProgress;
          if (rootComponentInstance instanceof React.Component) {
            downloadProgressCallback = downloadProgressCallback.bind(
              rootComponentInstance,
            );
          }
        }

        let handleBinaryVersionMismatchCallback;
        if (
          rootComponentInstance &&
          rootComponentInstance.codePushOnBinaryVersionMismatch
        ) {
          handleBinaryVersionMismatchCallback =
            rootComponentInstance.codePushOnBinaryVersionMismatch;
          if (rootComponentInstance instanceof React.Component) {
            handleBinaryVersionMismatchCallback = handleBinaryVersionMismatchCallback.bind(
              rootComponentInstance,
            );
          }
        }
      }

      render() {
        const props = {...this.props};

        // we can set ref property on class components only (not stateless)
        // check it by render method
        if (RootComponent.prototype.render) {
          props.ref = 'rootComponent';
        }

        return <RootComponent {...props} />;
      }
    };

    return hoistStatics(extended, RootComponent);
  };

  if (typeof options === 'function') {
    // Infer that the root component was directly passed to us.
    return decorator(options);
  } else {
    return decorator;
  }
}

// If the "NativeCodePush" variable isn't defined, then
// the app didn't properly install the native module,
// and therefore, it doesn't make sense initializing
// the JS interface when it wouldn't work anyways.
if (NativeCodePush) {
  CodePush = codePushify;
  Object.assign(CodePush, {
    checkForUpdate,
    getConfiguration,
    getCurrentPackage,
    getUpdateMetadata,
    log,
  });
} else {
  log(
    "The CodePush module doesn't appear to be properly installed. Please double-check that everything is setup correctly.",
  );
}

module.exports = CodePush;
