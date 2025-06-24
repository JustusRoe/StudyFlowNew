package com.studyflow.studyplanner.repository;

import com.studyflow.studyplanner.model.Course;
import com.studyflow.studyplanner.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for accessing and managing Course entities.
 */
public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByUser(User user);

    Optional<Course> findByCourseIdentifierAndUser(String courseId, User user);
}