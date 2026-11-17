import PostalMime from 'npm:postal-mime@2.7.5';
import {perform,MailError,dial,endpoint} from './core.mjs';
// Public verification key only. The private signing key lives exclusively in Sites secrets.
const PUBLIC_KEY={key_ops:['verify'],ext:true,kty:'EC',x:'qZtJnMMN_HUoNlSSlxeqUGZeCBfHT4miWQxHgX-PqaI',y:'zovP7pSpMlJGLj765jWOtvsl4GD_gacSAwFVEG-2W24',crv:'P-256'};
const key=crypto.subtle.importKey('jwk',PUBLIC_KEY,{name:'ECDSA',namedCurve:'P-256'},false,['verify']);
const runtime={resolve:(host:string)=>Deno.resolveDns(host,'A'),connect:(hostname:string,port:number)=>Deno.connect({hostname,port}),upgrade:(conn:Deno.TcpConn,hostname:string)=>Deno.startTls(conn,{hostname})};
const reply=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
async function readBody(req:Request){const reader=req.body?.getReader();const chunks:Uint8Array[]=[];let size=0;while(reader){const{done,value}=await reader.read();if(done)break;size+=value.length;if(size>31*1024*1024){await reader.cancel();throw new MailError('Request too large.',413);}chunks.push(value);}const bytes=new Uint8Array(size);let off=0;for(const c of chunks){bytes.set(c,off);off+=c.length;}return new TextDecoder().decode(bytes);}
Deno.serve(async req=>{
 let timer:ReturnType<typeof setTimeout>|undefined;
 try{
  if(req.method!=='POST')return reply({error:'Use the Tmailer website to access mail.'},405);
  const action=new URL(req.url).pathname.split('/').pop()||'',stamp=req.headers.get('X-Tmailer-Time')||'',signature=req.headers.get('X-Tmailer-Signature')||'';
  if(!/^\d+$/.test(stamp)||Math.abs(Date.now()-Number(stamp))>60000||!signature)return reply({error:'Unauthorized.'},401);
  const body=await readBody(req);let valid=false;try{valid=await crypto.subtle.verify({name:'ECDSA',hash:'SHA-256'},await key,Uint8Array.from(atob(signature),c=>c.charCodeAt(0)),new TextEncoder().encode(stamp+'\n'+action+'\n'+body));}catch{}
  if(!valid)return reply({error:'Unauthorized.'},401);
  if(action==='health')return reply({ready:true,protocols:['imap','pop3','smtp'],smtp_port:465});
  if(action==='probe'){
   const results=[];for(const [protocol,host] of [['imap','imap.gmail.com'],['pop3','pop.gmail.com'],['smtp','smtp.gmail.com']]){let w;try{w=await dial(endpoint(host,protocol,'tls'),runtime);const greeting=await w.line();results.push({protocol,tls:true,greeting:greeting.slice(0,180)});}catch(e){results.push({protocol,tls:false,error:e instanceof Error?e.message:'Connection failed'});}finally{w?.close();}}return reply({results});
  }
  const input=JSON.parse(body);const timeout=new Promise((_,reject)=>{timer=setTimeout(()=>reject(new MailError('The mail server took too long to respond. Check the server address and try again.',504)),50000);});
  const result=await Promise.race([perform(action,input.config,input.data||{},runtime,raw=>PostalMime.parse(raw,{maxNestingDepth:30,maxHeadersSize:1024*1024})),timeout]);return reply(result);
 }catch(error){if(error instanceof MailError)return reply({error:error.message},error.status);if(error instanceof SyntaxError)return reply({error:'Invalid request.'},400);const text=error instanceof Error?error.message:'';if(/certificate|tls|peer|issuer/i.test(text))return reply({error:'The mail server’s TLS certificate could not be verified. Check the hostname and encryption setting.'},502);return reply({error:'The mail server could not be reached. Check its hostname, port, and whether IMAP or POP3 is enabled.'},502);
 }finally{if(timer)clearTimeout(timer);}
});
