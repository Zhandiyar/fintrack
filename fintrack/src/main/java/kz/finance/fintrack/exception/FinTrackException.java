package kz.finance.fintrack.exception;

public class FinTrackException extends RuntimeException {

    private final int status;
    private final String error;

    public FinTrackException(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public FinTrackException(int status, String message) {
        super(message);
        this.status = status;
        this.error = null;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }
}

