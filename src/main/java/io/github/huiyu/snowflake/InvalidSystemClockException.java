package io.github.huiyu.snowflake;

public class InvalidSystemClockException extends RuntimeException {

    public InvalidSystemClockException() {
        super();
    }

    public InvalidSystemClockException(String message) {
        super(message);
    }

    public InvalidSystemClockException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidSystemClockException(Throwable cause) {
        super(cause);
    }

    protected InvalidSystemClockException(String message,
                                          Throwable cause,
                                          boolean enableSuppression,
                                          boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
