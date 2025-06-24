package com.studyflow.studyplanner.repository;

import com.studyflow.studyplanner.model.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for accessing and managing User entities.
 */
@Repository
public interface UserRepository extends CrudRepository<User, Long> {
    User findByEmail(String email);
    User findByResetToken(String resetToken);
}
