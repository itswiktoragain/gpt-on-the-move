import {env} from 'cloudflare:workers';
export const dynamic='force-dynamic';
export function GET(){return new Response('window.TMAILER_CONFIG='+JSON.stringify({desktop:false,managed:true,ready:!!env.MAIL_BACKEND_URL&&!!env.MAIL_BACKEND_SIGNING_KEY,cloudEnabled:true,apiBase:''})+';',{headers:{'Content-Type':'application/javascript','Cache-Control':'no-store'}})}
