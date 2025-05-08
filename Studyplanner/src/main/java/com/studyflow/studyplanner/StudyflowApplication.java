package com.studyflow.studyplanner;

import com.studyflow.studyplanner.service.UserService;
import com.studyflow.studyplanner.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class StudyflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudyflowApplication.class, args);
    }

    /// Creating TestUser
	/// Name: test
	/// Mail: test@test.com
	/// password: test
    @Bean
    public CommandLineRunner seedTestUser(UserService userService, UserRepository userRepository) {
        return args -> {
            String email = "test@test.com";
            userService.registerAndLogin("test", email, "test");
            };
        };
    }

