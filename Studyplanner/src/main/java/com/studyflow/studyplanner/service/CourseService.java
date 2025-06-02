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
import java.util.*;

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
     * Erstellt einen neuen Kurs und speichert ihn in der Datenbank.
     */
    @Transactional
    public Course createCourse(String name, String description, String color, String userEmail, String courseIdentifier) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null) throw new RuntimeException("User not found");

        Course course = new Course(name, color, user);
        course.setDescription(description); // Set description here
        course.setCourseIdentifier(courseIdentifier);
        return courseRepository.save(course);
    }

    /**
     * Fügt einem Kurs ein Event hinzu (per ID).
     */
    @Transactional
    public Course addEventToCourse(Long courseId, Long eventId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        CalendarEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        // Setze Event-Farbe auf Kursfarbe
        event.setColor(course.getColor());
        eventRepository.save(event);

        // Event-ID zum Kurs hinzufügen und alle Events einfärben
        course.addEventId(eventId);
        Course updated = courseRepository.save(course);
        applyCourseColorToEvents(updated);
        return updated;
    }

    /**
     * Liefert einen Kurs mit vollständigen Eventdaten (für Progress-Berechnung).
     */
    public Course getCourseWithProgress(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        List<CalendarEvent> events = eventRepository.findAllById(course.getEventIds());
        course.setResolvedEvents(events);
        return course;
    }

    /**
     * Gibt alle Kurse eines Users mit vollständigem Fortschritt zurück.
     */
    public List<Course> getCoursesWithProgress(String userEmail) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null) throw new RuntimeException("User not found");

        List<Course> courses = courseRepository.findByUser(user);
        for (Course course : courses) {
            List<CalendarEvent> events = eventRepository.findAllById(course.getEventIds());
            course.setResolvedEvents(events);
            // Fortschritt in Prozent berechnen (Wert wird dynamisch über getProgressPercent() bereitgestellt)
            double progressPercent = course.getProgressPercent();
            // Optional: Wenn weitere Verarbeitung/Logging nötig ist, kann hier darauf zugegriffen werden.
        }
        return courses;
    }

    /**
     * Holt die Kursdetails inkl. Events für ein bestimmtes Course-Id und den zugehörigen Benutzer.
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

        return course;
    }

    public Optional<Course> getById(Long id) {
        return courseRepository.findById(id);
    }

    public void deleteCourse(Long id) {
        courseRepository.deleteById(id);
    }

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
     * Aktualisiert einen Kurs und seine Events, sofern er dem Benutzer gehört.
     */
    @Transactional
    public Course updateCourse(Long id, String name, String description, String color, String userEmail) {
        // Finde den Kurs
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Prüfe, ob der Kurs dem Benutzer gehört
        User user = userRepository.findByEmail(userEmail);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        if (!course.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        // Aktualisiere Felder
        course.setName(name);
        course.setDescription(description);
        course.setColor(color);

        // Speichere Kurs
        Course savedCourse = courseRepository.save(course);

        // Setze Kursfarbe auf alle Events
        applyCourseColorToEvents(savedCourse);
        return savedCourse;
    }

    /**
     * Gibt alle Events eines Kurses zurück, mit Benutzerüberprüfung.
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
     * Entfernt ein Event aus einem Kurs (mit Benutzerüberprüfung).
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

    // Neues Event anlegen
    CalendarEvent event = new CalendarEvent();
    event.setTitle(title);
    event.setDescription(description);
    event.setType(type);
    event.setColor(color);
    event.setStartTime(startTime);
    event.setEndTime(endTime);
    event.setUserId(user.getId());

    // Event speichern
    CalendarEvent savedEvent = eventRepository.save(event);

    // Event-ID zum Kurs hinzufügen
    course.addEventId(savedEvent.getId());
    courseRepository.save(course);

    // Optional: Farbe auf alle Events des Kurses anwenden (kann man je nach Bedarf machen)
    applyCourseColorToEvents(course);
}}