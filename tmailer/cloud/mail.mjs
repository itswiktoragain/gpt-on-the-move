import {authenticate,loadVault,encrypt,decrypt,b64,digest,ApiError} from './server.mjs';
import {readRecipients,saveRecipients,sentAddresses} from './recipients.mjs';
const enc=new TextEncoder(),TTL=7200;
const response=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
const q=(env,sql,...args)=>env.DB.prepare(sql).bind(...args);
export async function backend(env,action,config={},data={}){
 if(!env.MAIL_BACKEND_URL||!env.MAIL_BACKEND_SIGNING_KEY)throw new ApiError('Mail connections are temporarily unavailable.',503);
 if(new URL(env.MAIL_BACKEND_URL).protocol!=='https:')throw new ApiError('Mail backend configuration is invalid.',503);
 const body=JSON.stringify({config,data}),stamp=String(Date.now());
 const key=await crypto.subtle.importKey('jwk',JSON.parse(env.MAIL_BACKEND_SIGNING_KEY),{name:'ECDSA',namedCurve:'P-256'},false,['sign']);
 const signature=await crypto.subtle.sign({name:'ECDSA',hash:'SHA-256'},key,enc.encode(stamp+'\n'+action+'\n'+body));
 let raw='';for(const byte of new Uint8Array(signature))raw+=String.fromCharCode(byte);
 const res=await fetch(env.MAIL_BACKEND_URL.replace(/\/$/,'')+'/api/'+action,{method:'POST',headers:{'Content-Type':'application/json','X-Tmailer-Time':stamp,'X-Tmailer-Signature':btoa(raw)},body,signal:AbortSignal.timeout(55000)});
 const result=await res.json();if(!res.ok)throw new ApiError(result.error||'The mail server rejected the request.',res.status);return result;
}
async function body(req){const reader=req.body?.getReader();let parts=[],size=0;while(reader){const{done,value}=await reader.read();if(done)break;size+=value.length;if(size>31*1024*1024){await reader.cancel();throw new ApiError('Request too large.',413);}parts.push(value);}const all=new Uint8Array(size);let n=0;for(const p of parts){all.set(p,n);n+=p.length;}return JSON.parse(new TextDecoder().decode(all)||'{}');}
export async function handleMail(request,env){try{
 if(request.headers.get('Origin')!==new URL(request.url).origin)throw new ApiError('Origin not allowed.',403);
 if(!request.headers.get('Content-Type')?.startsWith('application/json'))throw new ApiError('Use a JSON request.',415);
 const action=new URL(request.url).pathname.replace(/^\/api\//,'');if(!['session','connect','disconnect','folders','messages','message','send','recipients'].includes(action))throw new ApiError('Not found.',404);
 const data=await body(request),now=Math.floor(Date.now()/1000),user=await authenticate(request,env);
 const ip=request.headers.get('cf-connecting-ip')||'unknown',bucket=Math.floor(now/60),limitKey=await digest('mail:'+ip+':'+bucket);
 const count=await q(env,'INSERT INTO tmailer_auth_limits(key,count,expires_at) VALUES(?,1,?) ON CONFLICT(key) DO UPDATE SET count=count+1 RETURNING count',limitKey,now+120).first();if(count.count>120)throw new ApiError('Too many mail requests. Try again in a minute.',429);
 if(action==='session'){
  const token=b64(crypto.getRandomValues(new Uint8Array(32))),hash=await digest(token);
  await env.DB.batch([q(env,'DELETE FROM tmailer_mail_sessions WHERE expires_at<?',now),q(env,'DELETE FROM tmailer_auth_limits WHERE expires_at<?',now),q(env,'INSERT INTO tmailer_mail_sessions(token_hash,user_id,encrypted_config,expires_at) VALUES(?,?,NULL,?)',hash,user?.id||null,now+TTL)]);return response({token});
 }
 const token=request.headers.get('X-Tmailer-Token')||'';if(!token||token.length>100)throw new ApiError('Reconnect your mailbox to continue.',401);
 const hash=await digest(token),session=await q(env,'SELECT * FROM tmailer_mail_sessions WHERE token_hash=? AND expires_at>?',hash,now).first();if(!session||(session.user_id&&session.user_id!==user?.id))throw new ApiError('Reconnect your mailbox to continue.',401);
 if(action==='disconnect'){await q(env,'UPDATE tmailer_mail_sessions SET encrypted_config=NULL WHERE token_hash=?',hash).run();return response({connected:false});}
 if(action==='connect'){
  let config=data;if(data.saved_account_id){if(!user)throw new ApiError('Sign in to use your saved mail accounts.',401);config=(await loadVault(env,user,data.saved_account_id)).config;}
  const result=await backend(env,'connect',config);await q(env,'UPDATE tmailer_mail_sessions SET user_id=?,encrypted_config=?,expires_at=? WHERE token_hash=?',user?.id||null,await encrypt(env,config,'mail:'+hash),now+TTL,hash).run();return response(result);
 }
 if(!session.encrypted_config)throw new ApiError('Connect a mailbox first.',401);
 const config=await decrypt(env,session.encrypted_config,'mail:'+hash);
 if(action==='recipients'){let history=await readRecipients(env,user,config.email);let source='history';if(data.importSent){try{const imported=await backend(env,'recipients',config);source=imported.source;history=await saveRecipients(env,user,config.email,imported.recipients||[]);}catch{return response({recipients:history,source:'unavailable'});}}return response({recipients:history,source});}
 const result=await backend(env,action,config,data);
 if(action==='send'&&result.sent&&user){try{await saveRecipients(env,user,config.email,sentAddresses(data,result.refused||[]),true);}catch{result.history_warning='Message sent, but recipient history could not be saved.';}}
 return response(result);
 }catch(e){return response({error:e instanceof ApiError?e.message:e instanceof SyntaxError?'Invalid JSON request.':'The mail connection could not be completed. Please try again.'},e instanceof ApiError?e.status:e instanceof SyntaxError?400:502);}}
