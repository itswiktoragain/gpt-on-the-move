import test from 'node:test';
import assert from 'node:assert/strict';
import {perform,endpoint,publicIPv4,buildMessage,Wire} from '../supabase/functions/tmailer-mail/core.mjs';
const enc=new TextEncoder(),dec=new TextDecoder();
const config={email:'test@example.com',password:'secret',protocol:'imap',incoming:'imap.example.com',incoming_security:'tls',smtp:'smtp.example.com',smtp_security:'tls'};
const raw='From: Sender <sender@example.com>\r\nTo: test@example.com\r\nSubject: Test\r\nDate: Tue, 17 Nov 2026 10:00:00 +0100\r\n\r\nHello żółw';
const parsed={from:{name:'Sender',address:'sender@example.com'},to:[{address:'test@example.com'}],subject:'Test',text:'Hello żółw',date:'2026-11-17T09:00:00Z',attachments:[]};
class Conn{
 constructor(greeting,reply){this.buf=enc.encode(greeting);this.reply=reply;this.closed=false;this.tls=false;this.received=[];}
 emit(s){this.buf=Uint8Array.from([...this.buf,...enc.encode(s)]);}
 async read(b){assert.ok(this.buf.length,'server must have a pending response');const n=Math.min(b.length,this.buf.length,13);b.set(this.buf.subarray(0,n));this.buf=this.buf.slice(n);return n;}
 async write(b){const s=dec.decode(b);this.received.push(s);this.reply(this,s);return b.length;}
 close(){this.closed=true;}
}
function fixture({badSmtp=false}={}){
 const connections=[];let sent='';
 const runtime={resolve:async()=>['8.8.8.8'],upgrade:async(c)=>{c.tls=true;return c;},connect:async(ip,port)=>{
  let c;if(port===993||port===143)c=new Conn('* OK ready\r\n',(c,s)=>{const tag=s.split(' ')[0],cmd=s.slice(tag.length+1).trim();if(cmd==='STARTTLS')return c.emit(tag+' OK go\r\n');assert.ok(c.tls,'authentication and mail only over TLS');
   if(cmd.startsWith('LOGIN')){assert.ok(cmd.includes('"secret"'));c.emit(tag+' OK logged in\r\n');}
   else if(cmd.startsWith('EXAMINE'))c.emit('* 1 EXISTS\r\n'+tag+' OK selected\r\n');
   else if(cmd.startsWith('LIST'))c.emit('* LIST (\\HasNoChildren) "/" "INBOX"\r\n* LIST (\\Noselect) "/" "Hidden"\r\n* LIST () "/" "Sent"\r\n'+tag+' OK list\r\n');
   else if(cmd==='UID SEARCH ALL')c.emit('* SEARCH 7\r\n'+tag+' OK search\r\n');
   else if(cmd.includes('RFC822.SIZE'))c.emit('* 1 FETCH (UID 7 RFC822.SIZE '+enc.encode(raw).length+')\r\n'+tag+' OK size\r\n');
   else if(cmd.startsWith('UID FETCH'))c.emit('* 1 FETCH (BODY[] {'+enc.encode(raw).length+'}\r\n'+raw+' UID 7 FLAGS (\\Seen))\r\n'+tag+' OK fetched\r\n');
   else throw Error('Unexpected IMAP command '+cmd);
  });
  else if(port===995)c=new Conn('+OK ready\r\n',(c,s)=>{assert.ok(c.tls);const cmd=s.trim();if(cmd.startsWith('USER ')||cmd.startsWith('PASS '))c.emit('+OK accepted\r\n');else if(cmd==='UIDL')c.emit('+OK\r\n1 stable-uid\r\n.\r\n');else if(cmd.startsWith('LIST'))c.emit('+OK 1 '+enc.encode(raw).length+'\r\n');else if(cmd.startsWith('TOP')||cmd.startsWith('RETR'))c.emit('+OK\r\n'+raw+'\r\n.\r\n');else throw Error('Unexpected POP3 command');});
  else if(port===465)c=new Conn('220 ready\r\n',(c,s)=>{assert.ok(c.tls);if(c.data){sent=s;c.data=false;return c.emit('250 accepted\r\n');}if(s.startsWith('EHLO'))c.emit('250-ready\r\n250 AUTH PLAIN LOGIN\r\n');else if(s.startsWith('AUTH'))c.emit(badSmtp?'535 wrong login\r\n':'235 authenticated\r\n');else if(s.startsWith('MAIL')||s.startsWith('RCPT'))c.emit('250 OK\r\n');else if(s==='DATA\r\n'){c.data=true;c.emit('354 send\r\n');}else throw Error('Unexpected SMTP command');});
  else throw Error('Wrong port');connections.push(c);return c;
 }};return{runtime,connections,get sent(){return sent;}};
}
test('IMAP literals survive fragmentation, UID flags after the literal, and UTF-8 byte lengths',async()=>{const f=fixture();const parse=async bytes=>{assert.equal(dec.decode(bytes),raw);return parsed;};const list=await perform('messages',config,{folder:'INBOX'},f.runtime,parse);assert.equal(list.total,1);assert.equal(list.messages[0].id,'7');assert.equal(list.messages[0].seen,true);const message=await perform('message',config,{folder:'INBOX',id:'7'},f.runtime,parse);assert.equal(message.body,'Hello żółw');assert.ok(f.connections.every(c=>c.closed));});
test('POP3 uses stable UIDL IDs for list and retrieval and closes sockets',async()=>{const f=fixture(),c={...config,protocol:'pop3',incoming:'pop.example.com'};const list=await perform('messages',c,{},f.runtime,async()=>parsed);assert.equal(list.messages[0].id,'stable-uid');const m=await perform('message',c,{id:'stable-uid'},f.runtime,async bytes=>{assert.equal(dec.decode(bytes).trim(),raw);return parsed;});assert.equal(m.body,'Hello żółw');assert.ok(f.connections.every(c=>c.closed));});
test('SMTP sends Unicode MIME with private Bcc envelope and attachments',async()=>{const f=fixture();const r=await perform('send',config,{to:'to@example.com',bcc:'hidden@example.com',subject:'Żółw',body:'Hello ✓',attachments:[{name:'report.txt',data:btoa('hello')}]},f.runtime);assert.equal(r.sent,true);assert.ok(f.connections[0].received.includes('RCPT TO:<hidden@example.com>\r\n'));assert.ok(!f.sent.includes('hidden@example.com'));assert.ok(f.sent.includes(Buffer.from('Żółw').toString('base64')));assert.ok(f.sent.includes('aGVsbG8='));assert.ok(f.sent.endsWith('\r\n.\r\n'));assert.ok(f.connections.every(c=>c.closed));});
test('SMTP failure keeps verified incoming mail usable and reports sending failure',async()=>{const f=fixture({badSmtp:true});const r=await perform('connect',config,{},f.runtime);assert.equal(r.connected,true);assert.equal(r.smtp_ready,false);assert.match(r.warning,/SMTP login rejected/);const folders=await perform('folders',config,{},f.runtime);assert.deepEqual(folders.folders,['INBOX','Sent']);});
test('STARTTLS occurs before IMAP credentials and all standard ports are validated',async()=>{const f=fixture();await perform('connect',{...config,incoming_security:'starttls'}, {},f.runtime);assert.equal(f.connections[0].received[0],'T1 STARTTLS\r\n');assert.equal(endpoint('smtp.example.com:587','smtp','starttls').port,465);assert.equal(endpoint('smtps://smtp.example.com','smtp','starttls').security,'tls');assert.throws(()=>endpoint('imaps://name:password@imap.example.com','imap'));assert.throws(()=>endpoint('imap.example.com:22','imap'));assert.throws(()=>endpoint('https://example.com','imap'));});
test('Private DNS targets, CRLF protocol injection, invalid message IDs and oversized literals fail closed',async()=>{for(const ip of ['127.0.0.1','10.0.0.1','169.254.169.254','100.100.100.1','172.20.0.1','192.168.0.1','::1'])assert.equal(publicIPv4(ip),false);const f=fixture();await assert.rejects(()=>perform('connect',config,{}, {...f.runtime,resolve:async()=>['127.0.0.1']}),/public/);await assert.rejects(()=>perform('connect',{...config,password:'secret\r\nLOGOUT'}, {},f.runtime),/invalid/);await assert.rejects(()=>perform('message',config,{id:'7) BODY[]'},f.runtime),/identifier/);const c=new Conn('',()=>{}),w=new Wire(c,async c=>c);await assert.rejects(()=>w.literal(31*1024*1024),/30 MiB/);w.close();assert.throws(()=>buildMessage(config,{to:'a@example.com\r\nBcc: victim@example.com'}));assert.throws(()=>buildMessage(config,{to:'a@example.com',subject:'x\r\nBcc: victim@example.com'}));});
