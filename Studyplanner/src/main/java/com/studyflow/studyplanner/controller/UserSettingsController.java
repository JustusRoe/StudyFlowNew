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

    /**
     * Displays the user settings page with the current user's data and CSRF token.
     */
    @GetMapping("/user_settings")
    public String showSettings(Model model, Principal principal, HttpServletRequest request) {
        User user = userRepository.findByEmail(principal.getName());
        model.addAttribute("user", user);

        // Add CSRF token to the model for form protection
        CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
        model.addAttribute("_csrf", token);

        return "user_settings";
    }

    /**
     * Handles updates to user settings (password, study times, study days, etc.).
     */
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
        // Update password if provided
        if (formUser.getPassword() != null && !formUser.getPassword().isEmpty()) {
            userService.changePassword(user, formUser.getPassword());
        }
        // Update preferred study times and days
        user.setPreferredStartTime(formUser.getPreferredStartTime());
        user.setPreferredEndTime(formUser.getPreferredEndTime());
        user.setPreferredBreakTime(formUser.getPreferredBreakTime());
        user.setPreferredStudyDays(formUser.getPreferredStudyDays());
        user.setPreferredStudySessionDuration(formUser.getPreferredStudySessionDuration());

        userService.save(user);
        return "redirect:/user_settings?success";
    }
}