package com.studyflow.studyplanner.controller;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;

///
/// 
///  Comming soooooon....
/// 
/// 
@Controller
public class HomeController {

  @GetMapping("/private")
  String privatePage() { return "private"; }
}