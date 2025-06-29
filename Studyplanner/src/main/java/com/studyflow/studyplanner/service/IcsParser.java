package com.studyflow.studyplanner.service;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.*;
import net.fortuna.ical4j.model.parameter.Value;

import com.studyflow.studyplanner.model.CalendarEvent;
import com.studyflow.studyplanner.repository.CalendarEventRepository;
import com.studyflow.studyplanner.repository.CourseRepository;
import com.studyflow.studyplanner.repository.UserRepository;
import com.studyflow.studyplanner.model.User;
import com.studyflow.studyplanner.model.Course;
import com.studyflow.studyplanner.service.CourseService;

import java.io.InputStream;
import java.time.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Import .ics files
public class IcsParser {
    public static List<CalendarEvent> parseIcs(InputStream inputStream, Long userId, UserRepository userRepo, 
    CourseRepository courseRepo, CalendarEventRepository eventRepo, CourseService courseService, String userEmail) throws Exception {
        System.out.println("Starting ICS import for userEmail: " + userEmail);
        CalendarBuilder builder = new CalendarBuilder();
        Calendar calendar = builder.build(inputStream);

        List<CalendarEvent> events = new ArrayList<>();
        List<CalendarComponent> components = calendar.getComponents(Component.VEVENT);

        // finds user by id so that the courses are assigned to the correct user
        User user = userRepo.findByEmail(userEmail);
        if (user == null) throw new RuntimeException("User not found");

        Map<String, Course> courseMap = new HashMap<>();
        Map<String, List<CalendarEvent>> eventMap = new HashMap<>();
        List<CalendarEvent> allEvents = new ArrayList<>();
        
        // goes through all events inside the .ics file
        for (Component component :  components) {
            if (component instanceof VEvent vevent) {
                String summary = vevent.getSummary() != null ? vevent.getSummary().getValue() : "Untitled";
                String courseId = null;
                String courseName = null;
                int firstSpace = summary.indexOf(' ');
                if (firstSpace == 8) {
                    courseId = summary.substring(0, 8);
                    // Suche nach "Frankfurt am Main" (case-insensitive)
                    String lower = summary.toLowerCase();
                    int frankfurtIdx = lower.indexOf("frankfurt am main");
                    if (frankfurtIdx > 9) {
                        // Hole den Bereich zwischen den ersten 8 Zeichen und "Frankfurt am Main"
                        String between = summary.substring(9, frankfurtIdx).trim();
                        courseName = between;
                    } else {
                        // Fallback: alles nach den ersten 8 Zeichen
                        courseName = summary.substring(9).trim();
                    }
                } else {
                    System.out.println("Skipping event: SUMMARY does not have exactly 8 chars before first space: " + summary);
                    continue;
                }

                String description = vevent.getDescription() != null ? vevent.getDescription().getValue() : "";
                String location = vevent.getLocation() != null ? vevent.getLocation().getValue() : "";

                // find or creates the course in the DB
                Optional<Course> optionalCourse = courseRepo.findByCourseIdentifierAndUser(courseId, user);

                Course course = courseMap.get(courseId);
                if (course == null) {
                    if (optionalCourse.isPresent()) {
                        course = optionalCourse.get();
                    } else {
                        System.out.println("Creating new course: " + courseName + " with ID: " + courseId + " for user: " + userEmail);
                        course = courseService.createCourse(courseName, generateDefaultColor(courseId), userEmail, courseId, 2);
                        System.out.println("Created course: " + course);
                    }
                    courseMap.put(courseId, course);
                    eventMap.put(courseId, new ArrayList<>());
                }

                // gets start/end date & duration
                Date startDate = vevent.getStartDate().getDate();
                Date endDate = vevent.getEndDate().getDate();
                long durationMillis = endDate.getTime() - startDate.getTime();

                RRule rruleProp = vevent.getProperty(Property.RRULE);
                if (rruleProp != null) { // takes care of repeating events
                    System.out.println("Repeating event detected with rule: " + rruleProp.getValue());
                    Recur recur = rruleProp.getRecur();
                    DateTime periodEnd = new DateTime(LocalDate.now().plusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()); // repeat ends one year later by default
                    
                    DateList recurDates = recur.getDates(startDate, periodEnd, Value.DATE_TIME);
                    for (Date recurDate : recurDates) {
                        LocalDateTime start = toLocalDateTime(recurDate);
                        LocalDateTime end = start.plus(Duration.ofMillis(durationMillis));

                        // create and save the event, and connect to course
                        CalendarEvent event = createEvent(courseName, description, start, end, userId, courseId, course.getColor());
                        eventMap.get(courseId).add(event);
                    }
                } else { // takes care of singular events
                    LocalDateTime start = toLocalDateTime(startDate);
                    LocalDateTime end = toLocalDateTime(endDate);

                    // create and save the event, and connect to course
                    CalendarEvent event = createEvent(courseName, description, start, end, userId, courseId, course.getColor());
                    eventMap.get(courseId).add(event);
                }
            }
        }

        for (Map.Entry<String, List<CalendarEvent>> entry : eventMap.entrySet()) {
            String courseId = entry.getKey();
            List<CalendarEvent> courseEvents = entry.getValue();

            List<CalendarEvent> savedEvents = eventRepo.saveAll(courseEvents);
            events.addAll(savedEvents);

            Course course = courseMap.get(courseId);
            for (CalendarEvent event : savedEvents) {
                course.addEventId(event.getId());
            }
            courseRepo.save(course);
        }
        return events;
    }
    // converts Date to LocalDateTime
    private static LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    // create CalendarEvent with CourseId and Lecture type
    private static CalendarEvent createEvent(String title, String description, 
    LocalDateTime start, LocalDateTime end, Long userId, String courseId, String color) {
        CalendarEvent event = new CalendarEvent(title, start, end, userId);
        event.setDescription(description);
        event.setType("lecture"); // all imported events are considered lectures
        event.setColor(color);
        event.setCourseId(courseId); // saves courseId for reference
        return event;
    }

    // automatically generates a color from the courseId string
    private static String generateDefaultColor(String input) {
        int hash = input != null ? input.hashCode() : new java.util.Random().nextInt();
        int r = (hash >> 16) & 0xFF;
        int g = (hash >> 8) & 0xFF;
        int b = hash & 0xFF;
        return String.format("#%02X%02X%02X70", r, g, b);
    }
}