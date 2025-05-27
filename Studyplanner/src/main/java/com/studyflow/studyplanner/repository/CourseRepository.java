package com.studyflow.studyplanner.repository;

import com.studyflow.studyplanner.model.Course;
import com.studyflow.studyplanner.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByUser(User user);
}
