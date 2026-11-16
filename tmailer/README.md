# Tmailer 1.1

Website and native desktop email client for IMAP, POP3 and SMTP. Branding and logo by Wiktor Laskowski.

The website is hosted on Sites. Its hosting runtime cannot open mail-server TCP connections. The web UI therefore needs an independently hosted Tmailer mail service; it clearly reports when that service is not configured. Desktop apps run their service locally and connect directly to your email servers.

## Desktop downloads

The `Tmailer installers` GitHub Actions workflow builds a Windows x64 MSI, separate Apple silicon and Intel macOS DMGs, and a Linux DEB. Successful builds are published in the `tmailer-v1.1.0` GitHub Release. The Windows and macOS packages are unsigned; no signing identity was supplied.

- Windows: install the MSI, then launch Tmailer from Start. Uses the system Edge WebView2 renderer; WebView2 Runtime must be installed.
- macOS: open the matching architecture’s DMG and drag Tmailer into Applications. Uses system WebKit. The build runs on macOS 15.
- Linux Mint / Ubuntu / Debian: install the DEB with `sudo apt install ./Tmailer-1.1.0-linux.deb`. The package installs Python and QtWebEngine dependencies through your package manager. Launch Tmailer from the applications menu.

The desktop clients create a native window. Closing that window shuts down the local mail service and clears its sessions.

## Hosting

The complete mail backend is `backend/server.py`, with `backend/mailcore.py`. It requires Python 3.10+ and no pip dependencies. It must run on a host that permits outgoing IMAP/POP3/SMTP TCP connections. Serve it behind HTTPS (for example, the provider's TLS proxy).

```sh
TMAILER_ORIGINS=https://tmailer.tata-i-wikto-6410.chatgpt.site \
python3 backend/server.py --public --host 0.0.0.0 --port 8080 --no-browser
```

The deployment manifest `railway.json` and Dockerfile are included. Connect the service using its HTTPS origin in the website’s **Connection settings**. The website stores that address in the device browser; it sends mail credentials directly to that chosen service. Trust the service operator before logging in.

Hosted mode allows public internet addresses on mail ports 110, 143, 465, 587, 993, 995 and 2525. It rejects internal, loopback, private and link-local destinations, pins resolved IPs at socket creation, checks TLS certificates and hostnames, and rate-limits login attempts. Separate unpredictable in-memory session tokens isolate each mailbox. Idle sessions expire after 30 minutes and are removed on subsequent API traffic. Passwords are never saved to disk.

`TMAILER_ORIGINS` is a comma-separated allowlist of website origins permitted to use the service. Cross-origin access is denied unless the requesting origin is on that list. No credentials belong in the repository or frontend configuration.

The backend’s `/health` endpoint returns its version. It can serve the same mail UI at its own root as well, but this project’s main website is on Sites.

For local development only:

```sh
python3 backend/server.py
```

## Mail features

- IMAP folders and POP3 inbox; pages of 40 headers, with search over the loaded page.
- Plain-text rendering of email, reply, text forwarding, attachment downloads.
- SMTP sending with To, Cc, Bcc, optional separate SMTP credentials, and file attachments up to 20 MiB total.
- Incoming message size limit: 30 MiB. HTML mail is rendered as text; remote images and scripts are not loaded.
- TLS or STARTTLS are required; certificate checking is always enabled.
- Password/app-password authentication only; OAuth-only providers require a future OAuth implementation.
- Messages stay on the server. Reading does not mark them as read. POP3 never deletes messages.
- Unsent drafts are not saved. Forwarding copies the body only; reattach original files if needed.
- Sent copies are not uploaded to IMAP; some providers save them automatically via SMTP.
- Optional account/server settings are remembered without passwords. Web settings use local browser storage; desktop storage uses its local service. Passwords are cleared on disconnect, session expiration or service shutdown.

## Build and test

```sh
python3 -m unittest discover -s tests -v
node --check dist/app.js
```

Windows/macOS: install `pyinstaller` and `pywebview`, then run `python packaging/build_native.py` on the target OS. Windows also needs WiX 5.0.2 and .NET 8. macOS uses `hdiutil` to create and verify DMGs. Linux uses `dpkg-deb` and packages source with distro-managed dependencies.

The GitHub workflow is included at the repository root. Tests use local protocol fixtures only and never send email to external recipients.
