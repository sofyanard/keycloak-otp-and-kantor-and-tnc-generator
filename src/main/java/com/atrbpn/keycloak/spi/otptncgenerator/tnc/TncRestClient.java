package com.atrbpn.keycloak.spi.otptncgenerator.tnc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class TncRestClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static TncResponse postTncRequest(String endpointUrl, TncRequest request) throws IOException {
        
        // Do REST POST request
        URL url = new URL(endpointUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // Serialize TncRequest to JSON and send
        String jsonRequest = objectMapper.writeValueAsString(request);
        conn.getOutputStream().write(jsonRequest.getBytes("UTF-8"));

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("REST request failed with status: " + status);
        }

        StringBuilder jsonBuilder = new StringBuilder();
        try (Scanner scanner = new Scanner(conn.getInputStream())) {
            while (scanner.hasNextLine()) {
                jsonBuilder.append(scanner.nextLine());
            }
        }
        conn.disconnect();
        String jsonString = jsonBuilder.toString();

        // Read field "message" to determine type
        String message = objectMapper.readTree(jsonString).get("message").asText();

        // Return one of the class objects
        Object responseObj;
        if (message != null && message.toLowerCase().contains("pengguna sudah menggunakan t&c terbaru")) {
            responseObj = objectMapper.readValue(jsonString, TncUpdatedResponse.class);
        } else if (message != null && message.toLowerCase().contains("perbaharui t&c terbaru")) {
            responseObj = objectMapper.readValue(jsonString, TncUnupdatedResponse.class);
        } else {
            throw new IOException("Unknown TNC response type: " + message);
        }
        return toCommonTncData(responseObj);
    }

    public static TncResponse toCommonTncData(Object tncResponse) {
        if (tncResponse instanceof TncUnupdatedResponse) {
            TncUnupdatedResponse r = (TncUnupdatedResponse) tncResponse;
            return new TncResponse(
                r.isSuccess(),
                r.getMessage(),
                r.getData() != null ? r.getData().getKonten() : null,
                r.getData() != null ? r.getData().getUrl() : null
            );
        } else if (tncResponse instanceof TncUpdatedResponse) {
            TncUpdatedResponse r = (TncUpdatedResponse) tncResponse;
            // Assume only one item in data list
            TncUpdatedResponse.TncUpdatedResponseData d = (r.getData() != null && !r.getData().isEmpty()) ? r.getData().get(0) : null;
            return new TncResponse(
                r.isSuccess(),
                r.getMessage(),
                d != null ? d.getKontenText() : null,
                d != null ? d.getUrl() : null
            );
        }
        throw new IllegalArgumentException("Unknown TNC response type: " + tncResponse.getClass());
    }

}
