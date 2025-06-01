package com.studyflow.studyplanner.controller;

import com.studyflow.studyplanner.model.Course;
import com.studyflow.studyplanner.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

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
        Course created = courseService.createCourse(course.getName(), course.getDescription(), course.getColor(), email);
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
}