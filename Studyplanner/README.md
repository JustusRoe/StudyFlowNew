# StudyFlow

StudyFlow is a smart study planner web application for students. It helps you organize lectures, deadlines, and self-study sessions, track your progress. The platform is built with Java Spring Boot (backend), Thymeleaf (server-side rendering), and modern HTML/CSS/JS (frontend).

## Features

- **Personal Calendar:** Import .ics files, manage events, and visualize your study schedule.
- **Course Management:** Create, edit, and delete courses. Assign events to courses.
- **Deadline & Self-Study Planning:** Add deadlines, auto-plan self-study sessions, and track workload.
- **Progress Tracking:** Visualize your progress per course and overall.
- **User Profile:** Set study preferences (times, days, breaks), change password, and manage your account.
- **Authentication:** Secure login, registration, and password reset via email.
- **Responsive UI:** Modern, mobile-friendly design.

## Technology Stack

- **Backend:** Java 17+, Spring Boot, Spring Security, Spring Data JPA, PostgreSQL
- **Frontend:** Thymeleaf, HTML5, CSS3, JavaScript (FullCalendar, custom scripts)
- **Email:** Spring Mail (Gmail SMTP)
- **Build/Run:** Maven (or Maven Wrapper)

## Getting Started

### Prerequisites

- Java 17+
- PostgreSQL database (or use the provided Railway connection)
- Maven **or** use the included Maven Wrapper (`./mvnw` or `mvnw.cmd`)

### Configuration

Edit `src/main/resources/application.properties` for your environment:

- Database connection (`spring.datasource.*`)
- Mail connection (`spring.mail.*`)
- (Optional) Logging levels

### Build & Run

Using Maven Wrapper (recommended):

```bash
./mvnw clean install
./mvnw spring-boot:run
```

Or, if you have Maven installed:

```bash
mvn clean install
mvn spring-boot:run
```

The app will be available at [http://localhost:8080](http://localhost:8080). 

### Database

- The app uses PostgreSQL. Tables are auto-created/updated via JPA (`spring.jpa.hibernate.ddl-auto=update`).

### Email

- Password reset emails are sent via Gmail SMTP. Update credentials in `application.properties` for production.

## Project Structure

- `src/main/java/com/studyflow/studyplanner/`  
  - `controller/` — Spring MVC controllers (routing, API)
  - `model/` — JPA entities (User, Course, CalendarEvent)
  - `repository/` — Spring Data repositories
  - `service/` — Business logic and utilities
- `src/main/resources/templates/` — Thymeleaf HTML templates
- `src/main/resources/static/` — Static assets (CSS, JS, images)
- `src/main/resources/application.properties` — Configuration

## Key Endpoints

- `/` — Welcome page
- `/login`, `/signup`, `/logout` — Authentication
- `/dashboard` — Main dashboard (calendar, courses, events)
- `/user_settings` — Profile and preferences
- `/forgot-password`, `/reset-password` — Password reset flow
- `/calendar/*` — Calendar event API
- `/courses/*` — Course management API
- `/manage-deadlines` — Deadline management UI

## Development Notes

- DevTools and Thymeleaf cache are disabled in production config.
- All business logic is in the `service` layer; controllers are thin.
- Security is handled via Spring Security and a custom `UserDetailsService`.
- For production, update all secrets and URLs in `application.properties`.

## License

This project is for educational purposes.

---

**StudyFlow** — Find your flow. Fuel your focus.