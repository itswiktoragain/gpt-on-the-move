# Tmailer 1.4

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

## Web mail connections

The website's native IMAP/POP3/SMTP service runs as a Supabase Edge Function. Sites keeps cloud identity, encrypted credentials and session state, then signs each backend request with an ECDSA P-256 private key. The function contains only the public verification key and rejects unsigned or expired requests. No provider API, browser extension, local bridge or user-supplied service URL is required.

Short-lived mailbox sessions are encrypted in D1 and use hashed opaque tokens. Sessions expire after two hours. Signing out removes browser access, and Disconnect clears the session's mail credentials. Saved vault records remain until the user removes them. Saved mailboxes reconnect on page load.

Public DNS addresses are checked and pinned before TLS, certificate verification is mandatory, and only standard mail ports are allowed. The web backend uses SMTP SSL/TLS port 465 because the host blocks ports 25 and 587. A saved SMTP 587 setting tries the same hostname on 465. If SMTP fails, verified incoming mail remains usable and a separate sending warning is shown. Providers supporting only 587 require the desktop client until another hosting transport is available.

Owner-only runtime configuration names:

- `TMAILER_VAULT_KEY`: base64url-encoded random 32-byte vault encryption key; preserve it across deployments.
- `MAIL_BACKEND_URL`: HTTPS URL of the deployed Tmailer mail function.
- `MAIL_BACKEND_SIGNING_KEY`: private ECDSA P-256 JWK, kept only in Sites secrets. Its public counterpart is in the mail function.

ChatGPT identity uses the Sites platform's stamped user ID and reserved sign-in routes. The app does not implement a separate ChatGPT OAuth flow. To make ChatGPT sign-in optional, the site's sign-in page must be accessible without the platform's mandatory private-site login gate. The application's own authentication protects all saved cloud records.

## Desktop apps

Downloads are published as Windows x64 MSI, macOS Apple silicon and Intel DMGs, and a Linux DEB. Windows and macOS packages are unsigned.

- Windows 10 / 11: install the MSI and launch Tmailer from Start. Requires the system Edge WebView2 Runtime.
- macOS 15+: open the DMG for the matching architecture and copy Tmailer into Applications. Uses native WebKit.
- Linux Mint / Ubuntu / Debian: `sudo apt install ./Tmailer-1.4.0-linux.deb`. The package installs Python and QtWebEngine dependencies through the system package manager.

Desktop clients open a native window and connect directly to IMAP, POP3 and SMTP servers using Python's standard library. They can use mail independently of the hosted web backend. Closing the native window stops its local mail server and clears in-memory mail sessions. Cloud vault records remain online.

## Mail features

IMAP folders, POP3 inbox, pages of 40 headers, search over the loaded page, sanitized HTML and plain-text email rendering, replies, text forwarding and attachment downloads are included. SMTP supports To, Cc, Bcc, separate outgoing credentials and attachments up to 20 MiB total. Incoming message downloads are limited to 30 MiB.

TLS or STARTTLS and valid certificates are required. Password / app-password authentication is supported; OAuth-only mail providers require a separate future integration. Reading is non-destructive and does not change the read flag. POP3 messages are never deleted. Rich-text composing sends multipart HTML/plain-text alternatives. Drafts are not saved. Forwarding copies the body; reattach files manually. Sent copies are not uploaded to IMAP, though some SMTP providers save them themselves.

## Source and builds

- `ui/`: shared website and desktop HTML, scripts, local SVG sprite and styling.
- `cloud/server.mjs`: account authentication, encrypted vault, preferences and desktop authorization.
- `cloud/mail.mjs`: encrypted mailbox sessions and signed backend requests.
- `supabase/functions/tmailer-mail/`: native Deno socket protocol service.
- `app/`, `worker/`: Sites/Vinext routes and runtime integration.
- `db/schema.ts`, `drizzle/`: schema and immutable generated D1 migrations.
- `backend/`: direct IMAP/POP3/SMTP backend and local HTTP server.
- `desktop/`: native window launchers.
- `packaging/`: native installers, icons and WiX definition.

The Sites build preserves its starter integration. `npm run build` copies UI assets into `public/` and builds the Worker. Generate new schema changes with `npm run db:generate`; publishing applies migrations. Do not put runtime secrets in the hosting manifest or source.

For native builds, run `python packaging/build_native.py` on the target OS. Windows/macOS need PyInstaller and pywebview; Windows also needs WiX 5.0.2 and .NET 8, while macOS uses hdiutil. Linux uses dpkg-deb and distro-managed Qt packages. GitHub Actions builds all four platforms and publishes a new version tag without moving existing release tags.

Checks:

```sh
node --test tests/*.test.mjs
python3 -m unittest discover -s tests -v
node --check ui/app.js
node --check ui/cloud.js
```

Cloud tests use disposable local SQLite fixtures and cover hashing, sessions, encryption, tamper rejection, ownership isolation, persisted settings, ChatGPT linking, cross-origin rejection and one-use desktop approval. Protocol tests use local mail-server fixtures and never send mail to external recipients. No real provider mailbox or real user credentials were used during testing.

## Formatting, spam review and recipients

HTML email is sanitized with locally bundled DOMPurify 3.4.18 and rendered in a sandboxed iframe without scripts or same-origin access. Remote images are blocked until the reader opts in for that message. Inline PNG/JPEG/GIF/WebP CID images are supported. The composer supports bold, italic, underline, lists and links; pasted HTML is reduced to safe editing markup.

Spam review is advisory scoring from provider spam headers, authentication-result headers, junk-folder placement, common urgent/prize subjects and executable attachment names. No mail is moved or deleted automatically. The page filter operates on loaded messages; open the provider's Junk folder to inspect all server-side junk.

Recipient suggestions match email addresses and display names, support keyboard selection, and handle To/Cc/Bcc separately. Successful sends remember accepted recipients only. Tmailer imports recipients from the latest 40 messages in a detected IMAP Sent folder; POP3 has no Sent folder and builds history from new sends. Signed-in web history is encrypted in D1, scoped to the Tmailer user and sending mailbox. Desktop history is stored in a private local file; anonymous web history is stored on the current browser. The optional sample inbox is explicitly labeled and cannot send mail.
