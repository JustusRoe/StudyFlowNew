package com.studyflow.studyplanner.service;

import com.studyflow.studyplanner.model.CalendarEvent;
import com.studyflow.studyplanner.model.Course;
import com.studyflow.studyplanner.model.User;
import com.studyflow.studyplanner.repository.CalendarEventRepository;
import com.studyflow.studyplanner.repository.CourseRepository;
import com.studyflow.studyplanner.repository.UserRepository;
import jakarta.transaction.Transactional;
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
    private List<TimeSlot> calculateFreeSlots(User user, List<CalendarEvent> existingEvents, LocalDateTime from, LocalDateTime to) {
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
                    slotStart = e.getEndTime().isAfter(slotStart) ? e.getEndTime() : slotStart;
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

        int workloadTotal = switch (course.getDifficulty()) {
            case 1 -> 100; 
            case 2 -> 130; 
            case 3 -> 160;
            default -> 130;
        };

        int lectureHours = 33;
        int remainingWorkload = workloadTotal - lectureHours;

        // Total points for deadlines are always 120
        int totalPoints = 120;

        double share = (double) deadline.getPoints() / totalPoints;
        int targetHours = (int) Math.round(remainingWorkload * share);

        List<CalendarEvent> allUserEvents = eventRepository.findByUserId(user.getId());

        // --- Adjustment: Consider StudyStart ---
        LocalDateTime studyStart = deadline.getStudyStart() != null ? deadline.getStudyStart() : LocalDateTime.now();
        LocalDateTime studyEnd = deadline.getStartTime();

        // All free slots in the period [studyStart, studyEnd)
        List<TimeSlot> freeSlots = calculateFreeSlots(user, allUserEvents, studyStart, studyEnd.minusMinutes(1));

        // Distribute sessions evenly
        int sessionsNeeded = (int) Math.ceil(targetHours / 1.0); // max 1h per session
        int hoursLeft = targetHours;
        int planned = 0;

        // Flat list of all possible hour slots in the free time windows
        List<LocalDateTime> possibleStarts = new ArrayList<>();
        for (TimeSlot slot : freeSlots) {
            LocalDateTime slotStart = slot.start();
            while (slotStart.plusHours(1).isBefore(slot.end()) || slotStart.plusHours(1).equals(slot.end())) {
                if (!slotStart.plusHours(1).isAfter(studyEnd)) {
                    possibleStarts.add(slotStart);
                }
                slotStart = slotStart.plusHours(1);
            }
        }

        // Distribute evenly
        int interval = possibleStarts.size() >= sessionsNeeded ? possibleStarts.size() / sessionsNeeded : 1;
        int used = 0;
        for (int i = 0; i < possibleStarts.size() && planned < targetHours; i += interval) {
            LocalDateTime sessionStart = possibleStarts.get(i);
            int duration = Math.min(2, targetHours - planned);
            LocalDateTime sessionEnd = sessionStart.plusHours(duration);
            if (sessionEnd.isAfter(studyEnd)) {
                sessionEnd = studyEnd;
                duration = (int) java.time.Duration.between(sessionStart, sessionEnd).toHours();
                if (duration <= 0) continue;
            }

            CalendarEvent session = new CalendarEvent();
            session.setTitle("📖 Selfstudy for " + deadline.getTitle());
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
            course.addEventId(saved.getId());

            planned += duration;
            used++;
            if (used >= sessionsNeeded) break;
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
        course.addEventId(saved.getId());
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
                map.put("points", e.getPoints());
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