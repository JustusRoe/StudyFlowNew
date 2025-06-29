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

    /**
     * Serves the manage-deadlines page (HTML).
     * If no courseId is provided, renders the page with course selection only.
     * If courseId is provided, adds course and deadline data to the model.
     */
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

    /**
     * Returns all deadlines for a given course as a list of maps (API).
     */
    @GetMapping("/api/courses/{id}/deadlines")
    @ResponseBody
    public List<Map<String, Object>> getCourseDeadlines(@PathVariable Long id, Principal principal) {
        String email = principal.getName();
        List<CalendarEvent> deadlines = courseService.getDeadlines(id, email);
        return deadlines.stream()
            .map(ev -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", ev.getId());
                map.put("title", ev.getTitle());
                map.put("startTime", ev.getStartTime() != null ? ev.getStartTime().toString() : "");
                map.put("studyTimeNeeded", ev.getStudyTimeNeeded());
                return map;
            })
            .toList();
    }

    /**
     * Creates a new deadline event for a course.
     */
    @PostMapping("/deadlines/create")
    @ResponseBody
    public CalendarEvent createDeadline(@RequestBody Map<String, Object> payload, @RequestParam Long courseId, Principal principal) {
        String email = principal.getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        CalendarEvent event = new CalendarEvent();
        event.setTitle((String) payload.get("title"));
        event.setStartTime(java.time.LocalDateTime.parse((String) payload.get("startTime")));
        event.setEndTime(java.time.LocalDateTime.parse((String) payload.get("endTime")));
        event.setType("deadline");
        event.setDeadline(true);
        // Use studyTimeNeeded from payload
        Object studyTimeObj = payload.get("studyTimeNeeded");
        int studyTimeNeeded = 0;
        if (studyTimeObj instanceof Integer) {
            studyTimeNeeded = (Integer) studyTimeObj;
        } else if (studyTimeObj instanceof String) {
            try {
                studyTimeNeeded = Integer.parseInt((String) studyTimeObj);
            } catch (Exception ignored) {}
        }
        event.setStudyTimeNeeded(studyTimeNeeded);
        event.setUserId(user.getId());
        event.setCourseId(String.valueOf(courseId));
        // Set studyStart if provided
        if (payload.get("studyStart") != null) {
            event.setStudyStart(java.time.LocalDateTime.parse((String) payload.get("studyStart")));
        }
        // Set course color
        Course course = courseService.getById(courseId).orElseThrow(() -> new RuntimeException("Course not found"));
        event.setColor(course.getColor());
        CalendarEvent saved = calendarService.saveEvent(event);

        // Add event to course
        courseService.addEventToCourse(courseId, saved.getId());

        return saved;
    }

    /**
     * Updates an existing deadline event.
     */
    @PostMapping("/deadlines/update/{id}")
    @ResponseBody
    public CalendarEvent updateDeadline(@PathVariable Long id, @RequestBody CalendarEvent updated, Principal principal) {
        CalendarEvent existing = calendarService.findById(id);
        existing.setTitle(updated.getTitle());
        existing.setStartTime(updated.getStartTime());
        existing.setEndTime(updated.getEndTime());
        existing.setStudyTimeNeeded(updated.getStudyTimeNeeded());
        existing.setColor(updated.getColor());
        existing.setType(updated.getType());
        existing.setDescription(updated.getDescription());
        return calendarService.saveEvent(existing);
    }

    /**
     * Deletes a deadline event and all associated self-study sessions.
     */
    @DeleteMapping("/deadlines/delete/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteDeadline(@PathVariable Long id) {
        // First delete all associated self-study sessions
        courseService.deleteSelfStudySessionsForDeadline(id);
        calendarService.deleteEvent(id);
        return ResponseEntity.ok().build();
    }

    // --- Selfstudy API ---

    /**
     * Returns all self-study sessions for a course.
     */
    @GetMapping("/api/courses/{courseId}/selfstudy")
    @ResponseBody
    public List<Map<String, Object>> getSelfstudySessions(@PathVariable Long courseId, Principal principal) {
        String email = principal.getName();
        List<CalendarEvent> events = courseService.getCourseEvents(courseId, email);
        return events.stream()
            .filter(e -> "self-study".equalsIgnoreCase(e.getType()))
            .map(e -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", e.getId());
                map.put("title", e.getTitle());
                map.put("startTime", e.getStartTime() != null ? e.getStartTime().toString() : "");
                map.put("endTime", e.getEndTime() != null ? e.getEndTime().toString() : "");
                map.put("relatedDeadlineId", e.getRelatedDeadlineId());
                // Optionally add deadline title if available
                if (e.getRelatedDeadlineId() != null) {
                    CalendarEvent deadline = null;
                    try {
                        deadline = courseService.getCourseEvents(courseId, email).stream()
                            .filter(ev -> ev.getId().equals(e.getRelatedDeadlineId()))
                            .findFirst().orElse(null);
                    } catch (Exception ignored) {}
                    if (deadline != null) {
                        map.put("relatedDeadlineTitle", deadline.getTitle());
                    }
                }
                return map;
            })
            .toList();
    }

    /**
     * Adds a manual self-study session to a course.
     */
    @PostMapping("/courses/{courseId}/add-selfstudy")
    @ResponseBody
    public ResponseEntity<?> addManualSelfstudySession(
            @PathVariable Long courseId,
            @RequestBody Map<String, Object> payload,
            Principal principal) {
        String email = principal.getName();
        String title = (String) payload.get("title");
        String description = (String) payload.getOrDefault("description", "Manual self-study session");
        String color = (String) payload.getOrDefault("color", "#F4B400");
        String startTimeStr = (String) payload.get("startTime");
        String endTimeStr = (String) payload.get("endTime");
        Long relatedDeadlineId = null;
        if (payload.get("relatedDeadlineId") != null) {
            try {
                relatedDeadlineId = Long.parseLong(payload.get("relatedDeadlineId").toString());
            } catch (Exception ignored) {}
        }

        java.time.LocalDateTime startTime = java.time.LocalDateTime.parse(startTimeStr);
        java.time.LocalDateTime endTime = java.time.LocalDateTime.parse(endTimeStr);

        CalendarEvent event = new CalendarEvent();
        event.setTitle(title);
        event.setDescription(description);
        event.setColor(color);
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        event.setType("self-study");
        event.setRelatedDeadlineId(relatedDeadlineId);

        courseService.addManualSelfstudySession(courseId, event.getTitle(), event.getDescription(), event.getColor(), event.getStartTime(), event.getEndTime(), email);
        return ResponseEntity.ok().build();
    }

}
