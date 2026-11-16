import json
from pathlib import Path
import sys
import threading
import tempfile
import unittest
import urllib.request
import urllib.error
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'backend'))
import server
import mailcore
CONFIG=dict(email='you@example.com',password='secret',protocol='imap',incoming='imaps://example.com',incoming_security='tls',smtp='example.com:587',smtp_security='starttls')

class ServerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server=server.MailServer(('127.0.0.1',0))
        cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True);cls.thread.start()
        cls.base=f'http://127.0.0.1:{cls.server.server_port}'
    @classmethod
    def tearDownClass(cls):cls.server.shutdown();cls.server.server_close();cls.thread.join()
    def post(self,action,data=None,token=None,origin=None):
        headers={'Content-Type':'application/json'}
        if token:headers['X-Tmailer-Token']=token
        if origin:headers['Origin']=origin
        request=urllib.request.Request(self.base+'/api/'+action,data=json.dumps(data or {}).encode(),headers=headers)
        with urllib.request.urlopen(request) as r:return json.load(r)
    def test_static_and_runtime_config(self):
        with urllib.request.urlopen(self.base) as r:self.assertIn(b'Tmailer',r.read())
        with urllib.request.urlopen(self.base+'/runtime-config.js') as r:self.assertIn(b'"ready": true',r.read())
    def test_requires_session_token(self):
        with self.assertRaises(urllib.error.HTTPError) as e:self.post('messages')
        self.assertEqual(e.exception.code,401)
    def test_cross_origin_blocked(self):
        with self.assertRaises(urllib.error.HTTPError) as e:self.post('session',origin='https://evil.invalid')
        self.assertEqual(e.exception.code,403)
    def test_mailboxes_are_isolated(self):
        a=self.post('session')['token'];b=self.post('session')['token']
        with patch.object(mailcore,'check'),patch.object(mailcore,'list_messages',return_value={'messages':[],'total':0}):
            self.post('connect',CONFIG,a)
            self.assertEqual(self.post('messages',token=a)['total'],0)
            with self.assertRaises(urllib.error.HTTPError) as e:self.post('messages',token=b)
            self.assertEqual(e.exception.code,400)
            self.assertIsNone(self.server.session(b).account)
            self.post('disconnect',token=a)
            self.assertIsNone(self.server.session(a).account)
    def test_expired_session_rejected(self):
        token=self.server.new_session('fixture')
        self.server.sessions[token].updated-=server.SESSION_TTL+1
        self.assertIsNone(self.server.session(token))
    def test_public_origin_allowlist(self):
        old_public=self.server.public
        self.server.public=True;self.server.origins.add('https://mail.example.com')
        try:
            with self.assertRaises(urllib.error.HTTPError):self.post('session')
            self.assertIn('token',self.post('session',origin='https://mail.example.com'))
        finally:self.server.public=old_public;self.server.origins.clear()
    def test_public_network_rejects_private_ip(self):
        import socket
        original=socket.create_connection
        try:
            server.install_public_network_policy()
            with self.assertRaises(ValueError):socket.create_connection(('127.0.0.1',993))
            with self.assertRaises(ValueError):socket.create_connection(('example.com',22))
        finally:socket.create_connection=original

class SettingsTests(unittest.TestCase):
    def test_only_nonsecret_fields_persist(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(server,'config_path',return_value=Path(directory)/'settings.json'):
            server.save_settings(dict(CONFIG,smtp_password='another-secret'),True)
            text=server.config_path().read_text()
            self.assertNotIn('secret',text)
            self.assertNotIn('password',text)
            self.assertEqual(server.load_settings()['email'],'you@example.com')
            server.save_settings(CONFIG,False)
            self.assertFalse(server.config_path().exists())
