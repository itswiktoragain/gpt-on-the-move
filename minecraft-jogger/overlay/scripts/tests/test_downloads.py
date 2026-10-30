#!/usr/bin/env python3
"""Exercise the actual Android downloader against deterministic in-memory HTTP connections.

Only Android/platform plumbing is stubbed; download and atomic file code are the app source.
Requires the JDK and the repository's bundled Commons IO dependency. No network or account.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
sources = {
    'android/util/Log.java': '''package android.util;
public class Log { public static int i(String a,String b){return 0;} public static int i(String a,String b,Throwable t){return 0;} }''',
    'androidx/annotation/Nullable.java': 'package androidx.annotation; public @interface Nullable {}',
    'net/kdt/pojavlaunch/Tools.java': '''package net.kdt.pojavlaunch;
import java.io.*; import java.nio.file.*;
public class Tools {
 public static final String APP_NAME="Jogger downloader test";
 public static final File DIR_CACHE=new File(System.getProperty("java.io.tmpdir"));
 public interface DownloaderFeedback {void updateProgress(int curr,int max);}
 public static String read(InputStream stream)throws IOException{throw new UnsupportedOperationException();}
 public static void write(String path,String value)throws IOException{throw new UnsupportedOperationException();}
 public static boolean compareSHA1(File f,String s){throw new UnsupportedOperationException();}
}''',
    'net/kdt/pojavlaunch/utils/FileUtils.java': '''package net.kdt.pojavlaunch.utils;
import java.io.*;
public class FileUtils {
 public static void ensureParentDirectory(File f)throws IOException {File d=f.getAbsoluteFile().getParentFile();if(!d.isDirectory()&&!d.mkdirs())throw new IOException("mkdir");}
 public static boolean ensureParentDirectorySilently(File f){try{ensureParentDirectory(f);return true;}catch(IOException e){return false;}}
}''',
    'DownloadUtilsTest.java': r'''import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.kdt.pojavlaunch.utils.DownloadUtils;
public class DownloadUtilsTest {
 static int checks;
 static class Connection extends HttpURLConnection {
  Connection(URL u){super(u);}
  public void connect(){} public void disconnect(){} public boolean usingProxy(){return false;}
  public int getResponseCode(){return url.getPath().equals("/error")?503:200;}
  public String getResponseMessage(){return "test";}
  public long getContentLengthLong(){return url.getPath().contains("truncated")?100:(url.getPath().contains("unknown")?-1:5);}
  public InputStream getInputStream(){
   if(url.getPath().equals("/io-failure")) return new InputStream(){int read;public int read()throws IOException{if(read++==0)return 42;throw new IOException("connection dropped");}};
   return new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));
  }
 }
 interface Call {void run()throws IOException;}
 static void eq(String expected,File f)throws IOException {checks++;if(!new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8).equals(expected))throw new AssertionError("Destination changed unexpectedly");}
 static void fails(Call action)throws IOException {checks++;try{action.run();}catch(IOException expected){return;}throw new AssertionError("Failed transfer was reported as success");}
 public static void main(String[] args)throws Exception {
  URL.setURLStreamHandlerFactory(p->p.equals("jogger-test")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Connection(u);}}:null);
  Path dir=Files.createTempDirectory("jogger-download-tests-");File target=dir.resolve("client.jar").toFile();
  DownloadUtils.downloadFile("jogger-test://host/good",target);eq("hello",target);
  Files.write(target.toPath(),"existing good jar".getBytes(StandardCharsets.UTF_8));
  fails(()->DownloadUtils.downloadFile("jogger-test://host/truncated",target));eq("existing good jar",target);
  fails(()->DownloadUtils.downloadFileMonitored("jogger-test://host/truncated",target,null,(n,max)->{}));eq("existing good jar",target);
  fails(()->DownloadUtils.downloadFile("jogger-test://host/error",target));eq("existing good jar",target);
  fails(()->DownloadUtils.downloadFile("jogger-test://host/io-failure",target));eq("existing good jar",target);
  final int[] progress={0};
  DownloadUtils.downloadFileMonitored("jogger-test://host/good",target,new byte[2],(n,max)->progress[0]=n);eq("hello",target);
  checks++;if(progress[0]!=5)throw new AssertionError("Incorrect progress");
  DownloadUtils.downloadFile("jogger-test://host/unknown",target);eq("hello",target);
  checks++;if(dir.toFile().list().length!=1)throw new AssertionError("Partial files were left behind");
  Files.delete(target.toPath());Files.delete(dir);
  System.out.println("PASS: "+checks+" actual downloader success/failure/atomicity assertions");
 }
}''',
}
with tempfile.TemporaryDirectory(prefix='jogger-downloader-jvm-') as temp:
    temp = Path(temp)
    files = []
    for name, content in sources.items():
        file = temp / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content)
        files.append(str(file))
    classes = temp / 'classes'
    classes.mkdir()
    commons = root / 'app_pojavlauncher/libs/ExagearApacheCommons.jar'
    actual = root / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/DownloadUtils.java'
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '8', '-cp', str(commons), '-d', str(classes), *files, str(actual)], check=True)
    subprocess.run(['java', '-cp', str(classes) + ':' + str(commons), 'DownloadUtilsTest'], check=True)
