export type DownloadProgressCallback = (progress: DownloadProgress) => void;

export interface DownloadProgress {
  /**
   * The total number of bytes expected to be received for this update.
   */
  totalBytes: number;

  /**
   * The number of bytes downloaded thus far.
   */
  receivedBytes: number;
}

export interface Package {
  /**
   * The app binary version that this update is dependent on. This is the value that was
   * specified via the appStoreVersion parameter when calling the CLI's release command.
   */
  appVersion: string;

  /**
   * The deployment key that was used to originally download this update.
   */
  deploymentKey: string;

  /**
   * The description of the update. This is the same value that you specified in the CLI when you released the update.
   */
  description: string;

  /**
   * Indicates whether the update is considered mandatory. This is the value that was specified in the CLI when the update was released.
   */
  isMandatory: boolean;

  /**
   * The SHA hash value of the update.
   */
  packageHash: string;

  /**
   * The size of the code contained within the update, in bytes.
   */
  packageSize: number;
}

export interface LocalPackage extends Package {}

export interface RemotePackage extends Package {
  /**
   * The URL at which the package is available for download.
   */
  downloadUrl: string;
}

/**
 * Decorates a React Component configuring it to sync for updates with the CodePush server.
 *
 * @param x the React Component that will decorated
 */
declare function CodePush(x: any): any;

declare namespace CodePush {
  /**
   * Asks the CodePush service whether the configured app deployment has an update available.
   */
  function checkForUpdate(): Promise<RemotePackage | null>;

  /**
   * Retrieves the metadata for an installed update (e.g. description, mandatory).
   */
  function getUpdateMetadata(): Promise<LocalPackage | null>;

  /**
   * Allows checking for an update, downloading it and installing it, all with a single call.
   */
  function sync(): Promise<RemotePackage | null>;
}

export default CodePush;
