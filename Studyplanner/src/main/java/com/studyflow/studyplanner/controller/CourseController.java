package com.studyflow.studyplanner.controller;

import com.studyflow.studyplanner.model.Course;
import com.studyflow.studyplanner.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/courses")
public class CourseController {

    private final CourseService courseService;

    @Autowired
    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    /**
     * Erstellt einen neuen Kurs für den eingeloggten Nutzer.
     */
    @PostMapping("/create")
    public ResponseEntity<Course> createCourse(@RequestBody Course course, Principal principal) {
        String email = principal.getName();
        Course created = courseService.createCourse(
            course.getName(),
            course.getDescription(),
            course.getColor(),
            email,
            course.getCourseIdentifier(),
            course.getDifficulty()
        );
        return ResponseEntity.ok(created);
    }

    /**
     * Aktualisiert einen bestehenden Kurs anhand der ID.
     */
    @PostMapping("/update/{id}")
    public ResponseEntity<?> updateCourse(@PathVariable Long id, @RequestBody Map<String, String> updates, Principal principal) {
        try {
            String email = principal.getName();
            String name = updates.get("name");
            String description = updates.get("description");
            String color = updates.get("color");

            Course updated = courseService.updateCourse(id, name, description, color, email);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Update failed: " + e.getMessage());
        }
    }

    /**
     * Gibt für den eingeloggten Nutzer alle Kurse mit Details und Fortschritt zurück.
     */
    @GetMapping("/user")
    public ResponseEntity<List<Map<String, Object>>> getUserCourses(Principal principal) {
        String email = principal.getName();
        List<Course> courses = courseService.getCoursesWithProgress(email);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Course course : courses) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", course.getId());
            item.put("name", course.getName());
            item.put("description", course.getDescription());
            item.put("color", course.getColor());
            item.put("progressPercent", course.getProgressPercent());
            item.put("courseIdentifier", course.getCourseIdentifier());
            item.put("difficulty", course.getDifficulty());
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Gibt Kursdetails anhand der Kurs-ID zurück.
     */
    @GetMapping("/description/{id}")
    public ResponseEntity<Course> getDescription(@PathVariable Long id, Principal principal) {
        String email = principal.getName();
        Course course = courseService.getCourseDetails(id, email);
        if (course == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(course);
    }

    /**
     * Löscht einen Kurs anhand der ID, wenn er dem eingeloggten Nutzer gehört.
     */
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id, Principal principal) {
        try {
            courseService.deleteCourse(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Delete failed: " + e.getMessage());
        }
    }

    /**
     * Gibt alle Kalender-Events zurück, die einem Kurs zugeordnet sind.
     */
    @GetMapping("/events/{courseId}")
    public ResponseEntity<?> getCourseEvents(@PathVariable Long courseId, Principal principal) {
        try {
            String email = principal.getName();
            return ResponseEntity.ok(courseService.getCourseEvents(courseId, email));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Could not load course events: " + e.getMessage());
        }
    }

    /**
     * Entfernt ein Kalender-Event aus einem Kurs.
     */
    @PostMapping("/events/remove")
    public ResponseEntity<?> removeEventFromCourse(@RequestBody Map<String, Long> request, Principal principal) {
        try {
            Long courseId = request.get("courseId");
            Long eventId = request.get("eventId");
            String email = principal.getName();
            courseService.removeEventFromCourse(courseId, eventId, email);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Could not remove event: " + e.getMessage());
        }
    }

    /**
     * Fügt einem Kurs ein neues Kalender-Event hinzu.
     */
    @PostMapping("/events/add")
    public ResponseEntity<?> addEventToCourse(@RequestBody Map<String, String> request, Principal principal) {
        try {
            String title = request.get("title");
            String description = request.get("description");
            String type = request.get("type");
            String color = request.get("color");
            Long courseId = Long.parseLong(request.get("courseId"));

            LocalDateTime startTime = LocalDateTime.parse(request.get("startTime"));
            LocalDateTime endTime = LocalDateTime.parse(request.get("endTime"));

            String email = principal.getName();
            courseService.addEventToCourse(courseId, title, description, type, color, startTime, endTime, email);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Could not add event: " + e.getMessage());
        }
    }

    /**
     * Führt die automatische Planung von Selfstudy-Sessions für eine Deadline durch.
     */
    @PostMapping("/{id}/autoplan")
    public ResponseEntity<?> autoPlanSelfstudy(@PathVariable Long id,
                                               @RequestParam Long deadlineId,
                                               Principal principal) {
        try {
            String email = principal.getName();
            courseService.autoPlanSelfstudySessions(id, deadlineId, email);
            return ResponseEntity.ok("Planning complete");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Auto planning failed: " + e.getMessage());
        }
    }

    /**
     * Gibt alle Deadline-Events eines Kurses zurück.
     */
    @GetMapping("/{id}/deadlines")
    public ResponseEntity<?> getDeadlines(@PathVariable Long id, Principal principal) {
        try {
            String email = principal.getName();
            return ResponseEntity.ok(courseService.getDeadlines(id, email));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Could not load deadlines: " + e.getMessage());
        }
    }

    /**
     * Fügt eine manuelle Selfstudy-Session zu einem Kurs hinzu.
     */
    @PostMapping("/{id}/add-selfstudy")
    public ResponseEntity<?> addSelfstudy(@PathVariable Long id,
                                          @RequestBody Map<String, String> body,
                                          Principal principal) {
        try {
            String email = principal.getName();
            String title = body.get("title");
            String description = body.get("description");
            String color = body.get("color");
            String startTimeStr = body.get("startTime");
            String endTimeStr = body.get("endTime");

            LocalDateTime startTime = LocalDateTime.parse(startTimeStr);
            LocalDateTime endTime = LocalDateTime.parse(endTimeStr);

            courseService.addManualSelfstudySession(id, title, description, color, startTime, endTime, email);
            return ResponseEntity.ok("Selfstudy session added.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Could not add selfstudy session: " + e.getMessage());
        }
    }

    /**
     * Gibt Fortschrittsinformationen zu einem Kurs zurück.
     */
    @GetMapping("/{id}/progress")
    public ResponseEntity<?> getProgress(@PathVariable Long id, Principal principal) {
        try {
            String email = principal.getName();
            return ResponseEntity.ok(courseService.getProgressInfo(id, email));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Could not load progress: " + e.getMessage());
        }
    }}