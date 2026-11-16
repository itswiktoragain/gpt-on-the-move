'use strict';
const CLOUD_ORIGIN=cfg.desktop?(cfg.cloudOrigin||'https://tmailer.tata-i-wikto-6410.chatgpt.site'):'';
const cloudState={user:null,token:'',accounts:[],editingId:null,mode:'login',device:null,pollTimer:null};
async function cloudApi(path,method='GET',data){
 const headers={'Content-Type':'application/json'};
 if(cfg.desktop){headers['X-Tmailer-Desktop']='1';if(cloudState.token)headers['X-Tmailer-Cloud-Token']=cloudState.token;}
 let response;try{response=await fetch(CLOUD_ORIGIN+'/api/cloud/'+path,{method,headers,body:method==='GET'?undefined:JSON.stringify(data||{}),credentials:cfg.desktop?'omit':'same-origin'});}catch{throw Error('Tmailer cloud could not be reached. Check your connection.');}
 let result;try{result=await response.json();}catch{throw Error('Tmailer cloud returned an invalid response.');}
 if(!response.ok)throw Error(result.error||'Cloud request failed.');return result;
}
function cloudIdentity(){
 connectionUI();
 $('#cloudLabel').textContent=cloudState.user?cloudState.user.displayName:'Sign in';
 $('#cloudBtn').title=cloudState.user?'Your Tmailer account':'Sign in to Tmailer';
 $('#cloudAccountsSection').hidden=!cloudState.user;
 $('#accountRememberText').textContent=cloudState.user?'Save account details and password to my Tmailer account':'Remember server settings on this device. Passwords are never saved.';
 $('#cloudProfileEmail').textContent=cloudState.user?.email||'';$('#linkChatGPT').hidden=!!cfg.desktop||!!cloudState.user?.chatgptLinked;
 if(cloudState.user){$('#profileName').value=cloudState.user.displayName;$('#syncReadingPane').checked=!!cloudState.user.settings.readingPaneHidden;$('.mailbox').classList.toggle('readingHidden',!!cloudState.user.settings.readingPaneHidden);}
 if(cloudState.user&&!state.ready)labelIcon($('#connectBtn'),'Save mail account','cloud');
 renderSavedAccounts();
}
function editMailAccount(account){
 const f=$('#accountForm');f.reset();cloudState.editingId=account?.id||null;
 for(const key of ['email','username','protocol','incoming','incoming_security','smtp','smtp_security','smtp_username'])if(account&&f.elements[key])f.elements[key].value=account[key]||'';
 f.elements.password.required=!account;f.elements.password.placeholder=account?'Saved securely online':'Mail password or app password';f.elements.remember.checked=true;openAccount();
}
async function saveCloudMail(config){
 const r=await cloudApi('vault','POST',{id:cloudState.editingId||undefined,config});cloudState.editingId=r.id;cloudState.accounts=r.accounts;renderSavedAccounts();return r.id;
}
async function loadSavedMail(){const r=await cloudApi('vault');cloudState.accounts=r.accounts;renderSavedAccounts();}
function savedRow(account,full=false){
 const row=el('div',undefined,full?'savedAccountRow':'savedSidebarRow');const use=el('button',undefined,full?'savedAccountUse':'savedSidebarUse');use.append(tmIcon('mail'),el('span',account.email));use.title='Use '+account.email;use.onclick=()=>useSavedMail(account.id);row.append(use);
 if(full){const edit=labelIcon(el('button',undefined,'iconButton'),'Edit','settings',false);edit.onclick=()=>{$('#cloudProfileDialog').close();editMailAccount(account);};const remove=labelIcon(el('button',undefined,'iconButton'),'Remove','trash',false);remove.onclick=async()=>{if(!confirm('Remove '+account.email+' and its saved login from your Tmailer account?'))return;try{await cloudApi('vault/'+account.id,'DELETE',{});await loadSavedMail();notice('Saved mail account removed.');}catch(e){notice(e.message,true);}};row.append(edit,remove);}return row;
}
function renderSavedAccounts(){
 const sidebar=$('#cloudAccountRows'),profile=$('#savedCloudAccounts');sidebar.replaceChildren();profile.replaceChildren();
 for(const account of cloudState.accounts){sidebar.append(savedRow(account));profile.append(savedRow(account,true));}
 if(!cloudState.accounts.length)profile.append(el('p','No saved mail accounts yet. Add a mailbox to save its settings and password online.','muted'));
}
async function useSavedMail(id){
 if(!state.ready){notice('Your mail account is saved online. Mail connections are awaiting activation.',true);return;}
 const account=cloudState.accounts.find(a=>a.id===id);if(!account)return;
 let config;if(cfg.desktop){config=(await cloudApi('vault/'+id+'/use','POST',{})).config;}else config={saved_account_id:id};
 try{const r=await api('connect',config);state.connected=true;state.email=r.email;state.folder='INBOX';state.offset=0;$('#folderTitle').textContent='Inbox';cloudState.editingId=id;connectionUI();emptyReader();if($('#cloudProfileDialog').open)$('#cloudProfileDialog').close();await loadFolders();await refresh();await saveCloudPreferences({lastMailboxId:id});notice('Connected using your saved mail account.');}catch(e){notice(e.message,true);}
}
async function saveCloudPreferences(patch){if(!cloudState.user)return;const settings={...cloudState.user.settings,...patch};const r=await cloudApi('profile','PUT',{displayName:cloudState.user.displayName,settings});cloudState.user=r.user;}
function authMode(mode){cloudState.mode=mode;$('#cloudAuthTitle').textContent=mode==='register'?'Create your Tmailer account':'Sign in to Tmailer';$('#cloudDisplayNameLabel').hidden=mode!=='register';$('#cloudPassword').autocomplete=mode==='register'?'new-password':'current-password';$('#cloudAuthSubmit').textContent=mode==='register'?'Create account':'Sign in';$('#cloudModeToggle').textContent=mode==='register'?'Already have an account? Sign in':'New to Tmailer? Create an account';}
function openCloud(){if(cloudState.user){cloudIdentity();open('cloudProfileDialog');}else{authMode('login');open('cloudAuthDialog');}}
$('#cloudBtn').onclick=openCloud;$('#accountBtn').onclick=()=>editMailAccount(state.connected?cloudState.accounts.find(a=>a.id===cloudState.editingId):null);$('#welcomeConnect').onclick=()=>editMailAccount();$('#listConnect').onclick=()=>editMailAccount();$('#cloudModeToggle').onclick=()=>authMode(cloudState.mode==='login'?'register':'login');$('#newCloudMailbox').onclick=()=>editMailAccount();
$('#cloudAuthForm').onsubmit=async e=>{e.preventDefault();const button=$('#cloudAuthSubmit');button.disabled=true;try{const r=await cloudApi(cloudState.mode,'POST',{email:$('#cloudEmail').value,password:$('#cloudPassword').value,displayName:$('#cloudDisplayName').value});cloudState.user=r.user;if(r.token)cloudState.token=r.token;$('#cloudPassword').value='';$('#cloudAuthDialog').close();cloudIdentity();await loadSavedMail();notice(cloudState.mode==='register'?'Tmailer account created. Your mail accounts can now be saved online.':'Signed in. Your saved mail accounts and settings are synced.');maybeApproveDesktop();}catch(err){notice(err.message,true);}finally{button.disabled=false;}};
$('#cloudProfileForm').onsubmit=async e=>{e.preventDefault();try{const r=await cloudApi('profile','PUT',{displayName:$('#profileName').value,settings:{...cloudState.user.settings,readingPaneHidden:$('#syncReadingPane').checked}});cloudState.user=r.user;cloudIdentity();notice('Account settings saved online.');}catch(err){notice(err.message,true);}};
$('#cloudLogout').onclick=async()=>{try{if(state.connected){try{await api('disconnect');}catch{}}await cloudApi('logout','POST',{});state.connected=false;state.email='';state.token='';clearBlobs();resetMailbox();connectionUI();$('#accountForm').reset();cloudState.user=null;cloudState.token='';cloudState.accounts=[];cloudState.editingId=null;$('#cloudProfileDialog').close();cloudIdentity();notice('Signed out of Tmailer.');}catch(err){notice(err.message,true);}};
const layoutAction=$('#layoutBtn').onclick;$('#layoutBtn').onclick=()=>{layoutAction();if(cloudState.user)saveCloudPreferences({readingPaneHidden:$('.mailbox').classList.contains('readingHidden')}).catch(err=>notice(err.message,true));};
const chatgptReturn='/?tmailer_signin=chatgpt';
$('#cloudChatGPT').href='/signin-with-chatgpt?return_to='+encodeURIComponent(chatgptReturn);$('#cloudChatGPT').target='_top';
$('#linkChatGPT').href='/signin-with-chatgpt?return_to='+encodeURIComponent(chatgptReturn);$('#linkChatGPT').target='_top';
async function beginDesktopChatGPT(e){e.preventDefault();try{const r=await cloudApi('device/start','POST',{});cloudState.device=r;$('#desktopAuthStatus').hidden=false;$('#desktopAuthStatus').textContent='Finish signing in and approve Tmailer desktop in the browser.';const a=el('a');a.href=r.authorizeUrl;a.target='_blank';a.rel='noopener';document.body.append(a);a.click();a.remove();clearInterval(cloudState.pollTimer);const end=Date.now()+300000;cloudState.pollTimer=setInterval(async()=>{if(Date.now()>end){clearInterval(cloudState.pollTimer);$('#desktopAuthStatus').textContent='Desktop sign-in expired. Try again.';return;}try{const result=await cloudApi('device/poll','POST',{id:r.id,secret:r.secret});if(result.token){clearInterval(cloudState.pollTimer);cloudState.token=result.token;const me=await cloudApi('me');cloudState.user=me.user;$('#cloudAuthDialog').close();cloudIdentity();await loadSavedMail();notice('Tmailer desktop is signed in and synced.');}}catch(err){clearInterval(cloudState.pollTimer);$('#desktopAuthStatus').textContent=err.message;}},2500);}catch(err){notice(err.message,true);}}
if(cfg.desktop){$('#cloudChatGPT').onclick=beginDesktopChatGPT;$('#linkChatGPT').hidden=true;}
function maybeApproveDesktop(){const id=new URL(location.href).searchParams.get('tmailer_device');if(id&&cloudState.user){$('#approveDesktop').dataset.id=id;open('desktopApprovalDialog');}}
$('#approveDesktop').onclick=async()=>{try{await cloudApi('device/approve','POST',{id:$('#approveDesktop').dataset.id});$('#desktopApprovalDialog').close();notice('Tmailer desktop approved. You can return to the app.');history.replaceState(null,'','/');}catch(err){notice(err.message,true);}};
window.tmailerCloud={get user(){return cloudState.user;},get editingId(){return cloudState.editingId;},openMailAccount:()=>editMailAccount(state.connected?cloudState.accounts.find(a=>a.id===cloudState.editingId):null),saveMail:saveCloudMail,getNativeConfig:async id=>(await cloudApi('vault/'+id+'/use','POST',{})).config};
(async()=>{try{const params=new URL(location.href).searchParams;const device=params.get('tmailer_device');if(params.get('tmailer_signin')==='chatgpt'){const r=await cloudApi('chatgpt','POST',{});cloudState.user=r.user;history.replaceState(null,'',device?'/?tmailer_device='+encodeURIComponent(device):'/');}else cloudState.user=(await cloudApi('me')).user;cloudIdentity();if(cloudState.user)await loadSavedMail();if(device){if(!cloudState.user)openCloud();else maybeApproveDesktop();const returnTo='/?tmailer_signin=chatgpt&tmailer_device='+encodeURIComponent(device);$('#cloudChatGPT').href='/signin-with-chatgpt?return_to='+encodeURIComponent(returnTo);}}catch(err){cloudIdentity();notice(err.message,true);}})();
