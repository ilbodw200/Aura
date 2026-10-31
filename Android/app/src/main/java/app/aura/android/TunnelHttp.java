package app.aura.android;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.*;

/** An HTTPS request through this session's authenticated, loopback-only SOCKS inlet. */
final class TunnelHttp {
    static final class Endpoint {final int port;final String user,password;Endpoint(int p,String u,String s){port=p;user=u;password=s;}}
    static String publicIP(Endpoint endpoint)throws Exception{
        if(endpoint==null)throw new IOException("Tunnel is not running");
        String host="api.ipify.org";
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress("127.0.0.1",endpoint.port),5000);socket.setSoTimeout(10000);
            DataOutputStream out=new DataOutputStream(socket.getOutputStream());DataInputStream in=new DataInputStream(socket.getInputStream());
            out.write(new byte[]{5,1,2});out.flush();if(in.readUnsignedByte()!=5||in.readUnsignedByte()!=2)throw new IOException("SOCKS authentication required");
            byte[] user=endpoint.user.getBytes(StandardCharsets.UTF_8),password=endpoint.password.getBytes(StandardCharsets.UTF_8);out.writeByte(1);out.writeByte(user.length);out.write(user);out.writeByte(password.length);out.write(password);out.flush();if(in.readUnsignedByte()!=1||in.readUnsignedByte()!=0)throw new IOException("SOCKS authentication failed");
            byte[] domain=host.getBytes(StandardCharsets.US_ASCII);out.write(new byte[]{5,1,0,3});out.writeByte(domain.length);out.write(domain);out.writeShort(443);out.flush();if(in.readUnsignedByte()!=5||in.readUnsignedByte()!=0)throw new IOException("Proxy connection failed");in.readUnsignedByte();int type=in.readUnsignedByte();int n=type==1?4:type==4?16:type==3?in.readUnsignedByte():-1;if(n<0)throw new IOException("Invalid SOCKS response");in.readFully(new byte[n+2]);
            try(SSLSocket tls=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(socket,host,443,true)){
                SSLParameters parameters=tls.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");tls.setSSLParameters(parameters);tls.setSoTimeout(10000);tls.startHandshake();
                tls.getOutputStream().write(("GET / HTTP/1.1\r\nHost: "+host+"\r\nUser-Agent: AURA/1.0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));tls.getOutputStream().flush();
                String ip=body(tls.getInputStream()).trim();if(!ip.matches("[0-9a-fA-F:.]{3,45}")||(!ip.contains(":")&&!ip.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")))throw new IOException("Invalid IP response");return InetAddress.getByName(ip).getHostAddress();
            }
        }
    }
    private static String line(InputStream input)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();int b;while((b=input.read())!=-1){if(b=='\n')return out.toString("US-ASCII").trim();if(out.size()>=8192)throw new IOException("Header too large");out.write(b);}throw new EOFException();}
    static String body(InputStream input)throws IOException{
        if(!line(input).matches("HTTP/1\\.[01] 200(?: .*)?"))throw new IOException("IP service unavailable");boolean chunked=false;int length=-1;for(int i=0;i<64;i++){String h=line(input);if(h.isEmpty())break;String lower=h.toLowerCase(java.util.Locale.ROOT);if(lower.startsWith("transfer-encoding:"))chunked=lower.contains("chunked");if(lower.startsWith("content-length:"))length=Integer.parseInt(h.substring(h.indexOf(':')+1).trim());if(i==63)throw new IOException("Too many headers");}
        ByteArrayOutputStream out=new ByteArrayOutputStream();if(chunked){for(int i=0;i<64;i++){int size=Integer.parseInt(line(input).split(";",2)[0].trim(),16);if(size==0)break;if(size<0||out.size()+size>1024)throw new IOException("Response too large");for(int j=0;j<size;j++){int b=input.read();if(b<0)throw new EOFException();out.write(b);}if(!line(input).isEmpty())throw new IOException("Invalid chunk");}}else{if(length>1024)throw new IOException("Response too large");for(int i=0;length<0||i<length;i++){int b=input.read();if(b<0){if(length>=0&&i<length)throw new EOFException();break;}if(out.size()>=1024)throw new IOException("Response too large");out.write(b);}}
        return out.toString("UTF-8");
    }
}
