package com.studyflow.studyplanner.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a course for a user. Each course can have multiple calendar events (by event IDs)
 * and is linked to a user. Used for grouping lectures, deadlines, and self-study sessions.
 */
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
    private int difficulty; // 1 = easy, 2 = medium, 3 = hard

    // List of event IDs associated with this course
    @ElementCollection
    @CollectionTable(name = "course_event_ids", joinColumns = @JoinColumn(name = "course_id"))
    @Column(name = "event_id")
    private List<Long> eventIds = new ArrayList<>();

    // Linked user (owner of the course)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Non-persistent events, resolved at runtime (for progress calculation)
    @Transient
    private List<CalendarEvent> resolvedEvents = new ArrayList<>();

    public Course() {}

    public Course(String name, String color, User user) {
        this.name = name;
        this.color = color;
        this.user = user;
    }

    // --- Getters & Setters ---

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

    /**
     * Returns the progress as a fraction (0.0 - 1.0) based on completed events.
     */
    @Transient
    public double getProgress() {
        if (resolvedEvents == null || resolvedEvents.isEmpty()) return 0.0;

        long completed = resolvedEvents.stream().filter(CalendarEvent::isCompleted).count();
        return (double) completed / resolvedEvents.size();
    }
    
    /**
     * Returns the progress as a percentage (0-100).
     */
    @JsonProperty("progressPercent")
    public int getProgressPercent() {
        return (int) Math.round(getProgress() * 100);
    }

    /**
     * Returns the total self-study hours for this course.
     */
    @JsonProperty("selfStudyHours")
    @Transient
    public int getSelfStudyHours() {
        return resolvedEvents.stream()
            .filter(e -> "self-study".equalsIgnoreCase(e.getType()))
            .mapToInt(CalendarEvent::getDurationInHours)
            .sum();
    }

    /**
     * Returns the workload target based on course difficulty.
     */
    @JsonProperty("workloadTarget")
    @Transient
    public int getWorkloadTarget() {
        return switch (difficulty) {
            case 1 -> 100; // easy
            case 2 -> 130; // medium
            case 3 -> 160; // hard
            default -> 130; // fallback
        };
    }

    // Progress fraction for compatibility with frontend
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