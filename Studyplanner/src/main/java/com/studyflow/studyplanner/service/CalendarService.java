package com.studyflow.studyplanner.service;

import com.studyflow.studyplanner.model.CalendarEvent;
import com.studyflow.studyplanner.repository.CalendarEventRepository;
import com.studyflow.studyplanner.model.User;
import java.time.*;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
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
            case 1 -> 100; // was 120
            case 2 -> 130; // was 150
            case 3 -> 160; // was 180
            default -> 130;
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

    public List<CalendarEvent> calculateStudySlotsForDeadline(User user, CalendarEvent deadline, List<CalendarEvent> existingEvents, int hoursToPlan) {
        List<CalendarEvent> sessions = new ArrayList<>();

        Set<DayOfWeek> preferredDays = new HashSet<>();
        for (String day : user.getPreferredStudyDays().split(",")) {
            preferredDays.add(DayOfWeek.valueOf(day.trim().toUpperCase()));
        }

        LocalTime start = LocalTime.parse(user.getPreferredStartTime());
        LocalTime end = LocalTime.parse(user.getPreferredEndTime());
        Duration breakDuration = Duration.parse("PT" + user.getPreferredBreakTime().replace(":", "H") + "M");

        LocalDate currentDate = LocalDate.now();
        LocalDate deadlineDate = deadline.getStartTime().toLocalDate();
        int plannedHours = 0;

        while (!currentDate.isAfter(deadlineDate) && plannedHours < hoursToPlan) {
            final LocalDate loopDate = currentDate;
            if (preferredDays.contains(currentDate.getDayOfWeek())) {
                LocalDateTime slotStart = currentDate.atTime(start);
                LocalDateTime slotEnd = currentDate.atTime(end);
                List<CalendarEvent> dayEvents = existingEvents.stream()
                    .filter(e -> !e.getStartTime().toLocalDate().isAfter(loopDate) &&
                                 !e.getEndTime().toLocalDate().isBefore(loopDate))
                    .collect(Collectors.toList());

                LocalDateTime currentSlot = slotStart;
                while (currentSlot.plusHours(1).isBefore(slotEnd) && plannedHours < hoursToPlan) {
                    final LocalDateTime slotStartForCheck = currentSlot;
                    boolean overlaps = dayEvents.stream().anyMatch(e ->
                        !(slotStartForCheck.plusHours(1).isBefore(e.getStartTime()) || slotStartForCheck.isAfter(e.getEndTime()))
                    );

                    if (!overlaps) {
                        CalendarEvent session = new CalendarEvent();
                        session.setTitle("Self-Study for " + deadline.getTitle());
                        session.setStartTime(currentSlot);
                        session.setEndTime(currentSlot.plusHours(1));
                        session.setUserId(deadline.getUserId());
                        session.setCourseId(deadline.getCourseId());
                        session.setColor(deadline.getColor());
                        session.setType("self-study");
                        session.setGeneratedByEngine(true);

                        sessions.add(session);
                        plannedHours++;
                    }

                    currentSlot = currentSlot.plusHours(1);
                }
            }

            currentDate = currentDate.plusDays(1);
        }

        return sessions;
    }
}