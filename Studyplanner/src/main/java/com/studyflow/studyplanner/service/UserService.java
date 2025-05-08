package com.studyflow.studyplanner.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyflow.studyplanner.model.User;
import com.studyflow.studyplanner.repository.UserRepository;

@Service
public class UserService {

    // Dependencies required for user operations
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Autowired
    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.userRepository        = userRepository;
        this.passwordEncoder       = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    /**
     * Registers a new user and logs them in automatically.
     * This method is transactional to ensure DB consistency.
     */
    @Transactional
    public User registerAndLogin(String username, String email, String rawPassword) {
        if (userRepository.findByEmail(email) != null) {
            throw new RuntimeException("Email already exists");
        }
        String encodedPassword = passwordEncoder.encode(rawPassword);
        User user = new User(username, encodedPassword, email);
        User saved = userRepository.save(user);
        autoLogin(email, rawPassword);
        return saved;
    }

    /**
     * Finds a user by ID or throws an exception if not found.
     */
    public User findById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    /**
     * Performs programmatic login using Spring Security.
     */
    private void autoLogin(String email, String rawPassword) {
        UsernamePasswordAuthenticationToken token =
            new UsernamePasswordAuthenticationToken(email, rawPassword);
        Authentication auth = authenticationManager.authenticate(token);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Autowired
    private EmailService emailService;

    /**
     * Initiates the password reset process by generating a token.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        String token = generateResetToken(user.getId());
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);
        emailService.sendPasswordResetEmail(email, token);
    }

    /**
     * Resets the user's password using a token.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token);
        if (user == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Invalid or expired reset token");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    /**
     * Generates a unique password reset token.
     */
    private String generateResetToken(Long userId) {
        return userId + "_" + UUID.randomUUID().toString();
    }

    /**
     * Changes a user's password securely.
     */
    @Transactional
    public void changePassword(User user, String newRawPassword) {
        user.setPassword(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);
    }

    /**
     * Saves any updates to a user entity.
     */
    @Transactional
    public void save(User user) {
        userRepository.save(user);
    }
}
