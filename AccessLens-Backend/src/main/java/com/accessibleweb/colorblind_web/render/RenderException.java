package com.accessibleweb.colorblind_web.render;
/** Thrown when a page cannot be rendered or sampled. Mapped to HTTP 502 by the controller. */
public class RenderException extends RuntimeException {

    public RenderException(String message) {
        super(message);
    }

    public RenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
