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
     * Gibt nur die Kursnamen des eingeloggten Nutzers zurück.
     */
    @GetMapping("/user")
    public List<Map<String, String>> getUserCourseNames(Principal principal) {
        String email = principal.getName();
        List<Course> courses = courseService.getCoursesWithProgress(email);

        List<Map<String, String>> result = new ArrayList<>();
        for (Course course : courses) {
            Map<String, String> item = new HashMap<>();
            item.put("name", course.getName());
            result.add(item);
        }
        return result;
    }
}