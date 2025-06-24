// loads login form into backend
document.getElementById('loginForm').addEventListener('submit', function(e) {
    const user = document.getElementById('username').value.trim();
    const pass = document.getElementById('password').value;
  
    if (user === '' || pass === '') {
      e.preventDefault();
      document.getElementById('errorMsg').textContent = 'Please enter username and password.';
    }
  });
  