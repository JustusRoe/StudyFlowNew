package com.studyflow.studyplanner.controller;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.studyflow.studyplanner.model.User;
import com.studyflow.studyplanner.repository.UserRepository;
import com.studyflow.studyplanner.service.UserService;

@Controller
public class UserSettingsController {

    private final UserService userService;
    private final UserRepository userRepository;

    public UserSettingsController(UserService userService, UserRepository userRepository) {
        this.userService    = userService;
        this.userRepository = userRepository;
    }

    @GetMapping("/user_settings")
public String showSettings(Model model, Principal principal, HttpServletRequest request) {
    User user = userRepository.findByEmail(principal.getName());
    model.addAttribute("user", user);

    // manueller CSRF-Token
    CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
    model.addAttribute("_csrf", token);

    return "user_settings";
}

    @PostMapping("/user_settings")
    public String updateSettings(
        @Valid @ModelAttribute("user") User formUser,
        BindingResult binding,
        Principal principal
    ) {
        if (binding.hasErrors()) {
            return "user_settings";
        }

        User user = userRepository.findByEmail(principal.getName());
        // Passwort
        if (formUser.getPassword() != null && !formUser.getPassword().isEmpty()) {
            userService.changePassword(user, formUser.getPassword());
        }
        // Zeiten und Tage
        user.setPreferredStartTime(formUser.getPreferredStartTime());
        user.setPreferredEndTime(formUser.getPreferredEndTime());
        user.setPreferredBreakTime(formUser.getPreferredBreakTime());
        user.setPreferredStudyDays(formUser.getPreferredStudyDays());

        userService.save(user);
        return "redirect:/user_settings?success";
    }
}