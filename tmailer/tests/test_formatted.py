import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'backend'))
import unittest
from email.message import EmailMessage
from unittest.mock import patch
from contextlib import contextmanager
import mailcore
from signals import spam_signals

class FormattedMail(unittest.TestCase):
 def test_multipart_and_inline_images_survive_parsing(self):
  msg=EmailMessage();msg['Subject']='Formatted test';msg['From']='a@example.com';msg['X-Spam-Flag']='YES';msg.set_content('Hello world');msg.add_alternative('<p>Hello <b>world</b><img src="cid:test"></p>',subtype='html');msg.get_payload()[1].add_related(b'png','image','png',cid='<test>')
  parsed=mailcore.parse_message(msg.as_bytes(),'1');self.assertIn('<b>world</b>',parsed['html']);self.assertEqual(parsed['body'].strip(),'Hello world');self.assertEqual(parsed['spam']['level'],'likely_spam');self.assertEqual(parsed['attachments'][0]['cid'],'test');self.assertTrue(parsed['attachments'][0]['inline'])
 def test_smtp_sends_plain_and_html_and_no_bcc_header(self):
  captured=[]
  class SMTP:
   def send_message(self,msg,**kw):captured.append((msg,kw));return {}
  @contextmanager
  def outgoing(c):yield SMTP()
  with patch.object(mailcore,'outgoing',outgoing):mailcore.send({'email':'me@example.com'},{'to':'you@example.com','bcc':'hidden@example.com','body':'Hello','html':'<p><b>Hello</b></p>'})
  msg,kw=captured[0];self.assertIsNotNone(msg.get_body(preferencelist=('html',)));self.assertEqual(msg.get_body(preferencelist=('plain',)).get_content().strip(),'Hello');self.assertIsNone(msg.get('Bcc'));self.assertIn('hidden@example.com',kw['to_addrs'])
 def test_spam_signals_are_advisory(self):
  m=EmailMessage();m['Subject']='Meeting notes';self.assertEqual(spam_signals(m)['level'],'none');m['Authentication-Results']='dmarc=fail';self.assertEqual(spam_signals(m)['level'],'suspicious')
