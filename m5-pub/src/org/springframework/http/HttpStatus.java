package org.springframework.http;

/** Minimal status codes used by the standalone controller exercise. */
public enum HttpStatus {
    OK(200),
    CREATED(201),
    NO_CONTENT(204),
    BAD_REQUEST(400),
    NOT_FOUND(404);

    private final int value;

    HttpStatus(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
