package com.atrbpn.keycloak.spi.otptncgenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * <pre>
 *     com.atrbpn.keycloak.spi.otpgenerator.RegexTest
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 02 Jun 2022 14:36
 */
public class RegexTest {

    private String masking(String value) {
        if(value.contains("@")) {
            return value.replaceAll("(?<=.).(?=[^@]*?.@)", "*");
        } else {
            return value.replaceAll("\\d(?=(?:\\D*\\d){4})", "*");
        }
    }

    @Test
    public void testRegex() {
        String phoneRegex = masking("0877662525411");
        Assertions.assertEquals("*********5411", phoneRegex);

        String emailRegex = masking("edwin@redhat.com");
        Assertions.assertEquals("e***n@redhat.com", emailRegex);
    }

}
