package com.minecraftjogger.touch;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class JoggerTouchAgent {
    private JoggerTouchAgent() {}
    public static void premain(String arguments,Instrumentation instrumentation) {
        if(arguments==null)return;final String[] parts=arguments.split(":",2);
        if(parts.length!=2||!parts[1].matches("[0-9a-f]{64}"))return;
        final int port;try{port=Integer.parseInt(parts[0]);if(port<1024||port>65535)return;}catch(NumberFormatException ex){return;}
        System.setProperty("minecraftjogger.touch.agent","1.1.0");
        ClassicGameHooks.install(instrumentation);
        Thread worker=new Thread(()->connect(port,parts[1]),"Jogger touch input agent");worker.setDaemon(true);worker.start();
    }
    private static void connect(int port,String token) {
        for(int retry=0;retry<8;retry++) {
            try(Socket socket=new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),1500);
                socket.setTcpNoDelay(true);socket.setSoTimeout(1800);
                BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8));
                writer.write("JOGGER1 "+token+"\n");writer.flush();if(!"READY".equals(readBounded(reader)))return;
                System.out.println("[Minecraft Jogger] Classic touch agent connected.");InputState state=new InputState();
                try(ClassicGameHooks.Session session=new ClassicGameHooks.Session(writer)) {
                    while(!Thread.currentThread().isInterrupted()) {
                        try {String line=readBounded(reader);if(line==null)return;if(!ClassicGameHooks.line(line))write(writer,state.accept(line));}
                        catch(SocketTimeoutException lostHeartbeat){TouchRay.clear();write(writer,state.release());}
                    }
                }return;
            }catch(IOException unavailable){try{Thread.sleep(700);}catch(InterruptedException end){Thread.currentThread().interrupt();return;}}
        }
        System.out.println("[Minecraft Jogger] Native touch fallback active.");
    }
    private static void write(BufferedWriter writer,List<String> lines)throws IOException {
        synchronized(writer){for(String line:lines){writer.write(line);writer.newLine();}if(!lines.isEmpty())writer.flush();}
    }
    private static String readBounded(BufferedReader reader)throws IOException {
        StringBuilder s=new StringBuilder();int c;
        while((c=reader.read())!=-1){if(c=='\n')return s.toString();if(c=='\r')continue;if(s.length()>=160)throw new IOException("Touch message too long");s.append((char)c);}
        return s.length()==0?null:s.toString();
    }
}
