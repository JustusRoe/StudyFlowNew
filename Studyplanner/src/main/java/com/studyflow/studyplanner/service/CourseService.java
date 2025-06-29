package com.studyflow.studyplanner.service;

import com.studyflow.studyplanner.model.CalendarEvent;
import com.studyflow.studyplanner.model.Course;
import com.studyflow.studyplanner.model.User;
import com.studyflow.studyplanner.repository.CalendarEventRepository;
import com.studyflow.studyplanner.repository.CourseRepository;
import com.studyflow.studyplanner.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

record TimeSlot(LocalDateTime start, LocalDateTime end) {}

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CalendarEventRepository eventRepository;

    @Autowired
    public CourseService(CourseRepository courseRepository,
                         UserRepository userRepository,
                         CalendarEventRepository eventRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * Calculates all free time slots for a user between two dates, considering existing events and user preferences.
     */
    private List<TimeSlot> calculateFreeSlots(User user, List<CalendarEvent> existingEvents, LocalDateTime from, LocalDateTime to, java.time.Duration breakDuration) {
        List<TimeSlot> slots = new ArrayList<>();

        Set<DayOfWeek> preferredDays = Arrays.stream(user.getPreferredStudyDays().split(","))
            .map(String::trim)
            .map(String::toUpperCase)
            .map(DayOfWeek::valueOf)
            .collect(Collectors.toSet());

        LocalTime start = LocalTime.parse(user.getPreferredStartTime());
        LocalTime end = LocalTime.parse(user.getPreferredEndTime());

        LocalDateTime current = from.withHour(start.getHour()).withMinute(start.getMinute());

        while (!current.isAfter(to)) {
            if (preferredDays.contains(current.getDayOfWeek())) {
                LocalDateTime dayStart = current.withHour(start.getHour()).withMinute(start.getMinute());
                LocalDateTime dayEnd = current.withHour(end.getHour()).withMinute(end.getMinute());

                List<CalendarEvent> dayEvents = existingEvents.stream()
                    .filter(e -> !e.getEndTime().isBefore(dayStart) && !e.getStartTime().isAfter(dayEnd))
                    .sorted(Comparator.comparing(CalendarEvent::getStartTime))
                    .toList();

                LocalDateTime slotStart = dayStart;

                for (CalendarEvent e : dayEvents) {
                    if (slotStart.isBefore(e.getStartTime())) {
                        LocalDateTime slotEnd = e.getStartTime();
                        if (java.time.Duration.between(slotStart, slotEnd).toMinutes() >= 30) {
                            slots.add(new TimeSlot(slotStart, slotEnd));
                        }
                    }
                    slotStart = e.getEndTime().isAfter(slotStart) ? e.getEndTime().plus(breakDuration) : slotStart;
                }

                if (slotStart.isBefore(dayEnd)) {
                    if (java.time.Duration.between(slotStart, dayEnd).toMinutes() >= 30) {
                        slots.add(new TimeSlot(slotStart, dayEnd));
                    }
                }
            }
            current = current.plusDays(1);
        }

        return slots;
    }

    /**
     * Creates a new course and saves it to the database.
     */
    @Transactional
    public Course createCourse(String name, String color, String userEmail, String courseIdentifier, int difficulty) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null) throw new RuntimeException("User not found");

        Course course = new Course(name, color, user);
        course.setCourseIdentifier(courseIdentifier);
        course.setDifficulty(difficulty);
        return courseRepository.save(course);
    }

    /**
     * Adds an event to a course by event ID.
     */
    @Transactional
    public Course addEventToCourse(Long courseId, Long eventId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        CalendarEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        // Set event color to course color
        event.setColor(course.getColor());
        eventRepository.save(event);

        // Add event ID to course and color all events
        course.addEventId(eventId);
        Course updated = courseRepository.save(course);
        applyCourseColorToEvents(updated);
        return updated;
    }

    /**
     * Returns a course with all event data resolved (for progress calculation).
     */
    public Course getCourseWithProgress(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        List<CalendarEvent> events = eventRepository.findAllById(course.getEventIds());
        course.setResolvedEvents(events);
        return course;
    }

    /**
     * Returns all courses for a user with progress information.
     */
    public List<Course> getCoursesWithProgress(String userEmail) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null) throw new RuntimeException("User not found");

        List<Course> courses = courseRepository.findByUser(user);
        for (Course course : courses) {
            List<CalendarEvent> events = eventRepository.findAllById(course.getEventIds());
            course.setResolvedEvents(events);
            // Calculate progress in percent (value is dynamically provided via getProgressPercent())
            double progressPercent = course.getProgressPercent();
            // Optional: If further processing/logging is needed, it can be accessed here.
        }
        return courses;
    }

    /**
     * Returns course details including events for a given course ID and user.
     */
    public Course getCourseDetails(Long courseId, String userEmail) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        if (!course.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        List<CalendarEvent> events = eventRepository.findAllById(course.getEventIds());
        course.setResolvedEvents(events);

        // These are now available via getters for the sidebar/modal:
        // course.getProgressPercent(), course.getSelfStudyHours(), course.getWorkloadTarget(), etc.

        return course;
    }

    public Optional<Course> getById(Long id) {
        return courseRepository.findById(id);
    }

    /**
     * Deletes a course and all associated events.
     */
    @Transactional
    public void deleteCourse(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Delete all associated events
        List<Long> eventIds = course.getEventIds();
        if (eventIds != null && !eventIds.isEmpty()) {
            eventRepository.deleteAllById(eventIds);
        }

        // Then delete the course itself
        courseRepository.deleteById(id);
    }

    /**
     * Applies the course color to all associated events.
     */
    private void applyCourseColorToEvents(Course course) {
        List<Long> eventIds = course.getEventIds();
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }
        List<CalendarEvent> events = eventRepository.findAllById(eventIds);
        for (CalendarEvent event : events) {
            event.setColor(course.getColor());
        }
        eventRepository.saveAll(events);
    }

    /**
     * Updates a course and its events if it belongs to the user.
     */
    @Transactional
    public Course updateCourse(Long id, String name, String color, String userEmail) {
        // Find the course
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Check if the course belongs to the user
        User user = userRepository.findByEmail(userEmail);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        if (!course.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        // Update fields
        course.setName(name);
        course.setColor(color);

        // Save course
        Course savedCourse = courseRepository.save(course);

        // Set course color to all events
        applyCourseColorToEvents(savedCourse);
        return savedCourse;
    }

    /**
     * Returns all events for a course, with user verification.
     */
    public List<CalendarEvent> getCourseEvents(Long courseId, String userEmail) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null) throw new RuntimeException("User not found");

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        if (!course.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        return eventRepository.findAllById(course.getEventIds());
    }

    /**
     * Removes an event from a course (with user verification).
     */
    @Transactional
    public void removeEventFromCourse(Long courseId, Long eventId, String userEmail) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null) throw new RuntimeException("User not found");

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        if (!course.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        course.removeEventId(eventId);
        courseRepository.save(course);
    }

    /**
     * Adds a new event to a course, creating the event and linking it to the course.
     */
    @Transactional
    public void addEventToCourse(Long courseId, String title, String description, String type, String color,
                                 LocalDateTime startTime, LocalDateTime endTime, String userEmail) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null) throw new RuntimeException("User not found");

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        if (!course.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        // Create new event
        CalendarEvent event = new CalendarEvent();
        event.setTitle(title);
        event.setDescription(description);
        event.setType(type);
        event.setColor(color);
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setUserId(user.getId());

        // Save event
        CalendarEvent savedEvent = eventRepository.save(event);

        // Add event ID to course
        course.addEventId(savedEvent.getId());
        courseRepository.save(course);

        // Optional: Apply color to all course events (can be done as needed)
        applyCourseColorToEvents(course);
    }

    /**
     * Automatically plans self-study sessions for a deadline, distributing sessions in available slots.
     * Study hours are distributed as evenly as possible between studyStart and deadline, respecting preferred study days, session duration, and break time.
     */
    @Transactional
    public void autoPlanSelfstudySessions(Long courseId, Long deadlineId, String userEmail) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null) throw new RuntimeException("User not found");
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        if (!course.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        List<CalendarEvent> events = eventRepository.findAllById(course.getEventIds());
        CalendarEvent deadline = eventRepository.findById(deadlineId)
                .orElseThrow(() -> new RuntimeException("Deadline event not found"));
        if (!deadline.isDeadline()) {
            throw new RuntimeException("Event is not a deadline");
        }
        int targetHours = deadline.getStudyTimeNeeded();
        if (targetHours <= 0) throw new RuntimeException("No study time set for this deadline");
        List<CalendarEvent> allUserEvents = eventRepository.findByUserId(user.getId());
        LocalDateTime studyStart = deadline.getStudyStart() != null ? deadline.getStudyStart() : LocalDateTime.now();
        LocalDateTime studyEnd = deadline.getStartTime();

        // Exclude the deadline day itself from planning
        studyEnd = studyEnd.toLocalDate().atStartOfDay();

        // Parse break duration
        java.time.Duration breakDuration = java.time.Duration.ZERO;
        try {
            String[] parts = user.getPreferredBreakTime().split(":");
            breakDuration = java.time.Duration.ofHours(Long.parseLong(parts[0])).plusMinutes(Long.parseLong(parts[1]));
        } catch (Exception e) {
            breakDuration = java.time.Duration.ofMinutes(10); // fallback
        }
        // Parse session duration (now string "hh:mm")
        java.time.Duration sessionDuration;
        try {
            String[] parts = user.getPreferredStudySessionDuration().split(":");
            sessionDuration = java.time.Duration.ofHours(Long.parseLong(parts[0])).plusMinutes(Long.parseLong(parts[1]));
            if (sessionDuration.isZero() || sessionDuration.isNegative()) sessionDuration = java.time.Duration.ofHours(1);
        } catch (Exception e) {
            sessionDuration = java.time.Duration.ofHours(1);
        }

        // 1. Collect all possible study slots between studyStart and studyEnd that match preferredStudyDays
        Set<DayOfWeek> preferredDays = Arrays.stream(user.getPreferredStudyDays().split(","))
            .map(String::trim)
            .map(String::toUpperCase)
            .map(DayOfWeek::valueOf)
            .collect(Collectors.toSet());

        LocalTime preferredStart = LocalTime.parse(user.getPreferredStartTime());
        LocalTime preferredEnd = LocalTime.parse(user.getPreferredEndTime());

        // Build a list of all possible session slots (start, end) on preferred days, respecting session duration and break
        List<TimeSlot> possibleSlots = new ArrayList<>();
        LocalDateTime current = studyStart.withHour(preferredStart.getHour()).withMinute(preferredStart.getMinute()).withSecond(0).withNano(0);

        while (!current.isBefore(studyEnd)) {
            // This loop will not run if studyStart >= studyEnd, so fix the condition:
            break;
        }
        while (current.isBefore(studyEnd)) {
            if (preferredDays.contains(current.getDayOfWeek())) {
                LocalDateTime dayStart = current.withHour(preferredStart.getHour()).withMinute(preferredStart.getMinute());
                LocalDateTime dayEnd = current.withHour(preferredEnd.getHour()).withMinute(preferredEnd.getMinute());
                // Make sure dayEnd does not exceed studyEnd
                if (dayEnd.isAfter(studyEnd)) {
                    dayEnd = studyEnd;
                }
                LocalDateTime slotStart = dayStart.isAfter(current) ? dayStart : current;
                while (!slotStart.plus(sessionDuration).isAfter(dayEnd) && !slotStart.plus(sessionDuration).isAfter(studyEnd)) {
                    // Check for overlap with existing events
                    boolean overlaps = false;
                    for (CalendarEvent e : allUserEvents) {
                        if (!(slotStart.plus(sessionDuration).isBefore(e.getStartTime()) || slotStart.isAfter(e.getEndTime()))) {
                            overlaps = true;
                            break;
                        }
                    }
                    if (!overlaps) {
                        possibleSlots.add(new TimeSlot(slotStart, slotStart.plus(sessionDuration)));
                    }
                    slotStart = slotStart.plus(sessionDuration).plus(breakDuration);
                }
            }
            current = current.plusDays(1).withHour(preferredStart.getHour()).withMinute(preferredStart.getMinute());
        }

        if (possibleSlots.isEmpty()) throw new RuntimeException("No available time slots for self-study sessions.");

        // 2. Distribute study hours as evenly as possible over the available slots (spread across the whole period)
        long totalMinutes = targetHours * 60L;
        long sessionMinutes = sessionDuration.toMinutes();
        int sessionsNeeded = (int) Math.ceil(totalMinutes / (double) sessionMinutes);
        int slotsToUse = Math.min(sessionsNeeded, possibleSlots.size());

        // Calculate indices for evenly distributed sessions
        List<Integer> slotIndices = new ArrayList<>();
        for (int i = 0; i < slotsToUse; i++) {
            int idx = (int) Math.round(i * (possibleSlots.size() - 1) / (double) (slotsToUse - 1));
            slotIndices.add(idx);
        }
        // If only one session, just pick the first slot
        if (slotsToUse == 1) slotIndices.set(0, 0);

        // Distribute hours as evenly as possible
        List<Long> sessionMinutesList = new ArrayList<>();
        long base = totalMinutes / slotsToUse;
        long remainder = totalMinutes % slotsToUse;
        for (int i = 0; i < slotsToUse; i++) {
            sessionMinutesList.add(base + (i < remainder ? 1 : 0));
        }

        // 3. Create events for each planned session, using the selected slots
        for (int i = 0; i < slotsToUse; i++) {
            long durationMin = sessionMinutesList.get(i);
            TimeSlot slot = possibleSlots.get(slotIndices.get(i));
            LocalDateTime sessionStart = slot.start();
            LocalDateTime sessionEnd = sessionStart.plusMinutes(durationMin);
            if (sessionEnd.isAfter(slot.end())) {
                sessionEnd = slot.end();
                durationMin = java.time.Duration.between(sessionStart, sessionEnd).toMinutes();
                if (durationMin <= 0) continue;
            }
            if (sessionEnd.isAfter(studyEnd)) {
                sessionEnd = studyEnd;
                durationMin = java.time.Duration.between(sessionStart, sessionEnd).toMinutes();
                if (durationMin <= 0) continue;
            }
            CalendarEvent session = new CalendarEvent();
            session.setTitle("📖 Selfstudy for " + deadline.getTitle() + ", " + course.getName());
            session.setStartTime(sessionStart);
            session.setEndTime(sessionEnd);
            session.setUserId(user.getId());
            session.setColor(course.getColor());
            session.setType("self-study");
            session.setDescription("Auto-planned session");
            session.setCourseId(course.getId().toString());
            session.setGeneratedByEngine(true);
            session.setRelatedDeadlineId(deadlineId);
            CalendarEvent saved = eventRepository.save(session);

            if (!course.getEventIds().contains(saved.getId())) {
                course.getEventIds().add(saved.getId());
            }
        }
        courseRepository.save(course);
    }

    /**
     * Returns all deadlines for a course for the logged-in user.
     * Only events with isDeadline == true and matching courseId.
     */
    public List<CalendarEvent> getDeadlines(Long courseId, String userEmail) {
        Course course = getCourseDetails(courseId, userEmail);
        String courseIdStr = String.valueOf(courseId);
        return course.getResolvedEvents().stream()
            .filter(e -> e.isDeadline() && e.getCourseId() != null && e.getCourseId().equals(courseIdStr))
            .toList();
    }

    /**
     * Returns progress information for a course as a map.
     */
    public Map<String, Object> getProgressInfo(Long courseId, String userEmail) {
        Course course = getCourseDetails(courseId, userEmail);
        Map<String, Object> map = new HashMap<>();
        map.put("progressPercent", course.getProgressPercent());
        map.put("selfStudyHours", course.getSelfStudyHours());
        map.put("workloadTarget", course.getWorkloadTarget());
        return map;
    }

    /**
     * Adds a manual self-study session to a course.
     */
    @Transactional
    public void addManualSelfstudySession(Long courseId, String title, String description, String color,
                                          LocalDateTime startTime, LocalDateTime endTime, String userEmail) {
        User user = userRepository.findByEmail(userEmail);
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new RuntimeException("Course not found"));
        if (!course.getUser().getId().equals(user.getId())) throw new RuntimeException("Access denied");

        CalendarEvent event = new CalendarEvent();
        event.setTitle(title);
        event.setDescription(description);
        event.setType("self-study");
        event.setColor(color);
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setUserId(user.getId());
        event.setCourseId(course.getId().toString());
        event.setGeneratedByEngine(false);

        CalendarEvent saved = eventRepository.save(event);

        // Ensure the event ID is added to the course_event_ids join table
        if (!course.getEventIds().contains(saved.getId())) {
            course.getEventIds().add(saved.getId());
        }

        courseRepository.save(course);
    }

    /**
     * Returns all deadlines for a course as a reduced map list for tables.
     * Filters by courseId (as String) and isDeadline == true.
     */
    public List<Map<String, Object>> getDeadlinesForTable(Long courseId, String userEmail) {
        Course course = getCourseDetails(courseId, userEmail);
        String courseIdStr = String.valueOf(courseId);
        return course.getResolvedEvents().stream()
            .filter(e -> e.isDeadline() && (
                // Accept both numeric and string courseId for robustness
                courseIdStr.equals(e.getCourseId()) || 
                (e.getCourseId() != null && e.getCourseId().equals(course.getCourseIdentifier()))
            ))
            .map(e -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", e.getId());
                map.put("title", e.getTitle());
                map.put("startTime", e.getStartTime() != null ? e.getStartTime().toString() : "");
                map.put("studyTimeNeeded", e.getStudyTimeNeeded());
                return map;
            })
            .toList();
    }

    /**
     * Deletes all self-study sessions associated with a specific deadline.
     */
    @Transactional
    public void deleteSelfStudySessionsForDeadline(Long deadlineId) {
        List<CalendarEvent> toDelete = eventRepository.findAll().stream()
            .filter(e -> "self-study".equalsIgnoreCase(e.getType()))
            .filter(e -> e.getRelatedDeadlineId() != null && e.getRelatedDeadlineId().equals(deadlineId))
            .toList();

        for (CalendarEvent ev : toDelete) {
            eventRepository.deleteById(ev.getId());
        }
    }
}