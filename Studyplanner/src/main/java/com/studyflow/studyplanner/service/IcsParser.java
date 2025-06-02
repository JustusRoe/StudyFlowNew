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
import java.util.List;
import java.util.Optional;

// Import .ics files
public class IcsParser {
    public static List<CalendarEvent> parseIcs(InputStream inputStream, Long userId, UserRepository userRepo, 
    CourseRepository courseRepo, CalendarEventRepository eventRepo, CourseService courseService) throws Exception {
        CalendarBuilder builder = new CalendarBuilder();
        Calendar calendar = builder.build(inputStream);

        List<CalendarEvent> events = new ArrayList<>();
        List<CalendarComponent> components = calendar.getComponents(Component.VEVENT);

        // finds user by id so that the courses are assigned to the correct user
        User user = userRepo.findById(userId).orElseThrow();
        String userEmail = user.getEmail();

        // goes through all events inside the .ics file
        for (Component component :  components) {
            if (component instanceof VEvent vevent) {
                System.out.println("Parsing event: " + vevent.getSummary());
                
                // extracts the summary, description, and location fields
                String summary = vevent.getSummary() != null ? vevent.getSummary().getValue() : "Untitled";
                String description = vevent.getDescription() != null ? vevent.getDescription().getValue() : "";
                String location = vevent.getLocation() != null ? vevent.getLocation().getValue() : "";

                // parses the course name, instructor, and courseid from summary
                String[] parts = summary.split(",");
                String courseName = parts[0].trim();
                String instructor = parts.length > 1 ? parts[1].trim() : "";
                String courseId = summary.contains("[") && summary.contains("]") ?
                summary.substring(summary.indexOf("[") + 1, summary.indexOf("]")) : "unknown";

                // find or creates the course in the DB
                Optional<Course> optionalCourse = courseRepo.findByCourseId(courseId);

                Course course;
                if (optionalCourse.isPresent()) {
                    course = optionalCourse.get();
                } else {
                    course = courseService.createCourse(courseName, generateDefaultColor(courseId), userEmail);
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
                        CalendarEvent saved = eventRepo.save(event);
                        courseService.addEventToCourse(course.getId(), saved.getId());
                        events.add(saved);
                    }
                } else { // takes care of singular events
                    LocalDateTime start = toLocalDateTime(startDate);
                    LocalDateTime end = toLocalDateTime(endDate);

                    // create and save the event, and connect to course
                    CalendarEvent event = createEvent(courseName, description, start, end, userId, courseId, course.getColor());
                    CalendarEvent saved = eventRepo.save(event);
                    courseService.addEventToCourse(course.getId(), saved.getId());
                    events.add(saved);
                }
            }
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
        int hash = input.hashCode();
        int r = (hash & 0xFF0000) >> 16;
        int g = (hash & 0x00FF00) >> 8;
        int b = hash & 0x0000FF;
        return String.format("#%02X%02X%02X", r, g, b);
    }
}