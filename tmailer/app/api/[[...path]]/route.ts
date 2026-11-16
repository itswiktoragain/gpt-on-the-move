import {env} from 'cloudflare:workers';
import {authenticate,loadVault} from '../../../cloud/server.mjs';
export const dynamic='force-dynamic';
const json=(value:unknown,status=200)=>new Response(JSON.stringify(value),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
export async function POST(request:Request){
 if(request.headers.get('Origin')!==new URL(request.url).origin)return json({error:'Origin not allowed.'},403);
 if(!env.MAIL_BACKEND_URL)return json({error:'Tmailer’s built-in mail connections are awaiting activation.'},503);
 const action=new URL(request.url).pathname.replace(/^\/api\//,'');
 if(!['session','connect','disconnect','folders','messages','message','send'].includes(action))return json({error:'Not found.'},404);
 try{
  const text=await request.text();if(text.length>31*1024*1024)return json({error:'Request too large.'},413);
  let data=JSON.parse(text||'{}');
  if(action==='connect'&&data.saved_account_id){const user=await authenticate(request,env);if(!user)return json({error:'Sign in to use your saved mail accounts.'},401);data=(await loadVault(env,user,data.saved_account_id)).config;}
  const base=new URL(env.MAIL_BACKEND_URL);if(base.protocol!=='https:')return json({error:'Mail backend configuration is invalid.'},503);
  const response=await fetch(base.href.replace(/\/$/,'')+'/api/'+action,{method:'POST',headers:{'Content-Type':'application/json','X-Tmailer-Token':request.headers.get('X-Tmailer-Token')||'',...(env.MAIL_BACKEND_SECRET?{'X-Tmailer-Service-Key':env.MAIL_BACKEND_SECRET}:{})},body:JSON.stringify(data)});
  return new Response(response.body,{status:response.status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
 }catch{return json({error:'The mail connection could not be completed. Try again later.'},502);}
}
