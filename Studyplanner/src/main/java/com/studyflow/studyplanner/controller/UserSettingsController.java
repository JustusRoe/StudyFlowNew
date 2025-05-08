package com.studyflow.studyplanner.controller;

import java.security.Principal;

import com.studyflow.studyplanner.model.User;
import com.studyflow.studyplanner.repository.UserRepository;
import com.studyflow.studyplanner.service.UserService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.validation.BindingResult;
import jakarta.validation.Valid;

@Controller
public class UserSettingsController {

    private final UserRepository userRepository;

    private final UserService userService;

    public UserSettingsController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @GetMapping("/user_settings")
    public String showSettings(Model model, Principal principal) {
        String email = principal.getName();
        User user = userRepository.findByEmail(email);

        model.addAttribute("user", user);
        return "user_settings"; // user_settings.html template
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

        if (formUser.getPassword() != null && !formUser.getPassword().isEmpty()) {
            userService.changePassword(user, formUser.getPassword());
        }

        user.setPreferredStartTime(formUser.getPreferredStartTime()); // ToDo: endtime has to be after start time
        user.setPreferredEndTime(formUser.getPreferredEndTime());

        userService.save(user);

        return "redirect:/user_settings?success";
    }
}
