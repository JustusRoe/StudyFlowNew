document.addEventListener("DOMContentLoaded", function () {
    const urlParams = new URLSearchParams(window.location.search);
    const courseId = urlParams.get("courseId");
    if (!courseId) {
        // Don't run the rest of the code if no course is selected
        return;
    }

    // --- Load course info and update progress ---
    fetch(`/courses/details/${courseId}`)
        .then(res => res.json())
        .then(course => {
            document.getElementById("deadlineCourseInfo").innerHTML = `
                <h2>${course.name}</h2>
                <div>Difficulty: ${["Easy", "Medium", "Hard"][course.difficulty - 1] || "Unknown"}</div>
                <div>Progress: ${course.progressPercent}%</div>
            `;
            document.getElementById("progressBarInner").style.width = `${course.progressPercent}%`;
            document.getElementById("progressLabel").textContent = `${course.progressPercent}%`;
        });

    // --- Deadlines ---
    function loadDeadlines() {
        fetch(`/api/courses/${courseId}/deadlines`)
            .then(res => {
                if (!res.ok) {
                    throw new Error("Failed to fetch deadlines");
                }
                return res.json();
            })
            .then(deadlines => {
                const tbody = document.querySelector("#deadlineTable tbody");
                tbody.innerHTML = "";
                const selector = document.getElementById("autoDeadlineSelector");
                selector.innerHTML = '<option value="" disabled selected>Select a deadline</option>';
                if (!Array.isArray(deadlines) || deadlines.length === 0) {
                    tbody.innerHTML = '<td class="empty-row" colspan="4">No deadlines yet.</td>';
                    return;
                }
                deadlines.forEach(dl => {
                    const tr = document.createElement("tr");
                    tr.innerHTML = `
                        <td>${dl.title}</td>
                        <td>${(dl.startTime || "").slice(0,16).replace("T", " ")}</td>
                        <td>${dl.studyTimeNeeded ?? ""}</td>
                        <td>
                            <button class="btn btn-small btn-danger delete-btn">🗑️</button>
                        </td>
                    `;
                    // Remove edit button, only delete remains
                    tr.querySelector(".delete-btn").onclick = () => {
                        if (confirm("Delete this deadline?")) {
                            fetch(`/deadlines/delete/${dl.id}`, { method: "DELETE" })
                                .then(() => loadDeadlines());
                        }
                    };
                    tbody.appendChild(tr);

                    // For auto-plan selector
                    const opt = document.createElement("option");
                    opt.value = dl.id;
                    opt.textContent = `${dl.title} (${(dl.startTime || "").slice(0, 16)})`;
                    selector.appendChild(opt);
                });
            })
            .catch(err => {
                const tbody = document.querySelector("#deadlineTable tbody");
                tbody.innerHTML = `<tr><td colspan='4'>Error loading deadlines: ${err.message}</td></tr>`;
            });
    }
    loadDeadlines();

    // Add/Edit Deadline
    document.getElementById("deadlineForm").onsubmit = function (e) {
        e.preventDefault();
        const title = document.getElementById("deadlineTitle").value.trim();
        const date = document.getElementById("deadlineDate").value;
        const studyTimeNeeded = parseInt(document.getElementById("deadlineStudyTime").value, 10);
        // changed: studyStart is now a date (yyyy-MM-dd)
        const studyStartDate = document.getElementById("deadlineStudyStart").value;
        const editingId = document.getElementById("editingDeadlineId").value;

        if (!title || !date || !studyTimeNeeded || !studyStartDate) return;

        // Start/End time are the same for deadlines
        const startTime = date;
        const endTime = date;

        // Convert studyStartDate (yyyy-MM-dd) to yyyy-MM-ddT00:00
        const studyStart = studyStartDate + "T00:00";

        const payload = {
            title,
            startTime,
            endTime,
            type: "deadline",
            isDeadline: true,
            studyTimeNeeded,
            studyStart
        };

        // fetch courseId from URL
        const urlParams = new URLSearchParams(window.location.search);
        const courseId = urlParams.get("courseId");

        fetch(`/deadlines/create?courseId=${courseId}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        })
        .then(res => {
            if (!res.ok) throw new Error("Failed to create deadline");
            return res.json();
        })
        .then(() => {
            loadDeadlines();
            document.getElementById("deadlineForm").reset();
        })
        .catch(err => alert("Fehler beim Erstellen der Deadline: " + err.message));
    };

    // --- Self-study sessions ---
    function loadSessions() {
        fetch(`/api/courses/${courseId}/selfstudy`)
            .then(res => res.json())
            .then(events => {
                const list = document.getElementById("sessionList");
                list.innerHTML = "";
                if (!Array.isArray(events) || events.length === 0) {
                    list.innerHTML = '<li class="empty-session">No planned self-study sessions yet.</li>';
                    return;
                }
                events.forEach(ev => {
                    const deadlineInfo = ev.relatedDeadlineTitle ? ` [${ev.relatedDeadlineTitle}]` : "";
                    list.innerHTML += `<li>${ev.title} (${ev.startTime.slice(0, 16)} - ${ev.endTime.slice(0, 16)})${deadlineInfo}</li>`;
                });
            });
    }
    loadSessions();

    // Manual self-study session
    document.getElementById("selfstudyForm").onsubmit = function (e) {
        e.preventDefault();
        // Use selected deadline for title and relation
        const deadlineSelector = document.getElementById("autoDeadlineSelector");
        const deadlineOption = deadlineSelector.options[deadlineSelector.selectedIndex];
        const deadlineTitle = deadlineOption && deadlineOption.value ? deadlineOption.textContent.split(' (')[0] : "";
        const deadlineId = deadlineSelector.value;

        const start = document.getElementById("selfstudyStart").value;
        const end = document.getElementById("selfstudyEnd").value;
        if (!start || !end) return;

        if (!deadlineId) {
            alert("Please select a deadline for this session.");
            return;
        }

        // Validate: end must be after start
        if (new Date(end) <= new Date(start)) {
            alert("End time must be after start time.");
            return;
        }

        // Default title as in autoplan
        const title = "📖 Selfstudy for " + deadlineTitle;

        fetch(`/courses/${courseId}/add-selfstudy`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                title,
                description: "Manual self-study session",
                color: "#F4B400",
                startTime: start,
                endTime: end,
                relatedDeadlineId: deadlineId
            })
        }).then(() => {
            document.getElementById("selfstudyForm").reset();
            loadSessions();
        });
    };

    // Auto-plan button
    document.getElementById("autoPlanBtn").onclick = function () {
        const deadlineId = document.getElementById("autoDeadlineSelector").value;
        if (!deadlineId) {
            alert("Please select a deadline.");
            return;
        }
        fetch(`/courses/${courseId}/autoplan?deadlineId=${deadlineId}`, {
            method: "POST"
        })
        .then(res => {
            if (!res.ok) throw new Error("Auto planning failed");
            return res.text();
        })
        .then(() => {
            alert("Self-study sessions planned!");
            loadSessions();
        })
        .catch(err => alert(err.message));
    };

    // --- Course buttons in header ---
    fetch("/courses/user")
        .then(res => res.json())
        .then(courses => {
            const bar = document.getElementById("courseButtonBar");
            bar.innerHTML = "";
            const urlParams = new URLSearchParams(window.location.search);
            const courseId = urlParams.get("courseId");
            courses.forEach(course => {
                const btn = document.createElement("button");
                btn.textContent = course.name;
                btn.className = "btn course-btn";
                btn.style.background = course.color || "#2e3a78";
                btn.style.color = getContrastingTextColor(course.color || "#2e3a78");
                btn.style.whiteSpace = "nowrap";
                btn.style.overflow = "hidden";
                btn.style.textOverflow = "ellipsis";
                btn.style.maxWidth = "400px";
                btn.style.minWidth = "90px";
                btn.style.fontSize = "1rem";
                btn.style.padding = "0.5rem 1.1rem";
                if (courseId && String(course.id) === String(courseId)) {
                    btn.style.outline = "3px solid #222";
                    btn.disabled = true;
                    btn.style.opacity = "1";
                }
                btn.onclick = function () {
                    window.location.href = "/manage-deadlines?courseId=" + course.id;
                };
                bar.appendChild(btn);
            });
        });

    function getContrastingTextColor(bgColor) {
        // Simple function to determine contrasting text color (black or white)
        const color = bgColor.startsWith("#") ? bgColor.slice(1) : bgColor;
        const r = parseInt(color.substring(0, 2), 16);
        const g = parseInt(color.substring(2, 4), 16);
        const b = parseInt(color.substring(4, 6), 16);
        const brightness = (r * 299 + g * 587 + b * 114) / 1000;
        return (brightness > 128) ? "black" : "white";
    }
});
