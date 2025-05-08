// static/js/forgot-password.js

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('forgotPasswordForm');
    const messageEl = document.getElementById('message');
  
    // Ensure the form will post to the normal controller
    form.setAttribute('action', '/forgot-password');
    form.setAttribute('method', 'post');
  
    form.addEventListener('submit', (e) => {
      // Clear any previous messages
      messageEl.textContent = '';
      messageEl.style.color = '';
  
      const email = form.email.value.trim();
      // Basic client-side validation
      if (!email) {
        e.preventDefault();
        messageEl.textContent = 'Please enter your email address.';
        messageEl.style.color = 'red';
        return;
      }
      // if email present, allow the form to submit normally and let the controller render the next view
    });
  });
  