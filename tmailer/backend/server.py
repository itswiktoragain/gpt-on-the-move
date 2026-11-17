"""Tmailer HTTP mail service and local desktop server, Python 3.10+."""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
import os
from pathlib import Path
import secrets
import sys
import socket
import socketserver
import threading
import time
from urllib.parse import urlsplit
import webbrowser
import mailcore

ROOT = Path(getattr(sys,'_MEIPASS',Path(__file__).resolve().parents[1]))
ASSETS = ROOT / 'ui'
MAX_REQUEST = 31 * 1024 * 1024
SESSION_TTL = 30 * 60
SAFE_FIELDS = ('email','username','protocol','incoming','incoming_security','smtp','smtp_security','smtp_username')


def config_path():
    if sys.platform == 'win32':
        return Path(os.environ.get('APPDATA',str(Path.home()))) / 'Tmailer' / 'settings.json'
    if sys.platform == 'darwin':
        return Path.home() / 'Library/Application Support/Tmailer/settings.json'
    return Path(os.environ.get('XDG_CONFIG_HOME',str(Path.home()/'.config'))) / 'tmailer/settings.json'


def load_settings():
    try:
        data=json.loads(config_path().read_text())
        return {k:data[k] for k in SAFE_FIELDS if k in data}
    except (OSError,ValueError):
        return {}


def save_settings(c,remember):
    path=config_path()
    if not remember:
        path.unlink(missing_ok=True)
        return
    path.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
    temp=path.with_suffix('.tmp')
    fd=os.open(temp,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w') as stream:
        json.dump({k:c.get(k,'') for k in SAFE_FIELDS},stream)
    temp.replace(path)


def load_recipient_history(email):
    path=config_path().with_name('recipients.json')
    try:return json.loads(path.read_text()).get(email.lower(),[])
    except (OSError,ValueError):return []

def save_recipient_history(email,items,increment=False):
    path=config_path().with_name('recipients.json')
    try:all_history=json.loads(path.read_text())
    except (OSError,ValueError):all_history={}
    contacts={r['email'].lower():r for r in all_history.get(email.lower(),[])}
    for item in items:
        addr=item['email'].strip().lower()
        if '@' not in addr or addr==email.lower():continue
        old=contacts.get(addr,{})
        contacts[addr]={'email':addr,'name':item.get('name') or old.get('name',''),'count':old.get('count',0)+int(increment),'lastUsed':int(time.time()*1000) if increment else old.get('lastUsed',0)}
    history=sorted(contacts.values(),key=lambda r:(-r['lastUsed'],-r['count'],r['email']))[:500]
    all_history[email.lower()]=history;path.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
    temp=path.with_suffix('.tmp');fd=os.open(temp,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w') as f:json.dump(all_history,f)
    temp.replace(path);return history


def install_public_network_policy():
    """Resolve once at actual connect and reject nonpublic destinations."""
    original = socket.create_connection
    def connect(address, timeout=socket._GLOBAL_DEFAULT_TIMEOUT, source_address=None, **kwargs):
        host, port = address
        if int(port) not in (110, 143, 465, 587, 993, 995, 2525):
            raise ValueError('This hosted service supports mail ports 110, 143, 465, 587, 993, 995 and 2525.')
        candidates = socket.getaddrinfo(host, port, type=socket.SOCK_STREAM)
        if not candidates or any(not ipaddress.ip_address(row[4][0]).is_global for row in candidates):
            raise ValueError('Hosted mail servers must use public internet addresses.')
        last = None
        for family, kind, proto, _, remote in candidates:
            try:
                return original((remote[0], port), timeout=timeout, source_address=source_address, **kwargs)
            except OSError as exc:
                last = exc
        raise last or OSError('Could not connect to mail server.')
    socket.create_connection = connect


class Session:
    def __init__(self):
        self.account = None
        self.lock = threading.Lock()
        self.updated = time.monotonic()


class MailServer(ThreadingHTTPServer):
    daemon_threads = True
    def server_bind(self):
        socketserver.TCPServer.server_bind(self)
        self.server_name=self.server_address[0]
        self.server_port=self.server_address[1]
    def __init__(self, address, origins=(), public=False):
        super().__init__(address, Handler)
        self.public = public
        self.origins = set(origins)
        self.sessions = {}
        self.session_lock = threading.Lock()
        self.attempts = {}
        self.created = {}
    def session(self, token):
        with self.session_lock:
            now = time.monotonic()
            for key in list(self.sessions):
                if now - self.sessions[key].updated > SESSION_TTL:
                    self.sessions.pop(key)
            s = self.sessions.get(token)
            if s:
                s.updated = now
            return s
    def new_session(self, peer):
        with self.session_lock:
            now = time.monotonic()
            for key in list(self.sessions):
                if now - self.sessions[key].updated > SESSION_TTL:
                    self.sessions.pop(key)
            for key in list(self.created):
                self.created[key] = [t for t in self.created[key] if now - t < 3600]
                if not self.created[key]:
                    self.created.pop(key)
            for key in list(self.attempts):
                self.attempts[key] = [t for t in self.attempts[key] if now - t < 900]
                if not self.attempts[key]:
                    self.attempts.pop(key)
            recent = self.created.setdefault(peer, [])
            if len(self.sessions) >= 200 or len(recent) >= 30:
                raise ValueError('Too many sessions. Try again later.')
            recent.append(now)
            token = secrets.token_urlsafe(32)
            self.sessions[token] = Session()
            return token
    def login_limit(self, peer):
        if not self.public:
            return
        with self.session_lock:
            now=time.monotonic()
            history=[t for t in self.attempts.get(peer,[]) if now-t<900]
            if len(history)>=12:
                raise ValueError('Too many login attempts. Try again in 15 minutes.')
            self.attempts[peer]=history+[now]


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass
    def origin_allowed(self):
        origin = self.headers.get('Origin')
        if origin in self.server.origins:
            return True
        return not self.server.public and (not origin or origin == f'http://127.0.0.1:{self.server.server_port}') and self.headers.get('Host') == f'127.0.0.1:{self.server.server_port}'
    def respond(self, code, data, mime='application/json'):
        body=json.dumps(data).encode() if mime=='application/json' else data
        self.send_response(code)
        self.send_header('Content-Type',mime)
        self.send_header('Content-Length',str(len(body)))
        self.send_header('Cache-Control','no-store')
        self.send_header('X-Content-Type-Options','nosniff')
        self.send_header('Referrer-Policy','no-referrer')
        self.send_header('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data:; frame-src 'self' blob:; child-src blob:; connect-src 'self' https: http://127.0.0.1:*; object-src 'none'; base-uri 'none'; frame-ancestors 'none'")
        if self.origin_allowed() and self.headers.get('Origin'):
            self.send_header('Access-Control-Allow-Origin',self.headers['Origin'])
            self.send_header('Vary','Origin')
            self.send_header('Access-Control-Allow-Headers','Content-Type, X-Tmailer-Token')
            self.send_header('Access-Control-Allow-Methods','POST, OPTIONS')
        self.end_headers()
        self.wfile.write(body)
    def do_OPTIONS(self):
        self.respond(200 if self.origin_allowed() else 403,{})
    def do_GET(self):
        path=urlsplit(self.path).path
        if not self.server.public and not self.origin_allowed():
            return self.respond(403,{'error':'Invalid local host.'})
        if path=='/health':
            return self.respond(200,{'ok':True,'app':'Tmailer','version':'1.4.0'})
        if path=='/runtime-config.js':
            data={'desktop':not self.server.public,'apiBase':'','ready':True,'cloudEnabled':True,'cloudOrigin':'https://tmailer.tata-i-wikto-6410.chatgpt.site'}
            return self.respond(200,('window.TMAILER_CONFIG='+json.dumps(data)+';').encode(),'application/javascript')
        names={'/':'index.html','/index.html':'index.html','/app.js':'app.js','/style.css':'style.css','/logo.svg':'logo.svg','/icons.svg':'icons.svg','/icons.js':'icons.js','/cloud.js':'cloud.js','/downloads.json':'downloads.json','/purify.js':'purify.js','/mail-tools.js':'mail-tools.js','/sample.js':'sample.js'}
        filename=names.get(path)
        if not filename or not (ASSETS/filename).is_file():
            return self.respond(404,{'error':'Not found.'})
        mime={'.html':'text/html; charset=utf-8','.js':'application/javascript','.css':'text/css','.svg':'image/svg+xml','.json':'application/json'}[Path(filename).suffix]
        self.respond(200,(ASSETS/filename).read_bytes(),mime)
    def do_POST(self):
        if not self.origin_allowed():
            return self.respond(403,{'error':'This website is not allowed by the Tmailer service.'})
        data={};session=None
        try:
            length=int(self.headers.get('Content-Length','0'))
            if not 0<length<=MAX_REQUEST:
                raise ValueError('Request is empty or too large.')
            data=json.loads(self.rfile.read(length))
            if not isinstance(data,dict):raise ValueError('Invalid request.')
            name=self.path.removeprefix('/api/')
            if not self.path.startswith('/api/'):return self.respond(404,{'error':'Not found.'})
            if name=='session':
                return self.respond(200,{'token':self.server.new_session(self.client_address[0])})
            session=self.server.session(self.headers.get('X-Tmailer-Token',''))
            if not session:return self.respond(401,{'error':'Your session expired. Connect again.'})
            with session.lock:
                if name=='settings':
                    return self.respond(200,{} if self.server.public else load_settings())
                if name=='connect':
                    self.server.login_limit(self.client_address[0])
                    c=mailcore.validate(data);mailcore.check(c)
                    if not self.server.public and 'remember' in data:
                        save_settings(c,bool(data['remember']))
                    session.account=c
                    return self.respond(200,{'email':c['email'],'protocol':c['protocol']})
                if name=='disconnect':
                    session.account=None
                    return self.respond(200,{})
                if name=='quit' and not self.server.public:
                    self.respond(200,{})
                    threading.Thread(target=self.server.shutdown,daemon=True).start()
                    return
                if not session.account:raise ValueError('Connect your email account first.')
                c=session.account
                if name=='folders':result={'folders':mailcore.folders(c)}
                elif name=='messages':result=mailcore.list_messages(c,data.get('folder','INBOX'),data.get('offset',0))
                elif name=='message':result=mailcore.get_message(c,data.get('folder','INBOX'),data['id'])
                elif name=='recipients':
                    history=load_recipient_history(c['email'])
                    if data.get('importSent'):
                        try:history=save_recipient_history(c['email'],mailcore.sent_recipients(c))
                        except Exception:pass
                    result={'recipients':history}
                elif name=='send':
                    result=mailcore.send(c,data)
                    if result.get('sent'):
                        pairs=mailcore.getaddresses([data.get(k,'') for k in ('to','cc','bcc')])
                        refused=set(result.get('refused',[]))
                        try:save_recipient_history(c['email'],[{'email':addr,'name':name} for name,addr in pairs if addr not in refused],True)
                        except Exception:result['history_warning']='Message sent, but recipient history could not be saved.'
                else:return self.respond(404,{'error':'Unknown action.'})
                self.respond(200,result)
        except Exception as exc:
            message=str(exc)
            for source in (data if isinstance(data,dict) else {},session.account if session and session.account else {}):
                for key in ('password','smtp_password'):
                    if source.get(key):message=message.replace(source[key],'[redacted]')
            self.respond(400,{'error':message[:650] or 'Mail service request failed.'})


def main():
    parser=argparse.ArgumentParser(description='Tmailer website mail service')
    parser.add_argument('--host',default='127.0.0.1')
    parser.add_argument('--port',type=int,default=int(os.environ.get('PORT',0)))
    parser.add_argument('--public',action='store_true')
    parser.add_argument('--no-browser',action='store_true')
    parser.add_argument('--origin',action='append',default=[])
    args=parser.parse_args()
    origins=args.origin+[o.strip() for o in os.environ.get('TMAILER_ORIGINS','').split(',') if o.strip()]
    if args.public:
        if not origins:parser.error('--public requires --origin or TMAILER_ORIGINS')
        install_public_network_policy()
    elif args.host!='127.0.0.1':parser.error('Use --public when listening beyond localhost.')
    server=MailServer((args.host,args.port),origins,args.public)
    url=f'http://127.0.0.1:{server.server_port}/'
    if sys_stdout := getattr(__import__('sys'),'stdout',None):
        print('Tmailer is running at '+url,flush=True)
    if not args.no_browser and not args.public:webbrowser.open(url)
    try:server.serve_forever()
    except KeyboardInterrupt:pass
    finally:
        server.sessions.clear();server.server_close()

if __name__=='__main__':main()
