package com.studyflow.studyplanner.model;

import com.studyflow.studyplanner.model.CalendarEvent;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(
    name = "courses",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"course_identifier", "user_id"})
        }
    )
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String color;

    @Column(unique = false)
    private String courseIdentifier; // stores the ICS courseId like [xxxxxx]

    @Column(name = "difficulty")
    private int difficulty; // 1 = leicht, 2 = mittel, 3 = schwer

    // Event-IDs als einfache Liste (One-to-Many auf IDs)
    @ElementCollection
    @CollectionTable(name = "course_event_ids", joinColumns = @JoinColumn(name = "course_id"))
    @Column(name = "event_id")
    private List<Long> eventIds = new ArrayList<>();

    // Verknüpfter Benutzer
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Nicht persistente Events zur Laufzeit (für Progress-Berechnung)
    @Transient
    private List<CalendarEvent> resolvedEvents = new ArrayList<>();

    public Course() {}

    public Course(String name, String color, User user) {
        this.name = name;
        this.color = color;
        this.user = user;
    }

    /* --- Getter & Setter --- */

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getCourseIdentifier() {
        return courseIdentifier;
    }

    public void setCourseIdentifier(String courseIdentifier) {
        this.courseIdentifier = courseIdentifier;
    }

    public List<Long> getEventIds() {
        return eventIds;
    }

    public void setEventIds(List<Long> eventIds) {
        this.eventIds = eventIds;
    }

    public void addEventId(Long eventId) {
        this.eventIds.add(eventId);
    }

    public void removeEventId(Long eventId) {
        this.eventIds.remove(eventId);
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<CalendarEvent> getResolvedEvents() {
        return resolvedEvents;
    }

    public void setResolvedEvents(List<CalendarEvent> resolvedEvents) {
        this.resolvedEvents = resolvedEvents;
    }

    @Transient
    public double getProgress() {
        if (resolvedEvents == null || resolvedEvents.isEmpty()) return 0.0;

        long completed = resolvedEvents.stream().filter(CalendarEvent::isCompleted).count();
        return (double) completed / resolvedEvents.size();
    }
    
    @JsonProperty("progressPercent")
    public int getProgressPercent() {
        return (int) Math.round(getProgress() * 100);
    }

    @JsonProperty("selfStudyHours")
    @Transient
    public int getSelfStudyHours() {
        return resolvedEvents.stream()
            .filter(e -> "self-study".equalsIgnoreCase(e.getType()))
            .mapToInt(CalendarEvent::getDurationInHours)
            .sum();
    }

    @JsonProperty("workloadTarget")
    @Transient
    public int getWorkloadTarget() {
        return switch (difficulty) {
            case 1 -> 100; // leicht (was 120 war)
            case 2 -> 130; // mittel (was 150 war)
            case 3 -> 160; // schwer (was 180 war)
            default -> 130; // fallback
        };
    }

    // Optional: For gamification/fish mascot scaling
    @JsonProperty("progressFraction")
    @Transient
    public double getProgressFraction() {
        return getProgress();
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }
}