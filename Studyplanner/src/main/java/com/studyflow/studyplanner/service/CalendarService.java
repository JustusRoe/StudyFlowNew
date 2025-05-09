package com.studyflow.studyplanner.service;

import com.studyflow.studyplanner.model.CalendarEvent;
import com.studyflow.studyplanner.repository.CalendarEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

// handles the event import logic of saving imported lectures and fetching them later to show on the calendar page

@Service
public class CalendarService {
    private final CalendarEventRepository eventRepository;

    @Autowired
    public CalendarService(CalendarEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public CalendarEvent saveEvent(CalendarEvent event) {
        return eventRepository.save(event);
    }

    public List<CalendarEvent> getUserEvents(Long userId) {
        return eventRepository.findByUserId(userId);
    }

    public List<CalendarEvent> getUserEventsByType(Long userId, String type) {
        return eventRepository.findByUserIdAndTypeIgnoreCase(userId, type);
    }

    public CalendarEvent findById(Long id) {
        return eventRepository.findById(id).orElseThrow(() -> new RuntimeException("Event not found: " + id));
    }

    public void deleteEvent(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new RuntimeException("Event not found: " + eventId);
        }
        eventRepository.deleteById(eventId);
}




}