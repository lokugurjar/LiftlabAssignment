package com.example.analytics.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

  @Value("${app.active-user-window-min}") private long auWin;
  @Value("${app.pageview-window-min}") private long pvWin;
  @Value("${app.active-session-window-min}") private long asWin;

  @GetMapping("/")
  public String index(Model model) {
    model.addAttribute("auWin", auWin);
    model.addAttribute("pvWin", pvWin);
    model.addAttribute("asWin", asWin);
    return "index";
  }
}
