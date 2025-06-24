// Fetch and display all events assigned to a specific course in the course detail sidebar
function getCourseEvents(courseId) {
    fetch(`/courses/events/${courseId}`)
        .then(res => {
            if (!res.ok) throw new Error("Failed to fetch course events");
            return res.json();
        })
        .then(events => {
            const list = document.getElementById("eventList");
            list.innerHTML = "";
            if (events.length === 0) {
                list.innerHTML = "<li>No events assigned to this course.</li>";
                return;
            }
            events.forEach(event => {
                const li = document.createElement("li");
                li.innerHTML = `<span>${event.title} (${event.type})</span> <span class="remove-event" title="Remove">❌</span>`;
                li.querySelector(".remove-event").onclick = () => {
                    removeEventFromCourse(courseId, event.id);
                };
                list.appendChild(li);
            });
        })
        .catch(err => {
            console.error("Error loading course events:", err);
        });
}

// Remove an event from a course (used in course detail sidebar)
function removeEventFromCourse(courseId, eventId) {
    fetch(`/courses/events/remove`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ courseId, eventId })
    })
    .then(res => {
        if (!res.ok) throw new Error("Failed to remove event from course");
        getCourseEvents(courseId);
    })
    .catch(err => {
        console.error("Error removing event:", err);
    });
}

// Main initialization after DOM is loaded
document.addEventListener('DOMContentLoaded', function () {
    // Log to confirm script is running
    console.log("✅ calendar.js is loaded and running!");

    // Reference to the calendar container element
    const calendarEl = document.getElementById('calendar');
    let currentCourseFilter = "";

    // Initialize FullCalendar with configuration and event handlers
    const calendar = new FullCalendar.Calendar(calendarEl, {
        initialView: 'dayGridMonth',
        selectable: true,
        editable: true,
        eventDisplay: 'block',
        headerToolbar: {
            left: 'prev today next',
            center: 'title',
            right: 'dayGridMonth,timeGridWeek,timeGridDay'
        },

        // Load and filter events from backend, applying type filters
        events: function (info, successCallback, failureCallback) {
            fetch('/calendar/events')
                .then(response => {
                    if (!response.ok) {
                        return response.text().then(text => {
                            console.error("calendar/events error:", text);
                            failureCallback && failureCallback("Server error: could not load events.");
                            return [];
                        });
                    }
                    return response.json();
                })
                .then(allEvents => {
                    if (!Array.isArray(allEvents)) {
                        console.error("calendar/events did not return an array:", allEvents);
                        failureCallback && failureCallback("Server error: could not load events.");
                        return;
                    }
                    // Get all checked event type filters
                    const selectedTypes = Array.from(
                        document.querySelectorAll('#filter-controls input[type=checkbox]:checked')
                    ).map(cb => cb.value.toLowerCase());

                    // Filter events by selected types
                    const filtered = allEvents.filter(event =>
                        event.type && selectedTypes.includes(event.type.toLowerCase())
                    );
                    successCallback(filtered);
                })
                .catch(err => {
                    console.error("Error loading events:", err);
                    failureCallback && failureCallback(err);
                });
        },

        // Custom rendering for events in the calendar
        eventContent: function (arg) {
            const { event, el } = arg;
            const type = event.extendedProps.type;
            const fillType = event.extendedProps.fillType;
            const color = event.backgroundColor;

            // For lectures and self-study, show a colored dot and time
            if (type === "lecture" || type === "self-study") {
                const dot = document.createElement("span");
                dot.style.backgroundColor = color;
                dot.style.borderRadius = "50%";
                dot.style.width = "10px";
                dot.style.height = "10px";
                dot.style.display = "inline-block";
                dot.style.marginRight = "6px";

                const time = document.createElement("strong");
                time.innerText = event.start.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

                const title = document.createElement("span");
                title.innerText = ` ${event.title}`;

                return { domNodes: [dot, time, title] };
            }

            // For custom events, apply partial fill style if needed
            if (type === "custom") {
                if (fillType === "partial-fill") {
                    arg.el.style.background = `linear-gradient(to right, ${color} 50%, transparent 50%)`;
                }
            }

            // For other types, use default rendering
            return true;
        },

        // Handler for selecting a time range to add a new event
        select: openAddEventSidebar,

        // Handler for clicking an existing event to edit it
        eventClick: function(info) {
            openEditEventSidebar(info.event);
        }
    });

    // Render the calendar
    calendar.render();
    window.calendar = calendar;

    // --- Add Event Sidebar logic ---
    // Opens the sidebar for adding a new event
    function openAddEventSidebar(info) {
        console.log("Opening add sidebar", info);
        const sidebar = document.getElementById("addEventSidebar");
        sidebar.classList.add("open");

        // Reset form fields
        document.getElementById("addEventTitle").value = "";
        document.getElementById("addEventStart").value = info.startStr.slice(0, 16);
        document.getElementById("addEventEnd").value = info.endStr.slice(0, 16);
        document.getElementById("addEventLocation").value = "";
        document.getElementById("addEventColor").value = "#4285F4";
        document.getElementById("addEventType").value = "custom";
        document.getElementById("addEventType").addEventListener("change", function () {
            const fillTypeWrapper = document.getElementById("fillTypeWrapper");
            if (this.value === "custom") {
                fillTypeWrapper.style.display = "block";
            } else {
                fillTypeWrapper.style.display = "none";
            }
        });

        // Populate course dropdown
        loadCoursesForDropdown("addEventCourse");

        // Button references
        const saveBtn = document.getElementById("saveNewEvent");
        const cancelBtn = document.getElementById("cancelNewEvent");
        const closeBtn = sidebar.querySelector(".close-sidebar");

        // Remove previous handlers
        saveBtn.onclick = null;
        cancelBtn.onclick = null;
        if (closeBtn) closeBtn.onclick = null;

        // Helper to close sidebar and refresh calendar
        function closeSidebarAndUnselect() {
            sidebar.classList.remove("open");
            calendar.unselect();
            calendar.refetchEvents();
            calendar.setOption('selectable', false);
            setTimeout(() => calendar.setOption('selectable', true), 0);
        }

        // Save new event handler
        saveBtn.onclick = function () {
            const title = document.getElementById("addEventTitle").value.trim();
            const startTime = document.getElementById("addEventStart").value;
            const type = document.getElementById("addEventType").value;

            if (!title || !startTime || !type) {
                alert("Please fill in all required fields: Title, Start Time, and Type.");
                return;
            }

            const newEvent = {
                title: title,
                startTime: startTime,
                endTime: document.getElementById("addEventEnd").value,
                location: document.getElementById("addEventLocation").value,
                type: type,
                color: document.getElementById("addEventColor").value,
                courseId: document.getElementById("addEventCourse").value || null
            };

            fetch("/calendar/create", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(newEvent)
            }).then(() => {
                closeSidebarAndUnselect();
            });
        };

        // Cancel and close handlers
        cancelBtn.onclick = closeSidebarAndUnselect;
        if (closeBtn) closeBtn.onclick = closeSidebarAndUnselect;
    }

    // --- Edit Event Sidebar logic ---
    // Opens the sidebar for editing an existing event
    function openEditEventSidebar(event) {
        const sidebar = document.getElementById("editEventSidebar");
        sidebar.classList.add("open");

        // Populate form fields with event data
        document.getElementById("editEventId").value = event.id;
        document.getElementById("editEventTitle").value = event.title;
        document.getElementById("editEventStart").value = event.startStr.slice(0, 16);
        document.getElementById("editEventEnd").value = event.endStr.slice(0, 16);
        document.getElementById("editEventLocation").value = event.extendedProps.location || "";
        document.getElementById("editEventColor").value = event.backgroundColor;
        document.getElementById("editEventType").value = event.extendedProps.type || "custom";

        // Populate course dropdown
        loadCoursesForDropdown("editEventCourse");

        // Button references
        const saveBtn = document.getElementById("saveEditedEvent");
        const deleteBtn = document.getElementById("deleteEvent");
        const cancelBtn = document.getElementById("cancelEditEvent");
        const closeBtn = sidebar.querySelector(".close-sidebar");

        // Remove previous handlers
        saveBtn.onclick = null;
        deleteBtn.onclick = null;
        cancelBtn.onclick = null;
        if (closeBtn) closeBtn.onclick = null;

        // Helper to close sidebar and refresh calendar
        function closeSidebarAndUnselect() {
            sidebar.classList.remove("open");
            calendar.unselect();
            calendar.refetchEvents();
        }

        // Save edited event handler
        saveBtn.onclick = () => {
            const title = document.getElementById("addEventTitle").value.trim();
            const startTime = document.getElementById("addEventStart").value;
            const type = document.getElementById("addEventType").value;

            if (!title || !startTime || !type) {
                alert("Please fill in all required fields: Title, Start Time, and Type.");
                return;
            }

            fetch(`/calendar/update/${event.id}`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    title: title,
                    startTime: startTime,
                    endTime: document.getElementById("editEventEnd").value,
                    location: document.getElementById("editEventLocation").value,
                    color: document.getElementById("editEventColor").value,
                    type: type
                })
            }).then(() => {
                closeSidebarAndUnselect();
            });
        };

        // Delete event handler
        deleteBtn.onclick = () => {
            if (confirm("Delete this event? This action cannot be undone.")) {
                fetch(`/calendar/delete/${event.id}`, { method: "DELETE" }).then(() => {
                    closeSidebarAndUnselect();
                });
            }
        };

        // Cancel and close handlers
        cancelBtn.onclick = closeSidebarAndUnselect;
        if (closeBtn) closeBtn.onclick = closeSidebarAndUnselect;
    }

    // Helper to close the add event sidebar and refresh calendar
    window.closeAddEventSidebar = function() {
        document.getElementById("addEventSidebar").classList.remove("open");
        calendar.unselect();
        calendar.refetchEvents();
    };

    // Helper to close the edit event sidebar and refresh calendar
    window.closeEditEventSidebar = function() {
        document.getElementById("editEventSidebar").classList.remove("open");
        calendar.unselect();
        calendar.refetchEvents();
    };

    // ESC key closes any open sidebar overlays
    document.addEventListener("keydown", function(event) {
        if (event.key === "Escape") {
            const addEventSidebar = document.getElementById("addEventSidebar");
            if (addEventSidebar && addEventSidebar.classList.contains("open")) {
                addEventSidebar.classList.remove("open");
                calendar.unselect();
                calendar.refetchEvents();
            }
            const editEventSidebar = document.getElementById("editEventSidebar");
            if (editEventSidebar && editEventSidebar.classList.contains("open")) {
                editEventSidebar.classList.remove("open");
                calendar.unselect();
                calendar.refetchEvents();
            }
            const editCourseSidebar = document.getElementById("editCourseSidebar");
            if (editCourseSidebar && editCourseSidebar.classList.contains("open")) {
                editCourseSidebar.classList.remove("open");
                document.querySelectorAll('.sidebar-tabs .tab-btn').forEach(btn => btn.classList.remove('active'));
                const overviewBtn = document.querySelector('.sidebar-tabs .tab-btn[data-tab="overview"]');
                if (overviewBtn) overviewBtn.classList.add('active');
                document.querySelectorAll('.tab-content').forEach(tab => tab.style.display = "none");
                const overviewTab = document.getElementById("tab-overview");
                if (overviewTab) overviewTab.style.display = "block";
            }
            const courseSidebar = document.getElementById("courseSidebar");
            if (courseSidebar && courseSidebar.classList.contains("open")) {
                courseSidebar.classList.remove("open");
            }
        }
    });

    // Refresh calendar events when event type checkboxes are toggled
    document.querySelectorAll('#filter-controls input[type=checkbox]').forEach(cb => {
        cb.addEventListener('change', () => {
            calendar.refetchEvents();
        });
    });

    // Filter events by course name (if implemented)
    window.applyFilter = function () {
        const input = document.getElementById('course');
        currentCourseFilter = input.value;
        calendar.refetchEvents();
    };

    // Populate a select dropdown with all user courses
    function loadCoursesForDropdown(selectId) {
        fetch("/courses/user")
        .then(res => res.json())
        .then(courses => {
            const dropdown = document.getElementById(selectId);
            if (!dropdown) return;
            dropdown.innerHTML = '<option value="">None</option>';
            courses.forEach(course => {
                const opt = document.createElement("option");
                opt.value = course.id;
                opt.textContent = course.name;
                dropdown.appendChild(opt);
            });
        });
    }

    // ICS calendar import logic
    const fileInput = document.getElementById('file');
    const importButton = document.getElementById('import-button');
    if (importButton && fileInput) {
        importButton.addEventListener('click', () => {
            fileInput.click();
        });

        fileInput.addEventListener('change', () => {
            const loadingPopup = document.getElementById('loadingPopup');
            
            if (!fileInput.files || fileInput.files.length === 0) {
                console.warn("No file selected");
                return;
            }
            
            const formData = new FormData();
            formData.append('file', fileInput.files[0]);

            if (loadingPopup) {
                loadingPopup.style.display = 'flex';
            } else {
                console.warn("Loading popup element not found.");
            }

            fetch('/calendar/upload', {
                method: 'POST',
                body: formData
            })
            .then(res => {
                if (!res.ok) throw new Error('Upload failed');
                return res.text();
            })
            .then(() => {
                calendar.refetchEvents();
                loadCourses();
                loadUpcomingEvents();
                loadCoursesForDropdown("addEventCourse");
                loadCoursesForDropdown("editEventCourse");
                loadCoursesForDropdown("courseSelectForActions");
                alert('Calendar imported successfully!');
            })
            .catch(err => {
                alert(err.message);
            })
            .finally(() => {
                if (loadingPopup) loadingPopup.style.display = 'none';
            });
        });
    }

    // Helper to get a readable color for text on a given background color
    function getContrastingTextColor(hexColor) {
        const hex = hexColor.replace('#', '');
        const r = parseInt(hex.substr(0,2),16);
        const g = parseInt(hex.substr(2,2),16);
        const b = parseInt(hex.substr(4,2),16);
        const brightness = (r * 299 + g * 587 + b * 114) / 1000;
        return brightness > 125 ? '#000000' : '#ffffff';
    }

    // Load and display all user courses in the sidebar and dropdowns
    function loadCourses() {
        fetch("/courses/user")
        .then(response => {
            if (!response.ok) {
                throw new Error("Failed to fetch courses");
            }
            return response.json();
        })
        .then(courses => {
            const courseList = document.getElementById("course-list");
            courseList.innerHTML = "";

            if (!Array.isArray(courses) || courses.length === 0) {
                courseList.innerHTML = "<li class='placeholder'>No courses yet.</li>";
                const dropdown = document.getElementById("courseSelectForActions");
                if (dropdown) {
                    dropdown.innerHTML = '<option value="" disabled selected>Select course</option>';
                }
                return;
            }

            courses.forEach(course => {
                const progress = typeof course.progressPercent === "number" ? course.progressPercent : 0;
                const li = document.createElement("li");
                const textColor = getContrastingTextColor(course.color || '#ffffff');
                li.style.backgroundColor = course.color || '#eeeeee';
                li.style.color = textColor;
                li.classList.add('course-item');
                li.textContent = `${course.name} – ${progress}% complete`;
                li.dataset.id = course.id;
                li.style.cursor = "pointer";

                li.addEventListener("click", (e) => {
                    e.preventDefault();
                    openCourseDetailSidebar(course.id);
                });

                courseList.appendChild(li);
            });

            // Update course dropdown for actions
            const dropdown = document.getElementById("courseSelectForActions");
            if (dropdown) {
                dropdown.innerHTML = '<option value="" disabled selected>Select course</option>';
                courses.forEach(course => {
                    const option = document.createElement("option");
                    option.value = course.id;
                    option.textContent = course.name;
                    dropdown.appendChild(option);
                });
            }
        })
        .catch(error => {
            console.error("Error loading courses:", error);
            alert("Could not load courses.");
        });
    }

    // Load and display upcoming events in the sidebar
    function loadUpcomingEvents() {
        fetch("/calendar/upcoming?limit=4")
            .then(response => response.json())
            .then(events => {
                const upcomingList = document.getElementById("upcoming-events-list");
                upcomingList.innerHTML = "";
                if (!Array.isArray(events) || events.length === 0) {
                    upcomingList.innerHTML = "<li class='placeholder'>No upcoming events.</li>";
                    return;
                }
                events.forEach(event => {
                    const li = document.createElement("li");
                    const dateStr = new Date(event.startTime).toLocaleString();
                    li.textContent = `${event.title} (${dateStr})`;
                    upcomingList.appendChild(li);
                });
            })
            .catch(error => {
                console.error("Error loading upcoming events:", error);
                alert("Could not load upcoming events.");
            });
    }

    // Initial load of courses and upcoming events
    loadCourses();
    loadUpcomingEvents();

    // --- Course creation sidebar logic ---
    // Reference to the course creation sidebar
    const sidebar = document.getElementById('courseSidebar');
    const courseList = document.getElementById('course-list');

    // Open course creation sidebar when button is clicked
    const createCourseBtn = document.getElementById('create-course-btn');
    if (createCourseBtn) {
        createCourseBtn.addEventListener('click', () => {
            sidebar.classList.add('open');
        });
    }

    // Close course creation sidebar and reset fields
    window.closeSidebar = function () {
        sidebar.classList.remove('open');
        document.getElementById('courseName').value = "";
        document.getElementById('courseDescription').value = "";
    };

    // Save a new course to the backend
    window.saveCourse = function () {
        const nameInput = document.getElementById('courseName');
        const descInput = document.getElementById('courseDescription');

        const name = nameInput.value.trim();
        const description = descInput.value.trim();

        if (!name) {
            alert("Please enter a course name.");
            return;
        }

        fetch('/courses/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                name: name,
                description: description,
                color: '#4287f5'
            })
        })
        .then(res => {
            if (!res.ok) throw new Error("Failed to create course");
            return res.json();
        })
        .then(() => {
            closeSidebar();
            loadCourses();
        })
        .catch(error => {
            console.error("Error creating course:", error);
            alert("Could not create course.");
        });
    };

    // --- Course editing sidebar logic ---
    // Open the course detail sidebar and show the overview tab
    window.openCourseDetailSidebar = function(courseId) {
        window.currentCourseId = courseId;
        fetch(`/courses/details/${courseId}`)
            .then(res => res.json())
            .then(course => {
                const sidebar = document.getElementById("editCourseSidebar");
                sidebar.classList.add("open");
                document.querySelectorAll('.sidebar-tabs .tab-btn').forEach(btn => btn.classList.remove('active'));
                const overviewBtn = document.querySelector('.sidebar-tabs .tab-btn[data-tab="overview"]');
                if (overviewBtn) overviewBtn.classList.add('active');
                document.querySelectorAll('.tab-content').forEach(tab => tab.style.display = "none");
                const overviewTab = document.getElementById("tab-overview");
                if (overviewTab) overviewTab.style.display = "block";
                const nameInput = document.getElementById("editCourseName");
                if (nameInput) nameInput.value = course.name || "";
                const colorInput = document.getElementById("editCourseColor");
                if (colorInput) colorInput.value = course.color || "#4287f5";
                const diffInput = document.getElementById("editCourseDifficulty");
                if (diffInput) diffInput.value = course.difficulty || 1;
                const progress = course.progressPercent || 0;
                const progressBar = document.getElementById("editCourseProgressBar");
                if (progressBar) progressBar.style.width = `${progress}%`;
                const progressLabel = document.getElementById("editCourseProgressLabel");
                if (progressLabel) progressLabel.textContent = `${progress}%`;
                const overviewName = document.getElementById("overviewCourseName");
                if (overviewName) overviewName.textContent = course.name || "-";
                const overviewColorBox = document.getElementById("overviewCourseColorBox");
                if (overviewColorBox) overviewColorBox.style.background = course.color || "#4287f5";
                const overviewColor = document.getElementById("overviewCourseColor");
                if (overviewColor) overviewColor.textContent = course.color || "-";
                const overviewDiff = document.getElementById("overviewCourseDifficulty");
                if (overviewDiff) overviewDiff.textContent =
                    (["Easy", "Medium", "Hard"][course.difficulty - 1] || "-");
                const overviewProgressBar = document.getElementById("overviewCourseProgressBar");
                if (overviewProgressBar) overviewProgressBar.style.width = `${progress}%`;
                const overviewProgressLabel = document.getElementById("overviewCourseProgressLabel");
                if (overviewProgressLabel) overviewProgressLabel.textContent = `${progress}%`;
                const overviewStats = document.getElementById("overviewCourseStats");
                if (overviewStats) overviewStats.innerHTML = "";
            });
    };

    // --- API calls for course sidebar tabs (Deadlines & Lectures) ---
    // Helper to load deadlines for the current course and fill the deadlines table
    function loadCourseDeadlines(courseId) {
        fetch(`/api/courses/${courseId}/deadlines`)
            .then(res => res.json())
            .then(deadlines => {
                const tbody = document.querySelector("#deadlinesTable tbody");
                if (!tbody) return;
                tbody.innerHTML = "";
                if (!Array.isArray(deadlines) || deadlines.length === 0) {
                    tbody.innerHTML = "<tr><td colspan='3'>No deadlines yet.</td></tr>";
                    return;
                }
                deadlines.forEach(dl => {
                    const tr = document.createElement("tr");
                    tr.innerHTML = `
                        <td>${dl.title}</td>
                        <td>${(dl.startTime || "").slice(0,16).replace("T", " ")}</td>
                        <td>${dl.points ?? ""}</td>
                    `;
                    tbody.appendChild(tr);
                });
            });
    }

    // Helper to load lectures for the current course and fill the lectures table
    function loadCourseLectures(courseId) {
        fetch(`/courses/events/${courseId}`)
            .then(res => res.json())
            .then(events => {
                const tbody = document.querySelector("#lecturesTable tbody");
                if (!tbody) return;
                tbody.innerHTML = "";
                if (!Array.isArray(events) || events.length === 0) {
                    tbody.innerHTML = "<tr><td colspan='3'>No lectures yet.</td></tr>";
                    return;
                }
                events.filter(ev => ev.type && ev.type.toLowerCase() === "lecture").forEach(ev => {
                    const tr = document.createElement("tr");
                    tr.innerHTML = `
                        <td>${ev.title}</td>
                        <td>${(ev.startTime || "").slice(0,16).replace("T", " ")}</td>
                        <td>${(ev.endTime || "").slice(0,16).replace("T", " ")}</td>
                    `;
                    tbody.appendChild(tr);
                });
            });
    }

    // --- Tab switching logic for course sidebar (with API calls) ---
    document.querySelectorAll('.sidebar-tabs .tab-btn').forEach(btn => {
        btn.addEventListener('click', function () {
            document.querySelectorAll('.sidebar-tabs .tab-btn').forEach(b => b.classList.remove('active'));
            this.classList.add('active');
            document.querySelectorAll('.tab-content').forEach(tab => tab.style.display = "none");
            document.getElementById('tab-' + this.dataset.tab).style.display = "";

            // Settings tab: sync values from overview
            if (this.dataset.tab === "settings") {
                const name = document.getElementById("editCourseName");
                const desc = document.getElementById("editCourseDescription");
                const color = document.getElementById("editCourseColor");
                const diff = document.getElementById("editCourseDifficulty");
                if (document.getElementById("editCourseNameSettings") && name)
                    document.getElementById("editCourseNameSettings").value = name.value;
                if (document.getElementById("editCourseDescriptionSettings") && desc)
                    document.getElementById("editCourseDescriptionSettings").value = desc.value;
                if (document.getElementById("editCourseColorSettings") && color)
                    document.getElementById("editCourseColorSettings").value = color.value;
                if (document.getElementById("editCourseDifficultySettings") && diff)
                    document.getElementById("editCourseDifficultySettings").value = diff.value;
            }
            // Deadlines tab: load deadlines from API
            if (this.dataset.tab === "deadlines" && window.currentCourseId) {
                loadCourseDeadlines(window.currentCourseId);
            }
            // Lectures tab: load lectures from API
            if (this.dataset.tab === "lectures" && window.currentCourseId) {
                loadCourseLectures(window.currentCourseId);
            }
        });
    });

    // --- Also load deadlines/lectures when opening the sidebar directly on those tabs ---
    // (If user opens sidebar and a non-overview tab is already active)
    const activeTabBtn = document.querySelector('.sidebar-tabs .tab-btn.active');
    if (activeTabBtn && window.currentCourseId) {
        if (activeTabBtn.dataset.tab === "deadlines") {
            loadCourseDeadlines(window.currentCourseId);
        }
        if (activeTabBtn.dataset.tab === "lectures") {
            loadCourseLectures(window.currentCourseId);
        }
    }

    // Save course settings from the settings tab
    window.saveCourseSettings = function () {
        const id = window.currentCourseId;
        const nameInput = document.getElementById("editCourseNameSettings");
        const colorInput = document.getElementById("editCourseColorSettings");
        const diffInput = document.getElementById("editCourseDifficultySettings");
        const name = nameInput ? nameInput.value.trim() : "";
        const color = colorInput ? colorInput.value : "";
        const difficulty = diffInput ? diffInput.value : "";

        if (!name) {
            alert("Course name cannot be empty.");
            return;
        }

        fetch(`/courses/update/${id}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                name: name,
                color: color,
                difficulty: difficulty
            })
        })
        .then(() => {
            const nameEdit = document.getElementById("editCourseName");
            if (nameEdit) nameEdit.value = name;
            const colorEdit = document.getElementById("editCourseColor");
            if (colorEdit) colorEdit.value = color;
            const diffEdit = document.getElementById("editCourseDifficulty");
            if (diffEdit) diffEdit.value = difficulty;
            document.getElementById("editCourseSidebar").classList.remove("open");
            if (typeof loadCourses === "function") loadCourses();
            if (window.calendar && typeof window.calendar.refetchEvents === "function") window.calendar.refetchEvents();
        });
    };

    // Update course from overview tab (not settings)
    window.updateCourse = function () {
        const id = window.currentCourseId;
        const name = document.getElementById("editCourseName").value.trim();
        const description = document.getElementById("editCourseDescription").value.trim();
        const color = document.getElementById("editCourseColor").value;
        const difficulty = document.getElementById("editCourseDifficulty").value;

        if (!name) {
            alert("Course name cannot be empty.");
            return;
        }

        fetch(`/courses/update/${id}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                name: name,
                description: description,
                color: color,
                difficulty: difficulty
            })
        })
        .finally(() => {
            document.getElementById("editCourseSidebar").classList.remove("open");
            loadCourses();
        });
    };

    // Close the course editing sidebar and reset to overview tab
    window.closeEditSidebar = function () {
        const sidebar = document.getElementById("editCourseSidebar");
        sidebar.classList.remove("open");
        document.querySelectorAll('.sidebar-tabs .tab-btn').forEach(btn => btn.classList.remove('active'));
        const overviewBtn = document.querySelector('.sidebar-tabs .tab-btn[data-tab="overview"]');
        if (overviewBtn) overviewBtn.classList.add('active');
        document.querySelectorAll('.tab-content').forEach(tab => tab.style.display = "none");
        const overviewTab = document.getElementById("tab-overview");
        if (overviewTab) overviewTab.style.display = "block";
    };

    // Delete a course from the backend
    window.deleteCourse = function () {
        if (!window.currentCourseId) return;
        if (!confirm("Are you sure you want to delete this course? This cannot be undone.")) return;

        fetch(`/courses/delete/${window.currentCourseId}`, {
            method: 'DELETE'
        })
        .then(res => {
            if (!res.ok) throw new Error("Failed to delete course");
            document.getElementById("editCourseSidebar").classList.remove("open");
            loadCourses();
        })
        .catch(err => {
            console.error("Error deleting course:", err);
            alert("Could not delete course.");
        });
    };

    // Ensure closeSidebar and closeEditSidebar exist for close button handlers
    if (typeof window.closeSidebar !== "function") {
        window.closeSidebar = function () {
            const sidebar = document.getElementById('courseSidebar');
            if (sidebar) sidebar.classList.remove('open');
            const nameInput = document.getElementById('courseName');
            if (nameInput) nameInput.value = "";
            const descInput = document.getElementById('courseDescription');
            if (descInput) descInput.value = "";
        };
    }
    if (typeof window.closeEditSidebar !== "function") {
        window.closeEditSidebar = function () {
            const sidebar = document.getElementById("editCourseSidebar");
            if (sidebar) sidebar.classList.remove("open");
            document.querySelectorAll('.sidebar-tabs .tab-btn').forEach(btn => btn.classList.remove('active'));
            const overviewBtn = document.querySelector('.sidebar-tabs .tab-btn[data-tab="overview"]');
            if (overviewBtn) overviewBtn.classList.add('active');
            document.querySelectorAll('.tab-content').forEach(tab => tab.style.display = "none");
            const overviewTab = document.getElementById("tab-overview");
            if (overviewTab) overviewTab.style.display = "block";
        };
    }

    // Attach close handlers to sidebar close buttons
    const closeCourseSidebarBtn = document.getElementById('closeCourseSidebarBtn');
    if (closeCourseSidebarBtn) {
        closeCourseSidebarBtn.addEventListener('click', window.closeSidebar);
    }
    const closeEditCourseSidebarBtn = document.getElementById('closeEditCourseSidebarBtn');
    if (closeEditCourseSidebarBtn) {
        closeEditCourseSidebarBtn.addEventListener('click', window.closeEditSidebar);
    }

    // Attach delete handler for deleteCourseButtonOverview to delete course
    const deleteBtnOverview = document.getElementById("deleteCourseButtonOverview");
    if (deleteBtnOverview) {
        deleteBtnOverview.onclick = function () {
            if (!window.currentCourseId) return;
            if (!confirm("Are you sure you want to delete this course? This cannot be undone.")) return;
            fetch(`/courses/delete/${window.currentCourseId}`, {
                method: 'DELETE'
            })
            .then(res => {
                if (!res.ok) throw new Error("Failed to delete course");
                document.getElementById("editCourseSidebar").classList.remove("open");
                if (typeof loadCourses === "function") loadCourses();
                if (window.calendar && typeof window.calendar.refetchEvents === "function") window.calendar.refetchEvents();
            })
            .catch(err => {
                console.error("Error deleting course:", err);
                alert("Could not delete course.");
            });
        };
    }

    // Attach handler for manage-deadlines-btn to redirect to new page 
    const manageDeadlinesBtn = document.getElementById("manage-deadlines-btn");
    if (manageDeadlinesBtn) {
        manageDeadlinesBtn.addEventListener("click", function () {
            window.location.href = "/manage-deadlines";
        });
    }
});