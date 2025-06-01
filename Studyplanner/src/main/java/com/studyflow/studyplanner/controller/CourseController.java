package com.studyflow.studyplanner.controller;

import com.studyflow.studyplanner.model.Course;
import com.studyflow.studyplanner.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
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
    public Course createCourse(@RequestBody Course course, Principal principal) {
        String email = principal.getName();
        return courseService.createCourse(course.getName(), course.getColor(), email);
    }

    /**
     * Aktualisiert einen bestehenden Kurs anhand der ID.
     */
    @PostMapping("/update/{id}")
    public Course updateCourse(@PathVariable Long id, @RequestBody Map<String, String> updates, Principal principal) {
        String email = principal.getName();
        String name = updates.get("name");
        String description = updates.get("description");
        String color = updates.get("color");
        return courseService.updateCourse(id, name, description, color, email);
    }

    /**
     * Gibt für den eingeloggten Nutzer alle Kurse mit Details und Fortschritt zurück.
     */
    @GetMapping("/user")
    public List<Map<String, Object>> getUserCourses(Principal principal) {
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
        return result;
    }

    /**
     * Gibt Kursdetails anhand der Kurs-ID zurück.
     */
    @GetMapping("/description/{id}")
    public Course getDescription(@PathVariable Long id, Principal principal) {
        String email = principal.getName();
        return courseService.getCourseDetails(id, email);
    }
}