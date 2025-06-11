package com.studyflow.studyplanner.controller;

import com.studyflow.studyplanner.model.CalendarEvent;
import com.studyflow.studyplanner.model.User;
import com.studyflow.studyplanner.repository.CalendarEventRepository;
import com.studyflow.studyplanner.repository.UserRepository;
import com.studyflow.studyplanner.service.CalendarService;
import com.studyflow.studyplanner.service.IcsParser;
import com.studyflow.studyplanner.repository.CourseRepository;
import com.studyflow.studyplanner.service.CourseService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/calendar")
public class CalendarController {

    private final CalendarService calendarService;
    private final UserRepository userRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final CourseRepository courseRepository;
    private final CourseService courseService;

    @Autowired
    public CalendarController(CalendarService calendarService,
                              UserRepository userRepository,
                              CalendarEventRepository calendarEventRepository,
                              CourseRepository courseRepository,
                              CourseService courseService) {
        this.calendarService = calendarService;
        this.userRepository = userRepository;
        this.calendarEventRepository = calendarEventRepository;
        this.courseRepository = courseRepository;
        this.courseService = courseService;
    }

    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<String> uploadCalendar(@RequestParam("file") MultipartFile file, Principal principal) {
        try {
            InputStream inputStream = file.getInputStream();
            String email = principal.getName();
            User user = userRepository.findByEmail(email);
            Long userId = user.getId();
            
            List<CalendarEvent> events = IcsParser.parseIcs(inputStream, userId,
            userRepository, courseRepository, calendarEventRepository, courseService, email);
            
            for (CalendarEvent event : events) {
                calendarService.saveEvent(event);
            }
            
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("success");
        } catch (Exception e) {
            return ResponseEntity.status(500).contentType(MediaType.TEXT_PLAIN)
                    .body("error: " + e.getMessage());
        }
    }

    @GetMapping("/events")
    @ResponseBody
    public List<Map<String, Object>> getEventsForFrontend(Principal principal) {
        User user = userRepository.findByEmail(principal.getName());
        List<CalendarEvent> events = calendarService.getUserEvents(user.getId());

        List<Map<String, Object>> result = new ArrayList<>();
        for (CalendarEvent event : events) {
            Map<String, Object> json = new HashMap<>();
            json.put("id", event.getId());
            json.put("title", event.getTitle());
            json.put("start", event.getStartTime().toString());
            json.put("end", event.getEndTime().toString());
            json.put("color", event.getColor());
            json.put("description", event.getDescription());
            json.put("type", event.getType());
            json.put("courseId", event.getCourseId());
            json.put("completed", event.isCompleted()); // always dynamic
            json.put("isDeadline", event.isDeadline());
            json.put("points", event.getPoints());
            json.put("generatedByEngine", event.isGeneratedByEngine());
            json.put("duration", event.getDurationInHours());
            result.add(json);
        }
        return result;
    }

    @GetMapping("/upcoming")
    @ResponseBody
    public List<Map<String, Object>> getUpcomingEvents(Principal principal,
                                                       @RequestParam(defaultValue = "4") int limit) {
        User user = userRepository.findByEmail(principal.getName());
        LocalDateTime now = LocalDateTime.now();

        List<CalendarEvent> events = calendarEventRepository
                .findByUserIdAndStartTimeAfterOrderByStartTimeAsc(user.getId(), now, PageRequest.of(0, limit));

        List<Map<String, Object>> result = new ArrayList<>();
        for (CalendarEvent event : events) {
            Map<String, Object> json = new HashMap<>();
            json.put("title", event.getTitle());
            json.put("startTime", event.getStartTime().toString());
            result.add(json);
        }
        return result;
    }

    @PostMapping("/create")
    @ResponseBody
    public CalendarEvent createEvent(@RequestBody CalendarEvent event, Principal principal) {
        User user = userRepository.findByEmail(principal.getName());
        event.setUserId(user.getId());
        if (event.getColor() == null || event.getColor().isEmpty()) {
            event.setColor(getColorForEventType(event.getType()));
        }
        event.setCourseId(event.getCourseId());
        // Removed: event.setCompleted(event.isCompleted());
        // Fix: Ensure isDeadline is set correctly from the request (not default false)
        // If the JSON contains "isDeadline", it will be set by Jackson. But if not, check type:
        if ("exam".equalsIgnoreCase(event.getType()) || "assignment".equalsIgnoreCase(event.getType())) {
            // Only override if not explicitly set
            // If isDeadline is not set (default false), set to true for exam/assignment
            if (!event.isDeadline()) {
                event.setDeadline(true);
            }
        }
        event.setPoints(event.getPoints());
        event.setGeneratedByEngine(event.isGeneratedByEngine());
        return calendarService.saveEvent(event);
    }

    @PostMapping("/update/{id}")
    @ResponseBody
    public CalendarEvent updateEvent(@PathVariable Long id,
                                     @RequestBody CalendarEvent updated,
                                     Principal principal) {
        CalendarEvent existing = calendarEventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with ID: " + id));

        Long userId = userRepository.findByEmail(principal.getName()).getId();
        if (!existing.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        existing.setTitle(updated.getTitle());
        existing.setStartTime(updated.getStartTime());
        existing.setEndTime(updated.getEndTime());
        existing.setColor(updated.getColor());
        existing.setDescription(updated.getDescription());
        existing.setType(updated.getType());
        existing.setCourseId(updated.getCourseId());
        // Removed: existing.setCompleted(updated.isCompleted());
        existing.setDeadline(updated.isDeadline());
        existing.setPoints(updated.getPoints());
        existing.setGeneratedByEngine(updated.isGeneratedByEngine());

        return calendarService.saveEvent(existing);
    }

    @DeleteMapping("/delete/{id}")
    @ResponseBody
    public void deleteEvent(@PathVariable Long id, Principal principal) {
        CalendarEvent event = calendarEventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with ID: " + id));

        Long userId = userRepository.findByEmail(principal.getName()).getId();
        if (event.getUserId().equals(userId)) {
            calendarService.deleteEvent(id);
        }
    }

    private String getColorForEventType(String type) {
        return switch (type == null ? "" : type.toLowerCase()) {
            case "lecture" -> "#4285F4";
            case "assignment" -> "#0F9D58";
            case "exam" -> "#DB4437";
            case "self-study" -> "#F4B400";
            default -> "#aaaaaa";
        };
    }
}