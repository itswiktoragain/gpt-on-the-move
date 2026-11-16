import {env} from 'cloudflare:workers';
import {handleCloud} from '../../../../cloud/server.mjs';
export const dynamic='force-dynamic';
export const GET=(request:Request)=>handleCloud(request,env);
export const POST=(request:Request)=>handleCloud(request,env);
export const PUT=(request:Request)=>handleCloud(request,env);
export const DELETE=(request:Request)=>handleCloud(request,env);
export const OPTIONS=(request:Request)=>handleCloud(request,env);
