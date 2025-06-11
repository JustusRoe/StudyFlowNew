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

document.addEventListener('DOMContentLoaded', function () {
    console.log("✅ calendar.js is loaded and running!");

    const calendarEl = document.getElementById('calendar');
    let currentCourseFilter = "";

    /* --- Initialize FullCalendar --- */
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

        /* --- Load and filter events --- */
        events: function (info, successCallback, failureCallback) {
            fetch('/calendar/events')
                .then(response => {
                    if (!response.ok) {
                        // Try to parse error message if possible
                        return response.text().then(text => {
                            console.error("calendar/events error:", text);
                            failureCallback && failureCallback("Server error: could not load events.");
                            return [];
                        });
                    }
                    return response.json();
                })
                .then(allEvents => {
                    // Defensive: Only filter if allEvents is an array
                    if (!Array.isArray(allEvents)) {
                        console.error("calendar/events did not return an array:", allEvents);
                        failureCallback && failureCallback("Server error: could not load events.");
                        return;
                    }
                    const selectedTypes = Array.from(
                        document.querySelectorAll('#filter-controls input[type=checkbox]:checked')
                    ).map(cb => cb.value.toLowerCase());

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

        // lecture events show color dot + time + title only
        eventContent: function (arg) {
        const { event } = arg;
        if (event.extendedProps.type === "lecture") {
            const dot = document.createElement("span");
            dot.style.backgroundColor = event.backgroundColor;
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
        return true; // default rendering
        },

        // clicking empty calendar cell to add new event
        select: openAddEventSidebar,

        // click existing event to open edit/delete modal
        eventClick: function(info) {
            openEditEventSidebar(info.event);
        }
    });

    calendar.render();
    window.calendar = calendar;

    // --- Add Event Sidebar ---
    function openAddEventSidebar(info) {
        const sidebar = document.getElementById("addEventSidebar");
        sidebar.classList.add("open");

        document.getElementById("addEventTitle").value = "";
        // Fix: Use correct format for datetime-local input
        document.getElementById("addEventStart").value = info.startStr.slice(0, 16);
        document.getElementById("addEventEnd").value = info.endStr.slice(0, 16);
        document.getElementById("addEventLocation").value = "";
        document.getElementById("addEventColor").value = "#4285F4";
        document.getElementById("addEventType").value = "custom";

        loadCoursesForDropdown("addEventCourse");

        const saveBtn = document.getElementById("saveNewEvent");
        const cancelBtn = document.getElementById("cancelNewEvent");
        const closeBtn = sidebar.querySelector(".close-sidebar");

        saveBtn.onclick = null;
        cancelBtn.onclick = null;
        if (closeBtn) closeBtn.onclick = null;

        function closeSidebarAndUnselect() {
            sidebar.classList.remove("open");
            calendar.unselect();
            calendar.refetchEvents();
        }

        saveBtn.onclick = function () {
            const newEvent = {
                title: document.getElementById("addEventTitle").value,
                startTime: document.getElementById("addEventStart").value,
                endTime: document.getElementById("addEventEnd").value,
                location: document.getElementById("addEventLocation").value,
                type: document.getElementById("addEventType").value,
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

        cancelBtn.onclick = closeSidebarAndUnselect;
        if (closeBtn) closeBtn.onclick = closeSidebarAndUnselect;
    }

    // --- Edit Event Sidebar ---
    function openEditEventSidebar(event) {
        const sidebar = document.getElementById("editEventSidebar");
        sidebar.classList.add("open");

        document.getElementById("editEventId").value = event.id;
        document.getElementById("editEventTitle").value = event.title;
        document.getElementById("editEventStart").value = event.startStr.slice(0, 16);
        document.getElementById("editEventEnd").value = event.endStr.slice(0, 16);
        document.getElementById("editEventLocation").value = event.extendedProps.location || "";
        document.getElementById("editEventColor").value = event.backgroundColor;
        document.getElementById("editEventType").value = event.extendedProps.type || "custom";

        loadCoursesForDropdown("editEventCourse");

        const saveBtn = document.getElementById("saveEditedEvent");
        const deleteBtn = document.getElementById("deleteEvent");
        const cancelBtn = document.getElementById("cancelEditEvent");
        const closeBtn = sidebar.querySelector(".close-sidebar");

        saveBtn.onclick = null;
        deleteBtn.onclick = null;
        cancelBtn.onclick = null;
        if (closeBtn) closeBtn.onclick = null;

        function closeSidebarAndUnselect() {
            sidebar.classList.remove("open");
            calendar.unselect();
            calendar.refetchEvents();
        }

        saveBtn.onclick = () => {
            fetch(`/calendar/update/${event.id}`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    title: document.getElementById("editEventTitle").value,
                    startTime: document.getElementById("editEventStart").value,
                    endTime: document.getElementById("editEventEnd").value,
                    location: document.getElementById("editEventLocation").value,
                    color: document.getElementById("editEventColor").value,
                    type: document.getElementById("editEventType").value
                })
            }).then(() => {
                closeSidebarAndUnselect();
            });
        };

        deleteBtn.onclick = () => {
            if (confirm("Delete this event? This action cannot be undone.")) {
                fetch(`/calendar/delete/${event.id}`, { method: "DELETE" }).then(() => {
                    closeSidebarAndUnselect();
                });
            }
        };

        cancelBtn.onclick = closeSidebarAndUnselect;
        if (closeBtn) closeBtn.onclick = closeSidebarAndUnselect;
    }

    // Sidebar close helpers for HTML close buttons
    window.closeAddEventSidebar = function() {
        document.getElementById("addEventSidebar").classList.remove("open");
        calendar.unselect();
        calendar.refetchEvents();
    };
    window.closeEditEventSidebar = function() {
        document.getElementById("editEventSidebar").classList.remove("open");
        calendar.unselect();
        calendar.refetchEvents();
    };

    // --- ESC handler for all sidebars ---
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
            }
            const courseSidebar = document.getElementById("courseSidebar");
            if (courseSidebar && courseSidebar.classList.contains("open")) {
                courseSidebar.classList.remove("open");
            }
        }
    });

    /* --- Event Type Checkboxes --- */
    document.querySelectorAll('#filter-controls input[type=checkbox]').forEach(cb => {
        cb.addEventListener('change', () => {
            calendar.refetchEvents();
        });
    });

    /* --- Course Name Filter (text input) --- */
    window.applyFilter = function () {
        const input = document.getElementById('course');
        currentCourseFilter = input.value;
        calendar.refetchEvents();
    };

    // Load course list for dropdown (add/edit modal)
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

    /* --- ICS Upload --- */
    const fileInput = document.getElementById('file');
    const importButton = document.getElementById('import-button');

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
            loadingPopup.style.display = 'none'; // removes loading popup
        });
    });

    /* --- Color for Event Types --- */
    function getColorForEventType(type) {
        switch (type.toLowerCase()) {
            case 'lecture': return '#4285F4';
            case 'assignment': return '#0F9D58';
            case 'exam': return '#DB4437';
            case 'self-study': return '#F4B400';
            default: return '#aaaaaa';
        }
    }

    /* ---------------------------------------
       📌 Sidebar für "Create Course"
    ---------------------------------------- */

    const sidebar = document.getElementById('courseSidebar');
    const courseList = document.getElementById('course-list');

    // Öffnen
    document.getElementById('create-course-btn').addEventListener('click', () => {
        sidebar.classList.add('open');
    });

    // Schließen
    window.closeSidebar = function () {
        sidebar.classList.remove('open');
        document.getElementById('courseName').value = "";
        document.getElementById('courseDescription').value = "";
    };

    // Speichern
    window.saveCourse = function () {
        const nameInput = document.getElementById('courseName');
        const descInput = document.getElementById('courseDescription');

        const name = nameInput.value.trim();
        const description = descInput.value.trim();

        if (!name) {
            alert("Please enter a course name.");
            return;
        }

        // Kurs an Backend senden
        fetch('/courses/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                name: name,
                description: description,
                color: '#4287f5' // feste Farbe – optional dynamisch machen
            })
        })
        .then(res => {
            if (!res.ok) throw new Error("Failed to create course");
            return res.json();
        })
        .then(() => {
            closeSidebar();
            loadCourses(); // 🔁 Liste komplett neu laden!
        })
        .catch(error => {
            console.error("Error creating course:", error);
            alert("Could not create course.");
        });
    };

    /* ---------------------------------------
       📌 Sidebar für "Edit Course"
    ---------------------------------------- */

    window.closeEditSidebar = function () {
        const sidebar = document.getElementById("editCourseSidebar");
        sidebar.classList.remove("open");
        document.getElementById("editCourseName").value = "";
        document.getElementById("editCourseDescription").value = "";
        document.getElementById("editCourseColor").value = "#000000";
        const deleteBtn = document.getElementById("deleteCourseButton");
        if (deleteBtn) {
            deleteBtn.style.display = "none";
            deleteBtn.onclick = null;
        }
        const eventList = document.getElementById("eventList");
        if(eventList) {
            eventList.innerHTML = "";
        }
    };

    window.updateCourse = function () {
        const id = window.currentCourseId;
        const name = document.getElementById("editCourseName").value.trim();
        const description = document.getElementById("editCourseDescription").value.trim();
        const color = document.getElementById("editCourseColor").value;

        if (!name) {
            alert("Course name cannot be empty.");
            return;
        }

        fetch(`/courses/update/${id}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, description, color })
        })
        .finally(() => {
            // Always close sidebar and reload course list
            document.getElementById("editCourseSidebar").classList.remove("open");
            loadCourses();
        });
    };

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

    function getContrastingTextColor(hexColor) {
        const hex = hexColor.replace('#', '');
        const r = parseInt(hex.substr(0,2),16);
        const g = parseInt(hex.substr(2,2),16);
        const b = parseInt(hex.substr(4,2),16);
        const brightness = (r * 299 + g * 587 + b * 114) / 1000;
        return brightness > 125 ? '#000000' : '#ffffff';
    }

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
                const li = document.createElement("li");
                const progress = course.progressPercent ?? 0;
                const textColor = getContrastingTextColor(course.color || '#ffffff');
                li.style.backgroundColor = course.color || '#eeeeee';
                li.style.color = textColor;
                li.classList.add('course-item');
                li.textContent = `${course.name} – ${progress}% complete`;
                li.dataset.id = course.id;
                li.style.cursor = "pointer";

                li.addEventListener("click", () => {
                    openCourseDetailSidebar(course.id);
                });

                courseList.appendChild(li);
            });

            // Dropdown für zentrale Kursaktionen aktualisieren
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
    // Load all
    loadCourses();
    loadUpcomingEvents();

    // --- Course Sidebar/Modal logic ---
    function openCourseDetailSidebar(courseId) {
        fetch(`/courses/details/${courseId}`)
            .then(res => res.json())
            .then(course => {
                const sidebar = document.getElementById("courseDetailSidebar");
                sidebar.querySelector(".course-title").textContent = course.name;
                sidebar.querySelector(".course-desc").innerHTML = `
                    <div style="margin-bottom:0.5rem;"><strong>Description:</strong> ${course.description || "-"}</div>
                    <div style="margin-bottom:0.5rem;"><strong>Difficulty:</strong> ${["Easy", "Medium", "Hard"][course.difficulty - 1] || "Unknown"}</div>
                    <div style="margin-bottom:0.5rem;"><strong>Self-study:</strong> ${course.selfStudyHours || 0}h of ${course.workloadTarget || 0}h</div>
                `;
                sidebar.querySelector(".course-difficulty").style.display = "none";
                sidebar.querySelector(".course-selfstudy").style.display = "none";
                sidebar.querySelector(".course-progress-bar-inner").style.width = `${course.progressPercent || 0}%`;
                sidebar.querySelector(".course-progress-label").textContent = `${course.progressPercent || 0}%`;
                const fish = sidebar.querySelector(".mascot-fish");
                fish.style.transform = `scale(${0.7 + (course.progressPercent || 0) / 100 * 1.3})`;
                sidebar.querySelector(".manage-deadlines-btn").onclick = () => {
                    window.location.href = `/manage-deadlines?courseId=${courseId}`;
                };
                sidebar.querySelector(".plan-selfstudy-btn").onclick = () => {
                    window.location.href = `/plan-selfstudy?courseId=${courseId}`;
                };
                sidebar.classList.add("open");
            });
    }

    // Attach click handler to course list items after loading courses
    function loadCourses() {
        fetch("/courses/user")
            .then(response => response.json())
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
                    const li = document.createElement("li");
                    const progress = course.progressPercent ?? 0;
                    const textColor = getContrastingTextColor(course.color || '#ffffff');
                    li.style.backgroundColor = course.color || '#eeeeee';
                    li.style.color = textColor;
                    li.classList.add('course-item');
                    li.textContent = `${course.name} – ${progress}% complete`;
                    li.dataset.id = course.id;
                    li.style.cursor = "pointer";

                    li.addEventListener("click", () => {
                        openCourseDetailSidebar(course.id);
                    });

                    courseList.appendChild(li);
                });

                // Dropdown für zentrale Kursaktionen aktualisieren
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

    // --- Course Detail Sidebar close logic ---
    window.closeCourseDetailSidebar = function() {
        document.getElementById("courseDetailSidebar").classList.remove("open");
    };

    // Optional: Close course detail sidebar on ESC or click outside
    document.addEventListener("keydown", function(event) {
        if (event.key === "Escape") {
            const sidebar = document.getElementById("courseDetailSidebar");
            if (sidebar && sidebar.classList.contains("open")) {
                sidebar.classList.remove("open");
            }
        }
    });
    document.addEventListener("mousedown", function(event) {
        const sidebar = document.getElementById("courseDetailSidebar");
        if (sidebar && sidebar.classList.contains("open")) {
            if (event.target === sidebar) {
                sidebar.classList.remove("open");
            }
        }
    });
});