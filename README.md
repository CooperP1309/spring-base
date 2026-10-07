# Spring Base

A Spring Boot starter with JWT authentication, an admin portal, email verification and password reset emails, all backed by a MySQL container. Setup and startup are handled by scripts for both Windows and Linux.

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
   .\setup.ps1
   ```
   This walks you through the setup of the project and automatically deploys and connects the database container to your project. For the SMTP prompts, see [Setting up your SMTP](#setting-up-your-smtp) below.

3. Start the server:
   ```powershell
   .\start.ps1
   ```
**Important!** If you get:
```
RenameItemIOError,Microsoft.PowerShell.Commands.RenameItemCommand ECHO is on. Cannot start maven from wrapper
```
try running from an administrator powershell windows (just for the first startup).

## Usage on Linux

1. Make the scripts executable (one-time):
   ```bash
   chmod +x setup.sh start.sh mvnw
   ```
2. Run the setup script:
   ```bash
   ./setup.sh
   ```
   Same as above, this walks you through each setup step of the server and automatically wires up the database container. For the SMTP prompts, see [Setting up your SMTP](#setting-up-your-smtp) below.

3. Start the server:
   ```bash
   ./start.sh
   ```

## Setting up your SMTP

Both setup scripts prompt for the same SMTP configuration, used to send account-verification and password reset emails to users. You'll be asked to pick a provider:

| Option | Provider | Host | Port | TLS |
|---|---|---|---|---|
| 1 | Gmail / Google Workspace | `smtp.gmail.com` | 587 | STARTTLS |
| 2 | Microsoft 365 / Outlook | `smtp.office365.com` | 587 | STARTTLS |
| 3 | Amazon SES | *(your region's endpoint)* | 587 | STARTTLS |
| 4 | SendGrid | `smtp.sendgrid.net` | 587 | STARTTLS |
| 5 | Custom / Other | you provide host, port, and TLS mode | | |

After picking a provider, you'll enter the sender email address, SMTP username (same as sender address for vast majority of cases), and SMTP password (confirmed twice). With these details, the server will be able to send verification and password reset emails from the specified address.

The final component of password resetting and email verification is a **public base URL** — This is the start of the URL that specifies the HTTP scheme and the host. Example with `http://localhost:8005` as the "Public Base URL":




When in production, use `https://<your-domain>.com` or similar.

### Side Notes for Setting up SMTP

**Gmail users:** you'll need an [App Password](https://myaccount.google.com/apppasswords) rather than your normal account password — this requires 2-Step Verification to be enabled before Google lets you make one. Enter it exactly as Google shows it (16 characters as 4 space-separated groups, spaces included). It's super straight forward!

**Microsoft 365 users:** the account needs SMTP AUTH enabled; use an app password if MFA is on.

**SendGrid users:** the SMTP username is literally `apikey` — the password is your actual API key.

All values (including passwords) are written in plain text to `src/main/resources/application.properties`, where they can be reviewed or changed later.

## Deployment Recommendations

### Deploying over the Internet

When deploying this server over the internet via HTTPS, ensure that
you answer yes to the "Cookie Security" part of the setup script.

Furthermore, a typical deployment in this manner will often sit behind
a proxy server. Running Caddy as your proxy server is an easy way to setup TLS termination for HTTPS.

The configuration of such a Caddy File is as simple as this:



### Restrictive Local Deployments

For heavily restricted local network deployments, it's a good idea to 
remove the /verify endpoint by only permitting it to admins in the security config chain.

This way, users can create an account, but require manual approval of an admin to use the system.