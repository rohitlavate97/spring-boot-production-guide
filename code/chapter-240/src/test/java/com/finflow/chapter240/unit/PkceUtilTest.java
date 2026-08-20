package com.finflow.chapter240.unit;

import com.finflow.chapter240.security.PkceUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PkceUtilTest {

    @Test
    public void testPkceGenerationAndVerification_succeeds() {
        String verifier = PkceUtil.generateCodeVerifier();
        assertThat(verifier).isNotBlank();
        assertThat(verifier.length()).isGreaterThanOrEqualTo(43);

        String challenge = PkceUtil.generateCodeChallenge(verifier);
        assertThat(challenge).isNotBlank();
        assertThat(challenge).doesNotContain("=").doesNotContain("+").doesNotContain("/");

        boolean isValid = PkceUtil.verifyCodeChallenge(verifier, challenge);
        assertThat(isValid).isTrue();

        boolean isInvalid = PkceUtil.verifyCodeChallenge("tampered_code_verifier_string_12345", challenge);
        assertThat(isInvalid).isFalse();
    }
}
