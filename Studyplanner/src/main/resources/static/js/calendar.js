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

        eventContent: function (arg) {
            const { event, el } = arg;
            const type = event.extendedProps.type;
            const fillType = event.extendedProps.fillType;
            const color = event.backgroundColor;

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

            if (type === "custom") {
                if (fillType === "partial-fill") {
                arg.el.style.background = `linear-gradient(to right, ${color} 50%, transparent 50%)`;
                }
            }

            return true; // assignment, exam, custom(complete-fill)
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
        console.log("Opening add sidebar", info);
        const sidebar = document.getElementById("addEventSidebar");
        sidebar.classList.add("open");

        document.getElementById("addEventTitle").value = "";
        // Fix: Use correct format for datetime-local input
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

            calendar.setOption('selectable', false);
            setTimeout(() => calendar.setOption('selectable', true), 0);
        }

        saveBtn.onclick = function () {
            const title = document.getElementById("addEventTitle").value.trim();
            const startTime = document.getElementById("addEventStart").value;
            const type = document.getElementById("addEventType").value;

            if (!title || !startTime || !type) {
                alert("Please fill in all required fields: Title, Start Time, and Type.");
                return;
            }

            const fillTypeInput = document.getElementById("addEventFillType");

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

    // Öffnet die Sidebar mit Kursdetails und Editiermöglichkeiten
    window.openCourseDetailSidebar = function(courseId) {
        window.currentCourseId = courseId;
        fetch(`/courses/details/${courseId}`)
            .then(res => res.json())
            .then(course => {
                const sidebar = document.getElementById("editCourseSidebar");
                sidebar.classList.add("open");
                // Overview Tab
                document.getElementById("editCourseName").value = course.name;
                document.getElementById("editCourseDescription").value = course.description || "";
                document.getElementById("editCourseColor").value = course.color || "#4287f5";
                document.getElementById("editCourseDifficulty").value = course.difficulty || 1;
                // Progress
                const progress = course.progressPercent || 0;
                document.getElementById("editCourseProgressBar").style.width = `${progress}%`;
                document.getElementById("editCourseProgressLabel").textContent = `${progress}%`;
                // Stats
                document.getElementById("editCourseStats").innerHTML =
                    `<div>Self-study: ${course.selfStudyHours || 0}h of ${course.workloadTarget || 0}h</div>`;

                // Deadlines Tab
                fetch(`/api/courses/${courseId}/deadlines`)
                    .then(res => res.json())
                    .then(deadlines => {
                        const ul = document.getElementById("editCourseDeadlinesList");
                        ul.innerHTML = "";
                        if (!Array.isArray(deadlines) || deadlines.length === 0) {
                            ul.innerHTML = "<li class='placeholder'>No deadlines.</li>";
                            return;
                        }
                        deadlines.forEach(dl => {
                            const li = document.createElement("li");
                            li.textContent = `${dl.title} (${(dl.startTime || "").slice(0,16).replace("T", " ")}) – ${dl.points ?? ""} pts`;
                            ul.appendChild(li);
                        });
                    });

                // Lectures Tab
                fetch(`/courses/events/${courseId}`)
                    .then(res => res.json())
                    .then(events => {
                        const ul = document.getElementById("editCourseLecturesList");
                        ul.innerHTML = "";
                        if (!Array.isArray(events) || events.length === 0) {
                            ul.innerHTML = "<li class='placeholder'>No lectures.</li>";
                            return;
                        }
                        events.filter(ev => ev.type && ev.type.toLowerCase() === "lecture").forEach(ev => {
                            const li = document.createElement("li");
                            li.textContent = `${ev.title} (${(ev.startTime || "").slice(0,16).replace("T", " ")})`;
                            ul.appendChild(li);
                        });
                    });

                // Show delete button if allowed
                const deleteBtn = document.getElementById("deleteCourseButton");
                if (deleteBtn) {
                    deleteBtn.style.display = "block";
                    deleteBtn.onclick = window.deleteCourse;
                }
            });
    };

    // Tab switching logic (add settings tab logic)
    document.addEventListener("DOMContentLoaded", function () {
        document.querySelectorAll('.sidebar-tabs .tab-btn').forEach(btn => {
            btn.addEventListener('click', function () {
                document.querySelectorAll('.sidebar-tabs .tab-btn').forEach(b => b.classList.remove('active'));
                this.classList.add('active');
                document.querySelectorAll('.tab-content').forEach(tab => tab.style.display = "none");
                document.getElementById('tab-' + this.dataset.tab).style.display = "";
                // Settings tab: sync values from overview
                if (this.dataset.tab === "settings") {
                    document.getElementById("editCourseNameSettings").value = document.getElementById("editCourseName").value;
                    document.getElementById("editCourseDescriptionSettings").value = document.getElementById("editCourseDescription").value;
                    document.getElementById("editCourseColorSettings").value = document.getElementById("editCourseColor").value;
                    document.getElementById("editCourseDifficultySettings").value = document.getElementById("editCourseDifficulty").value;
                }
            });
        });
    });

    // Save settings from settings tab
    window.saveCourseSettings = function () {
        const id = window.currentCourseId;
        const name = document.getElementById("editCourseNameSettings").value.trim();
        const description = document.getElementById("editCourseDescriptionSettings").value.trim();
        const color = document.getElementById("editCourseColorSettings").value;
        // Wert aus Dropdown ist bereits "1", "2" oder "3"
        const difficulty = document.getElementById("editCourseDifficultySettings").value;

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
                difficulty: difficulty // als String "1", "2", "3"
            })
        })
        .then(() => {
            document.getElementById("editCourseName").value = name;
            document.getElementById("editCourseDescription").value = description;
            document.getElementById("editCourseColor").value = color;
            document.getElementById("editCourseDifficulty").value = difficulty;
            alert("Course settings saved!");
        });
    };

    // Update course with difficulty (from overview tab)
    window.updateCourse = function () {
        const id = window.currentCourseId;
        const name = document.getElementById("editCourseName").value.trim();
        const description = document.getElementById("editCourseDescription").value.trim();
        const color = document.getElementById("editCourseColor").value;
        // Wert aus Dropdown ist bereits "1", "2" oder "3"
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
                difficulty: difficulty // als String "1", "2", "3"
            })
        })
        .finally(() => {
            document.getElementById("editCourseSidebar").classList.remove("open");
            loadCourses();
        });
    };

    window.closeEditSidebar = function () {
        document.getElementById("editCourseSidebar").classList.remove("open");
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
                // Always use backend value for progressPercent
                const progress = typeof course.progressPercent === "number" ? course.progressPercent : 0;
                const li = document.createElement("li");
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
});