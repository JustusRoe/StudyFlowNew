package com.studyflow.studyplanner.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Creating SQL Table for USER
@Entity
@Table(name = "USERS")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;



    // Constructor
    public User() { }

    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email    = email;
    }

    // --- Getter & Setter ---
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }


    // for password-reset
    private String resetToken;
    private LocalDateTime resetTokenExpiry;

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public void setResetTokenExpiry(LocalDateTime resetTokenExpiry) {
        this.resetTokenExpiry = resetTokenExpiry;
    }

    public String getResetToken() {
        return resetToken;
    }

    public LocalDateTime getResetTokenExpiry() {
        return resetTokenExpiry;
    }


    // for profile settings

    @Column(name = "preferred_start_time")
    private String preferredStartTime = "09:00";

    @Column(name = "preferred_end_time")
    private String preferredEndTime   = "17:00";

    @Column(name = "preferred_break_time")
    private String preferredBreakTime = "01:00";

    @Column(name = "preferred_study_days")
    private String preferredStudyDays = "MONDAY,TUESDAY, WEDNESDAY, THURSDAY, FRIDAY";

    public String getPreferredStartTime() {
        return preferredStartTime;
    }

    public String getPreferredEndTime() {
        return preferredEndTime;
    }

    public String getPreferredStudyDays() {
        return preferredStudyDays;
    }

    public String getPreferredBreakTime() {
        return preferredBreakTime;
    }

    public void setPreferredStartTime(String preferredStartTime) {
        this.preferredStartTime = preferredStartTime;
    }

    public void setPreferredEndTime(String preferredEndTime) {
        this.preferredEndTime = preferredEndTime;
    }
    
    public void setPreferredStudyDays(String preferredStudyDays) {
        this.preferredStudyDays = preferredStudyDays;
    }
    
    public void setPreferredBreakTime(String preferredBreakTime) {
        this.preferredBreakTime = preferredBreakTime;
    }
}