package com.studyflow.studyplanner.service;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.*;
import net.fortuna.ical4j.model.parameter.Value;

import java.io.InputStream;
import java.time.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.studyflow.studyplanner.model.CalendarEvent;

// Import .ics files
public class IcsParser {
    public static List<CalendarEvent> parseIcs(InputStream inputStream, Long userId) throws Exception {
        CalendarBuilder builder = new CalendarBuilder();
        Calendar calendar = builder.build(inputStream);

        List<CalendarEvent> events = new ArrayList<>();

        List<CalendarComponent> components = calendar.getComponents(Component.VEVENT);

        for (Component component :  components) {
            if (component instanceof VEvent vevent) {
                System.out.println("Parsing event: " + vevent.getSummary());
                
                String title = vevent.getSummary() != null ? vevent.getSummary().getValue() : "Untitled";
                String description = vevent.getDescription() != null ? vevent.getDescription().getValue() : "";

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

                        CalendarEvent event = new CalendarEvent(title, start, end, userId);
                        event.setDescription(description);
                        event.setColor("#aaaaaa");

                        event.setType(classifyType(title, description));
                        events.add(event);
                    }
                } else { // takes care of singular events
                    LocalDateTime start = toLocalDateTime(startDate);
                    LocalDateTime end = toLocalDateTime(endDate);

                    CalendarEvent event = new CalendarEvent(title, start, end, userId);
                    event.setDescription(description);
                    event.setColor("#aaaaaa");
                    event.setType(classifyType(title, description));
                    events.add(event);
                }
            }
        }
        return events;
    }
    private static LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static CalendarEvent createEvent(String title, String description, LocalDateTime start, LocalDateTime end, Long userId) {
        CalendarEvent event = new CalendarEvent(title, start, end, userId);
        event.setDescription(description);
        event.setType(classifyType(title, description));
        event.setColor(getColorForType(event.getType()));
        return event;
    }

    private static String classifyType(String title, String description) {
        String lower = (title + " " + description).toLowerCase();
        if (lower.contains("assignment") || lower.contains("due") || lower.contains("deadline")) {
            return "assignment";
        } else if (lower.contains("Lecture") || lower.contains("class") || lower.contains("lectures"))  {
            return "lecture";
        } else {
            return "custom";
        }
    }

    private static String getColorForType(String type) {
        return switch (type.toLowerCase()) {
            case "lecture" -> "#4285F4"; // sky blue
            case "assignment" -> "#0F9D58"; // emerald
            case "exam" -> "#DB4437"; // crimson
            case "self-study" -> "#F4B400"; // amber
            default -> "#aaaaaa"; // gray
        };
    }
}