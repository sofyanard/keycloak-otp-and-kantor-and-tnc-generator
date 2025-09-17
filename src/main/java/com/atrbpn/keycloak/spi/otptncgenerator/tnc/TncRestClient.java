package com.atrbpn.keycloak.spi.otptncgenerator.tnc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import javax.naming.InitialContext;
import javax.naming.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TncRestClient {

    private static final Logger log = LoggerFactory.getLogger(TncRestClient.class);
    
    public static String tncApiBaseUrl;
    
    static {
        try {
            Context initCxt =  new InitialContext();

            tncApiBaseUrl = (String) initCxt.lookup("java:/tncApiBaseUrl");
            log.info("tncApiBaseUrl: {}", tncApiBaseUrl);

        } catch (Exception ex) {
            tncApiBaseUrl = null;
            log.error("unable to get jndi connection for SMTP or Environment");
            log.error(ex.getMessage(), ex);
        }
    }
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static TncResponse verifyUser(TncRequest request) throws IOException {
        
        // Do REST POST request
        URL url = new URL(tncApiBaseUrl);
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
        return toCommonResponse(responseObj);
    }

    public static TncResponse toCommonResponse(Object responseObj) {
        if (responseObj instanceof TncUnupdatedResponse) {
            TncUnupdatedResponse r = (TncUnupdatedResponse) responseObj;
            return new TncResponse(
                r.isSuccess(),
                r.getMessage(),
                r.getData() != null ? r.getData().getKonten() : null,
                r.getData() != null ? r.getData().getUrl() : null
            );
        } else if (responseObj instanceof TncUpdatedResponse) {
            TncUpdatedResponse r = (TncUpdatedResponse) responseObj;
            // Assume only one item in data list
            TncUpdatedResponse.TncUpdatedResponseData d = (r.getData() != null && !r.getData().isEmpty()) ? r.getData().get(0) : null;
            return new TncResponse(
                r.isSuccess(),
                r.getMessage(),
                d != null ? d.getKontenText() : null,
                d != null ? d.getUrl() : null
            );
        }
        throw new IllegalArgumentException("Unknown TNC response type: " + responseObj.getClass());
    }

}
