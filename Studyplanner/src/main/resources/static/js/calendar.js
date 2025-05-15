document.addEventListener('DOMContentLoaded', function () {
    console.log("✅ calendar.js is loaded and running!");

    const calendarEl = document.getElementById('calendar');
    let currentCourseFilter = "";

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
                .catch(error => failureCallback(error));
        },

        select: function (info) {
            const title = prompt('New event title:');
            if (!title) return;

            const description = prompt('Description?') || 'No description';
            const type = prompt('Type? (lecture, assignment, exam, self-study, custom)') || 'custom';

            fetch('/calendar/create', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    title,
                    description,
                    type,
                    color: getColorForEventType(type),
                    startTime: info.startStr,
                    endTime: info.endStr
                })
            })
            .then(response => response.json())
            .then(() => {
                calendar.refetchEvents();
            });
        },

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

    // Course filter input (if applicable)
    window.applyFilter = function () {
        const input = document.getElementById('course');
        currentCourseFilter = input.value;
        calendar.refetchEvents();
    };

    // Type checkboxes
    document.querySelectorAll('#filter-controls input[type=checkbox]').forEach(cb => {
        cb.addEventListener('change', () => {
            calendar.refetchEvents();
        });
    });

    // ICS upload
    const fileInput = document.getElementById('file');
    fileInput.addEventListener('change', () => {
        const formData = new FormData();
        formData.append('file', fileInput.files[0]);

        fetch('/calendar/upload', {
            method: 'POST',
            body: formData
        })
        .then(response => {
            if (!response.ok) throw new Error('Upload failed');
            return response.text();
        })
        .then(() => {
            alert('Calendar imported successfully!');
            calendar.refetchEvents();
        })
        .catch(err => alert(err.message));
    });

    // Color helper
    function getColorForEventType(type) {
        switch (type.toLowerCase()) {
            case 'lecture': return '#4285F4';
            case 'assignment': return '#0F9D58';
            case 'exam': return '#DB4437';
            case 'self-study': return '#F4B400';
            default: return '#aaaaaa';
        }
    }
});
