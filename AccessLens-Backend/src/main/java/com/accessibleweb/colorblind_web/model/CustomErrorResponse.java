package com.accessibleweb.colorblind_web.model;


import java.util.Date;

// Updated ErrorResponse class with getters and setters
public class CustomErrorResponse {
    private String message;
    private String timestamp;

    public CustomErrorResponse(String message) {
        this.message = message;
        this.timestamp = new Date().toString();
    }

    // Add getters and setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
