import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync,readdirSync} from 'node:fs';
import {handleMail} from '../cloud/mail.mjs';
import {handleCloud,b64} from '../cloud/server.mjs';
import {readRecipients,saveRecipients} from '../cloud/recipients.mjs';
class Statement{constructor(db,sql,args=[]){Object.assign(this,{db,sql,args});}bind(...args){return new Statement(this.db,this.sql,args);}async first(){return this.db.prepare(this.sql).get(...this.args)||null;}async run(){return this.db.prepare(this.sql).run(...this.args);}async all(){return{results:this.db.prepare(this.sql).all(...this.args)};}}
class D1{constructor(){this.db=new DatabaseSync(':memory:');this.db.exec('PRAGMA foreign_keys=ON');for(const f of readdirSync('drizzle').filter(f=>f.endsWith('.sql')).sort())this.db.exec(readFileSync('drizzle/'+f,'utf8'));}prepare(s){return new Statement(this.db,s);}async batch(items){const out=[];for(const i of items)out.push(await i.run());return out;}}
const origin='https://tmailer.test',config={email:'user@example.com',password:'never-plaintext-in-db',protocol:'imap',incoming:'imap.example.com',incoming_security:'tls',smtp:'smtp.example.com',smtp_security:'tls'};
function req(path,data={},token='',cookie=''){return new Request(origin+path,{method:'POST',headers:{Origin:origin,'Content-Type':'application/json','X-Tmailer-Token':token,Cookie:cookie,'cf-connecting-ip':'fixture'},body:JSON.stringify(data)});}
test('mail sessions encrypt credentials, sign server requests, isolate owners, expire and disconnect',async()=>{
 const pair=await crypto.subtle.generateKey({name:'ECDSA',namedCurve:'P-256'},true,['sign','verify']);const env={DB:new D1(),TMAILER_VAULT_KEY:b64(crypto.getRandomValues(new Uint8Array(32))),MAIL_BACKEND_URL:'https://backend.example.com',MAIL_BACKEND_SIGNING_KEY:JSON.stringify(await crypto.subtle.exportKey('jwk',pair.privateKey))};
 const originalFetch=globalThis.fetch;let calls=0;
 globalThis.fetch=async(url,options)=>{calls++;assert.ok(url.startsWith('https://backend.example.com/api/'));const stamp=options.headers['X-Tmailer-Time'],action=url.split('/').pop(),sig=Uint8Array.from(atob(options.headers['X-Tmailer-Signature']),c=>c.charCodeAt(0));assert.ok(await crypto.subtle.verify({name:'ECDSA',hash:'SHA-256'},pair.publicKey,sig,new TextEncoder().encode(stamp+'\n'+action+'\n'+options.body)));assert.equal(JSON.parse(options.body).config.password,config.password);assert.ok(!options.body.includes(env.MAIL_BACKEND_SIGNING_KEY));return Response.json(action==='connect'?{email:config.email,connected:true}:{messages:[],total:0});};
 try{
  const call=async(path,data={},token='',cookie='')=>{const r=await handleMail(req('/api/'+path,data,token,cookie),env);return{status:r.status,data:await r.json()};};
  const a=(await call('session')).data.token,b=(await call('session')).data.token;assert.equal((await call('connect',config,a)).status,200);assert.equal((await call('messages',{},b)).status,401);assert.equal((await call('messages',{},a)).status,200);
  const rows=env.DB.db.prepare('SELECT * FROM tmailer_mail_sessions').all();assert.ok(rows.every(r=>r.token_hash!==a&&r.token_hash!==b));assert.ok(rows.every(r=>!String(r.encrypted_config).includes(config.password)));
  const register=await handleCloud(req('/api/cloud/register',{email:'owner@example.com',password:'long valid password 123'}),env),cookie=register.headers.get('Set-Cookie').split(';')[0];
  const saved=await handleCloud(req('/api/cloud/vault',{config},'',cookie),env),savedId=(await saved.json()).id;assert.equal((await call('connect',{saved_account_id:savedId},b)).status,401);assert.equal((await call('connect',{saved_account_id:savedId},b,cookie)).status,200);assert.equal((await call('messages',{},b)).status,401);
  const other=await handleCloud(req('/api/cloud/register',{email:'other@example.com',password:'another long password 123'}),env),otherCookie=other.headers.get('Set-Cookie').split(';')[0],t=(await call('session',{},'',otherCookie)).data.token;assert.equal((await call('connect',{saved_account_id:savedId},t,otherCookie)).status,404);
  await call('disconnect',{},a);assert.equal((await call('messages',{},a)).status,401);env.DB.db.prepare('UPDATE tmailer_mail_sessions SET expires_at=0').run();assert.equal((await call('messages',{},b,cookie)).status,401);assert.equal(calls,3);
  const cross=new Request(origin+'/api/session',{method:'POST',headers:{Origin:'https://evil.example','Content-Type':'application/json'},body:'{}'});assert.equal((await handleMail(cross,env)).status,403);
 }finally{globalThis.fetch=originalFetch;}
});
test('cloud recipient history is encrypted and separated by user and sending mailbox',async()=>{
 const env={DB:new D1(),TMAILER_VAULT_KEY:b64(crypto.getRandomValues(new Uint8Array(32)))};
 for(const id of ['one','two'])env.DB.db.prepare('INSERT INTO tmailer_users(id,display_name,settings_json,created_at) VALUES(?,?,?,?)').run(id,id,'{}',0);
 await saveRecipients(env,{id:'one'},'first@example.com',[{email:'private-recipient@example.com',name:'Contact'}],true);
 assert.equal((await readRecipients(env,{id:'one'},'first@example.com'))[0].email,'private-recipient@example.com');
 assert.deepEqual(await readRecipients(env,{id:'two'},'first@example.com'),[]);assert.deepEqual(await readRecipients(env,{id:'one'},'second@example.com'),[]);
 const row=env.DB.db.prepare('SELECT * FROM tmailer_recipients').get();assert.ok(!row.encrypted_history.includes('private-recipient'));assert.ok(!row.id.includes('first@example.com'));
});
