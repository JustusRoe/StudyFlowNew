package com.studyflow.studyplanner.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * This controller only serves the dashboard HTML page.
 * All dynamic data (calendar, events, courses, etc.) is loaded from other controllers (e.g., CalendarController).
 */
@Controller
public class DashboardController {
    @GetMapping("/dashboard")
    public String showDashboard() {
        return "dashboard"; // Loads dashboard.html template
    }
}
