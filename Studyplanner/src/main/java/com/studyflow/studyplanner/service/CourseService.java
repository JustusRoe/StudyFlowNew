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
    public Course createCourse(String name, String color, String userEmail) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null) throw new RuntimeException("User not found");

        Course course = new Course(name, color, user);
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
        }
        return courses;
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
}
