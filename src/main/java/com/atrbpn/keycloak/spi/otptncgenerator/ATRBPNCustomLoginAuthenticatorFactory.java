package com.atrbpn.keycloak.spi.otptncgenerator;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.ConfigurableAuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * <pre>
 *     com.atrbpn.keycloak.spi.otpgenerator.ATRBPNCustomLoginAuthenticatorFactory
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 04 Apr 2022 12:40
 */
public class ATRBPNCustomLoginAuthenticatorFactory  implements AuthenticatorFactory, ConfigurableAuthenticatorFactory {

    public static final String PROVIDER_ID = "atrbpn-otp-kantor-tnc-login-api";

    private static final ATRBPNCustomLoginAuthenticator SINGLETON = new ATRBPNCustomLoginAuthenticator();

    public String getDisplayType() {
        return "ATRBPN OTP-Kantor-TnC Login API";
    }

    public String getReferenceCategory() {
        return "ATRBPN OTP-Kantor-Tnc Login API";
    }

    public boolean isConfigurable() {
        return false;
    }

    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    public boolean isUserSetupAllowed() {
        return false;
    }

    public String getHelpText() {
        return "ATRBPN OTP-Kantor-TnC Login API";
    }

    private static AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.DISABLED
    };

    public List<ProviderConfigProperty> getConfigProperties() {
        return new ArrayList<ProviderConfigProperty>();
    }

    public Authenticator create(KeycloakSession keycloakSession) {
        return SINGLETON;
    }

    public void init(Config.Scope scope) {

    }

    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {

    }

    public void close() {

    }

    public String getId() {
        return PROVIDER_ID;
    }

    public int order() {
        return 0;
    }
}