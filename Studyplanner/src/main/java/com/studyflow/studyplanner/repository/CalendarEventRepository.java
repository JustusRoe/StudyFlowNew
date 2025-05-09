package com.studyflow.studyplanner.repository;

import com.studyflow.studyplanner.model.CalendarEvent;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// saves and finds calendar events

@Repository
public interface CalendarEventRepository extends CrudRepository<CalendarEvent, Long> {
    List<CalendarEvent> findByUserId(Long userId);
    List<CalendarEvent> findByUserIdAndTypeIgnoreCase(Long userId, String type);
    CalendarEvent findById(long Id);
}