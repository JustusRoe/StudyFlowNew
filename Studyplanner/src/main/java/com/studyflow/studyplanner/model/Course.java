package com.studyflow.studyplanner.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String color;

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

    public List<Long> getEventIds() {
        return eventIds;
    }

    public void setEventIds(List<Long> eventIds) {
        this.eventIds = eventIds;
    }

    public void addEventId(Long eventId) {
        this.eventIds.add(eventId);
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
}
