package com.studyflow.studyplanner.controller;

import com.studyflow.studyplanner.model.User;
import com.studyflow.studyplanner.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    private final UserService userService;

    @Autowired
    public AuthController(UserService userService) {
        this.userService = userService;
    }



    // Mapping to Login-page / if error, logout, registred -> Message will be displayed to user
    @GetMapping("/login")
    public String login(
        @RequestParam(value="error",      required=false) String error,
        @RequestParam(value="logout",     required=false) String logout,
        @RequestParam(value="registered", required=false) String registered,
        Model model
    ) {
        if (error     != null) model.addAttribute("error",      "Invalid Userdata.");
        if (logout    != null) model.addAttribute("msg",        "Logout successfull.");
        if (registered!= null) model.addAttribute("msg",        "Registration comlpeted! Please log in!");
        return "login";
    }

    // Mapping to Signup-page
    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("form", new User());
        return "signup";
    }

    // Getting Data from Signup-page 
    @PostMapping("/signup")
    public String register(
        @ModelAttribute("form") User form,
        Model model
    ) {
        // try to execute UserService method -> see UserService.java for detail
        try {
            userService.registerAndLogin(
                form.getUsername(),
                form.getEmail(),
                form.getPassword()
            );
            // if Sign-Up successfull -> back to Login Page (CHANGE HERE)
            return "redirect:/login?registered";
        } 
        // if not possible because error in UserService method -> Error and return to Sign-Up page
        catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
            return "signup";
        }
    }

    
    // Password reset
    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam("email") String email, Model model) {
        try {
            userService.requestPasswordReset(email);
            model.addAttribute("message", "Password reset email sent");
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam("token") String token, Model model) {
        model.addAttribute("token", token);
        return "reset-password";
    }

 @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam("token") String token,
                                       @RequestParam("password") String password,
                                       @RequestParam("confirmPassword") String confirmPassword,
                                       Model model) {
        // Check if the new password and confirmation password match
        if (!password.equals(confirmPassword)) {
            // If passwords don't match, add an error message and return to the reset-password page
            model.addAttribute("error", "Passwords do not match");
            return "reset-password";
        }

        try {
            // Attempt to reset the password using the provided token and new password
            userService.resetPassword(token, password);
            
            // If successful, add a success message and redirect to the login page
            model.addAttribute("message", "Password has been reset successfully. You can now log in with your new password.");
            return "login";
        } catch (RuntimeException e) {
            // If an exception occurs (e.g., invalid or expired token),
            // add the error message to the model and return to the reset-password page
            model.addAttribute("error", e.getMessage());
            return "reset-password";
        }
    }
}
