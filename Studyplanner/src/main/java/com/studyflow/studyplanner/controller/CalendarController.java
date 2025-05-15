package com.studyflow.studyplanner.controller;

import com.studyflow.studyplanner.model.CalendarEvent;
import com.studyflow.studyplanner.model.User;
import com.studyflow.studyplanner.service.CalendarService;
import com.studyflow.studyplanner.service.IcsParser;
import com.studyflow.studyplanner.service.UserService;
import com.studyflow.studyplanner.repository.CalendarEventRepository;
import com.studyflow.studyplanner.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
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
    private final UserService userService;
    private final UserRepository userRepository;
    private final CalendarEventRepository calendarEventRepository;

    @Autowired
    public CalendarController(CalendarService calendarService, UserService userService, UserRepository userRepository, CalendarEventRepository calendarEventRepository) {
        this.calendarService = calendarService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.calendarEventRepository = calendarEventRepository;
    }

    // ICS upload (via AJAX)
    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<String> uploadCalendar(@RequestParam("file") MultipartFile file, Principal principal) {
        System.out.println("Upload triggered");
        try {
            InputStream inputStream = file.getInputStream();
            String email = principal.getName();
            User user = userRepository.findByEmail(email);
            System.out.println("Current user: " + email + " (ID: " + user.getId() + ")");

            List<CalendarEvent> events = IcsParser.parseIcs(inputStream, user.getId());
            System.out.println("Parsed " + events.size() + " events");

            for (CalendarEvent event : events) {
                System.out.println(" - Event: " + event.getTitle() + " @ " + event.getStartTime());
                calendarService.saveEvent(event);
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("success");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("error: " + e.getMessage());
        }
    }

    // Fetch all events for the current user
    @GetMapping("/events")
    @ResponseBody
    public List<Map<String, Object>> getEventsForFrontend(Principal principal) {
        String email = principal.getName();
        User user = userRepository.findByEmail(email);

        List<CalendarEvent> events = calendarService.getUserEvents(user.getId());

        List<Map<String, Object>> result = new ArrayList<>();
        for (CalendarEvent event : events) {
            Map<String, Object> jsonEvent = new HashMap<>();
            jsonEvent.put("id", event.getId());
            jsonEvent.put("title", event.getTitle());
            jsonEvent.put("start", event.getStartTime().toString());
            jsonEvent.put("end", event.getEndTime().toString());
            jsonEvent.put("color", event.getColor());
            jsonEvent.put("description", event.getDescription());
            jsonEvent.put("type", event.getType());
            result.add(jsonEvent);
        }

        return result;
    }

    // Create new event
    @PostMapping("/create")
    @ResponseBody
    public CalendarEvent createEvent(@RequestBody CalendarEvent event, Principal principal) {
        User user = userRepository.findByEmail(principal.getName());
        event.setUserId(user.getId());

        // Optional: if color not set, assign based on type
        if (event.getColor() == null || event.getColor().isEmpty()) {
            event.setColor(getColorForEventType(event.getType()));
        }

        return calendarService.saveEvent(event);
    }

    // Update existing event
    @PostMapping("/update/{id}")
    @ResponseBody
    public CalendarEvent updateEvent(@PathVariable Long id, @RequestBody CalendarEvent updated, Principal principal) {
        CalendarEvent existing = calendarEventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with ID: " + id));

        if (!existing.getUserId().equals(userRepository.findByEmail(principal.getName()).getId())) {
            throw new RuntimeException("Unauthorized");
        }

        existing.setTitle(updated.getTitle());
        existing.setStartTime(updated.getStartTime());
        existing.setEndTime(updated.getEndTime());
        existing.setColor(updated.getColor());
        existing.setDescription(updated.getDescription());

        return calendarService.saveEvent(existing);
    }

    // Delete event
    @DeleteMapping("/delete/{id}")
    @ResponseBody
    public void deleteEvent(@PathVariable Long id, Principal principal) {
        CalendarEvent event = calendarEventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found with ID: " + id));

        if (event.getUserId().equals(userRepository.findByEmail(principal.getName()).getId())) {
            calendarService.deleteEvent(id);
        }
    }

    // Color mapping by type
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
