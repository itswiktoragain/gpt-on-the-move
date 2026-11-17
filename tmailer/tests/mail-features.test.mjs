import test from 'node:test';
import assert from 'node:assert/strict';
import {spamSignals,extractRecipients} from '../supabase/functions/tmailer-mail/signals.mjs';
import {buildMessage} from '../supabase/functions/tmailer-mail/core.mjs';
import {mergeRecipients,sentAddresses} from '../cloud/recipients.mjs';
test('spam scoring distinguishes routine messages, suspicious authentication and provider spam',()=>{
 assert.equal(spamSignals([],'Invoice for November').level,'none');
 const warning=spamSignals([{key:'authentication-results',value:'mx.example; dmarc=fail'}],'Hello');assert.equal(warning.level,'suspicious');assert.ok(warning.reasons.some(s=>s.includes('DMARC')));
 assert.equal(spamSignals([{key:'x-spam-flag',value:'YES'}],'Weekly roundup').level,'likely_spam');assert.equal(spamSignals([],'Routine message','Junk').level,'likely_spam');
 assert.equal(spamSignals([],'Urgent: verify your account', '', [{filename:'invoice.exe'}]).level,'likely_spam');
});
test('formatted sends contain plain and HTML alternatives while excluding Bcc headers',()=>{const m=buildMessage({email:'me@example.com'},{to:'you@example.com, ',bcc:'hidden@example.com, ',body:'Hello world',html:'<p>Hello <strong>world</strong></p>',subject:'Formatted ✓',attachments:[{name:'hello.txt',data:btoa('attachment')}]});assert.match(m.raw,/multipart\/alternative/);assert.match(m.raw,/text\/plain; charset=UTF-8/);assert.match(m.raw,/text\/html; charset=UTF-8/);assert.ok(m.raw.includes(Buffer.from('<p>Hello <strong>world</strong></p>').toString('base64')));assert.ok(!m.raw.includes('hidden@example.com'));assert.ok(m.recipients.includes('hidden@example.com'));});
test('recipient history extracts display names, deduplicates, ranks recent sends, and excludes refused addresses',()=>{
 assert.deepEqual(extractRecipients('Mila <MILA@example.com>, team@example.com'),[{email:'mila@example.com',name:'Mila'},{email:'team@example.com',name:''}]);
 const first=mergeRecipients([],[{email:'mila@example.com',name:'Mila'},{email:'me@example.com'}],'me@example.com',true);assert.equal(first.length,1);assert.equal(first[0].count,1);
 const imported=mergeRecipients(first,[{email:'MILA@example.com',name:''}],'me@example.com');assert.equal(imported[0].count,1);assert.equal(imported[0].name,'Mila');
 assert.deepEqual(sentAddresses({to:'Mila <mila@example.com>',cc:'refused@example.com',bcc:'hidden@example.com'},['refused@example.com']).map(r=>r.email),['mila@example.com','hidden@example.com']);
});
