"""Tmailer's protocol layer. Only Python's standard library is required."""
import base64
import contextlib
import email
from email import policy
from email.message import EmailMessage
from email.utils import getaddresses, formatdate, make_msgid
from html.parser import HTMLParser
import imaplib
import poplib
import re
import smtplib
import ssl
from signals import spam_signals
from urllib.parse import urlsplit

TIMEOUT = 25
MAX_MESSAGE = 30 * 1024 * 1024


def endpoint(value, protocol, security):
    value = value.strip()
    u = urlsplit(value if '://' in value else '//' + value)
    if u.username or u.password or u.query or u.fragment or u.path not in ('', '/'):
        raise ValueError('Enter a server hostname or URL without credentials, path, or query.')
    schemes = {'imap': ('imap', 'imaps'), 'pop3': ('pop3', 'pop3s', 'pop', 'pops'), 'smtp': ('smtp', 'smtps')}
    if u.scheme and u.scheme not in schemes[protocol]:
        raise ValueError('The server URL scheme does not match the selected protocol.')
    if u.scheme in ('imaps', 'pop3s', 'pops', 'smtps'):
        security = 'tls'
    if security not in ('tls', 'starttls'):
        raise ValueError('Choose SSL/TLS or STARTTLS.')
    if not u.hostname or any(c.isspace() for c in u.hostname):
        raise ValueError('Enter a valid server hostname.')
    ports = {'imap': (993, 143), 'pop3': (995, 110), 'smtp': (465, 587)}
    return u.hostname, u.port or ports[protocol][security == 'starttls'], security


def validate(config):
    c = dict(config)
    if c.get('protocol') not in ('imap', 'pop3'):
        raise ValueError('Choose IMAP or POP3.')
    if not c.get('email') or '\n' in c['email'] or '\r' in c['email'] or '@' not in c['email']:
        raise ValueError('Enter your email address.')
    c['username'] = c.get('username', '').strip() or c['email']
    if not c.get('password'):
        raise ValueError('Enter your password or app password.')
    endpoint(c.get('incoming', ''), c['protocol'], c.get('incoming_security', 'tls'))
    endpoint(c.get('smtp', ''), 'smtp', c.get('smtp_security', 'starttls'))
    return c


@contextlib.contextmanager
def incoming(c):
    protocol = c['protocol']
    host, port, security = endpoint(c['incoming'], protocol, c['incoming_security'])
    ctx = ssl.create_default_context()
    conn = None
    try:
        if protocol == 'imap':
            conn = imaplib.IMAP4_SSL(host, port, ssl_context=ctx, timeout=TIMEOUT) if security == 'tls' else imaplib.IMAP4(host, port, timeout=TIMEOUT)
            if security == 'starttls':
                conn.starttls(ssl_context=ctx)
            conn.login(c['username'], c['password'])
        else:
            conn = poplib.POP3_SSL(host, port, context=ctx, timeout=TIMEOUT) if security == 'tls' else poplib.POP3(host, port, timeout=TIMEOUT)
            if security == 'starttls':
                conn.stls(context=ctx)
            conn.user(c['username'])
            conn.pass_(c['password'])
        yield conn
    finally:
        if conn:
            try:
                conn.logout() if protocol == 'imap' else conn.quit()
            except Exception:
                pass


@contextlib.contextmanager
def outgoing(c):
    host, port, security = endpoint(c['smtp'], 'smtp', c['smtp_security'])
    ctx = ssl.create_default_context()
    conn = None
    try:
        conn = smtplib.SMTP_SSL(host, port, timeout=TIMEOUT, context=ctx, local_hostname='[127.0.0.1]') if security == 'tls' else smtplib.SMTP(host, port, timeout=TIMEOUT, local_hostname='[127.0.0.1]')
        conn.ehlo()
        if security == 'starttls':
            conn.starttls(context=ctx)
            conn.ehlo()
        conn.login(c.get('smtp_username') or c['username'], c.get('smtp_password') or c['password'])
        yield conn
    finally:
        if conn:
            try:
                conn.quit()
            except Exception:
                pass


def check(c):
    with incoming(c):
        pass
    try:
        with outgoing(c):
            pass
    except Exception as exc:
        raise ValueError('Incoming login worked, but SMTP login failed: ' + str(exc)) from exc


def checked(result):
    status, data = result
    if status != 'OK':
        raise ValueError('The mail server rejected the request.')
    return data


def folders(c):
    if c['protocol'] == 'pop3':
        return ['INBOX']
    with incoming(c) as conn:
        rows = checked(conn.list())
        found = []
        for row in rows:
            if not isinstance(row, bytes) or b'\\Noselect' in row:
                continue
            match = re.match(rb'\([^)]*\) (?:"(?:[^"\\]|\\.)*"|NIL) (.*)$', row)
            if match:
                name = match[1].decode('ascii', errors='replace')
                if name.startswith('"') and name.endswith('"'):
                    name = name[1:-1].replace('\\"', '"').replace('\\\\', '\\')
                found.append(name)
        return sorted(set(['INBOX'] + found), key=lambda x: (x != 'INBOX', x.lower()))


def select(conn, folder):
    if '\r' in folder or '\n' in folder:
        raise ValueError('Invalid folder.')
    checked(conn.select('"' + folder.replace('\\', '\\\\').replace('"', '\\"') + '"', readonly=True))


def summary(raw, ident, seen=False, folder=''):
    msg = email.message_from_bytes(raw, policy=policy.default)
    return {'id': str(ident), 'subject': str(msg.get('Subject', '(No subject)')), 'from': str(msg.get('From', 'Unknown sender')), 'to': str(msg.get('To', '')), 'date': str(msg.get('Date', '')), 'seen': seen, 'cc': str(msg.get('Cc','')), 'spam': spam_signals(msg,folder)}


def list_messages(c, folder='INBOX', offset=0, limit=40):
    offset = max(0, int(offset))
    limit = min(50, max(1, int(limit)))
    with incoming(c) as conn:
        if c['protocol'] == 'imap':
            select(conn, folder)
            ids = checked(conn.uid('search', None, 'ALL'))[0].split()[::-1]
            messages = []
            for uid in ids[offset:offset + limit]:
                data = checked(conn.uid('fetch', uid, '(BODY.PEEK[HEADER] FLAGS)'))
                parts = [x for x in data if isinstance(x, tuple)]
                if parts:
                    messages.append(summary(parts[0][1], uid.decode(), b'\\Seen' in parts[0][0],folder))
            return {'messages': messages, 'total': len(ids)}
        rows = conn.uidl()[1]
        ids = [(int(row.split()[0]), row.split()[1].decode('ascii')) for row in rows][::-1]
        messages = []
        for number, uid in ids[offset:offset + limit]:
            try:
                raw = b'\r\n'.join(conn.top(number, 0)[1])
            except poplib.error_proto:
                sizes = dict((int(r.split()[0]), int(r.split()[1])) for r in conn.list()[1])
                if sizes.get(number, 0) > MAX_MESSAGE:
                    continue
                raw = b'\r\n'.join(conn.retr(number)[1])
            messages.append(summary(raw, uid,folder=folder))
        return {'messages': messages, 'total': len(ids)}


class TextHTML(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.text = []
        self.hidden = 0
    def handle_starttag(self, tag, attrs):
        if tag in ('script', 'style'):
            self.hidden += 1
        if tag in ('br', 'p', 'div', 'tr', 'li') and not self.hidden:
            self.text.append('\n')
    def handle_endtag(self, tag):
        if tag in ('script', 'style'):
            self.hidden = max(0, self.hidden - 1)
    def handle_data(self, data):
        if not self.hidden:
            self.text.append(data)


def parse_message(raw, ident,folder=''):
    msg = email.message_from_bytes(raw, policy=policy.default)
    result = summary(raw, ident)
    body = msg.get_body(preferencelist=('plain', 'html')) if msg.is_multipart() else msg
    text = ''
    if body and body.get_content_type() in ('text/plain', 'text/html'):
        try:
            text = body.get_content()
        except (LookupError, UnicodeError):
            text = (body.get_payload(decode=True) or b'').decode('utf-8', errors='replace')
        if body.get_content_type() == 'text/html':
            parser = TextHTML()
            parser.feed(text)
            text = ''.join(parser.text)
    attachments = []
    for part in msg.walk():
        if part.get_filename() or part.get_content_disposition() == 'attachment' or part.get('Content-ID'):
            data = part.get_payload(decode=True)
            if isinstance(data, bytes):
                attachments.append({'name': part.get_filename() or 'attachment', 'type': part.get_content_type(), 'cid': str(part.get('Content-ID','')).strip('<>'), 'inline': part.get_content_disposition()=='inline', 'data': base64.b64encode(data).decode()})
    html_body=msg.get_body(preferencelist=('html',)) if msg.is_multipart() else (msg if msg.get_content_type()=='text/html' else None)
    try:
        html=html_body.get_content() if html_body else ''
    except (LookupError,UnicodeError):
        html=''
    result.update(body=text, html=html, spam=spam_signals(msg,folder,attachments), attachments=attachments, reply_to=str(msg.get('Reply-To') or msg.get('From', '')), message_id=str(msg.get('Message-ID', '')))
    return result


def get_message(c, folder, ident):
    with incoming(c) as conn:
        if c['protocol'] == 'imap':
            if not str(ident).isdigit():
                raise ValueError('Invalid message identifier.')
            select(conn, folder)
            size_data = checked(conn.uid('fetch', str(ident), '(RFC822.SIZE)'))
            size_match = re.search(rb'RFC822.SIZE (\d+)', b' '.join(x for x in size_data if isinstance(x, bytes)))
            if size_match and int(size_match[1]) > MAX_MESSAGE:
                raise ValueError('This message exceeds the 30 MiB download limit.')
            data = checked(conn.uid('fetch', str(ident), '(BODY.PEEK[])'))
            chunks = [x[1] for x in data if isinstance(x, tuple)]
            if not chunks:
                raise ValueError('Message no longer exists. Refresh the inbox.')
            raw = chunks[0]
        else:
            mapping = {row.split()[1].decode('ascii'): int(row.split()[0]) for row in conn.uidl()[1]}
            if ident not in mapping:
                raise ValueError('Message no longer exists. Refresh the inbox.')
            number = mapping[ident]
            size = int(conn.list(number).decode().split()[2])
            if size > MAX_MESSAGE:
                raise ValueError('This message exceeds the 30 MiB download limit.')
            raw = b'\r\n'.join(conn.retr(number)[1])
        return parse_message(raw, ident,folder)


def addresses(value):
    if '\r' in value or '\n' in value:
        raise ValueError('Email addresses cannot contain newlines.')
    pairs = getaddresses([value])
    if not pairs or any(not addr or '@' not in addr or any(c.isspace() for c in addr) for _, addr in pairs):
        raise ValueError('Enter valid recipient email addresses, separated by commas.')
    return [a for _, a in pairs]


def send(c, draft):
    msg = EmailMessage()
    recipients = []
    for header, key in [('To', 'to'), ('Cc', 'cc'), ('Bcc', 'bcc')]:
        value = draft.get(key, '').strip()
        if value:
            recipients += addresses(value)
            if header != 'Bcc':
                msg[header] = value
    if not recipients:
        raise ValueError('Add at least one recipient.')
    msg['From'] = c['email']
    msg['Subject'] = draft.get('subject', '')
    msg['Date'] = formatdate(localtime=True)
    msg['Message-ID'] = make_msgid(domain=c['email'].rsplit('@',1)[-1])
    if draft.get('in_reply_to'):
        msg['In-Reply-To'] = draft['in_reply_to']
        msg['References'] = draft['in_reply_to']
    msg.set_content(draft.get('body', ''))
    if draft.get('html'):
        msg.add_alternative(str(draft['html']),subtype='html')
    total = 0
    for attachment in draft.get('attachments', []):
        data = base64.b64decode(attachment['data'], validate=True)
        total += len(data)
        if total > 20 * 1024 * 1024:
            raise ValueError('Attachments must total 20 MiB or less.')
        msg.add_attachment(data, maintype='application', subtype='octet-stream', filename=attachment['name'])
    with outgoing(c) as conn:
        refused = conn.send_message(msg, from_addr=c['email'], to_addrs=recipients)
    return {'sent': True, 'refused': list(refused), 'message_id': str(msg['Message-ID'])}


def sent_recipients(c):
    if c['protocol']!='imap':return []
    def decoded(value):
        def part(m):
            if not m[1]:return '&'
            try:return base64.b64decode(m[1].replace(',','/')+'===').decode('utf-16-be')
            except (ValueError,UnicodeError):return m[0]
        return re.sub(r'&([^-]*)-',part,value)
    sent=next((f for f in folders(c) if re.search(r'(?:^|[\/\]])(?:sent(?: mail| items| messages)?|wysłane|envoyés|gesendet)$',decoded(f),re.I)),None)
    if not sent:return []
    items=[]
    for msg in list_messages(c,sent)['messages']:
        for name,addr in getaddresses([msg['to'],msg.get('cc','')]):
            if '@' in addr and addr.lower()!=c['email'].lower():items.append({'email':addr.lower(),'name':name[:120]})
    return items
