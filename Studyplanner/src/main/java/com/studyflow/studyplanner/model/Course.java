package com.studyflow.studyplanner.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Course composed of multiple CalendarEvent lecture entries.
 */
public class Course {
    private final String id;
    private final String name;
    private final List<CalendarEvent> events = new ArrayList<>();

    public Course(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<CalendarEvent> getEvents() {
        return events;
    }
}