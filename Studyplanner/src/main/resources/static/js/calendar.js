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

    /* --- Initialisierung von FullCalendar --- */
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

        /* --- Events laden --- */
        events: function (info, successCallback, failureCallback) {
            const url = `/calendar/events?start=${info.startStr}&end=${info.endStr}` +
                        (currentCourseFilter ? `&course=${encodeURIComponent(currentCourseFilter)}` : "");

            fetch(url)
                .then(response => response.json())
                .then(allEvents => {
                    const selectedTypes = Array.from(
                        document.querySelectorAll('#filter-controls input[type=checkbox]:checked')
                    ).map(cb => cb.value.toLowerCase());

                    const filtered = allEvents.filter(event =>
                        event.type && selectedTypes.includes(event.type.toLowerCase())
                    );
                    successCallback(filtered);
                })
                .catch(failureCallback);
        },

        /* --- Event erstellen --- */
        select: function (info) {
            const title = prompt('New event title:');
            if (!title) return;

            const description = prompt('Description?') || 'No description';
            const type = prompt('Type? (lecture, assignment, exam, self-study, custom)') || 'custom';

            fetch('/calendar/create', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    title,
                    description,
                    type,
                    color: getColorForEventType(type),
                    startTime: info.startStr,
                    endTime: info.endStr
                })
            })
            .then(res => res.json())
            .then(() => calendar.refetchEvents());
        },

        /* --- Event bearbeiten oder löschen --- */
        eventClick: function (info) {
            const title = info.event.title;
            const desc = info.event.extendedProps.description || 'No description';
            const edit = confirm(`Event: ${title}\n${desc}\n\nEdit this event? (Cancel to delete)`);

            if (edit) {
                const newTitle = prompt("New title:", title);
                if (newTitle) {
                    fetch(`/calendar/update/${info.event.id}`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            title: newTitle,
                            startTime: info.event.startStr,
                            endTime: info.event.endStr,
                            color: info.event.backgroundColor,
                            description: desc
                        })
                    }).then(() => calendar.refetchEvents());
                }
            } else {
                if (confirm("Are you sure to delete this event?")) {
                    fetch(`/calendar/delete/${info.event.id}`, {
                        method: 'DELETE'
                    }).then(() => calendar.refetchEvents());
                }
            }
        }
    });

    calendar.render();

    loadCourses();
    loadUpcomingEvents();

    /* --- Typfilter Checkboxen --- */
    document.querySelectorAll('#filter-controls input[type=checkbox]').forEach(cb => {
        cb.addEventListener('change', () => {
            calendar.refetchEvents();
        });
    });

    /* --- Kursfilter (Text) --- */
    window.applyFilter = function () {
        const input = document.getElementById('course');
        currentCourseFilter = input.value;
        calendar.refetchEvents();
    };

    /* --- ICS Upload --- */
    const fileInput = document.getElementById('file');
    const importButton = document.getElementById('import-button');

    importButton.addEventListener('click', () => {
        fileInput.click();
    });

    fileInput.addEventListener('change', () => {
        const formData = new FormData();
        formData.append('file', fileInput.files[0]);

        const loadingPopup = document.getElementById('loadingPopup');
        loadingPopup.style.display = 'flex'; // shows loading popup

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
            alert('Calendar imported successfully!');
        })
        .catch(err => {
            alert(err.message);
        })
        .finally(() => {
            loadingPopup.style.display = 'none'; // removes loading popup
        });
    });

    /* --- Farbe für Eventtypen --- */
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
            .then(response => response.json())
            .then(courses => {
                const courseList = document.getElementById("course-list");
                courseList.innerHTML = "";

                if (!Array.isArray(courses) || courses.length === 0) {
                    courseList.innerHTML = "<li class='placeholder'>No courses yet.</li>";
                    return;
                }

                courses.forEach(course => {
                    const li = document.createElement("li");
                    const progress = course.progressPercent ?? 0;
                    // Set background and text color based on course color
                    const textColor = getContrastingTextColor(course.color || '#ffffff');
                    li.style.backgroundColor = course.color || '#eeeeee';
                    li.style.color = textColor;
                    li.classList.add('course-item');
                    li.textContent = `${course.name} – ${progress}% complete`;
                    li.dataset.id = course.id;
                    li.style.cursor = "pointer";

                    li.addEventListener("click", () => {
                        fetch(`/courses/description/${course.id}`)
                            .then(res => {
                                if (!res.ok) throw new Error("Failed to fetch course details");
                                return res.json();
                            })
                            .then(data => {
                                document.getElementById("editCourseName").value = data.name;
                                document.getElementById("editCourseDescription").value = data.description || "";
                                document.getElementById("editCourseColor").value = data.color || "#000000";
                                document.getElementById("editCourseSidebar").classList.add("open");
                                window.currentCourseId = data.id;

                                const deleteBtn = document.getElementById("deleteCourseButton");
                                if (deleteBtn) {
                                    deleteBtn.style.display = "block";
                                    deleteBtn.onclick = deleteCourse;
                                }

                                getCourseEvents(data.id);
                            })
                            .catch(err => {
                                console.error("Error loading course details:", err);
                                alert("Could not load course details.");
                            });
                    });

                    courseList.appendChild(li);
                });
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
});
    // Add Calendar Event to Course button handler: prompt for title, start, end
    const addEventToCourseBtn = document.getElementById("addEventToCourseBtn");
    if (addEventToCourseBtn) {
        addEventToCourseBtn.addEventListener("click", () => {
            const courseId = window.currentCourseId;
            if (!courseId) return;

            const title = prompt("Event Title:", "New Course Event");
            if (!title) return;

            const now = new Date();
            const startDefault = now.toISOString().slice(0, 16);
            const endDefault = new Date(now.getTime() + 60 * 60 * 1000).toISOString().slice(0, 16);
            const startInput = prompt("Start Time (YYYY-MM-DDTHH:MM)", startDefault);
            if (!startInput) return;

            const endInput = prompt("End Time (YYYY-MM-DDTHH:MM)", endDefault);
            if (!endInput) return;

            const newEvent = {
                title: title,
                description: "Course-linked event",
                type: "custom",
                color: document.getElementById("editCourseColor").value || "#aaaaaa",
                startTime: startInput,
                endTime: endInput,
                courseId: courseId
            };

            fetch("/courses/events/add", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(newEvent)
            })
            .then(res => {
                if (!res.ok) throw new Error("Failed to add event to course");
                return res.json();
            })
            .then(() => {
                getCourseEvents(courseId);
            })
            .catch(err => {
                
            });
        });
    }