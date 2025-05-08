// static/js/reset-password.js

document.addEventListener('DOMContentLoaded', () => {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');
    const form = document.getElementById('resetPasswordForm');
    const messageEl = document.getElementById('message');
  
    // Set the hidden token field so it gets posted
    document.getElementById('token').value = token || '';
  
    form.setAttribute('action', '/reset-password');
    form.setAttribute('method', 'post');
  
    form.addEventListener('submit', e => {
      messageEl.textContent = '';
      messageEl.style.color = '';
  
      const pwd = form.password.value.trim();
      const confirm = form.confirmPassword.value.trim();
  
      if (!pwd || !confirm) {
        e.preventDefault();
        messageEl.textContent = 'Please fill out both password fields.';
        messageEl.style.color = 'red';
        return;
      }
  
      if (pwd !== confirm) {
        e.preventDefault();
        messageEl.textContent = 'Passwords do not match.';
        messageEl.style.color = 'red';
      }
      // otherwise allow normal POST to server
    });
  });
  