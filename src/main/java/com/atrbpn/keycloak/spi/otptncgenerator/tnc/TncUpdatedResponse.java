package com.atrbpn.keycloak.spi.otptncgenerator.tnc;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TncUpdatedResponse {
    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private List<TncUpdatedResponseData> listTncUpdatedResponseData;

    // Getters and Setters

    public static class TncUpdatedResponseData {

        @JsonProperty("USERID")
        private String userId;

        @JsonProperty("USERNAME")
        private String username;

        @JsonProperty("REALM")
        private String realm;

        @JsonProperty("TNC")
        private String tnc;

        @JsonProperty("VERSI_TNC_USER")
        private String versiTncUser;

        @JsonProperty("TNC_TERBARU_ID")
        private String tncTerbaruId;

        @JsonProperty("VERSI_TNC_TERBARU")
        private String versiTncTerbaru;

        @JsonProperty("KONTEN_TEXT")
        private String kontenText;

        @JsonProperty("URL")
        private String url;

        @JsonProperty("STATUS_TNC")
        private int statusTnc;

        // Getters and Setters
        public String getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getRealm() { return realm; }
        public String getTnc() { return tnc; }
        public String getVersiTncUser() { return versiTncUser; }
        public String getTncTerbaruId() { return tncTerbaruId; }
        public String getVersiTncTerbaru() { return versiTncTerbaru; }
        public String getKontenText() { return kontenText; }
        public String getUrl() { return url; }
        public int getStatusTnc() { return statusTnc; }
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public List<TncUpdatedResponseData> getData() { return listTncUpdatedResponseData; }
}
