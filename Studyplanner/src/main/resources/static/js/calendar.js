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

        fetch('/calendar/upload', {
            method: 'POST',
            body: formData
        })
        .then(res => {
            if (!res.ok) throw new Error('Upload failed');
            return res.text();
        })
        .then(() => {
            alert('Calendar imported successfully!');
            calendar.refetchEvents();
        })
        .catch(err => alert(err.message));
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
        const name = document.getElementById('courseName').value.trim();
        const description = document.getElementById('courseDescription').value.trim();

        if (!name) {
            alert("Please enter a course name.");
            return;
        }

        // 🔧 TO-DO: Backend POST /courses/create

        // Simulierter Eintrag im Frontend
        if (document.querySelector('.placeholder')) courseList.innerHTML = "";

        const li = document.createElement('li');
        li.textContent = name;
        courseList.appendChild(li);

        closeSidebar();
    };
});
