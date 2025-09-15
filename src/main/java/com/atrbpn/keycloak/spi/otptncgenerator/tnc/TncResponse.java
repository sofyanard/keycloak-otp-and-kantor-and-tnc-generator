package com.atrbpn.keycloak.spi.otptncgenerator.tnc;

public class TncResponse {

    private boolean success;
    private String message;
    private String content;
    private String url;

    public TncResponse(boolean success, String message, String content, String url) {
        this.success = success;
        this.message = message;
        this.content = content;
        this.url = url;
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

}
