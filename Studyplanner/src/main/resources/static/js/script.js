
const calendar = document.getElementById('calendar');
const monthYear = document.getElementById('monthYear');
const prevMonth = document.getElementById('prevMonth');
const nextMonth = document.getElementById('nextMonth');

let currentDate = new Date();

function renderCalendar() {
  calendar.innerHTML = '';
  const year = currentDate.getFullYear();
  const month = currentDate.getMonth();
  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const monthNames = ["January", "February", "March", "April", "May", "June",
                      "July", "August", "September", "October", "November", "December"];
  monthYear.innerText = `${monthNames[month]} ${year}`;

  for (let i = 0; i < firstDay; i++) {
    const blank = document.createElement('div');
    calendar.appendChild(blank);
  }

  for (let i = 1; i <= daysInMonth; i++) {
    const day = document.createElement('div');
    day.innerText = i;
    day.addEventListener('click', () => {
      triggerFireworks();
    });
    calendar.appendChild(day);
  }
}

prevMonth.addEventListener('click', () => {
  currentDate.setMonth(currentDate.getMonth() - 1);
  renderCalendar();
});

nextMonth.addEventListener('click', () => {
  currentDate.setMonth(currentDate.getMonth() + 1);
  renderCalendar();
});

renderCalendar();

// Background animation
const canvas = document.getElementById('backgroundCanvas');
const ctx = canvas.getContext('2d');
canvas.width = window.innerWidth;
canvas.height = window.innerHeight;
let particles = [];

function createParticle() {
  return {
    x: Math.random() * canvas.width,
    y: Math.random() * canvas.height,
    size: Math.random() * 2 + 1,
    speedX: Math.random() * 0.5 - 0.25,
    speedY: Math.random() * 0.5 - 0.25
  };
}

for (let i = 0; i < 100; i++) {
  particles.push(createParticle());
}

function animateBackground() {
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  particles.forEach(p => {
    p.x += p.speedX;
    p.y += p.speedY;

    if (p.x < 0 || p.x > canvas.width || p.y < 0 || p.y > canvas.height) {
      Object.assign(p, createParticle());
    }

    ctx.beginPath();
    ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(255,255,255,0.5)';
    ctx.fill();
  });
  requestAnimationFrame(animateBackground);
}
animateBackground();

// Fireworks on click
function triggerFireworks() {
  for (let i = 0; i < 50; i++) {
    setTimeout(() => {
      const x = Math.random() * canvas.width;
      const y = Math.random() * canvas.height;
      const radius = Math.random() * 2 + 1;
      ctx.beginPath();
      ctx.arc(x, y, radius, 0, Math.PI * 2);
      ctx.fillStyle = `hsl(${Math.random() * 360}, 100%, 70%)`;
      ctx.fill();
    }, i * 10);
  }
}
