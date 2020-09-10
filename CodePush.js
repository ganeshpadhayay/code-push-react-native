import log from './logging';
import hoistStatics from 'hoist-non-react-statics';

let NativeCodePush = require('react-native').NativeModules.CodePush;

async function checkForUpdate() {
  //get native configuration data
  let nativeConfig = await getConfiguration();
  console.log('nativeConfig');
  console.log(nativeConfig);

  //get the local package info
  let localBundleData = await getUpdateMetadata();
  console.log('localBundleData');
  console.log(localBundleData);

  //make the network call to check if we have new updates
  if (localBundleData) {
    nativeConfig.bundleVersion = localBundleData.bundleVersion;
  }

  let remoteBundleData = await getRemoteBundleData(nativeConfig);
  console.log('remoteBundleData');
  console.log(remoteBundleData);

  remotePackage = null;

  if (remoteBundleData && remoteBundleData.success) {
    if (localBundleData) {
      if (localBundleData.label != remoteBundleData.label) {
        console.log('bundle version mismatch');
        remotePackage = await downloadAndInstallTheRemoteBundle(
          remoteBundleData,
        );
      } else {
        //do nothing
        console.log('bundle version match');
      }
    } else {
      //download the latest bundle
      console.log('no local bundle present');
      remotePackage = await downloadAndInstallTheRemoteBundle(remoteBundleData);
    }
  } else {
    console.log('no remote bundle present for this appVersion');
  }

  console.log('remote package');
  console.log(remotePackage);
  return remotePackage;
}

async function downloadAndInstallTheRemoteBundle(remoteBundleData) {
  console.log('in downloadAndInstallTheRemoteBundle' + remoteBundleData);
  //download the latest bundle
  let downloadedBundleData = await NativeCodePush.downloadUpdate(
    remoteBundleData,
  );
  console.log('downloadedBundleData');
  console.log(downloadedBundleData);
  //install it
  await NativeCodePush.installUpdate(downloadedBundleData);
  return downloadedBundleData;
}

async function getConfiguration() {
  return await NativeCodePush.getConfiguration();
}

//call native function to get the local package data if available
async function getUpdateMetadata() {
  let updateMetadata = await NativeCodePush.getUpdateMetadata();
  return updateMetadata;
}

//need to write this, make a network call here to check if we have a new bundle on server
async function getRemoteBundleData(nativeConfig) {
  console.log(nativeConfig);
  return {
    updateDownloadUrl:
      'https://codepushupdates.azureedge.net/storagev2/R8IL6ZJRtTDmbpk6niw-m9_xAW9V8280fa3d-d7c9-453c-a2a8-08f96a8cbd32',
    message: 'message',
    success: true,
    appVersion: '4.34',
    bundleVersion: 'v5',
    forceUpdate: 1,
    packageSize: 488754,
  };
}

let CodePush;

async function sync() {
  return await checkForUpdate();
}

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
        //call to sync
        sync();
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
    getUpdateMetadata,
    sync,
    log,
  });
} else {
  log(
    "The CodePush module doesn't appear to be properly installed. Please double-check that everything is setup correctly.",
  );
}

module.exports = CodePush;
