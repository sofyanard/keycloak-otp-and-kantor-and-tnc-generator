package com.atrbpn.keycloak.spi.otptncgenerator.tnc;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TncUnupdatedResponse {
    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private TncUnupdatedResponseData tncUnupdatedResponseData;

    // Getters and Setters

    public static class TncUnupdatedResponseData {
        
        @JsonProperty("url")
        private String url;

        @JsonProperty("konten")
        private String konten;

        // Getters and Setters
        public String getUrl() { return url; }
        public String getKonten() { return konten; }
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public TncUnupdatedResponseData getData() { return tncUnupdatedResponseData; }
}
