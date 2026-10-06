package org.springframework.http;

/** Minimal response wrapper used because Spring is not a build dependency. */
public final class ResponseEntity<T> {
    private final HttpStatus statusCode;
    private final T body;

    private ResponseEntity(HttpStatus statusCode, T body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public static <T> ResponseEntity<T> ok(T body) {
        return new ResponseEntity<>(HttpStatus.OK, body);
    }

    public static <T> ResponseEntity<T> of(HttpStatus statusCode, T body) {
        return new ResponseEntity<>(statusCode, body);
    }

    public static ResponseEntity<Void> noContent() {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT, null);
    }

    public static <T> ResponseEntity<T> badRequest(T body) {
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST, body);
    }

    public static <T> ResponseEntity<T> notFound(T body) {
        return new ResponseEntity<>(HttpStatus.NOT_FOUND, body);
    }

    public HttpStatus getStatusCode() {
        return statusCode;
    }

    public T getBody() {
        return body;
    }
}
