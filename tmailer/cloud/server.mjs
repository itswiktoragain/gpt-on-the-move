const ENC=new TextEncoder(),DEC=new TextDecoder();
const DAY=86400,MAX_BODY=32768,ORIGIN='https://tmailer.tata-i-wikto-6410.chatgpt.site';
const COOKIE='__Host-tmailer';
export class ApiError extends Error{constructor(message,status=400){super(message);this.status=status;}}
function now(){return Math.floor(Date.now()/1000);}
export function b64(bytes){let text='';for(const n of bytes)text+=String.fromCharCode(n);return btoa(text).replaceAll('+','-').replaceAll('/','_').replace(/=+$/,'');}
export function unb64(text){const raw=atob(text.replaceAll('-','+').replaceAll('_','/'));return Uint8Array.from(raw,c=>c.charCodeAt(0));}
function random(size=32){return b64(crypto.getRandomValues(new Uint8Array(size)));}
export async function digest(text){return b64(new Uint8Array(await crypto.subtle.digest('SHA-256',ENC.encode(text))));}
export async function passwordHash(password,salt=random(24)){
 const key=await crypto.subtle.importKey('raw',ENC.encode(password),'PBKDF2',false,['deriveBits']);
 const bits=await crypto.subtle.deriveBits({name:'PBKDF2',hash:'SHA-256',salt:unb64(salt),iterations:100000},key,256);
 return 'pbkdf2-sha256:100000:'+salt+':'+b64(new Uint8Array(bits));
}
function constantEqual(a,b){let n=a.length^b.length;const size=Math.max(a.length,b.length);for(let i=0;i<size;i++)n|=(a.charCodeAt(i)||0)^(b.charCodeAt(i)||0);return n===0;}
async function verifyPassword(password,hash){if(!hash||!hash.startsWith('pbkdf2-sha256:100000:'))return false;const salt=hash.split(':')[2];return constantEqual(await passwordHash(password,salt),hash);}
async function masterKey(env){if(!env.TMAILER_VAULT_KEY)throw new ApiError('Cloud storage is not configured.',503);const bytes=unb64(env.TMAILER_VAULT_KEY);if(bytes.length!==32)throw new ApiError('Cloud storage key is invalid.',503);return crypto.subtle.importKey('raw',bytes,'AES-GCM',false,['encrypt','decrypt']);}
export async function encrypt(env,value,owner){const iv=crypto.getRandomValues(new Uint8Array(12));const data=await crypto.subtle.encrypt({name:'AES-GCM',iv,additionalData:ENC.encode(owner)},await masterKey(env),ENC.encode(JSON.stringify(value)));return 'v1.'+b64(iv)+'.'+b64(new Uint8Array(data));}
export async function decrypt(env,value,owner){try{const[v,iv,data]=value.split('.');if(v!=='v1')throw Error();const plain=await crypto.subtle.decrypt({name:'AES-GCM',iv:unb64(iv),additionalData:ENC.encode(owner)},await masterKey(env),unb64(data));return JSON.parse(DEC.decode(plain));}catch{throw new ApiError('The saved mail account could not be unlocked.',500);}}
const query=(db,sql,...values)=>db.prepare(sql).bind(...values);
function desktopOrigin(req){const origin=req.headers.get('Origin');return !!origin&&/^http:\/\/127\.0\.0\.1:\d+$/.test(origin);}
function isDesktop(req){return desktopOrigin(req)&&req.headers.get('X-Tmailer-Desktop')==='1';}
function allowed(req){const origin=req.headers.get('Origin');return !origin||origin===new URL(req.url).origin||desktopOrigin(req);}
function response(req,value,status=200,extra={}){const headers={'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff',...extra};if(desktopOrigin(req)){headers['Access-Control-Allow-Origin']=req.headers.get('Origin');headers['Vary']='Origin';headers['Access-Control-Allow-Headers']='Content-Type, X-Tmailer-Cloud-Token, X-Tmailer-Desktop';headers['Access-Control-Allow-Methods']='GET, POST, PUT, DELETE, OPTIONS';}return new Response(JSON.stringify(value),{status,headers});}
async function body(req){const length=Number(req.headers.get('Content-Length')||0);if(length>MAX_BODY)throw new ApiError('Request is too large.',413);const reader=req.body?.getReader();let parts=[],size=0;if(reader){while(true){const{value,done}=await reader.read();if(done)break;size+=value.byteLength;if(size>MAX_BODY){await reader.cancel();throw new ApiError('Request is too large.',413);}parts.push(value);}}const bytes=new Uint8Array(size);let offset=0;for(const part of parts){bytes.set(part,offset);offset+=part.byteLength;}const content=DEC.decode(bytes);let value;try{value=JSON.parse(content||'{}');}catch{throw new ApiError('Invalid JSON request.');}if(!value||typeof value!=='object'||Array.isArray(value))throw new ApiError('Invalid request.');return value;}
function email(value){const v=String(value||'').trim().toLowerCase();if(v.length>254||!/^\S+@[^\s@]+\.[^\s@]+$/.test(v))throw new ApiError('Enter a valid email address.');return v;}
function password(value){if(typeof value!=='string'||value.length<12||value.length>1024)throw new ApiError('Use a Tmailer password between 12 and 1,024 characters.');return value;}
function cookie(token,age=30*DAY){return `${COOKIE}=${token}; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=${age}`;}
export async function authenticate(req,env){let token;if(isDesktop(req))token=req.headers.get('X-Tmailer-Cloud-Token');else token=req.headers.get('Cookie')?.split(';').map(x=>x.trim()).find(x=>x.startsWith(COOKIE+'='))?.slice(COOKIE.length+1);if(!token)return null;return query(env.DB,'SELECT u.*,s.token_hash AS session_hash FROM tmailer_sessions s JOIN tmailer_users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>?',await digest(token),now()).first();}
function publicUser(u){let settings={};try{settings=JSON.parse(u.settings_json||'{}');}catch{}return{id:u.id,email:u.email,displayName:u.display_name,hasPassword:!!u.password_hash,chatgptLinked:!!u.chatgpt_subject,settings};}
async function session(req,env,user){const token=random(),hash=await digest(token);await query(env.DB,'INSERT INTO tmailer_sessions(token_hash,user_id,expires_at,created_at) VALUES(?,?,?,?)',hash,user.id,now()+30*DAY,now()).run();const value={user:publicUser(user)};if(isDesktop(req))value.token=token;return response(req,value,200,isDesktop(req)?{}:{'Set-Cookie':cookie(token)});}
async function limit(req,env,kind,max=20,seconds=900){const ip=req.headers.get('cf-connecting-ip')||'unknown';const stamp=now(),bucket=Math.floor(stamp/seconds);const key=await digest(kind+':'+ip+':'+bucket);const row=await query(env.DB,'INSERT INTO tmailer_auth_limits(key,count,expires_at) VALUES(?,1,?) ON CONFLICT(key) DO UPDATE SET count=count+1 RETURNING count',key,stamp+seconds).first();if(row.count>max)throw new ApiError('Too many attempts. Try again later.',429);await env.DB.batch([query(env.DB,'DELETE FROM tmailer_auth_limits WHERE expires_at<?',stamp),query(env.DB,'DELETE FROM tmailer_sessions WHERE expires_at<?',stamp),query(env.DB,'DELETE FROM tmailer_devices WHERE expires_at<?',stamp)]);}
function cleanSettings(value){const out={};if(typeof value?.readingPaneHidden==='boolean')out.readingPaneHidden=value.readingPaneHidden;if(typeof value?.lastMailboxId==='string'&&value.lastMailboxId.length<100)out.lastMailboxId=value.lastMailboxId;return out;}
function safeConfig(c){const fields=['email','username','protocol','incoming','incoming_security','smtp','smtp_security','smtp_username'];return Object.fromEntries(fields.map(k=>[k,c[k]||'']));}
function checkEndpoint(value,protocol){let parsed;try{parsed=new URL(value.includes('://')?value:'mail://'+value);}catch{throw new ApiError('Enter a valid mail server hostname or URL.');}if(!parsed.hostname||parsed.username||parsed.password||parsed.search||parsed.hash||!['','/'].includes(parsed.pathname))throw new ApiError('Mail server URLs must not include credentials or paths.');const valid=protocol==='smtp'?['mail:','smtp:','smtps:']:protocol==='imap'?['mail:','imap:','imaps:']:['mail:','pop3:','pop3s:','pop:','pops:'];if(!valid.includes(parsed.protocol))throw new ApiError('The URL scheme does not match the mail protocol.');}
function validateMail(input,previous={}){const c={...previous};for(const key of ['email','username','password','protocol','incoming','incoming_security','smtp','smtp_security','smtp_username','smtp_password'])if(typeof input[key]==='string'){if(input[key].length>2048)throw new ApiError('A mail setting is too long.');if((key==='password'||key==='smtp_password')&&input[key]===''&&previous[key])continue;c[key]=input[key];}c.email=String(c.email||'').trim();if(!c.email.includes('@')||/[\r\n]/.test(c.email))throw new ApiError('Enter your email address.');c.username=c.username||c.email;if(!c.password)throw new ApiError('Enter your mail password or app password.');if(!['imap','pop3'].includes(c.protocol))throw new ApiError('Choose IMAP or POP3.');if(!['tls','starttls'].includes(c.incoming_security)||!['tls','starttls'].includes(c.smtp_security))throw new ApiError('Encrypted mail connections are required.');checkEndpoint(c.incoming||'',c.protocol);checkEndpoint(c.smtp||'','smtp');return c;}
async function vaultList(env,user){const rows=(await query(env.DB,'SELECT * FROM tmailer_vault WHERE user_id=? ORDER BY created_at',user.id).all()).results;const accounts=[];for(const row of rows){const config=await decrypt(env,row.encrypted_config,user.id+':'+row.id);accounts.push({id:row.id,...safeConfig(config),hasPassword:!!config.password,updatedAt:row.updated_at});}return accounts;}
export async function loadVault(env,user,id){const row=await query(env.DB,'SELECT * FROM tmailer_vault WHERE id=? AND user_id=?',id,user.id).first();if(!row)throw new ApiError('Mail account not found.',404);return{row,config:await decrypt(env,row.encrypted_config,user.id+':'+row.id)};}
export async function handleCloud(req,env){
 try{
  if(!allowed(req))throw new ApiError('Origin not allowed.',403);
  if(req.method==='OPTIONS')return response(req,{});
  if(!env.DB)throw new ApiError('Cloud storage is unavailable.',503);
  if(req.method!=='GET'&&!req.headers.get('Content-Type')?.startsWith('application/json'))throw new ApiError('Use a JSON request.',415);
  if(req.method!=='GET'&&!req.headers.get('Origin'))throw new ApiError('An Origin header is required.',403);
  const path=new URL(req.url).pathname.replace(/^\/api\/cloud\/?/,'').replace(/\/$/,'');
  const data=req.method==='GET'?{}:await body(req);
  const user=await authenticate(req,env);
  if(path==='me'&&req.method==='GET')return response(req,{user:user?publicUser(user):null});
  if(path==='register'&&req.method==='POST'){
   await limit(req,env,'register',6,3600);const login=email(data.email),pass=password(data.password),display=String(data.displayName||login.split('@')[0]).trim().slice(0,80);
   if(await query(env.DB,'SELECT id FROM tmailer_users WHERE login_email=?',login).first())throw new ApiError('An account with that login already exists.');
   const u={id:crypto.randomUUID(),email:login,login_email:login,password_hash:await passwordHash(pass),chatgpt_subject:null,display_name:display||login,settings_json:'{}'};
   await query(env.DB,'INSERT INTO tmailer_users(id,email,login_email,password_hash,display_name,settings_json,created_at) VALUES(?,?,?,?,?,?,?)',u.id,u.email,u.login_email,u.password_hash,u.display_name,'{}',now()).run();return session(req,env,u);
  }
  if(path==='login'&&req.method==='POST'){
   await limit(req,env,'login');const login=email(data.email);const u=await query(env.DB,'SELECT * FROM tmailer_users WHERE login_email=?',login).first();const pass=typeof data.password==='string'?data.password:'';
   if(!u||!await verifyPassword(pass,u.password_hash))throw new ApiError('Email or password is incorrect.',401);return session(req,env,u);
  }
  if(path==='chatgpt'&&req.method==='POST'){
   const subject=req.headers.get('oai-authenticated-user-id');if(!subject)throw new ApiError('Continue with ChatGPT to sign in.',401);
   let u=await query(env.DB,'SELECT * FROM tmailer_users WHERE chatgpt_subject=?',subject).first();
   if(user){if(u&&u.id!==user.id)throw new ApiError('This ChatGPT login is linked to another Tmailer account.',409);await query(env.DB,'UPDATE tmailer_users SET chatgpt_subject=? WHERE id=?',subject,user.id).run();u={...user,chatgpt_subject:subject};}
   if(!u){const contact=req.headers.get('oai-authenticated-user-email')||null;u={id:crypto.randomUUID(),email:contact,password_hash:null,chatgpt_subject:subject,display_name:contact?.split('@')[0]||'Tmailer user',settings_json:'{}'};await query(env.DB,'INSERT INTO tmailer_users(id,email,chatgpt_subject,display_name,settings_json,created_at) VALUES(?,?,?,?,?,?)',u.id,u.email,subject,u.display_name,'{}',now()).run();}return session(req,env,u);
  }
  if(path==='device/start'&&req.method==='POST'){
   if(!isDesktop(req))throw new ApiError('Use this flow from Tmailer desktop.',403);await limit(req,env,'device',12,900);const id=random(24),secret=random();await query(env.DB,'INSERT INTO tmailer_devices(id,secret_hash,state,expires_at) VALUES(?,?,?,?)',id,await digest(secret),'pending',now()+300).run();return response(req,{id,secret,authorizeUrl:ORIGIN+'/?tmailer_device='+encodeURIComponent(id)});
  }
  if(path==='device/poll'&&req.method==='POST'){
   if(!isDesktop(req))throw new ApiError('Use this flow from Tmailer desktop.',403);const row=await query(env.DB,'SELECT * FROM tmailer_devices WHERE id=? AND expires_at>?',String(data.id||''),now()).first();if(!row||!constantEqual(row.secret_hash,await digest(String(data.secret||''))))throw new ApiError('Desktop sign-in expired.',401);if(row.state==='consumed')throw new ApiError('Desktop sign-in was already used.',401);if(row.state!=='approved')return response(req,{pending:true});const claim=await query(env.DB,"UPDATE tmailer_devices SET state='consumed',encrypted_token=NULL WHERE id=? AND state='approved' RETURNING id",row.id).first();if(!claim)throw new ApiError('Desktop sign-in was already used.',401);const payload=await decrypt(env,row.encrypted_token,'device:'+row.id);return response(req,{token:payload.token});
  }
  if(!user)throw new ApiError('Sign in to your Tmailer account first.',401);
  if(path==='logout'&&req.method==='POST'){await query(env.DB,'DELETE FROM tmailer_sessions WHERE token_hash=?',user.session_hash).run();return response(req,{},200,{'Set-Cookie':cookie('',0)});}
  if(path==='profile'&&req.method==='PUT'){
   const display=String(data.displayName||user.display_name).trim().slice(0,80);const settings=JSON.stringify(cleanSettings(data.settings||JSON.parse(user.settings_json||'{}')));await query(env.DB,'UPDATE tmailer_users SET display_name=?,settings_json=? WHERE id=?',display,settings,user.id).run();return response(req,{user:publicUser({...user,display_name:display,settings_json:settings})});
  }
  if(path==='vault'&&req.method==='GET')return response(req,{accounts:await vaultList(env,user)});
  if(path==='vault'&&req.method==='POST'){
   const id=typeof data.id==='string'?data.id:crypto.randomUUID();const previous=data.id?(await loadVault(env,user,id)).config:{};const c=validateMail(data.config||{},previous),encrypted=await encrypt(env,c,user.id+':'+id),stamp=now();
   if(data.id)await query(env.DB,'UPDATE tmailer_vault SET email=?,protocol=?,encrypted_config=?,updated_at=? WHERE id=? AND user_id=?',c.email,c.protocol,encrypted,stamp,id,user.id).run();
   else await query(env.DB,'INSERT INTO tmailer_vault(id,user_id,email,protocol,encrypted_config,created_at,updated_at) VALUES(?,?,?,?,?,?,?)',id,user.id,c.email,c.protocol,encrypted,stamp,stamp).run();
   return response(req,{id,account:{id,...safeConfig(c),hasPassword:true},accounts:await vaultList(env,user)});
  }
  const match=/^vault\/([\w-]+)(\/use)?$/.exec(path);
  if(match){const{config}=await loadVault(env,user,match[1]);if(req.method==='DELETE'&&!match[2]){await query(env.DB,'DELETE FROM tmailer_vault WHERE id=? AND user_id=?',match[1],user.id).run();return response(req,{});}if(req.method==='POST'&&match[2]){if(!isDesktop(req))throw new ApiError('Direct credential export is only for your authenticated desktop app.',403);return response(req,{config});}}
  if(path==='device/approve'&&req.method==='POST'){
   const row=await query(env.DB,"SELECT * FROM tmailer_devices WHERE id=? AND state='pending' AND expires_at>?",String(data.id||''),now()).first();if(!row)throw new ApiError('Desktop sign-in expired or was already approved.');const token=random(),hash=await digest(token),encrypted=await encrypt(env,{token},'device:'+row.id);await env.DB.batch([query(env.DB,'INSERT INTO tmailer_sessions(token_hash,user_id,expires_at,created_at) VALUES(?,?,?,?)',hash,user.id,now()+30*DAY,now()),query(env.DB,"UPDATE tmailer_devices SET state='approved',encrypted_token=? WHERE id=? AND state='pending'",encrypted,row.id)]);return response(req,{approved:true});
  }
  throw new ApiError('Not found.',404);
 }catch(error){if(error instanceof ApiError)return response(req,{error:error.message},error.status);return response(req,{error:'The cloud account request failed. Please try again.'},500);}
}
