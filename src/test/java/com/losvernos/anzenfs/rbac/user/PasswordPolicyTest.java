package com.losvernos.anzenfs.rbac.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void acceptsAPasswordMeetingAllRequirements() {
        assertThatCode(() -> PasswordPolicy.validate("secret123"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsANullPassword() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAPasswordShorterThanEightCharacters() {
        assertThatThrownBy(() -> PasswordPolicy.validate("abc123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8 characters");
    }

    @Test
    void rejectsAPasswordWithoutADigit() {
        assertThatThrownBy(() -> PasswordPolicy.validate("onlyletters"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digit");
    }

    @Test
    void rejectsAPasswordWithoutALetter() {
        assertThatThrownBy(() -> PasswordPolicy.validate("12345678"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("letter");
    }
}
