package com.studyflow.studyplanner.controller;

import com.studyflow.studyplanner.model.Course;
import com.studyflow.studyplanner.service.CourseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    /**
     * Creates a new course for the logged-in user.
     */
    @PostMapping("/create")
    public ResponseEntity<Course> createCourse(@RequestBody Course course, Principal principal) {
        String email = principal.getName();
        Course created = courseService.createCourse(
            course.getName(),
            course.getColor(),
            email,
            course.getCourseIdentifier(),
            course.getDifficulty()
        );
        return ResponseEntity.ok(created);
    }

    /**
     * Updates an existing course by ID.
     */
    @PostMapping("/update/{id}")
    public ResponseEntity<?> updateCourse(@PathVariable Long id, @RequestBody Map<String, Object> updates, Principal principal) {
        try {
            String email = principal.getName();
            String name = (String) updates.get("name");
            String color = (String) updates.get("color");
            final int[] difficultyHolder = new int[]{1}; // Default

            if (updates.containsKey("difficulty")) {
                Object diffObj = updates.get("difficulty");
                if (diffObj != null) {
                    try {
                        difficultyHolder[0] = Integer.parseInt(diffObj.toString());
                    } catch (NumberFormatException ignored) {}
                }
            }

            Course course = courseService.getById(id).orElseThrow(() -> new RuntimeException("Course not found"));
            // Check if the course belongs to the user
            if (!course.getUser().getEmail().equals(email)) {
                return ResponseEntity.status(403).body("Access denied");
            }

            course.setName(name);
            course.setColor(color);
            course.setDifficulty(difficultyHolder[0]);
            courseService.updateCourse(id, name, color, email);

            // Save difficulty (optional, in case updateCourse does not handle it)
            courseService.getById(id).ifPresent(c -> {
                c.setDifficulty(difficultyHolder[0]);
                courseService.updateCourse(c.getId(), c.getName(), c.getColor(), email);
            });

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Update failed: " + e.getMessage());
        }
    }

    /**
     * Returns all courses with details and progress for the logged-in user.
     */
    @GetMapping("/user")
    public ResponseEntity<List<Map<String, Object>>> getUserCourses(Principal principal) {
        String email = principal.getName();
        List<Course> courses = courseService.getCoursesWithProgress(email);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Course course : courses) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", course.getId());
            item.put("name", course.getName());
            item.put("color", course.getColor());
            item.put("progressPercent", course.getProgressPercent());
            item.put("courseIdentifier", course.getCourseIdentifier());
            item.put("difficulty", course.getDifficulty());
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Returns course details by course ID.
     */
    @GetMapping("/description/{id}")
    public ResponseEntity<Course> getDescription(@PathVariable Long id, Principal principal) {
        String email = principal.getName();
        Course course = courseService.getCourseDetails(id, email);
        if (course == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(course);
    }

    /**
     * Returns course details including progress and self-study info for the sidebar.
     */
    @GetMapping("/details/{id}")
    public ResponseEntity<Course> getCourseDetails(@PathVariable Long id, Principal principal) {
        String email = principal.getName();
        Course course = courseService.getCourseDetails(id, email);
        if (course == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(course);
    }

    /**
     * Deletes a course by ID if it belongs to the logged-in user.
     */
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id, Principal principal) {
        try {
            courseService.deleteCourse(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Delete failed: " + e.getMessage());
        }
    }

    /**
     * Returns all calendar events assigned to a course.
     */
    @GetMapping("/events/{courseId}")
    public ResponseEntity<?> getCourseEvents(@PathVariable Long courseId, Principal principal) {
        try {
            String email = principal.getName();
            return ResponseEntity.ok(courseService.getCourseEvents(courseId, email));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Could not load course events: " + e.getMessage());
        }
    }

    /**
     * Removes a calendar event from a course.
     */
    @PostMapping("/events/remove")
    public ResponseEntity<?> removeEventFromCourse(@RequestBody Map<String, Long> request, Principal principal) {
        try {
            Long courseId = request.get("courseId");
            Long eventId = request.get("eventId");
            String email = principal.getName();
            courseService.removeEventFromCourse(courseId, eventId, email);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Could not remove event: " + e.getMessage());
        }
    }

    /**
     * Adds a new calendar event to a course.
     */
    @PostMapping("/events/add")
    public ResponseEntity<?> addEventToCourse(@RequestBody Map<String, String> request, Principal principal) {
        try {
            String title = request.get("title");
            String description = request.get("description");
            String type = request.get("type");
            String color = request.get("color");
            Long courseId = Long.parseLong(request.get("courseId"));

            LocalDateTime startTime = LocalDateTime.parse(request.get("startTime"));
            LocalDateTime endTime = LocalDateTime.parse(request.get("endTime"));

            String email = principal.getName();
            courseService.addEventToCourse(courseId, title, description, type, color, startTime, endTime, email);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Could not add event: " + e.getMessage());
        }
    }

    /**
     * Runs the automatic planning of self-study sessions for a deadline.
     */
    @PostMapping("/{id}/autoplan")
    public ResponseEntity<?> autoPlanSelfstudy(@PathVariable Long id,
                                               @RequestParam Long deadlineId,
                                               Principal principal) {
        try {
            String email = principal.getName();
            courseService.autoPlanSelfstudySessions(id, deadlineId, email);
            return ResponseEntity.ok("Planning complete");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Auto planning failed: " + e.getMessage());
        }
    }

    /**
     * Returns all deadline events for a course.
     */
    @GetMapping("/{id}/deadlines")
    public ResponseEntity<?> getDeadlines(@PathVariable Long id, Principal principal) {
        try {
            String email = principal.getName();
            return ResponseEntity.ok(courseService.getDeadlines(id, email));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Could not load deadlines: " + e.getMessage());
        }
    }

    /**
     * Adds a manual self-study session to a course.
     */
    @PostMapping("/{id}/add-selfstudy")
    public ResponseEntity<?> addSelfstudy(@PathVariable Long id,
                                          @RequestBody Map<String, String> body,
                                          Principal principal) {
        try {
            String email = principal.getName();
            String title = body.get("title");
            String description = body.get("description");
            String color = body.get("color");
            String startTimeStr = body.get("startTime");
            String endTimeStr = body.get("endTime");

            LocalDateTime startTime = LocalDateTime.parse(startTimeStr);
            LocalDateTime endTime = LocalDateTime.parse(endTimeStr);

            courseService.addManualSelfstudySession(id, title, description, color, startTime, endTime, email);
            return ResponseEntity.ok("Selfstudy session added.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Could not add selfstudy session: " + e.getMessage());
        }
    }

    /**
     * Returns progress information for a course.
     */
    @GetMapping("/{id}/progress")
    public ResponseEntity<?> getProgress(@PathVariable Long id, Principal principal) {
        try {
            String email = principal.getName();
            return ResponseEntity.ok(courseService.getProgressInfo(id, email));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Could not load progress: " + e.getMessage());
        }
    }
}