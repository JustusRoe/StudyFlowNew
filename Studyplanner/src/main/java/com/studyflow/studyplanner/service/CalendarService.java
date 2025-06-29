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

/**
 * Handles the event import logic, saving imported lectures and fetching them for the calendar page.
 * Also provides workload and self-study calculations.
 */
@Service
public class CalendarService {
    private final CalendarEventRepository eventRepository;

    // Fixed number of lecture hours used for workload calculation
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

    /**
     * Calculates the total workload hours for a course based on the given difficulty level.
     */
    public int calculateTotalWorkload(int difficultyLevel) {
        return switch (difficultyLevel) {
            case 1 -> 100; // easy
            case 2 -> 130; // medium
            case 3 -> 160; // hard
            default -> 130;
        };
    }

    /**
     * Calculates the required self-study hours for a course based on difficulty.
     */
    public int calculateSelfStudyHours(int difficultyLevel) {
        int total = calculateTotalWorkload(difficultyLevel);
        return Math.max(0, total - LECTURE_HOURS);
    }

    /**
     * Sums the planned self-study hours for a course from a list of events.
     */
    public int getPlannedSelfStudyHours(List<CalendarEvent> events) {
        return events.stream()
                .filter(e -> "self-study".equalsIgnoreCase(e.getType()))
                .mapToInt(CalendarEvent::getDurationInHours)
                .sum();
    }

    /**
     * Calculates the remaining self-study hours that need to be planned for a course.
     */
    public int getRemainingSelfStudyHours(int difficultyLevel, List<CalendarEvent> events) {
        int required = calculateSelfStudyHours(difficultyLevel);
        int planned = getPlannedSelfStudyHours(events);
        return Math.max(0, required - planned);
    }

    /**
     * Calculates and returns a list of self-study session events for a deadline, based on user preferences and available slots.
     * Uses deadline.studyTimeNeeded and user.preferredStudySessionDuration.
     */
    public List<CalendarEvent> calculateStudySlotsForDeadline(User user, CalendarEvent deadline, List<CalendarEvent> existingEvents) {
        List<CalendarEvent> sessions = new ArrayList<>();

        Set<DayOfWeek> preferredDays = new HashSet<>();
        for (String day : user.getPreferredStudyDays().split(",")) {
            preferredDays.add(DayOfWeek.valueOf(day.trim().toUpperCase()));
        }

        LocalTime start = LocalTime.parse(user.getPreferredStartTime());
        LocalTime end = LocalTime.parse(user.getPreferredEndTime());

        // Parse break duration (HH:mm)
        Duration breakDuration;
        try {
            String[] parts = user.getPreferredBreakTime().split(":");
            breakDuration = Duration.ofHours(Long.parseLong(parts[0])).plusMinutes(Long.parseLong(parts[1]));
        } catch (Exception e) {
            breakDuration = Duration.ofMinutes(10); // fallback
        }

        // Parse session duration (HH:mm)
        Duration sessionDuration;
        try {
            String[] parts = user.getPreferredStudySessionDuration().split(":");
            sessionDuration = Duration.ofHours(Long.parseLong(parts[0])).plusMinutes(Long.parseLong(parts[1]));
            if (sessionDuration.isZero() || sessionDuration.isNegative()) sessionDuration = Duration.ofHours(1);
        } catch (Exception e) {
            sessionDuration = Duration.ofHours(1);
        }

        int hoursToPlan = deadline.getStudyTimeNeeded();
        if (hoursToPlan <= 0) return sessions;

        LocalDateTime studyStart = deadline.getStudyStart() != null ? deadline.getStudyStart() : LocalDateTime.now();
        LocalDateTime studyEnd = deadline.getStartTime();

        // Build all possible session slots between studyStart and studyEnd
        List<LocalDateTime> possibleStarts = new ArrayList<>();
        LocalDateTime current = studyStart;
        while (!current.isAfter(studyEnd.minusMinutes(1))) {
            if (preferredDays.contains(current.getDayOfWeek())) {
                LocalDateTime dayStart = current.withHour(start.getHour()).withMinute(start.getMinute());
                LocalDateTime dayEnd = current.withHour(end.getHour()).withMinute(end.getMinute());
                LocalDateTime initialSlotStart = dayStart.isAfter(current) ? dayStart : current;
                LocalDateTime slotStart = initialSlotStart;
                while (slotStart.plus(sessionDuration).isBefore(dayEnd) || slotStart.plus(sessionDuration).equals(dayEnd)) {
                    if (!slotStart.plus(sessionDuration).isAfter(studyEnd)) {
                        // Check for overlap with existing events
                        boolean overlaps = false;
                        for (CalendarEvent e : existingEvents) {
                            if (!(slotStart.plus(sessionDuration).isBefore(e.getStartTime()) || slotStart.isAfter(e.getEndTime()))) {
                                overlaps = true;
                                break;
                            }
                        }
                        if (!overlaps) {
                            possibleStarts.add(slotStart);
                        }
                    }
                    slotStart = slotStart.plus(sessionDuration).plus(breakDuration);
                }
            }
            current = current.plusDays(1).withHour(0).withMinute(0);
        }

        // Calculate sessions needed based on minutes
        long totalMinutes = hoursToPlan * 60L;
        long sessionMinutes = sessionDuration.toMinutes();
        int sessionsNeeded = (int) Math.ceil(totalMinutes / (double) sessionMinutes);

        int planned = 0;
        int used = 0;
        int i = 0;
        while (planned < hoursToPlan && i < possibleStarts.size() && used < sessionsNeeded) {
            LocalDateTime sessionStart = possibleStarts.get(i);
            int duration = Math.min((int) sessionDuration.toHours(), hoursToPlan - planned);
            LocalDateTime sessionEnd = sessionStart.plusHours(duration);
            if (sessionEnd.isAfter(studyEnd)) {
                sessionEnd = studyEnd;
                duration = (int) Duration.between(sessionStart, sessionEnd).toHours();
                if (duration <= 0) { i++; continue; }
            }
            CalendarEvent session = new CalendarEvent();
            session.setTitle("Self-Study for " + deadline.getTitle());
            session.setStartTime(sessionStart);
            session.setEndTime(sessionEnd);
            session.setUserId(deadline.getUserId());
            session.setCourseId(deadline.getCourseId());
            session.setColor(deadline.getColor());
            session.setType("self-study");
            session.setGeneratedByEngine(true);
            session.setRelatedDeadlineId(deadline.getId());
            sessions.add(session);
            planned += duration;
            used++;
            i++;
        }

        return sessions;
    }
}