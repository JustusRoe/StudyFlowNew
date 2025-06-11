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
import java.util.List;

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
    public List<CalendarEvent> getCourseDeadlines(@PathVariable Long id, Principal principal) {
        String email = principal.getName();
        return courseService.getDeadlines(id, email);
    }

    @PostMapping("/calendar/create")
    @ResponseBody
    public CalendarEvent createDeadline(@RequestBody CalendarEvent event, Principal principal) {
        String email = principal.getName();
        User user = userRepository.findByEmail(email);
if (user == null) {
    throw new RuntimeException("User not found");
}
event.setUserId(user.getId());
        event.setDeadline(true);
        return calendarService.saveEvent(event);
    }

    @PostMapping("/calendar/update/{id}")
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

    @DeleteMapping("/calendar/delete/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteDeadline(@PathVariable Long id) {
        calendarService.deleteEvent(id);
        return ResponseEntity.ok().build();
    }
}
