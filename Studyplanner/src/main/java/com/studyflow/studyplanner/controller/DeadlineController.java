package com.studyflow.studyplanner.controller;

import com.studyflow.studyplanner.model.CalendarEvent;
import com.studyflow.studyplanner.model.Course;
import com.studyflow.studyplanner.model.User;
import com.studyflow.studyplanner.repository.UserRepository;
import com.studyflow.studyplanner.service.CalendarService;
import com.studyflow.studyplanner.service.CourseService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DeadlineController {

    private final CourseService courseService;
    private final CalendarService calendarService;
    private final UserRepository userRepository;

    @Autowired
    public DeadlineController(CourseService courseService, CalendarService calendarService, UserRepository userRepository) {
        this.courseService = courseService;
        this.calendarService = calendarService;
        this.userRepository = userRepository;
    }

    // Serve the manage-deadlines page (HTML)
    @GetMapping("/manage-deadlines")
    public String showManageDeadlinesPage(@RequestParam(value = "courseId", required = false) Long courseId, Principal principal, Model model) {
        if (courseId == null) {
            // No course selected, just render the page with course selection
            return "manage-deadlines";
        }
        String email = principal.getName();
        Course course = courseService.getCourseDetails(courseId, email);
        List<CalendarEvent> deadlines = courseService.getDeadlines(courseId, email);

        model.addAttribute("course", course);
        model.addAttribute("deadlines", deadlines);
        return "manage-deadlines";
    }

    @GetMapping("/api/courses/{id}/deadlines")
    @ResponseBody
    public List<Map<String, Object>> getCourseDeadlines(@PathVariable Long id, Principal principal) {
        String email = principal.getName();
        // Nutze die Service-Methode, die alle Deadlines für den Kurs liefert
        List<CalendarEvent> deadlines = courseService.getDeadlines(id, email);
        return deadlines.stream()
            .map(ev -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", ev.getId());
                map.put("title", ev.getTitle());
                map.put("startTime", ev.getStartTime() != null ? ev.getStartTime().toString() : "");
                map.put("points", ev.getPoints());
                return map;
            })
            .toList();
    }

    @PostMapping("/deadlines/create")
    @ResponseBody
    public CalendarEvent createDeadline(@RequestBody CalendarEvent event, @RequestParam Long courseId, Principal principal) {
        String email = principal.getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        event.setUserId(user.getId());
        event.setDeadline(true);
        event.setCourseId(String.valueOf(courseId));
        CalendarEvent saved = calendarService.saveEvent(event);

        // Event dem Kurs hinzufügen!
        courseService.addEventToCourse(courseId, saved.getId());

        return saved;
    }

    @PostMapping("/deadlines/update/{id}")
    @ResponseBody
    public CalendarEvent updateDeadline(@PathVariable Long id, @RequestBody CalendarEvent updated, Principal principal) {
        CalendarEvent existing = calendarService.findById(id);
        existing.setTitle(updated.getTitle());
        existing.setStartTime(updated.getStartTime());
        existing.setEndTime(updated.getEndTime());
        existing.setPoints(updated.getPoints());
        existing.setColor(updated.getColor());
        existing.setType(updated.getType());
        existing.setDescription(updated.getDescription());
        return calendarService.saveEvent(existing);
    }

    @DeleteMapping("/deadlines/delete/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteDeadline(@PathVariable Long id) {
        calendarService.deleteEvent(id);
        return ResponseEntity.ok().build();
    }

   
}
