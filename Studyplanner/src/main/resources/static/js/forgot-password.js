// static/js/forgot-password.js

// Wait for the DOM to be fully loaded
document.addEventListener('DOMContentLoaded', () => {
  // Get the form and message element
  const form = document.getElementById('forgotPasswordForm');
  const messageEl = document.getElementById('message');

  // Ensure the form posts to the correct controller
  form.setAttribute('action', '/forgot-password');
  form.setAttribute('method', 'post');

  // Handle form submission
  form.addEventListener('submit', (e) => {
    // Clear any previous messages
    if (messageEl) {
      messageEl.textContent = '';
      messageEl.style.color = '';
    }

    // Get the email value and trim whitespace
    const email = form.email.value.trim();

    // Basic client-side validation
    if (!email) {
      e.preventDefault();
      if (messageEl) {
        messageEl.textContent = 'Please enter your email address.';
        messageEl.style.color = '#e74c3c';
      }
      return;
    }
    // If email is present, allow the form to submit and let the controller handle the rest
  });
});
