// Native IMAP, POP3 and SMTP. No provider API or local bridge is used.
const enc=new TextEncoder(),dec=new TextDecoder(),MAX=30*1024*1024;
export class MailError extends Error { constructor(message,status=400){super(message);this.status=status;} }
export const bytes64=b=>{let s='';for(let i=0;i<b.length;i+=8192)s+=String.fromCharCode(...b.subarray(i,i+8192));return btoa(s);};
const b64=s=>bytes64(enc.encode(s));
const join=parts=>{const result=new Uint8Array(parts.reduce((n,p)=>n+p.length,0));let off=0;for(const p of parts){result.set(p,off);off+=p.length;}return result;};
const clean=v=>{if(typeof v!=='string'||/[\r\n\0]/.test(v)||v.length>4096)throw new MailError('A mail setting contains invalid characters.');return v;};
export const quote=v=>'"'+clean(v).replaceAll('\\','\\\\').replaceAll('"','\\"')+'"';
export function endpoint(value,protocol,security='tls'){
 let u;try{u=new URL(value.includes('://')?value:'mail://'+value);}catch{throw new MailError('Enter a valid mail server hostname.');}
 if(!['mail:',...(protocol==='imap'?['imap:','imaps:']:protocol==='pop3'?['pop:','pops:','pop3:','pop3s:']:['smtp:','smtps:'])].includes(u.protocol)||u.username||u.password||u.search||u.hash||!['','/'].includes(u.pathname))throw new MailError('Server URLs must match the mail protocol and contain no credentials or paths.');
 if(['imaps:','pops:','pop3s:','smtps:'].includes(u.protocol))security='tls';
 if(!['tls','starttls'].includes(security))throw new MailError('Use SSL/TLS or STARTTLS.');
 let port=Number(u.port)||(protocol==='imap'?(security==='tls'?993:143):protocol==='pop3'?(security==='tls'?995:110):(security==='tls'?465:587));
 // Supabase blocks SMTP 25/587. The same server's standard implicit-TLS endpoint is tried instead.
 if(protocol==='smtp'&&[25,587].includes(port)){port=465;security='tls';}
 if(!(protocol==='imap'?[143,993]:protocol==='pop3'?[110,995]:[465]).includes(port))throw new MailError('Use standard encrypted mail ports: IMAP 993/143, POP3 995/110, SMTP 465.');
 const hostname=u.hostname.toLowerCase();if(!/^[a-z0-9.-]+$/.test(hostname)||!hostname.includes('.')||hostname.endsWith('.')||hostname.length>253)throw new MailError('Use a public mail server hostname.');
 return {hostname,port,security};
}
export function publicIPv4(ip){const a=ip.split('.').map(Number);if(a.length!==4||a.some(n=>!Number.isInteger(n)||n<0||n>255))return false;const[x,y]=a;return !(x===0||x===10||x===127||x>=224||(x===100&&y>=64&&y<=127)||(x===169&&y===254)||(x===172&&y>=16&&y<=31)||(x===192&&(y===168||y===0||y===2))||(x===198&&(y===18||y===19||y===51))||(x===203&&y===0));}
export function validateConfig(c){
 if(!c||!['imap','pop3'].includes(c.protocol))throw new MailError('Choose IMAP or POP3.');
 for(const k of ['email','incoming','smtp','password'])if(typeof c[k]!=='string'||!c[k])throw new MailError('Enter your email, password, and incoming and outgoing servers.');
 const out={...c,username:c.username||c.email,smtp_username:c.smtp_username||c.username||c.email,smtp_password:c.smtp_password||c.password};
 for(const k of ['email','username','password','smtp_username','smtp_password'])clean(out[k]);
 if(!/^[^\s<>@]+@[^\s<>@]+\.[^\s<>@]+$/.test(out.email))throw new MailError('Enter a valid email address.');
 endpoint(c.incoming,c.protocol,c.incoming_security);endpoint(c.smtp,'smtp',c.smtp_security);return out;
}
export class Wire {
 constructor(conn,upgrade){this.conn=conn;this.upgradeFn=upgrade;this.buf=new Uint8Array();this.total=0;this.closed=false;this.timer=setTimeout(()=>this.close(),45000);}
 close(){clearTimeout(this.timer);if(!this.closed){this.closed=true;try{this.conn.close();}catch{}}}
 async write(data){const bytes=typeof data==='string'?enc.encode(data):data;let n=0;while(n<bytes.length)n+=await this.conn.write(bytes.subarray(n));}
 async fill(){const b=new Uint8Array(16384);const n=await this.conn.read(b);if(n===null)throw new MailError('The mail server closed the connection. Please reconnect.',502);this.total+=n;if(this.total>MAX+2*1024*1024)throw new MailError('This message exceeds the 30 MiB download limit.');this.buf=join([this.buf,b.subarray(0,n)]);}
 async lineBytes(){while(true){const n=this.buf.indexOf(10);if(n>=0){const line=this.buf.slice(0,n&&this.buf[n-1]===13?n-1:n);this.buf=this.buf.slice(n+1);return line;}if(this.buf.length>2*1024*1024)throw new MailError('The mail server returned an oversized response.',502);await this.fill();}}
 async line(){return dec.decode(await this.lineBytes());}
 async literal(n){if(n>MAX)throw new MailError('This message exceeds the 30 MiB download limit.');while(this.buf.length<n)await this.fill();const b=this.buf.slice(0,n);this.buf=this.buf.slice(n);return b;}
 async tls(hostname){if(this.buf.length)throw new MailError('Unexpected data before TLS negotiation.',502);this.conn=await this.upgradeFn(this.conn,hostname);}
}
export async function dial(ep,runtime){
 const addresses=await runtime.resolve(ep.hostname);if(!addresses.length||addresses.some(ip=>!publicIPv4(ip)))throw new MailError('Mail servers must resolve to public internet addresses.');
 let conn;try{conn=await runtime.connect(addresses[0],ep.port);const w=new Wire(conn,runtime.upgrade);if(ep.security==='tls')await w.tls(ep.hostname);return w;}catch(e){try{conn?.close();}catch{}throw e;}
}
export class Imap {
 constructor(w){this.w=w;this.tag=0;}
 async command(command){const tag='T'+(++this.tag);await this.w.write(tag+' '+command+'\r\n');const rows=[];while(true){const line=await this.w.line();if(line.startsWith(tag+' ')){if(!new RegExp('^'+tag+' OK(?: |$)','i').test(line))throw new MailError(/LOGIN|AUTHENTICATE/.test(command)?'Incoming login rejected. Check your username, app password, and whether IMAP is enabled.':'The IMAP server rejected this request.');return rows;}if(/^\* BYE/i.test(line))throw new MailError('The IMAP server ended the session.',502);const size=line.match(/\{(\d+)\}$/);rows.push({line,literal:size?await this.w.literal(Number(size[1])):null});}}
 async select(folder){await this.command('EXAMINE '+quote(folder||'INBOX'));}
}
async function incoming(c,runtime,fn){const ep=endpoint(c.incoming,c.protocol,c.incoming_security),w=await dial(ep,runtime);try{
 if(c.protocol==='imap'){const client=new Imap(w);if(!/^\* (OK|PREAUTH)/i.test(await w.line()))throw new MailError('The server did not return an IMAP greeting.',502);if(ep.security==='starttls'){await client.command('STARTTLS');await w.tls(ep.hostname);}await client.command('LOGIN '+quote(c.username)+' '+quote(c.password));return await fn(client);}
 if(!/^\+OK/.test(await w.line()))throw new MailError('The server did not return a POP3 greeting.',502);const p=new Pop(w);if(ep.security==='starttls'){await p.command('STLS');await w.tls(ep.hostname);}await p.command('USER '+clean(c.username));try{await p.command('PASS '+clean(c.password));}catch{throw new MailError('Incoming login rejected. Check your username, app password, and whether POP3 is enabled.');}return await fn(p);
 }finally{w.close();}}
export class Pop {
 constructor(w){this.w=w;}
 async command(cmd,multi=false){await this.w.write(cmd+'\r\n');const line=await this.w.line();if(!line.startsWith('+OK'))throw new MailError('The POP3 server rejected this request.');if(!multi)return line;const parts=[];while(true){let b=await this.w.lineBytes();if(b.length===1&&b[0]===46)break;if(b[0]===46)b=b.slice(1);parts.push(b,enc.encode('\r\n'));}return join(parts);}
 async uids(){return dec.decode(await this.command('UIDL',true)).trim().split(/\r?\n/).filter(Boolean).map(s=>{const[num,id]=s.split(/\s+/);return{num:Number(num),id};});}
}
export class Smtp {
 constructor(w){this.w=w;}
 async response(){const lines=[];while(true){const line=await this.w.line();if(!/^\d{3}[ -]/.test(line))throw new MailError('Unexpected SMTP response.',502);lines.push(line);if(line[3]===' ')return{code:Number(line.slice(0,3)),text:lines.join('\n')};}}
 async command(command,expected=[250]){await this.w.write(command+'\r\n');const r=await this.response();if(!expected.includes(r.code))throw new MailError(r.code===535||r.code===534?'SMTP login rejected. Check your outgoing username and app password.':'The SMTP server rejected this step (code '+r.code+').');return r;}
}
async function outgoing(c,runtime,fn){const ep=endpoint(c.smtp,'smtp',c.smtp_security),w=await dial(ep,runtime);try{const s=new Smtp(w);if((await s.response()).code!==220)throw new MailError('The server did not return an SMTP greeting.',502);let caps=(await s.command('EHLO tmailer.chatgpt.site')).text;if(ep.security==='starttls'){await s.command('STARTTLS',[220]);await w.tls(ep.hostname);caps=(await s.command('EHLO tmailer.chatgpt.site')).text;}
 if(/AUTH[^\n]*\bPLAIN\b/i.test(caps))await s.command('AUTH PLAIN '+b64('\0'+c.smtp_username+'\0'+c.smtp_password),[235]);else{await s.command('AUTH LOGIN',[334]);await s.command(b64(c.smtp_username),[334]);await s.command(b64(c.smtp_password),[235]);}return await fn(s);
 }catch(e){if(e instanceof MailError)throw e;throw new MailError('Could not reach the outgoing server over SSL/TLS on port 465. Check your provider’s SMTP settings.',502);}finally{w.close();}}
const address=v=>!v?'':(v.name?v.name+' <'+v.address+'>':v.address)||'';
const summary=(m,id,seen=false)=>({id:String(id),subject:m.subject||'(No subject)',from:address(m.from)||'Unknown sender',to:(m.to||[]).map(address).join(', '),date:m.date||'',seen});
export function decodeFolder(value){return value.replace(/&([^-]*)-/g,(_,v)=>{if(!v)return'&';try{const raw=atob(v.replaceAll(',','/'));let s='';for(let i=0;i<raw.length;i+=2)s+=String.fromCharCode((raw.charCodeAt(i)<<8)|raw.charCodeAt(i+1));return s;}catch{return '&'+v+'-';}});}
const folderName=line=>{const match=line.match(/^\* LIST \(([^)]*)\) (?:"(?:[^"\\]|\\.)*"|NIL) (.+)$/i);if(!match||/\\Noselect/i.test(match[1]))return null;const name=match[2];return name.startsWith('"')?name.slice(1,-1).replace(/\\(.)/g,'$1'):name;};
export function recipients(value){if(!value)return[];return clean(value).split(/[,;]/).map(v=>{const a=v.trim().match(/<([^<>]+)>$/)?.[1]||v.trim();if(!/^[A-Za-z0-9.!#$%&'*+\/=?^_`{|}~-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(a))throw new MailError('Enter recipient email addresses separated by commas.');return a;});}
export function buildMessage(c,d){
 const to=recipients(d.to),cc=recipients(d.cc),bcc=recipients(d.bcc),all=[...new Set([...to,...cc,...bcc])];if(!all.length||all.length>100)throw new MailError('Add between 1 and 100 recipients.');
 const boundary='tmailer-'+crypto.randomUUID(),id='<'+crypto.randomUUID()+'@'+c.email.split('@')[1]+'>';const headers=['From: '+clean(c.email),'To: '+to.join(', '),...(cc.length?['Cc: '+cc.join(', ')]:[]),'Subject: =?UTF-8?B?'+b64(clean(d.subject||''))+'?=','Date: '+new Date().toUTCString(),'Message-ID: '+id,'MIME-Version: 1.0'];
 if(d.in_reply_to){headers.push('In-Reply-To: '+clean(d.in_reply_to),'References: '+clean(d.in_reply_to));}
 const wrap=s=>s.match(/.{1,76}/g)?.join('\r\n')||'';
 const parts=['Content-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n'+wrap(b64(String(d.body||'')))];let total=0;
 for(const a of d.attachments||[]){if(typeof a.data!=='string'||!/^[A-Za-z0-9+/]*={0,2}$/.test(a.data))throw new MailError('Invalid attachment data.');let raw;try{raw=atob(a.data);}catch{throw new MailError('Invalid attachment data.');}total+=raw.length;if(total>20*1024*1024)throw new MailError('Attachments must total 20 MiB or less.');const filename=encodeURIComponent(clean(a.name||'attachment')).replaceAll("'",'%27');parts.push("Content-Type: application/octet-stream\r\nContent-Disposition: attachment; filename*=UTF-8''"+filename+'\r\nContent-Transfer-Encoding: base64\r\n\r\n'+wrap(a.data));}
 headers.push('Content-Type: multipart/mixed; boundary="'+boundary+'"');const raw=headers.join('\r\n')+'\r\n\r\n'+parts.map(p=>'--'+boundary+'\r\n'+p+'\r\n').join('')+'--'+boundary+'--\r\n';if(enc.encode(raw).length>MAX)throw new MailError('Message exceeds the 30 MiB limit.');return{raw,recipients:all,id};
}
export async function perform(action,config,data,runtime,parse){const c=validateConfig(config);
 if(action==='connect'){await incoming(c,runtime,async()=>{});let warning='';try{await outgoing(c,runtime,async()=>{});}catch(e){warning=e.message;}return{connected:true,email:c.email,protocol:c.protocol,smtp_ready:!warning,warning:warning?'Incoming mail connected. '+warning:''};}
 if(action==='folders'){if(c.protocol==='pop3')return{folders:['INBOX']};return incoming(c,runtime,async i=>({folders:[...new Set(['INBOX',...(await i.command('LIST "" "*"')).map(r=>folderName(r.line)).filter(Boolean)])]}));}
 if(action==='messages')return incoming(c,runtime,async i=>{const offset=Math.max(0,Number(data.offset)||0),limit=Math.min(40,Math.max(1,Number(data.limit)||40));const messages=[];
  if(c.protocol==='imap'){await i.select(data.folder);const found=(await i.command('UID SEARCH ALL')).find(r=>/^\* SEARCH(?: |$)/.test(r.line));const ids=(found?.line.slice(8).trim().split(/\s+/).filter(v=>/^\d+$/.test(v))||[]).reverse();const page=ids.slice(offset,offset+limit);if(page.length){const rows=await i.command('UID FETCH '+page.join(',')+' (UID FLAGS BODY.PEEK[HEADER.FIELDS (FROM TO SUBJECT DATE)])');for(let n=0;n<rows.length;n++){const r=rows[n];if(!r.literal)continue;const meta=r.line+' '+(rows[n+1]?.line||''),uid=meta.match(/\bUID (\d+)/i)?.[1];if(uid)messages.push(summary(await parse(r.literal),uid,/\\Seen/i.test(meta)));}messages.sort((a,b)=>page.indexOf(a.id)-page.indexOf(b.id));}return{messages,total:ids.length};}
  const ids=(await i.uids()).reverse();for(const item of ids.slice(offset,offset+limit)){let raw;try{raw=await i.command('TOP '+item.num+' 0',true);}catch{const size=Number((await i.command('LIST '+item.num)).split(/\s+/)[2]);if(size>MAX)continue;raw=await i.command('RETR '+item.num,true);}messages.push(summary(await parse(raw),item.id));}return{messages,total:ids.length};
 });
 if(action==='message')return incoming(c,runtime,async i=>{let raw;if(c.protocol==='imap'){if(!/^\d+$/.test(String(data.id)))throw new MailError('Invalid message identifier.');await i.select(data.folder);const size=(await i.command('UID FETCH '+data.id+' (RFC822.SIZE)')).map(r=>r.line).join(' ').match(/RFC822.SIZE (\d+)/i);if(size&&Number(size[1])>MAX)throw new MailError('This message exceeds the 30 MiB download limit.');raw=(await i.command('UID FETCH '+data.id+' (BODY.PEEK[])')).find(r=>r.literal)?.literal;}else{const item=(await i.uids()).find(v=>v.id===String(data.id));if(!item)throw new MailError('Message no longer exists. Refresh the inbox.');const size=Number((await i.command('LIST '+item.num)).split(/\s+/)[2]);if(size>MAX)throw new MailError('This message exceeds the 30 MiB download limit.');raw=await i.command('RETR '+item.num,true);}if(!raw)throw new MailError('Message no longer exists. Refresh the inbox.');const m=await parse(raw);return{...summary(m,data.id),body:m.text||stripHtml(m.html||''),reply_to:(m.replyTo||[]).map(address).join(', ')||address(m.from),message_id:m.messageId||'',attachments:(m.attachments||[]).map(a=>({name:a.filename||'attachment',type:a.mimeType||'application/octet-stream',data:bytes64(new Uint8Array(a.content))}))};});
 if(action==='send'){const mail=buildMessage(c,data);return outgoing(c,runtime,async s=>{await s.command('MAIL FROM:<'+clean(c.email)+'>');const refused=[];for(const recipient of mail.recipients){try{await s.command('RCPT TO:<'+recipient+'>',[250,251]);}catch{refused.push(recipient);}}if(refused.length===mail.recipients.length)throw new MailError('SMTP rejected every recipient.');await s.command('DATA',[354]);await s.w.write(mail.raw.replace(/^\./gm,'..')+'.\r\n');if((await s.response()).code!==250)throw new MailError('SMTP did not accept this message.');return{sent:true,refused,message_id:mail.id};});}
 throw new MailError('Not found.',404);
}
function stripHtml(html){return html.replace(/<(script|style)\b[^>]*>[\s\S]*?<\/\1>/gi,'').replace(/<\/?(?:p|div|br|li|tr)\b[^>]*>/gi,'\n').replace(/<[^>]+>/g,'').replace(/&nbsp;/g,' ').replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&amp;/g,'&');}
