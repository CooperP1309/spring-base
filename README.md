# Spring Base

A Spring Boot starter with JWT authentication, an admin portal, and email verification, backed by a MySQL container. Setup and startup are handled by scripts for both Windows and Linux.

## Glossary

- [Prerequisites](#prerequisites)
- [Usage on Windows](#usage-on-windows)
- [Usage on Linux](#usage-on-linux)
- [Setting up your SMTP](#setting-up-your-smtp)

## Prerequisites

- **Java 21** (JDK) — required to run `mvnw`/`mvnw.cmd`
- **Docker** — must be installed with the daemon running; used to host the MySQL container
- **Git** — to download the repository via:
   ```bash
   git clone https://GitHub.com/CooperP1309/spring-base
   ```

Maven itself does not need to be installed — the bundled `mvnw` / `mvnw.cmd` wrapper handles that.

## Usage on Windows

1. Allow local script execution (one-time, per user):
   ```powershell
   Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
   ```
2. Run the setup script:
   ```powershell
   .\setup_server.ps1
   ```
   This walks you through the JWT secret, database container name/password, server and MySQL ports, SMTP details, public base URL, and admin portal credentials, then writes `src/main/resources/application.properties` and deploys the MySQL container. For the SMTP prompts, see [Setting up your SMTP](#setting-up-your-smtp) below.
3. Start the server:
   ```powershell
   .\start_server.ps1
   ```

## Usage on Linux

1. Make the scripts executable (one-time):
   ```bash
   chmod +x setup_server.sh start_server.sh mvnw
   ```
2. Run the setup script:
   ```bash
   ./setup_server.sh
   ```
   Same as above, this configures the database, ports, SMTP, public base URL, and admin credentials, then writes `application.properties` and deploys the MySQL container. For the SMTP prompts, see [Setting up your SMTP](#setting-up-your-smtp) below.
3. Start the server:
   ```bash
   ./start_server.sh
   ```

## Setting up your SMTP

Both setup scripts prompt for the same SMTP configuration, used to send account-verification emails to new users. You'll be asked to pick a provider:

| Option | Provider | Host | Port | TLS |
|---|---|---|---|---|
| 1 | Gmail / Google Workspace | `smtp.gmail.com` | 587 | STARTTLS |
| 2 | Microsoft 365 / Outlook | `smtp.office365.com` | 587 | STARTTLS |
| 3 | Amazon SES | *(your region's endpoint)* | 587 | STARTTLS |
| 4 | SendGrid | `smtp.sendgrid.net` | 587 | STARTTLS |
| 5 | Custom / Other | you provide host, port, and TLS mode | | |

After picking a provider, you'll enter the sender address, SMTP username, and SMTP password (confirmed twice), plus a **public base URL** — the scheme and host used to build the verification link emailed to new users (e.g. `http://localhost:8005` for local testing, or `https://your-domain.com` in production).

**Gmail users:** you'll need an [App Password](https://myaccount.google.com/apppasswords) rather than your normal account password — this requires 2-Step Verification to be enabled first. Enter it exactly as Google shows it (16 characters as 4 space-separated groups, spaces included).

**Microsoft 365 users:** the account needs SMTP AUTH enabled; use an app password if MFA is on.

**SendGrid users:** the SMTP username is literally `apikey` — the password is your actual API key.

All values (including passwords) are written in plain text to `src/main/resources/application.properties`, where they can be reviewed or changed later.
