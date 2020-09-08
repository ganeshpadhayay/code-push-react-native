package com.codepush;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class CodePushUpdateUtils {

    public static void copyNecessaryFilesFromCurrentPackage(String diffManifestFilePath, String currentPackageFolderPath, String newPackageFolderPath) throws IOException {
        if (currentPackageFolderPath == null)
            return;
        FileUtils.copyDirectoryContents(currentPackageFolderPath, newPackageFolderPath);
        JSONObject diffManifest = CodePushUtils.getJsonObjectFromFile(diffManifestFilePath);
        try {
            JSONArray deletedFiles = diffManifest.getJSONArray("deletedFiles");
            for (int i = 0; i < deletedFiles.length(); i++) {
                String fileNameToDelete = deletedFiles.getString(i);
                File fileToDelete = new File(newPackageFolderPath, fileNameToDelete);
                if (fileToDelete.exists()) {
                    fileToDelete.delete();
                }
            }
        } catch (JSONException e) {
            throw new CodePushUnknownException("Unable to copy files from current package during diff update", e);
        }
    }

    public static String findJSBundleInUpdateContents(String folderPath, String expectedFileName) {
        File folder = new File(folderPath);
        File[] folderFiles = folder.listFiles();
        for (File file : folderFiles) {
            String fullFilePath = CodePushUtils.appendPathComponent(folderPath, file.getName());
            if (file.isDirectory()) {
                String mainBundlePathInSubFolder = findJSBundleInUpdateContents(fullFilePath, expectedFileName);
                if (mainBundlePathInSubFolder != null) {
                    return CodePushUtils.appendPathComponent(file.getName(), mainBundlePathInSubFolder);
                }
            } else {
                String fileName = file.getName();
                if (fileName.equals(expectedFileName)) {
                    return fileName;
                }
            }
        }

        return null;
    }
}