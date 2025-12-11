package kz.finance.security.exception;

public class AppleAuthException extends RuntimeException {
    public AppleAuthException(String message) {
        super(message);
    }

    public AppleAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}


