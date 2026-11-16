# Tmailer 1.2

Tmailer combines a Sites-hosted email interface, cloud account storage, and native Windows, macOS and Linux clients. Logo and branding by Wiktor Laskowski.

## Cloud accounts

Create a Tmailer account with an email address and a password of at least 12 characters, or choose **Continue with ChatGPT**. ChatGPT sign-in is optional. A password account can link ChatGPT from its account settings. Profiles are keyed by their own IDs; matching email addresses do not silently merge separate accounts.

When signed in, adding a mailbox can save its server settings and password online. The sidebar lists saved mailboxes. Account settings let you edit or remove them and sync the reading-pane preference between devices.

- Saved mail configurations are encrypted with AES-256-GCM, using a server-only 256-bit key and authenticated data binding each ciphertext to its owner and record.
- Tmailer account passwords are stored as per-user salted PBKDF2-SHA256 hashes at 100,000 iterations, not as recoverable passwords.
- Website sessions use Secure, HttpOnly, SameSite=Lax cookies. The database stores only hashes of session tokens; signing out revokes the current token.
- Every saved-account read, update and removal is authorized against its owning user.
- Native clients can sign in directly with their Tmailer password. Browser sign-in, including optional ChatGPT, uses a short-lived device request with a separate secret and explicit approval before issuing the desktop session.
- Mail-password export is available only to the authenticated desktop client; the website uses saved accounts through its server-side mail proxy.
- Login attempts are rate-limited, and state-changing API requests require an allowed Origin and JSON content type.

Cloud records are stored in the Site's D1 database. No separate service URL or external app is needed to save accounts and settings. The current version does not include email verification, automatic password recovery, inbox caching or cloud message storage. Mail remains on the provider's server. Keep a working sign-in method; the server encryption key must be preserved to decrypt stored mail accounts.

## Web mail connection status

Sites does not support raw mail-server TCP sockets. Cloud sign-in and encrypted storage run on Sites, while direct IMAP/POP3/SMTP operations require Tmailer's own TCP-capable backend. That backend has not yet been provisioned. The website clearly reports that mail connections await activation, and signed-in users can still save mail accounts online.

The application does not ask users to connect an external web-mail service or paste a service URL. Once the owner's backend is activated, users only enter their email login and server settings, or select a saved mailbox.

Owner-only runtime configuration names:

- `TMAILER_VAULT_KEY`: a base64url-encoded random 32-byte encryption key. Keep this secret, outside source and client bundles.
- `MAIL_BACKEND_URL`: HTTPS origin/path for the deployed Tmailer mail backend.
- `MAIL_BACKEND_SECRET`: optional shared server-to-server credential for that backend.

ChatGPT identity uses the Sites platform's stamped user ID and reserved sign-in routes. The app does not implement a separate ChatGPT OAuth flow. To make ChatGPT sign-in optional, the site's sign-in page must be accessible without the platform's mandatory private-site login gate. The application's own authentication protects all saved cloud records.

## Desktop apps

Downloads are published as Windows x64 MSI, macOS Apple silicon and Intel DMGs, and a Linux DEB. Windows and macOS packages are unsigned.

- Windows 10 / 11: install the MSI and launch Tmailer from Start. Requires the system Edge WebView2 Runtime.
- macOS 15+: open the DMG for the matching architecture and copy Tmailer into Applications. Uses native WebKit.
- Linux Mint / Ubuntu / Debian: `sudo apt install ./Tmailer-1.2.1-linux.deb`. The package installs Python and QtWebEngine dependencies through the system package manager.

Desktop clients open a native window and connect directly to IMAP, POP3 and SMTP servers using Python's standard library. They can use mail independently of the pending web connection backend. Closing the native window stops its local mail server and clears in-memory mail sessions. Cloud vault records remain online.

## Mail features

IMAP folders, POP3 inbox, pages of 40 headers, search over the loaded page, safe plain-text email rendering, replies, text forwarding and attachment downloads are included. SMTP supports To, Cc, Bcc, separate outgoing credentials and attachments up to 20 MiB total. Incoming message downloads are limited to 30 MiB.

TLS or STARTTLS and valid certificates are required. Password / app-password authentication is supported; OAuth-only mail providers require a separate future integration. Reading is non-destructive and does not change the read flag. POP3 messages are never deleted. Drafts are not saved. Forwarding copies the body; reattach files manually. Sent copies are not uploaded to IMAP, though some SMTP providers save them themselves.

## Source and builds

- `ui/`: shared website and desktop HTML, scripts, local SVG sprite and styling.
- `cloud/server.mjs`: account authentication, encrypted vault, preferences and desktop authorization.
- `app/`, `worker/`: Sites/Vinext routes and runtime integration.
- `db/schema.ts`, `drizzle/`: schema and immutable generated D1 migrations.
- `backend/`: direct IMAP/POP3/SMTP backend and local HTTP server.
- `desktop/`: native window launchers.
- `packaging/`: native installers, icons and WiX definition.

The Sites build preserves its starter integration. `npm run build` copies UI assets into `public/` and builds the Worker. Generate new schema changes with `npm run db:generate`; publishing applies migrations. Do not put runtime secrets in the hosting manifest or source.

For native builds, run `python packaging/build_native.py` on the target OS. Windows/macOS need PyInstaller and pywebview; Windows also needs WiX 5.0.2 and .NET 8, while macOS uses hdiutil. Linux uses dpkg-deb and distro-managed Qt packages. GitHub Actions builds all four platforms and publishes a new version tag without moving existing release tags.

Checks:

```sh
node --test tests/cloud.test.mjs
python3 -m unittest discover -s tests -v
node --check ui/app.js
node --check ui/cloud.js
```

Cloud tests use disposable local SQLite fixtures and cover hashing, sessions, encryption, tamper rejection, ownership isolation, persisted settings, ChatGPT linking, cross-origin rejection and one-use desktop approval. Protocol tests use local mail-server fixtures and never send mail to external recipients. No real provider mailbox or real user credentials were used during testing.
