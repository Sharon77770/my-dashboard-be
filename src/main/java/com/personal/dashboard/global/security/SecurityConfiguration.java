package com.personal.dashboard.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** Owns the environment-defined account and browser session authentication boundary. */
@Configuration
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfiguration {

  /** Missing credentials stop startup; configuration values never enter validation messages. */
  @Bean
  UserDetailsService dashboardAccount(Environment environment) {
    String accountId = environment.getProperty("DASHBOARD_AUTH_ID");
    String password = environment.getProperty("DASHBOARD_AUTH_PASSWORD");
    if (accountId == null || accountId.isBlank() || password == null || password.isBlank()) {
      throw new IllegalStateException("DASHBOARD_AUTH_ID and DASHBOARD_AUTH_PASSWORD must be set");
    }
    if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
      throw new IllegalStateException("DASHBOARD_AUTH_PASSWORD must not exceed 72 UTF-8 bytes");
    }
    String passwordHash = new BCryptPasswordEncoder().encode(password);
    return new InMemoryUserDetailsManager(
        User.withUsername(accountId).password("{bcrypt}" + passwordHash).roles("OWNER").build());
  }

  /** Spring Security handles password checks, session fixation protection, CSRF and logout. */
  @Bean
  SecurityFilterChain websiteSecurity(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            authorize ->
                authorize
                    .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        new org.springframework.security.web.util.matcher.AntPathRequestMatcher(
                            "/login"),
                        new org.springframework.security.web.util.matcher.AntPathRequestMatcher(
                            "/css/**"),
                        new org.springframework.security.web.util.matcher.AntPathRequestMatcher(
                            "/health"))
                    .permitAll()
                    .anyRequest()
                    .hasRole("OWNER"))
        .formLogin(
            login ->
                login
                    .loginPage("/login")
                    .usernameParameter("id")
                    .defaultSuccessUrl("/", true)
                    .failureUrl("/login?error")
                    .permitAll())
        .logout(logout -> logout.logoutSuccessUrl("/login?logout").deleteCookies("JSESSIONID"))
        .exceptionHandling(
            errors ->
                errors.authenticationEntryPoint(
                    (request, response, exception) -> {
                      String path =
                          request.getRequestURI().substring(request.getContextPath().length());
                      if (path.startsWith("/api/") || path.startsWith("/ws/"))
                        response.sendError(401);
                      else
                        new org.springframework.security.web.authentication
                                .LoginUrlAuthenticationEntryPoint("/login")
                            .commence(request, response, exception);
                    }))
        .requestCache(cache -> cache.disable());
    return http.build();
  }
}
