package com.studyflow.studyplanner.service;

import com.studyflow.studyplanner.model.CalendarEvent;
import com.studyflow.studyplanner.repository.CalendarEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

// handles the event import logic of saving imported lectures and fetching them later to show on the calendar page
import java.util.Arrays;

@Service
public class CalendarService {
    private final CalendarEventRepository eventRepository;
    private final List<String> validEventTypes = Arrays.asList("lecture", "assignment", "exam", "self-study");

    @Autowired
    public CalendarService(CalendarEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public CalendarEvent saveEvent(CalendarEvent event) {
        validateEventType(event.getType());
        return eventRepository.save(event);
    }

    public List<CalendarEvent> getUserEvents(Long userId) {
        return eventRepository.findByUserId(userId);
    }

    public List<CalendarEvent> getUserEventsByType(Long userId, String type) {
        validateEventType(type);
        return eventRepository.findByUserIdAndType(userId, type);
    }

    public void deleteEvent(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new RuntimeException("Event not found: " + eventId);
        }
        eventRepository.deleteById(eventId);
    }

    private void validateEventType(String type) {
        if (type == null || !validEventTypes.contains(type.toLowerCase())) {
            throw new IllegalArgumentException("Invalid event type: " + type);
        }
    }
}