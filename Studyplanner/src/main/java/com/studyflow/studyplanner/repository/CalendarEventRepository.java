package com.studyflow.studyplanner.repository;

import com.studyflow.studyplanner.model.CalendarEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for accessing and managing CalendarEvent entities.
 */
@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    List<CalendarEvent> findByUserId(Long userId);

    List<CalendarEvent> findByUserIdAndTypeIgnoreCase(Long userId, String type);

    List<CalendarEvent> findByUserIdAndStartTimeAfterOrderByStartTimeAsc(Long userId, LocalDateTime now, Pageable pageable);
}