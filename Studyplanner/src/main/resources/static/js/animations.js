const canvas = document.getElementById("background");
const ctx = canvas.getContext("2d");

let backgroundParticles = [];
let fireworks = [];
let colors = [
  "#ff6b6b",
  "#f7d794",
  "#4ecdc4",
  "#5f27cd",
  "#1dd1a1",
  "#ffe66d",
  "#ff6f91",
  "#6a82fb",
  "#fc5c7d",
  "#45aaf2",
];

function resizeCanvas() {
  canvas.width = window.innerWidth;
  canvas.height = window.innerHeight;
}

window.addEventListener("resize", resizeCanvas);
resizeCanvas();

class BackgroundParticle {
  constructor() {
    this.reset();
  }

  reset() {
    this.x = Math.random() * canvas.width;
    this.y = Math.random() * canvas.height;
    this.radius = Math.random() * 3 + 1;
    this.speed = Math.random() * 0.5 + 0.2;
    this.color = `rgba(255,255,255,${Math.random() * 0.5})`;
  }

  update() {
    this.y -= this.speed;
    if (this.y < 0) {
      this.reset();
      this.y = canvas.height;
    }
  }

  draw() {
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2);
    ctx.fillStyle = this.color;
    ctx.fill();
  }
}

class FireworkParticle {
  constructor(x, y, color) {
    this.x = x;
    this.y = y;
    this.speed = Math.random() * 7 + 3;
    this.angle = Math.random() * 2 * Math.PI;
    this.radius = Math.random() * 5 + 3;
    this.life = 150;
    this.color = color;
  }

  update() {
    this.x += Math.cos(this.angle) * this.speed;
    this.y += Math.sin(this.angle) * this.speed;
    this.speed *= 0.96;
    this.life--;
  }

  draw() {
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2);
    ctx.fillStyle = this.color;
    ctx.shadowBlur = 20;
    ctx.shadowColor = this.color;
    ctx.fill();
    ctx.shadowBlur = 0;
  }
}

function createBackgroundParticles() {
  for (let i = 0; i < 150; i++) {
    backgroundParticles.push(new BackgroundParticle());
  }
}

function animate() {
  ctx.fillStyle = "rgba(116, 235, 213, 0.2)";
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  backgroundParticles.forEach((p) => {
    p.update();
    p.draw();
  });

  fireworks.forEach((p, index) => {
    p.update();
    p.draw();
    if (p.life <= 0) fireworks.splice(index, 1);
  });

  requestAnimationFrame(animate);
}

function createFirework(x, y) {
  for (let i = 0; i < 80; i++) {
    const color = colors[Math.floor(Math.random() * colors.length)];
    fireworks.push(new FireworkParticle(x, y, color));
  }
}

createBackgroundParticles();
animate();

const form = document.getElementById("signupForm");
form.addEventListener("submit", (e) => {
  const buttonRect = form.querySelector("button").getBoundingClientRect();
  createFirework(buttonRect.left + buttonRect.width / 2, buttonRect.top);
});
