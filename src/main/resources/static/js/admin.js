(() => {
    const config = document.getElementById('adminConfig');

    // URLs are passed from the server via data-* attributes so Thymeleaf
    // can still rewrite them for the context path.
    const getUsersUrl = config.dataset.getUsersUrl;
    const deleteUserUrl = config.dataset.deleteUserUrl;
    const setAccountEnabledUrl = config.dataset.setAccountEnabledUrl;

    const statusEl = document.getElementById('status');
    const rowsEl = document.getElementById('userRows');

    function authHeaders() {
        const token = localStorage.getItem('token');
        return token ? { 'Authorization': 'Bearer ' + token } : {};
    }

    async function loadUsers() {
        statusEl.textContent = 'Loading...';
        rowsEl.innerHTML = '';

        try {
            const res = await fetch(getUsersUrl, { headers: authHeaders() });

            if (!res.ok) {
                statusEl.textContent = 'Failed to load users (' + res.status + ')';
                return;
            }

            const users = await res.json();
            users.forEach(renderRow);
            statusEl.textContent = users.length + ' user(s)';
        } catch (err) {
            statusEl.textContent = 'Error: ' + err;
            console.error(err);
        }
    }

    function renderRow(user) {
        const tr = document.createElement('tr');

        [user.id, user.fullName, user.email, user.role, user.emailVerified, user.createdAt]
            .forEach(value => {
                const td = document.createElement('td');
                td.textContent = value;
                tr.appendChild(td);
            });

        const actionTd = document.createElement('td');

        // Admin accounts can't be managed from the dashboard.
        if (user.role !== 'ADMIN') {
            const toggleBtn = document.createElement('button');
            toggleBtn.textContent = user.emailVerified ? 'Disable account' : 'Enable account';
            toggleBtn.addEventListener('click', () => setAccountEnabled(user.email, !user.emailVerified));
            actionTd.appendChild(toggleBtn);

            const deleteBtn = document.createElement('button');
            deleteBtn.textContent = 'Delete';
            deleteBtn.addEventListener('click', () => deleteUser(user.id));
            actionTd.appendChild(deleteBtn);
        }

        tr.appendChild(actionTd);

        rowsEl.appendChild(tr);
    }

    async function setAccountEnabled(email, enabled) {
        statusEl.textContent = (enabled ? 'Enabling ' : 'Disabling ') + email + '...';

        try {
            const res = await fetch(setAccountEnabledUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', ...authHeaders() },
                body: JSON.stringify({ email: email, enabled: enabled })
            });

            if (!res.ok) {
                statusEl.textContent = 'Update failed (' + res.status + ')';
                return;
            }

            loadUsers();
        } catch (err) {
            statusEl.textContent = 'Error: ' + err;
            console.error(err);
        }
    }

    async function deleteUser(id) {
        if (!confirm('Delete user ' + id + '?')) return;
        statusEl.textContent = 'Deleting user ' + id + '...';

        try {
            const res = await fetch(deleteUserUrl + '/' + id, {
                method: 'DELETE',
                headers: authHeaders()
            });

            if (!res.ok) {
                statusEl.textContent = 'Delete failed (' + res.status + ')';
                return;
            }

            loadUsers();
        } catch (err) {
            statusEl.textContent = 'Error: ' + err;
            console.error(err);
        }
    }

    document.getElementById('refreshBtn').addEventListener('click', loadUsers);
    loadUsers();
})();
