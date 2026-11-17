import {env} from 'cloudflare:workers';
import {handleMail} from '../../../cloud/mail.mjs';
export const dynamic='force-dynamic';
export async function POST(request:Request){return handleMail(request,env);}
