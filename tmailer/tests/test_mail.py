import base64
import contextlib
import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch, MagicMock
import urllib.request
import urllib.error
sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'backend'))
import mailcore as m
from email.message import EmailMessage

CONFIG = dict(email='you@example.com', username='you@example.com', password='secret', protocol='imap', incoming='imaps://mail.example.com:993', incoming_security='tls', smtp='smtp.example.com:587', smtp_security='starttls')


class ProtocolTests(unittest.TestCase):
    def test_endpoint_parsing_and_rejection(self):
        self.assertEqual(m.endpoint('imaps://host:999', 'imap', 'starttls'), ('host',999,'tls'))
        self.assertEqual(m.endpoint('host','pop3','tls'),('host',995,'tls'))
        self.assertEqual(m.endpoint('smtp://host:587','smtp','starttls'),('host',587,'starttls'))
        for value in ['https://host', 'imaps://user:secret@host', 'host/path', '', 'host:99999']:
            with self.assertRaises(ValueError):
                m.endpoint(value,'imap','tls')
    def test_mime_and_html_are_safe_text(self):
        msg=EmailMessage()
        msg['Subject']='Zażółć gęślą'
        msg['From']='Alice <alice@example.com>'
        msg.set_content('<p>Hello <b>Wiktor</b></p><script>danger()</script><img src="https://tracker.invalid">', subtype='html')
        msg.add_attachment(b'\x00\xff',maintype='application',subtype='octet-stream',filename='test.bin')
        parsed=m.parse_message(msg.as_bytes(),'1')
        self.assertIn('Hello Wiktor',parsed['body'])
        self.assertNotIn('danger',parsed['body'])
        self.assertNotIn('tracker',parsed['body'])
        self.assertEqual(parsed['subject'],'Zażółć gęślą')
        self.assertEqual(base64.b64decode(parsed['attachments'][0]['data']),b'\x00\xff')
    def test_smtp_starttls_before_login(self):
        conn=MagicMock()
        with patch.object(m.smtplib,'SMTP',return_value=conn) as constructor:
            with m.outgoing(CONFIG): pass
        names=[x[0] for x in conn.method_calls]
        self.assertEqual(names,['ehlo','starttls','ehlo','login','quit'])
        self.assertEqual(constructor.call_args.kwargs['local_hostname'],'[127.0.0.1]')
    def test_pop_starttls_before_login(self):
        c=dict(CONFIG,protocol='pop3',incoming='pop.example.com:110',incoming_security='starttls')
        conn=MagicMock()
        with patch.object(m.poplib,'POP3',return_value=conn):
            with m.incoming(c): pass
        self.assertEqual([x[0] for x in conn.method_calls],['stls','user','pass_','quit'])
    def test_imap_tls_verifies_certificates(self):
        conn=MagicMock()
        with patch.object(m.imaplib,'IMAP4_SSL',return_value=conn) as constructor:
            with m.incoming(CONFIG): pass
        ctx=constructor.call_args.kwargs['ssl_context']
        self.assertTrue(ctx.check_hostname)
        self.assertEqual(ctx.verify_mode,m.ssl.CERT_REQUIRED)
    def test_pop_uid_resolved_each_fetch_and_never_deleted(self):
        msg=EmailMessage(); msg.set_content('Hello POP3')
        conn=MagicMock()
        conn.uidl.return_value=(b'+OK',[b'3 stable-id'],0)
        conn.list.return_value=b'+OK 3 100'
        conn.retr.return_value=(b'+OK',msg.as_bytes().splitlines(),100)
        @contextlib.contextmanager
        def mock_incoming(c): yield conn
        with patch.object(m,'incoming',mock_incoming):
            result=m.get_message(dict(CONFIG,protocol='pop3'),'INBOX','stable-id')
        conn.retr.assert_called_once_with(3)
        conn.dele.assert_not_called()
        self.assertIn('Hello POP3',result['body'])
    def test_imap_uses_uid_and_peek(self):
        conn=MagicMock()
        conn.select.return_value=('OK',[b'1'])
        conn.uid.side_effect=[('OK',[b'1 (RFC822.SIZE 100)']),('OK',[(b'1 (BODY[] {20}',b'Subject: Hi\r\n\r\nHello'),b')'])]
        @contextlib.contextmanager
        def mock_incoming(c):yield conn
        with patch.object(m,'incoming',mock_incoming):
            self.assertEqual(m.get_message(CONFIG,'INBOX','1')['body'],'Hello')
        self.assertTrue(conn.select.call_args.kwargs['readonly'])
        self.assertEqual(conn.uid.call_args.args,('fetch','1','(BODY.PEEK[])'))
    def test_send_bcc_envelope_attachments_and_reply(self):
        conn=MagicMock();conn.send_message.return_value={}
        @contextlib.contextmanager
        def mock_outgoing(c):yield conn
        with patch.object(m,'outgoing',mock_outgoing):
            m.send(CONFIG,dict(to='a@example.com',bcc='hidden@example.com',subject='Hello',body='Test',in_reply_to='<old@example.com>',attachments=[dict(name='a.txt',data=base64.b64encode(b'abc').decode())]))
        msg=conn.send_message.call_args.args[0]
        self.assertNotIn('Bcc',msg)
        self.assertIn('hidden@example.com',conn.send_message.call_args.kwargs['to_addrs'])
        self.assertEqual(msg['In-Reply-To'],'<old@example.com>')
        self.assertEqual(next(msg.iter_attachments()).get_payload(decode=True),b'abc')
    def test_address_injection_rejected(self):
        with self.assertRaises(ValueError):m.addresses('a@example.com\r\nBcc: b@example.com')
        with self.assertRaises(ValueError):m.addresses('not-an-email')
