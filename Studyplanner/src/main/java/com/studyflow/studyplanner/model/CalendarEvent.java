package com.studyflow.studyplanner.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "calendar_events")
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    @Column(nullable = false)
    private LocalDateTime startTime;
    @Column(nullable = false)
    private LocalDateTime endTime;

    private Long userId;

    private String color;
    private String description;

    private String type;
    private boolean completed = false;

    private String courseId;

    private boolean isDeadline = false;
    private int points = 0;
    private boolean generatedByEngine = false;

    public CalendarEvent() {}

    public CalendarEvent(String title, LocalDateTime start, LocalDateTime end, Long userId) {
        this.title = title;
        this.startTime = start;
        this.endTime = end;
        this.userId = userId;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
    
    public boolean isCompleted() {
    return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public boolean isDeadline() {
        return isDeadline;
    }

    public void setDeadline(boolean isDeadline) {
        this.isDeadline = isDeadline;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public boolean isGeneratedByEngine() {
        return generatedByEngine;
    }

    public void setGeneratedByEngine(boolean generatedByEngine) {
        this.generatedByEngine = generatedByEngine;
    }

    public int getDurationInHours() {
        if (startTime != null && endTime != null) {
            return (int) java.time.Duration.between(startTime, endTime).toHours();
        }
        return 0;
    }}