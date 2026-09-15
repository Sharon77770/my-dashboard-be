package com.personal.dashboard.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/** Checks fail-closed configuration without printing rejected credential values. */
class SecurityConfigurationTest {

  private final SecurityConfiguration configuration = new SecurityConfiguration();

  @Test
  void missingAndBlankCredentialsAreRejected() {
    assertThatThrownBy(() -> configuration.dashboardAccount(new MockEnvironment()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                configuration.dashboardAccount(
                    new MockEnvironment()
                        .withProperty("DASHBOARD_AUTH_ID", "owner")
                        .withProperty("DASHBOARD_AUTH_PASSWORD", " ")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                configuration.dashboardAccount(
                    new MockEnvironment()
                        .withProperty("DASHBOARD_AUTH_ID", " ")
                        .withProperty("DASHBOARD_AUTH_PASSWORD", UUID.randomUUID().toString())))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void accountUsesEncodedPasswordAndOwnerRole() {
    String password = UUID.randomUUID().toString();
    var users =
        configuration.dashboardAccount(
            new MockEnvironment()
                .withProperty("DASHBOARD_AUTH_ID", "owner")
                .withProperty("DASHBOARD_AUTH_PASSWORD", password));
    var account = users.loadUserByUsername("owner");
    assertThat(account.getPassword()).isNotEqualTo(password);
    assertThat(
            PasswordEncoderFactories.createDelegatingPasswordEncoder()
                .matches(password, account.getPassword()))
        .isTrue();
    assertThat(account.getAuthorities()).extracting("authority").containsExactly("ROLE_OWNER");
  }

  @Test
  void bcryptLengthValidationDoesNotLeakCredential() {
    String password = UUID.randomUUID().toString().repeat(3);
    assertThatThrownBy(
            () ->
                configuration.dashboardAccount(
                    new MockEnvironment()
                        .withProperty("DASHBOARD_AUTH_ID", "owner")
                        .withProperty("DASHBOARD_AUTH_PASSWORD", password)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageNotContaining(password);
  }
}
