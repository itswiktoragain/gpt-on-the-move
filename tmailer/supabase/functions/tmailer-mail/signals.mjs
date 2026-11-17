// Advisory scoring, not a claim that a message is safe or malicious.
export function spamSignals(headers=[],subject='',folder='',attachments=[]){
 const h=Array.isArray(headers)?headers:Object.entries(headers).map(([key,value])=>({key,value}));
 const header=name=>h.filter(x=>String(x.key).toLowerCase()===name).map(x=>String(x.value)).join(' ');
 let score=0;const reasons=[];const add=(n,s)=>{score+=n;reasons.push(s);};
 if(/spam|junk/i.test(folder))add(8,'Your provider placed this message in a junk folder.');
 if(/^yes\b/i.test(header('x-spam-flag'))||/^yes\b/i.test(header('x-spam-status')))add(8,'A spam header marks this message as spam.');
 const rating=Number.parseFloat(header('x-spam-score'));if(rating>=5)add(5,'The message carries a high spam score ('+rating+').');
 const auth=header('authentication-results');if(/dmarc\s*=\s*fail/i.test(auth))add(3,'An authentication header reports a DMARC failure.');else if(/(?:spf|dkim)\s*=\s*fail/i.test(auth))add(2,'An authentication header reports an SPF or DKIM failure.');
 if(/(?:claim (?:your )?(?:prize|reward)|lottery winner|you have won|guaranteed profit|risk.free investment)/i.test(subject))add(3,'The subject uses common prize or guaranteed-profit wording.');
 if(/(?:urgent|immediate action|account suspended|verify (?:your )?account)/i.test(subject)&&/(?:payment|password|account|verify)/i.test(subject))add(2,'The subject pressures you to act on an account or payment.');
 if(attachments.some(a=>/\.(?:exe|scr|vbs|js|lnk|bat|cmd|msi|ps1)(?:\s|$)/i.test(a.filename||a.name||'')))add(3,'An attachment has a file type that can execute code.');
 return {score,level:score>=5?'likely_spam':score>=2?'suspicious':'none',reasons};
}
export function extractRecipients(value){
 const out=[];for(const part of String(value||'').split(/[,;]/)){const match=part.match(/([A-Z0-9.!#$%&'*+\/=?^_`{|}~-]+@[A-Z0-9.-]+\.[A-Z]{2,})/i);if(!match)continue;const email=match[1].trim();out.push({email:email.toLowerCase(),name:part.slice(0,match.index).replace(/["<>]/g,'').trim().slice(0,120)});}return out;
}
