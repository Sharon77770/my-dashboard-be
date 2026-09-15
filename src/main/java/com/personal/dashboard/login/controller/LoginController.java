package com.personal.dashboard.login.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Renders the login form; credentials are processed exclusively by Spring Security. */
@Controller
public class LoginController {

  /** Thymeleaf adds a session-bound CSRF token to the form. */
  @GetMapping("/login")
  public String login() {
    return "login";
  }
}
