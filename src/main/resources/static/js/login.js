(() => {
    const form = document.getElementById('loginForm');
    const signupBtn = document.getElementById('signupBtn');
    const statusEl = document.getElementById('status');

    // URLs are passed from the server via data-* attributes on the form,
    // so Thymeleaf can still rewrite them for the context path.
    const loginUrl = form.dataset.loginUrl;
    const registerUrl = form.dataset.registerUrl;

    signupBtn.addEventListener('click', () => {
        window.location.href = registerUrl;
    });

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        statusEl.textContent = 'Logging in...';

        const payload = {
            email: document.getElementById('email').value,
            password: document.getElementById('password').value
        };

        try {
            const res = await fetch(loginUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
                // keep the Set-Cookie from the response (default, made explicit)
                credentials: 'same-origin'
            });

            const text = await res.text();
            console.log('login response', res.status, text);

            if (!res.ok) {
                statusEl.textContent = 'Login failed (' + res.status + '): ' + text;
                return;
            }

            // The server also set an HttpOnly "jwt" cookie on this response,
            // which is what actually authenticates the navigation below.
            // Keeping it in localStorage too is only useful if you make
            // header-based fetch() calls to the API from JS.
            const data = JSON.parse(text);
            localStorage.setItem('token', data.token);
            statusEl.textContent = 'Logged in. Redirecting...';
            window.location.href = '/home-page';
        } catch (err) {
            statusEl.textContent = 'Error: ' + err;
            console.error(err);
        }
    });
})();
