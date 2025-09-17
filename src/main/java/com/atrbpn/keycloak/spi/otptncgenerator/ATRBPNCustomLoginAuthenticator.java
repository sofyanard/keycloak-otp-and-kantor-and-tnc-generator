package com.atrbpn.keycloak.spi.otptncgenerator;

import com.atrbpn.keycloak.spi.otptncgenerator.helper.DBHelper;
import com.atrbpn.keycloak.spi.otptncgenerator.helper.PostgresDBHelper;
import com.atrbpn.keycloak.spi.otptncgenerator.tnc.TncRequest;
import com.atrbpn.keycloak.spi.otptncgenerator.tnc.TncResponse;
import com.atrbpn.keycloak.spi.otptncgenerator.tnc.TncRestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.events.Errors;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordUserCredentialModel;
import org.keycloak.services.managers.BruteForceProtector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

/**
 * <pre>
 *     com.atrbpn.keycloak.spi.otpgenerator.ATRBPNCustomLoginAuthenticator
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 04 Apr 2022 12:38
 */
public class ATRBPNCustomLoginAuthenticator  implements Authenticator {

    private static final Logger log = LoggerFactory.getLogger(ATRBPNCustomLoginAuthenticator.class);

    private static final String Q_GET_KANTOR_BY_USERNAME = 
            "SELECT DISTINCT KANTORID, NAMAKANTOR, KODEKANTOR \n" +
            "FROM VIEW_USER_INTERNAL_KANTOR \n" +
            "WHERE USERNAME = ? \n" +
            "ORDER BY KODEKANTOR";
    
    private static final String Q_GET_EMAIL_NON_KEDINASAN = 
            "SELECT DISTINCT EMAILNONKEDINASAN \n" +
            "FROM VIEW_USER_INTERNAL_EMAILNONKEDINASAN \n" +
            "WHERE USERNAME = ? ";

    private static final String Q_INSERT_OTP = "insert into otp (id, user_id, created_date, otp) \n" +
            "values (?, ?, current_timestamp, ?)";
    
    private static String smtpHost;
    private static String smtpFrom;

    private static String environment;
    private static String otpMechanism;

    static {
        try {
            Context initCxt =  new InitialContext();

            smtpHost = (String) initCxt.lookup("java:/smtpHost");
            smtpFrom = (String) initCxt.lookup("java:/smtpFrom");
            environment = (String) initCxt.lookup("java:/environment");
            otpMechanism = (String) initCxt.lookup("java:/otpMechanism");

        } catch (Exception ex) {
            log.error("unable to get jndi connection for SMTP or Environment");
            log.error(ex.getMessage(), ex);
        }
    }

    public void authenticate(AuthenticationFlowContext authenticationFlowContext) {

        // reset previously failed flow
        authenticationFlowContext.resetFlow();

        // not bringing username
        if(authenticationFlowContext.getHttpRequest().getFormParameters().get("username") == null
                || authenticationFlowContext.getHttpRequest().getFormParameters().get("username").isEmpty()) {

            Response challenge =  Response.status(400)
                    .entity("{\"error\":\"invalid_request\",\"error_description\":\"Username atau Password tidak Ditemukan\"}")
                    .header("Content-Type", "application/json")
                    .build();
            authenticationFlowContext.getEvent().error(Errors.USER_NOT_FOUND);
            authenticationFlowContext.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
            return;
        }

        // not bringing password
        if(authenticationFlowContext.getHttpRequest().getFormParameters().get("password") == null
                || authenticationFlowContext.getHttpRequest().getFormParameters().get("password").isEmpty()) {

            Response challenge =  Response.status(400)
                    .entity("{\"error\":\"invalid_request\",\"error_description\":\"Username atau Password tidak Ditemukan\"}")
                    .header("Content-Type", "application/json")
                    .build();
            authenticationFlowContext.getEvent().error(Errors.USER_NOT_FOUND);
            authenticationFlowContext.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
            return;
        }

        // capture username
        String username = authenticationFlowContext.getHttpRequest().getFormParameters().getFirst("username").trim();
        try {
            username = URLDecoder.decode(username, "UTF-8");
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }

        // search for corresponding user
        UserModel userModel = authenticationFlowContext.getSession()
                .userStorageManager().getUserByUsername(username, authenticationFlowContext.getRealm());

        // user not exists
        if(userModel == null) {
            log.info(" invalid userModel for username : {} ", username);

            Response challenge =  Response.status(400)
                    .entity("{\"error\":\"invalid_request\",\"error_description\":\"Username atau Password tidak Ditemukan\"}")
                    .header("Content-Type", "application/json")
                    .build();
            authenticationFlowContext.getEvent().error(Errors.USER_NOT_FOUND);
            authenticationFlowContext.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
            return;
        }

        BruteForceProtector bruteForceProtector = authenticationFlowContext.getSession().getProvider(BruteForceProtector.class);

        // check whether user is blocked
        boolean isTemporarilyDisabled = bruteForceProtector.isTemporarilyDisabled(authenticationFlowContext.getSession(), authenticationFlowContext.getRealm(), userModel);

        if (isTemporarilyDisabled) {
            log.info(" user is temporarily blocked : {} ", username);

            Response challenge = Response.status(400)
                    .entity("{\"error\":\"invalid_request\",\"error_description\":\"Akun anda telah diblokir sementara karena terlalu banyak percobaan login. Silahkan coba beberapa saat lagi.\"}")
                    .header("Content-Type", "application/json")
                    .build();
            authenticationFlowContext.getEvent().error(Errors.USER_TEMPORARILY_DISABLED);
            authenticationFlowContext.failureChallenge(AuthenticationFlowError.USER_TEMPORARILY_DISABLED, challenge);

            return;
        }

        String password = authenticationFlowContext.getHttpRequest().getFormParameters().getFirst("password").trim();
        try {
            password = URLDecoder.decode(password, "UTF-8");
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }

        // password is incorrect
        PasswordUserCredentialModel credentialInput = UserCredentialModel.password(password);
        boolean valid = authenticationFlowContext.getSession().userCredentialManager().isValid(authenticationFlowContext.getRealm(),
                userModel,
                new PasswordUserCredentialModel[]{credentialInput} );
        if( !valid ) {
            log.info(" invalid password for username : {} ", username);

            Response challenge =  Response.status(400)
                    .entity("{\"error\":\"invalid_request\",\"error_description\":\"Username atau Password Salah\"}")
                    .header("Content-Type", "application/json")
                    .build();
            authenticationFlowContext.getEvent().error(Errors.INVALID_USER_CREDENTIALS);
            authenticationFlowContext.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);

            // increase the bruteforce counter
            bruteForceProtector.failedLogin(authenticationFlowContext.getRealm(), userModel, authenticationFlowContext.getConnection());

            return;
        }

        String response = "";

        // give a random otp
        String randomOTP = String.format("%06d", new Random().nextInt(999999));

        // generate a default otp when env is development
        if("development".equalsIgnoreCase(environment)) {
            randomOTP = "111111";
        }

        // capture the ip from X-Forwarded-For header
        String ip = authenticationFlowContext.getHttpRequest().getHttpHeaders().getHeaderString("X-Forwarded-For");
        if(ip ==null)
            ip = authenticationFlowContext.getSession().getContext().getConnection().getRemoteAddr();
        String agent = authenticationFlowContext.getHttpRequest().getHttpHeaders().getHeaderString(HttpHeaders.USER_AGENT);

        // save otp into db
        saveOtp(userModel.getUsername(), randomOTP);

        try {

            Map responseMap = new HashMap();
            responseMap.put("kantor", getKantorByUsername(username));

            final String myRandomOTP = randomOTP;
            final String myip = ip;

            // sending notification
            if("email".equalsIgnoreCase(otpMechanism)) {
                // responseMap.put("to", masking(userModel.getEmail()));
                String emailNonKedinasan = getEmailNonKedinasan(userModel.getUsername());
                responseMap.put("to", masking(emailNonKedinasan));

                // send async email
                Thread thread = new Thread(){
                    public void run(){
                        try {
                            sendEmail(userModel, myRandomOTP, myip, agent, emailNonKedinasan);
                        } catch (Exception ex) {
                            log.error(ex.getMessage(), ex);
                        }
                    }
                };
                thread.start();
            } else if("sms".equalsIgnoreCase(otpMechanism)) {

                String nomerTelepon = userModel.getAttributes().get("telepon")!=null?userModel.getAttributes().get("telepon").get(0):null;
                responseMap.put("to", masking(nomerTelepon));

                // send thru sms
                Thread thread = new Thread(){
                    public void run(){
                        try {
                            sendSMS(userModel, myRandomOTP);
                        } catch (Exception ex) {
                            log.error(ex.getMessage(), ex);
                        }
                    }
                };
                thread.start();
            }

            // Get TnC from external API
            if (TncRestClient.tncApiBaseUrl != null && !TncRestClient.tncApiBaseUrl.trim().isEmpty()) {
                TncRequest tncRequest = new TncRequest(userModel.getAttributes().get("orcluserid").get(0), "internal");
                TncResponse tncResponse = TncRestClient.verifyUser(tncRequest);
                log.info("TNC API response: {}", new ObjectMapper().writeValueAsString(tncResponse));
                responseMap.put("tnc", tncResponse);
            }

            // write response
            response = new ObjectMapper().writeValueAsString(responseMap);

        }  catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }

        // success
        log.info(" success generating otp and kantor for username : {} ", username);
        Response challenge =  Response.status(200)
                .entity(response)
                .header("Content-Type", "application/json")
                .build();
        authenticationFlowContext.forceChallenge(challenge);
        return;
    }

    /**
     *  adding non email functionality - Alternatif pengiriman OTP selain email (BPN RI) misalnya sms/wa jika terjadi kerusakan pada email service sms yg bisa digunakan
     *
     * @param userModel
     * @param randomOTP
     * @throws Exception
     */
    private void sendSMS(UserModel userModel, String randomOTP) throws Exception {
        String nohp = userModel.getAttributes().get("telepon")!=null?userModel.getAttributes().get("telepon").get(0):"";
        log.info("begin sending sms to {}", nohp);

        String smsBody = getSmsBody().replace("OTP", randomOTP);
        String url = "http://kkptraining.atrbpn.go.id/smsbpnri/smscontentprovider.aspx?nomortelepon="+nohp+"&isipesan="+smsBody;

        HttpURLConnection httpClient =
                (HttpURLConnection) new URL(url).openConnection();
        httpClient.setRequestMethod("GET");

        int responseCode = httpClient.getResponseCode();

        log.info("successfully sending {} with response code {}", url, responseCode);
    }

    private void sendEmail(UserModel userModel, String randomOTP, String ip, String agent, String emailNonKedinasan) throws Exception {
        // log.info("begin sending email to {} - username {}", userModel.getEmail(), userModel.getUsername());
        log.info("begin sending email to {} - username {}", emailNonKedinasan, userModel.getUsername());

        Properties props = System.getProperties();

        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.trust", smtpHost);
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", "25");
        props.put("mail.smtp.auth", "false");

        Session session = Session.getInstance(props);
        MimeMessage message = new MimeMessage(session);

        message.setFrom(new InternetAddress(smtpFrom));
        // message.addRecipient(Message.RecipientType.TO, new InternetAddress(userModel.getEmail()));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(emailNonKedinasan));

        String emailBody = getEmailBody().toString().replace("KODE-OTP", randomOTP)
                .replace("USERNAME", userModel.getFirstName()+" "+userModel.getLastName())
                .replace("IP-ADDRESS", ip)
                .replace("USER-DEVICE", agent);
        message.setSubject("[ATR BPN] OTP Aplikasi");
        message.setContent(emailBody,
                "text/html; charset=utf-8");
        Transport transport = session.getTransport("smtp");
        transport.connect();
        transport.sendMessage(message, message.getAllRecipients());
        transport.close();

        // log.info("successfully sending email to {} - username {}", userModel.getEmail(), userModel.getUsername());
        log.info("successfully sending email to {} - username {}", emailNonKedinasan, userModel.getUsername());
    }

    public void action(AuthenticationFlowContext authenticationFlowContext) {
        authenticationFlowContext.success();
    }

    public boolean requiresUser() {
        return false;
    }

    public boolean configuredFor(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel) {
        return false;
    }

    public void setRequiredActions(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel) {

    }

    public void close() {

    }

    private List<Map> getKantorByUsername(String username) {
        ResultSet rs = null;
        PreparedStatement st = null;
        Connection c = null;

        log.info("get Kantor for user {}", username);

        try {
            c = DBHelper.getConnection();

            st = c.prepareStatement(Q_GET_KANTOR_BY_USERNAME);
            st.setString(1, username);
            st.execute();
            rs = st.getResultSet();

            List<Map> result = new ArrayList<>();
            while (rs.next()) {
                final String kantorId = rs.getString(1);
                final String kantorname = rs.getString(2);

                result.add(new HashMap() {{
                    put("kantorid", kantorId);
                    put("kantorname", kantorname);
                }});
            }
            return result;
        } catch (SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(), ex);
        } finally {
            DBHelper.closeQuietly(c);
            DBHelper.closeQuietly(rs);
            DBHelper.closeQuietly(st);
        }
    }

    private String getEmailNonKedinasan(String username) {
        ResultSet rs = null;
        PreparedStatement st = null;
        Connection c = null;

        log.info("get EmailNonKedinasan for user {}", username);

        try {
            c = DBHelper.getConnection();

            st = c.prepareStatement(Q_GET_EMAIL_NON_KEDINASAN);
            st.setString(1, username);
            st.execute();
            rs = st.getResultSet();

            String emailNonKedinasan = "";
            while (rs.next()) {
                emailNonKedinasan = rs.getString(1);
            }
            return emailNonKedinasan;
        } catch (SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(), ex);
        } finally {
            DBHelper.closeQuietly(c);
            DBHelper.closeQuietly(rs);
            DBHelper.closeQuietly(st);
        }
    }

    private void saveOtp(String userid, String otp) {

        log.info("Inserting OTP to PGSQL for user {}", userid);

        PreparedStatement st = null;
        Connection c = null;

        try {
            c = PostgresDBHelper.getConnection();

            st = c.prepareStatement(Q_INSERT_OTP);
            st.setString(1, UUID.randomUUID().toString());
            st.setString(2, userid);
            st.setString(3, otp);
            st.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException("Database error:" + ex.getMessage(), ex);
        } finally {
            PostgresDBHelper.closeQuietly(c);
            PostgresDBHelper.closeQuietly(st);
        }
    }

    private String getEmailBody() {
        return "<html>\n" +
            "<head></head>\n" +
            "<body>\n" +
            "<img src=\"https://login.atrbpn.go.id/images/atrbpn-icon.png\" />\n" +
            "<h2>Otorisasi Akun Aplikasi</h2>\n" +
            "<p>Hai USERNAME,</p>\n" +
            "<br/>\n" +
            "<p>Tinggal selangkah lagi untuk menyelesaikan proses login pada aplikasi Kementerian Agraria dan Tata Ruang / Badan Pertanahan Nasional. Anda hanya perlu memasukkan kode OTP di bawah ini:</p>\n" +
            "<h4><b>KODE-OTP</b></h4>\n" +
            "<p>Kode ini hanya berlaku selama 5 menit. Segera masukkan kode OTP dan jangan berikan kode ini kepada siapapun.</p>\n" +
            "<p>Jika Anda merasa tidak pernah meminta kode OTP, abaikan email ini dan segera hubungi CSIRT Kementerian Agraria dan Tata Ruang / Badan Pertanahan Nasional - 081119310000. Kami siap membantu Anda.</p>\n" +
            "<br/>\n" +
            "Salam,<br/>\n" +
            "<br/>\n" +
            "<p>Pengelola Aplikasi<br/>\n" +
            "Kementerian Agraria dan Tata Ruang / Badan Pertanahan Nasional</p>\n" +
            "</body>\n" +
            "</html>";
    }

    private String getSmsBody() {
        return "ATRBPN - kode aktivasi akun anda adalah OTP";
    }

    private String masking(List<String> values) {
        if(values == null || values.isEmpty()) {
            log.info("empty values for masking");
            return "**";
        }
        return masking(values.get(0));
    }

    private String masking(String value) {
        if(value == null) {
            log.info("empty value for masking");
            return "**";
        }

        if(value.contains("@")) {
            return value.replaceAll("(?<=.).(?=[^@]*?.@)", "*");
        } else {
            return value.replaceAll("\\d(?=(?:\\D*\\d){4})", "*");
        }
    }
}