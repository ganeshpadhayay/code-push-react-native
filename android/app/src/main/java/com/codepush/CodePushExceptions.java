package com.codepush;

import java.net.MalformedURLException;

class CodePushInvalidUpdateException extends RuntimeException {
    public CodePushInvalidUpdateException(String message) {
        super(message);
    }
}

class CodePushMalformedDataException extends RuntimeException {
    public CodePushMalformedDataException(String path, Throwable cause) {
        super("Unable to parse contents of " + path + ", the file may be corrupted.", cause);
    }

    public CodePushMalformedDataException(String url, MalformedURLException cause) {
        super("The package has an invalid downloadUrl: " + url, cause);
    }
}

class CodePushUnknownException extends RuntimeException {

    public CodePushUnknownException(String message, Throwable cause) {
        super(message, cause);
    }

    public CodePushUnknownException(String message) {
        super(message);
    }
}

final class CodePushNotInitializedException extends RuntimeException {

    public CodePushNotInitializedException(String message, Throwable cause) {
        super(message, cause);
    }

    public CodePushNotInitializedException(String message) {
        super(message);
    }
}
