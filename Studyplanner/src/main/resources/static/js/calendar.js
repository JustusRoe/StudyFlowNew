document.addEventListener('DOMContentLoaded', function () {
    let calendarEl = document.getElementById('calendar');
    let currentCourseFilter = "";

    let calendar = new FullCalendar.Calendar(calendarEl, {
        initialView: 'dayGridMonth',
        selectable: true,
        editable: false,
        eventDisplay: 'block',
        headerToolbar: {
            left: 'prev today next',
            center: 'title',
            right: 'month,week,day'
        },
        events: function (info, successCallback, failureCallback) {
            const url = `/calendar/events?start=${info.startStr}&end=${info.endStr}` + 
                        (currentCourseFilter ? `&course=${encodeURIComponent(currentCourseFilter)}` : "");
            fetch(url)
                .then(response => response.json())
                .then(events => successCallback(events))
                .catch(error => failureCallback(error));
        },
        select: function (info) {
            const title = prompt('Title?');
            const description = prompt('Description?');
            const type = prompt('Type? (lecture, assignment, etc)');

            if (title) {
                fetch('/calendar/create', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        title,
                        description,
                        type,
                        color: "#aaaaaa",
                        startTime: info.startStr,
                        endTime: info.endStr
                    })
                })
                .then(response => response.json())
                .then(event => {
                    calendar.addEvent({
                        id: event.id,
                        title: event.title,
                        start: event.startTime,
                        end: event.endTime,
                        color: event.color,
                        description: event.description
                    });
                });
            }
        },
        eventClick: function (info) {
            const confirmed = confirm("Event: " + info.event.title + "\n" + info.event.extendedProps.description + "\n\nDelete this event?");
            if (confirmed) {
                fetch('/calendar/delete/' + info.event.id, {
                    method: 'DELETE'
                }).then(() => {
                    info.event.remove();
                });
            }
        }
    });

    calendar.render();

    // Filter input handler
    window.applyFilter = function () {
        const input = document.getElementById('course');
        currentCourseFilter = input.value;
        calendar.refetchEvents();
    };

    // ICS upload handler
    const form = document.getElementById('icsUploadForm');
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
            calendar.refetchEvents();
            alert('Calendar imported successfully!');
        })
        .catch(err => alert(err.message));
    });
});
