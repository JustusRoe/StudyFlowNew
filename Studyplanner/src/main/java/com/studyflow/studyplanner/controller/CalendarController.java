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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    /**
     * Handles uploading and importing a calendar (.ics) file for the current user.
     */
    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<String> uploadCalendar(@RequestParam("file") MultipartFile file, Principal principal) {
        try {
            // Preprocessing: Clean up ICS content before parsing
            String icsContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            // Replace all occurrences of ;VALUE=DATE;VALUE=DATE: with ;VALUE=DATE:
            icsContent = icsContent.replaceAll(";VALUE=DATE;VALUE=DATE:", ";VALUE=DATE:");
            // Optionally, remove any other duplicate VALUE=DATE
            icsContent = icsContent.replaceAll(";VALUE=DATE(;VALUE=DATE)+:", ";VALUE=DATE:");
            // Entferne alles nach END:VCALENDAR (inklusive nachfolgender Zeichen)
            icsContent = cleanIcsContent(icsContent);

            InputStream inputStream = new ByteArrayInputStream(icsContent.getBytes(StandardCharsets.UTF_8));
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
            e.printStackTrace();
            return ResponseEntity.status(500).contentType(MediaType.TEXT_PLAIN)
                    .body("error: " + e.getMessage());
        }
    }

    // Hilfsmethode: Entfernt alles nach END:VCALENDAR
    private String cleanIcsContent(String icsContent) {
        String endTag = "END:VCALENDAR";
        int idx = icsContent.indexOf(endTag);
        if (idx != -1) {
            return icsContent.substring(0, idx + endTag.length());
        }
        return icsContent;
    }

    /**
     * Returns all calendar events for the current user in a format suitable for the frontend.
     */
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
            json.put("completed", event.isCompleted());
            json.put("isDeadline", event.isDeadline());
            json.put("points", event.getPoints());
            json.put("generatedByEngine", event.isGeneratedByEngine());
            json.put("duration", event.getDurationInHours());
            json.put("fillType", event.getFillType());
            result.add(json);
        }
        return result;
    }

    /**
     * Returns a limited list of upcoming events for the current user.
     */
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

    /**
     * Creates a new calendar event for the current user.
     */
    @PostMapping("/create")
    @ResponseBody
    public CalendarEvent createEvent(@RequestBody CalendarEvent event, Principal principal) {
        User user = userRepository.findByEmail(principal.getName());
        event.setUserId(user.getId());
        if (event.getColor() == null || event.getColor().isEmpty()) {
            event.setColor(getColorForEventType(event.getType()));
        }
        event.setCourseId(event.getCourseId());
        // Ensure isDeadline is set correctly for exams and assignments
        if ("exam".equalsIgnoreCase(event.getType()) || "assignment".equalsIgnoreCase(event.getType())) {
            if (!event.isDeadline()) {
                event.setDeadline(true);
            }
        }
        event.setPoints(event.getPoints());
        event.setGeneratedByEngine(event.isGeneratedByEngine());
        return calendarService.saveEvent(event);
    }

    /**
     * Updates an existing calendar event for the current user.
     */
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
        existing.setDeadline(updated.isDeadline());
        existing.setPoints(updated.getPoints());
        existing.setGeneratedByEngine(updated.isGeneratedByEngine());
        existing.setFillType(updated.getFillType());

        return calendarService.saveEvent(existing);
    }

    /**
     * Deletes a calendar event for the current user.
     */
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

    /**
     * Returns a default color for a given event type.
     */
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