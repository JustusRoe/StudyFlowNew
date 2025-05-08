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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.Principal;
import java.util.*;
import java.time.LocalDateTime;

// accepts file uploads (.ics file), parses the file, and saves each event into the database

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

    // shows calendar page
    @GetMapping({"", "/"})
    public String showCalendarPage() {
        return "calendar";
    }

    // upload form
    @GetMapping("/upload")
    public String showUploadForm() {
        return "upload-calendar"; //creates uploadCalendar.html
    }

    // upload ics file and parse it
    @PostMapping("/upload")
    public String uploadCalendar(@RequestParam("file") MultipartFile file, Model model, Principal principal) {
        try {
            InputStream inputStream = file.getInputStream();
            
            // gets currently logged-in user
            String email = principal.getName();
            User user = userRepository.findByEmail(email); // could change to userService.findById(...) if using ID

            // parses and saves events
            List<CalendarEvent> events = IcsParser.parseIcs(inputStream, user.getId());
            for (CalendarEvent event : events) {
                calendarService.saveEvent(event);
            }

            model.addAttribute("message", "Upload successful! Imported " + events.size() + " events.");
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Upload failed: " + e.getMessage());
        }
        return "upload-calendar";
    }

    // returns events in json format for FullCalendar
    @GetMapping("/events")
    @ResponseBody
    public List<Map<String, Object>> getEventsForFrontend(
        Principal principal,
        @RequestParam(required = false) String course,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        String email = principal.getName();
        User user = userRepository.findByEmail(email);
        List<CalendarEvent> events = calendarService.getUserEvents(user.getId());

        // convert to FullCalendar format
        List<Map<String, Object>> result = new ArrayList<>();
        for (CalendarEvent event : events) {
            boolean matchesCourse = (course == null || event.getTitle().toLowerCase().contains(course.toLowerCase()));
            boolean matchesDate = (start == null || !event.getEndTime().isBefore(start)) && (end == null || !event.getStartTime().isAfter(end));
            
            if (matchesCourse && matchesDate) {
                Map<String, Object> jsonEvent = new HashMap<>();
                jsonEvent.put("title", event.getTitle());
                jsonEvent.put("start", event.getStartTime().toString());
                jsonEvent.put("end", event.getEndTime().toString());
                jsonEvent.put("color", event.getColor());
                jsonEvent.put("description", event.getDescription());
                result.add(jsonEvent);
            }
        }

        return result;
    }

    // Add event
    @PostMapping("/create")
    @ResponseBody
    public CalendarEvent createEvent(@RequestBody CalendarEvent event, Principal principal) {
        User user = userRepository.findByEmail(principal.getName());
        event.setUserId(user.getId());
        return calendarService.saveEvent(event);
    }

    // Edit event
    @PostMapping("/update/{id}")
    @ResponseBody
    public CalendarEvent updateEvent(@PathVariable Long id, @RequestBody CalendarEvent updated, Principal principal) {
        CalendarEvent existing = calendarEventRepository.findOneById(id); // this function is not implemented yet in the repository nor in the service
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
        CalendarEvent event = calendarEventRepository.findOneById(id);
        if (event.getUserId().equals(userRepository.findByEmail(principal.getName()).getId())) {
            calendarService.deleteEvent(id);
        }
    }

        
    // returns events in json format for FullCalendar with type filtering
    @GetMapping("/events")
    @ResponseBody
    public List<Map<String, Object>> getEventsForFrontend(
            Principal principal,
            @RequestParam(required = false) String course,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end
    ) {
        String email = principal.getName();
        User user = userRepository.findByEmail(email);
        List<CalendarEvent> events;
        
        if (type != null && !type.isEmpty()) {
            events = calendarService.getUserEventsByType(user.getId(), type);
        } else {
            events = calendarService.getUserEvents(user.getId());
        }

        // ... existing filtering logic ...  <---- whats this?!?!?

        return result; // ??????
    }

    private String getColorForEventType(String type) {
        return switch (type.toLowerCase()) {
            case "lecture" -> "#4285F4";
            case "assignment" -> "#0F9D58";
            case "exam" -> "#DB4437";
            case "self-study" -> "#F4B400";
            default -> "#9E9E9E";
        }; // Blue
        // Green
        // Red
        // Yellow
        // Grey
    }
}
