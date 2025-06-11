package com.studyflow.studyplanner.controller;

import com.studyflow.studyplanner.model.CalendarEvent;
import com.studyflow.studyplanner.model.Course;
import com.studyflow.studyplanner.model.User;
import com.studyflow.studyplanner.service.CalendarService;
import com.studyflow.studyplanner.service.CourseService;
import com.studyflow.studyplanner.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/deadlines")
    public String showDeadlinesPage(@RequestParam("courseId") Long courseId, Principal principal, Model model) {
        String email = principal.getName();
        Course course = courseService.getCourseDetails(courseId, email);
        List<CalendarEvent> deadlines = courseService.getDeadlines(courseId, email);

        model.addAttribute("course", course);
        model.addAttribute("deadlines", deadlines);
        return "deadlines";
    }

    @GetMapping("/selfstudy")
    public String showSelfstudyPage(@RequestParam("courseId") Long courseId, Principal principal, Model model) {
        String email = principal.getName();
        Course course = courseService.getCourseDetails(courseId, email);
        List<CalendarEvent> selfstudyEvents = calendarService.getUserEventsByType(course.getUser().getId(), "selfstudy");

        model.addAttribute("course", course);
        model.addAttribute("selfstudyEvents", selfstudyEvents);
        return "selfstudy";
    }

    @GetMapping("/api/courses/{id}/deadlines")
    @ResponseBody
    public List<CalendarEvent> getCourseDeadlines(@PathVariable Long id, Principal principal) {
        String email = principal.getName();
        return courseService.getDeadlines(id, email);
    }

    @GetMapping("/api/courses/{id}/selfstudy")
    @ResponseBody
    public List<CalendarEvent> getSelfstudyEvents(@PathVariable Long id, Principal principal) {
        String email = principal.getName();
        Course course = courseService.getCourseDetails(id, email);
        return calendarService.getUserEventsByType(course.getUser().getId(), "selfstudy");
    }
}
