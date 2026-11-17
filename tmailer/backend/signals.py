"""Advisory spam signals and per-mailbox recipient history."""
import re

def spam_signals(msg,folder='',attachments=()):
    score=0; reasons=[]
    def add(n,text):
        nonlocal score
        score+=n;reasons.append(text)
    if re.search(r'spam|junk',folder,re.I):add(8,'Your provider placed this message in a junk folder.')
    if re.match(r'yes\b',str(msg.get('X-Spam-Flag','')),re.I) or re.match(r'yes\b',str(msg.get('X-Spam-Status','')),re.I):add(8,'A spam header marks this message as spam.')
    try:
        rating=float(msg.get('X-Spam-Score','0'))
        if rating>=5:add(5,'The message carries a high spam score ('+str(rating)+').')
    except ValueError:pass
    auth=str(msg.get('Authentication-Results',''))
    if re.search(r'dmarc\s*=\s*fail',auth,re.I):add(3,'An authentication header reports a DMARC failure.')
    elif re.search(r'(?:spf|dkim)\s*=\s*fail',auth,re.I):add(2,'An authentication header reports an SPF or DKIM failure.')
    subject=str(msg.get('Subject',''))
    if re.search(r'claim (?:your )?(?:prize|reward)|lottery winner|you have won|guaranteed profit|risk.free investment',subject,re.I):add(3,'The subject uses common prize or guaranteed-profit wording.')
    if re.search(r'urgent|immediate action|account suspended|verify (?:your )?account',subject,re.I) and re.search(r'payment|password|account|verify',subject,re.I):add(2,'The subject pressures you to act on an account or payment.')
    if any(re.search(r'\.(?:exe|scr|vbs|js|lnk|bat|cmd|msi|ps1)(?:\s|$)',a.get('name',''),re.I) for a in attachments):add(3,'An attachment has a file type that can execute code.')
    return {'score':score,'level':'likely_spam' if score>=5 else 'suspicious' if score>=2 else 'none','reasons':reasons}
