"""Integration checks against local TLS mail servers. No external mail is sent."""
import base64
import contextlib
import os
from pathlib import Path
import shutil
import socketserver
import ssl
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'backend'))
import mailcore as m

RAW=b'From: Alice <alice@example.com>\r\nTo: you@example.com\r\nSubject: Local TLS test\r\nMessage-ID: <fixture@example.com>\r\nContent-Type: text/plain; charset=utf-8\r\n\r\nHello over real TLS.\r\n'

class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address=True
    daemon_threads=True
    def get_request(self):
        sock,addr=super().get_request()
        return self.ctx.wrap_socket(sock,server_side=True),addr

class Handler(socketserver.StreamRequestHandler):
    def line(self,value):self.wfile.write(value+b'\r\n');self.wfile.flush()
    def handle(self):
        protocol=self.server.protocol
        self.line({'imap':b'* OK Local IMAP', 'pop3':b'+OK Local POP3', 'smtp':b'220 Local SMTP'}[protocol])
        while True:
            line=self.rfile.readline().rstrip(b'\r\n')
            if not line:break
            if protocol=='imap':
                tag,cmd,*rest=line.split(b' ',2)
                cmd=cmd.upper();arg=rest[0] if rest else b''
                if cmd==b'CAPABILITY':self.line(b'* CAPABILITY IMAP4rev1')
                elif cmd==b'LIST':self.line(b'* LIST (\\HasNoChildren) "/" "INBOX"')
                elif cmd in (b'SELECT',b'EXAMINE'):self.line(b'* 1 EXISTS');self.line(b'* 0 RECENT')
                elif cmd==b'UID':
                    if arg.startswith(b'SEARCH'):self.line(b'* SEARCH 7')
                    elif b'RFC822.SIZE' in arg:self.line(b'* 1 FETCH (UID 7 RFC822.SIZE '+str(len(RAW)).encode()+b')')
                    else:
                        raw=RAW.split(b'\r\n\r\n')[0]+b'\r\n\r\n' if b'HEADER' in arg else RAW
                        self.line(b'* 1 FETCH (UID 7 FLAGS () BODY[] {'+str(len(raw)).encode()+b'}')
                        self.wfile.write(raw);self.line(b')')
                elif cmd==b'LOGOUT':self.line(b'* BYE');self.line(tag+b' OK logout');break
                self.line(tag+b' OK done')
            elif protocol=='pop3':
                cmd=line.split()[0].upper()
                if cmd==b'UIDL':self.line(b'+OK');self.line(b'1 stable-uid');self.line(b'.')
                elif cmd==b'STAT':self.line(b'+OK 1 '+str(len(RAW)).encode())
                elif cmd==b'LIST':self.line(b'+OK 1 '+str(len(RAW)).encode())
                elif cmd in (b'TOP',b'RETR'):
                    self.line(b'+OK');self.wfile.write(RAW);self.line(b'.')
                elif cmd==b'QUIT':self.line(b'+OK bye');break
                else:self.line(b'+OK')
            else:
                cmd=line.split()[0].upper()
                if cmd in (b'EHLO',b'HELO'):
                    self.line(b'250-localhost');self.line(b'250 AUTH PLAIN LOGIN')
                elif cmd==b'AUTH':self.line(b'235 authenticated')
                elif cmd==b'DATA':
                    self.line(b'354 send data')
                    chunks=[]
                    while True:
                        chunk=self.rfile.readline()
                        if chunk in (b'.\r\n',b''):break
                        chunks.append(chunk)
                    self.server.received.append(b''.join(chunks));self.line(b'250 accepted')
                elif cmd==b'QUIT':self.line(b'221 bye');break
                else:self.line(b'250 OK')

@unittest.skipUnless(shutil.which('openssl'),'OpenSSL is required for TLS fixture certificates')
class WireTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory();root=Path(cls.tmp.name)
        cls.cert=root/'cert.pem';key=root/'key.pem'
        subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-days','1','-keyout',str(key),'-out',str(cls.cert),'-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        ctx=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);ctx.load_cert_chain(cls.cert,key)
        cls.servers={}
        for protocol in ('imap','pop3','smtp'):
            s=Server(('127.0.0.1',0),Handler);s.ctx=ctx;s.protocol=protocol;s.received=[]
            threading.Thread(target=s.serve_forever,daemon=True).start();cls.servers[protocol]=s
        cls.env=patch.dict(os.environ,{'SSL_CERT_FILE':str(cls.cert)});cls.env.start()
    @classmethod
    def tearDownClass(cls):
        cls.env.stop()
        for s in cls.servers.values():s.shutdown();s.server_close()
        cls.tmp.cleanup()
    def config(self,protocol):
        return m.validate(dict(email='you@example.com',password='fixture-only',protocol=protocol,incoming=f'localhost:{self.servers[protocol].server_address[1]}',incoming_security='tls',smtp=f'localhost:{self.servers["smtp"].server_address[1]}',smtp_security='tls'))
    def test_imap_real_tls_list_and_receive(self):
        c=self.config('imap');m.check(c)
        self.assertEqual(m.folders(c),['INBOX'])
        rows=m.list_messages(c)
        self.assertEqual(rows['messages'][0]['id'],'7')
        self.assertIn('Hello over real TLS',m.get_message(c,'INBOX','7')['body'])
    def test_pop3_real_tls_list_and_receive(self):
        c=self.config('pop3');m.check(c)
        rows=m.list_messages(c)
        self.assertEqual(rows['messages'][0]['id'],'stable-uid')
        self.assertIn('Hello over real TLS',m.get_message(c,'INBOX','stable-uid')['body'])
    def test_smtp_real_tls_send_with_attachment(self):
        c=self.config('imap')
        result=m.send(c,dict(to='receiver@example.com',subject='TLS delivery',body='Sent over TLS',attachments=[dict(name='hello.txt',data=base64.b64encode(b'hi').decode())]))
        self.assertTrue(result['sent'])
        self.assertIn(b'Subject: TLS delivery',self.servers['smtp'].received[-1])
        self.assertIn(b'hello.txt',self.servers['smtp'].received[-1])
