package com.personal.dashboard.nas.config;

import com.personal.dashboard.nas.controller.DavServlet;
import com.personal.dashboard.nas.service.DavService;
import java.util.List;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class NasConfiguration {
  @Bean
  public ServletRegistrationBean<DavServlet> davServlet(DavService service) {
    var registration = new ServletRegistrationBean<>(new DavServlet(service), "/dav", "/dav/*");
    registration.setLoadOnStartup(1);
    return registration;
  }

  @Bean
  @Order(1)
  public SecurityFilterChain davSecurity(HttpSecurity http) throws Exception {
    return http.securityMatcher(new AntPathRequestMatcher("/dav/**"))
        .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("OWNER"))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())
        .requestCache(cache -> cache.disable())
        .httpBasic(
            basic ->
                basic
                    .realmName("Personal Drive")
                    .authenticationEntryPoint(
                        (request, response, error) -> {
                          response.setHeader(
                              "WWW-Authenticate",
                              "Basic realm=\"Personal Drive\", charset=\"UTF-8\"");
                          response.setStatus(401);
                        }))
        .logout(logout -> logout.disable())
        .build();
  }

  @Bean
  public WebSecurityCustomizer davMethods() {
    return web -> {
      var firewall = new StrictHttpFirewall();
      firewall.setAllowUrlEncodedPercent(true);
      firewall.setAllowedHttpMethods(
          List.of(
              "GET",
              "HEAD",
              "POST",
              "PUT",
              "PATCH",
              "DELETE",
              "OPTIONS",
              "PROPFIND",
              "PROPPATCH",
              "MKCOL",
              "COPY",
              "MOVE",
              "LOCK",
              "UNLOCK"));
      web.httpFirewall(firewall);
    };
  }
}
