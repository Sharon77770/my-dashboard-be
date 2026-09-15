package com.personal.dashboard.home.controller;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Connects the authenticated principal to the initial dashboard view. */
@Controller
public class HomeController {
  private final com.personal.dashboard.catalog.service.CatalogService catalog;

  public HomeController(com.personal.dashboard.catalog.service.CatalogService catalog) {
    this.catalog = catalog;
  }

  /** Displays only the signed-in account, without inventing task or connection data. */
  @GetMapping("/")
  public String home(Principal principal, Model model) {
    model.addAttribute("accountId", principal.getName());
    model.addAttribute("workspace", catalog.workspace());
    return "home";
  }
}
