document.addEventListener("DOMContentLoaded", function () {
    const urlParams = new URLSearchParams(window.location.search);
    const courseId = urlParams.get("courseId");
    if (!courseId) {
        // Don't run the rest of the code if no course is selected
        return;
    }

    // --- Load course info and update mascot/progress ---
    fetch(`/courses/details/${courseId}`)
        .then(res => res.json())
        .then(course => {
            document.getElementById("deadlineCourseInfo").innerHTML = `
                <h2>${course.name}</h2>
                <div>Difficulty: ${["Easy", "Medium", "Hard"][course.difficulty - 1] || "Unknown"}</div>
                <div>Progress: ${course.progressPercent}%</div>
                <div>Self-study: ${course.selfStudyHours}h of ${course.workloadTarget}h</div>
                <div>${course.description || ""}</div>
            `;
            document.getElementById("progressBarInner").style.width = `${course.progressPercent}%`;
            document.getElementById("progressLabel").textContent = `${course.progressPercent}%`;
            document.getElementById("mascotFish").style.transform = `scale(${0.7 + (course.progressPercent || 0) / 100 * 1.3})`;
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
                selector.innerHTML = '<option value="" disabled selected>Choose a deadline</option>';
                if (!Array.isArray(deadlines) || deadlines.length === 0) {
                    tbody.innerHTML = "<tr><td colspan='4'>No deadlines yet.</td></tr>";
                    return;
                }
                deadlines.forEach(dl => {
                    const tr = document.createElement("tr");
                    tr.innerHTML = `
                        <td>${dl.title}</td>
                        <td>${(dl.startTime || "").slice(0,16).replace("T", " ")}</td>
                        <td>${dl.points ?? ""}</td>
                        <td>
                            <button class="btn btn-small edit-btn">✏️</button>
                            <button class="btn btn-small btn-danger delete-btn">🗑️</button>
                        </td>
                    `;
                    tr.querySelector(".edit-btn").onclick = () => {
                        document.getElementById("deadlineTitle").value = dl.title;
                        document.getElementById("deadlineDate").value = (dl.startTime || "").slice(0,16);
                        document.getElementById("deadlinePoints").value = dl.points ?? "";
                        document.getElementById("editingDeadlineId").value = dl.id;
                    };
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
        const points = parseInt(document.getElementById("deadlinePoints").value, 10);
        const editingId = document.getElementById("editingDeadlineId").value;

        if (!title || !date || !points) return;

        // Default: 1 Stunde Dauer
        const startTime = date;
        const endTime = (() => {
            const d = new Date(date);
            d.setHours(d.getHours() + 1);
            return d.toISOString().slice(0, 16);
        })();

        const payload = {
            title,
            startTime,
            endTime,
            type: "exam",
            color: "#DB4437",
            isDeadline: true,
            points
        };

        // courseId aus URL holen
        const urlParams = new URLSearchParams(window.location.search);
        const courseId = urlParams.get("courseId");

        // KORREKT: courseId als Query-Parameter mitsenden!
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
            // Nach dem Erstellen neu laden
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
                    list.innerHTML = "<li>No planned self-study sessions.</li>";
                    return;
                }
                events.forEach(ev => {
                    const li = document.createElement("li");
                    li.textContent = `${ev.title} (${ev.startTime.slice(0, 16)} - ${ev.endTime.slice(0, 16)})`;
                    list.appendChild(li);
                });
            });
    }
    loadSessions();

    // Manual self-study session
    document.getElementById("selfstudyForm").onsubmit = function (e) {
        e.preventDefault();
        const title = document.getElementById("selfstudyTitle").value.trim();
        const date = document.getElementById("selfstudyDate").value;
        const duration = parseInt(document.getElementById("selfstudyDuration").value, 10);
        if (!title || !date || !duration) return;

        const startTime = date;
        const endTime = new Date(new Date(date).getTime() + duration * 60 * 60 * 1000)
            .toISOString().slice(0, 16);

        fetch(`/courses/${courseId}/add-selfstudy`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                title,
                description: "Manual self-study session",
                color: "#F4B400",
                startTime,
                endTime
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
});
