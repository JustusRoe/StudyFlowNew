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

    // Feste Lecture-Stunden
    private static final int LECTURE_HOURS = 33;

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

    // Calculates the total workload hours for a course based on given difficulty level.
    public int calculateTotalWorkload(int difficultyLevel) {
        return switch (difficultyLevel) {
            case 1 -> 120;
            case 2 -> 150;
            case 3 -> 180;
            default -> 150;
        };
    }

    // Berechnet Self-Study Stunden auf Basis der Schwierigkeit
    public int calculateSelfStudyHours(int difficultyLevel) {
        int total = calculateTotalWorkload(difficultyLevel);
        return Math.max(0, total - LECTURE_HOURS);
    }

    // Summiert die geplanten Self-Study-Stunden für einen Kurs
    public int getPlannedSelfStudyHours(List<CalendarEvent> events) {
        return events.stream()
                .filter(e -> "self-study".equalsIgnoreCase(e.getType()))
                .mapToInt(CalendarEvent::getDurationInHours)
                .sum();
    }

    // Berechnet die verbleibenden Self-Study-Stunden, die geplant werden müssen
    public int getRemainingSelfStudyHours(int difficultyLevel, List<CalendarEvent> events) {
        int required = calculateSelfStudyHours(difficultyLevel);
        int planned = getPlannedSelfStudyHours(events);
        return Math.max(0, required - planned);
    }

    // Calculates the workload weight for a deadline event based on its points and the course's total points/workload
    public int calculateDeadlineWorkload(CalendarEvent deadline, int totalWorkload, int totalPoints) {
        if (deadline.getPoints() <= 0 || totalPoints <= 0) return 0;
        return (int) ((deadline.getPoints() / (double) totalPoints) * totalWorkload);
    }
}