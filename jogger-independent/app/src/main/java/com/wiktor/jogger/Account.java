package com.wiktor.jogger;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Microsoft public-client OAuth flow. No password form and no borrowed launcher client ID. */
public final class Account {
 private static final String BASE="https://login.microsoftonline.com/consumers/oauth2/v2.0/";
 private static final String SCOPE="XboxLive.signin offline_access";
 public interface Code {void show(String code,String uri,int seconds);}
 private final Context context;private final File file;
 public Account(Context c){context=c;file=new File(c.getNoBackupFilesDir(),"microsoft-account.enc");}
 private SecretKey key()throws Exception{KeyStore k=KeyStore.getInstance("AndroidKeyStore");k.load(null);if(!k.containsAlias("JoggerAccountV1")){KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("JoggerAccountV1",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}return (SecretKey)k.getKey("JoggerAccountV1",null);}
 public synchronized JSONObject load()throws Exception{if(!file.isFile())return null;byte[] a=Files.readAllBytes(file.toPath());if(a.length<29)throw new IOException("Account store is damaged");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(a,0,12)));return new JSONObject(new String(c.doFinal(a,12,a.length-12),StandardCharsets.UTF_8));}
 public synchronized void save(JSONObject j)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());byte[] payload=c.doFinal(j.toString().getBytes(StandardCharsets.UTF_8));File tmp=new File(file.getParentFile(),"microsoft-account.tmp");try(FileOutputStream o=new FileOutputStream(tmp)){o.write(c.getIV());o.write(payload);o.getFD().sync();}Files.move(tmp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}
 public synchronized void remove(){file.delete();}
 private static String form(String...v)throws Exception{StringBuilder b=new StringBuilder();for(int i=0;i<v.length;i+=2){if(i>0)b.append('&');b.append(URLEncoder.encode(v[i],"UTF-8")).append('=').append(URLEncoder.encode(v[i+1],"UTF-8"));}return b.toString();}
 private static JSONObject good(JSONObject j,String stage)throws Exception{if(j.optInt("_http",200)>=400||j.has("error")){String e=j.optString("error",String.valueOf(j.optInt("_http")));throw new IOException(stage+" failed ("+e+"). Check your Microsoft account, app registration and Java Edition ownership.");}return j;}
 public JSONObject login(String client,AtomicBoolean stop,Code show)throws Exception{
  if(client==null||!client.matches("[0-9a-fA-F-]{36}"))throw new IOException("Set your Microsoft public-client application ID in Settings. This independent app does not impersonate another launcher's registered identity. The Minecraft demo can run without signing in.");
  JSONObject device=good(Core.request(BASE+"devicecode",null,form("client_id",client,"scope",SCOPE),null),"Device-code request");
  String uri=device.getString("verification_uri");URI u=new URI(uri);String h=u.getHost();if(!"https".equals(u.getScheme())||h==null||!(h.equals("microsoft.com")||h.endsWith(".microsoft.com")||h.equals("login.microsoftonline.com")||h.equals("login.live.com")))throw new IOException("Unexpected Microsoft sign-in URL");
  int expires=device.getInt("expires_in"),interval=Math.max(1,device.optInt("interval",5));long deadline=System.currentTimeMillis()+expires*1000L;show.show(device.getString("user_code"),uri,expires);
  while(!stop.get()&&System.currentTimeMillis()<deadline){for(int i=0;i<interval*4&&!stop.get();i++)Thread.sleep(250);if(stop.get())break;JSONObject t=Core.request(BASE+"token",null,form("grant_type","urn:ietf:params:oauth:grant-type:device_code","client_id",client,"device_code",device.getString("device_code")),null);String err=t.optString("error");if(err.equals("authorization_pending"))continue;if(err.equals("slow_down")){interval+=5;continue;}if(err.equals("expired_token"))throw new IOException("The sign-in code expired. Request a new one.");good(t,"Microsoft sign-in");JSONObject a=exchange(t.getString("access_token"));a.put("refresh",t.optString("refresh_token")).put("client",client);if(stop.get())throw new InterruptedIOException("Sign-in cancelled");save(a);return a;}
  throw new InterruptedIOException(stop.get()?"Sign-in cancelled":"The code expired");
 }
 public JSONObject current()throws Exception{JSONObject a=load();if(a==null)throw new IOException("Sign in with a Microsoft account first, or use the official demo");if(a.optLong("expires")>System.currentTimeMillis()+60000)return a;JSONObject t=good(Core.request(BASE+"token",null,form("grant_type","refresh_token","client_id",a.getString("client"),"refresh_token",a.getString("refresh"),"scope",SCOPE),null),"Account refresh");JSONObject n=exchange(t.getString("access_token"));n.put("refresh",t.optString("refresh_token",a.getString("refresh"))).put("client",a.getString("client"));save(n);return n;}
 private JSONObject exchange(String access)throws Exception{
  JSONObject x=good(Core.request("https://user.auth.xboxlive.com/user/authenticate",new JSONObject().put("Properties",new JSONObject().put("AuthMethod","RPS").put("SiteName","user.auth.xboxlive.com").put("RpsTicket","d="+access)).put("RelyingParty","http://auth.xboxlive.com").put("TokenType","JWT"),null,null),"Xbox authentication");
  JSONObject xs=good(Core.request("https://xsts.auth.xboxlive.com/xsts/authorize",new JSONObject().put("Properties",new JSONObject().put("SandboxId","RETAIL").put("UserTokens",new JSONArray().put(x.getString("Token")))).put("RelyingParty","rp://api.minecraftservices.com/").put("TokenType","JWT"),null,null),"Xbox authorization");String uhs=xs.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs");
  JSONObject mc=good(Core.request("https://api.minecraftservices.com/authentication/login_with_xbox",new JSONObject().put("identityToken","XBL3.0 x="+uhs+";"+xs.getString("Token")),null,null),"Minecraft authentication");String token=mc.getString("access_token");
  JSONObject ent=good(Core.request("https://api.minecraftservices.com/entitlements/mcstore",null,null,token),"Entitlement check");if(ent.optJSONArray("items")==null||ent.getJSONArray("items").length()==0)throw new IOException("This account does not have an active Minecraft Java entitlement");
  JSONObject profile=good(Core.request("https://api.minecraftservices.com/minecraft/profile",null,null,token),"Minecraft profile");return new JSONObject().put("id",profile.getString("id")).put("name",profile.getString("name")).put("mcToken",token).put("expires",System.currentTimeMillis()+mc.optInt("expires_in",86400)*1000L);
 }
}
