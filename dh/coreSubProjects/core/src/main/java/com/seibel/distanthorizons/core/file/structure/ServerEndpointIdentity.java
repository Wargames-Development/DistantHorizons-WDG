package com.seibel.distanthorizons.core.file.structure;
import java.util.Locale;
/** No DNS: aliases/IPs for one host intentionally require explicit migration, not guessing. */
public final class ServerEndpointIdentity {
    private ServerEndpointIdentity() { }
    public static String canonical(String input) {
        if(input==null)throw new IllegalArgumentException("Missing multiplayer endpoint");
        String s=input.trim().toLowerCase(Locale.ROOT), host=s, port="25565";
        if(s.startsWith("[")) {
            int end=s.indexOf(']'); if(end<2)throw new IllegalArgumentException("Invalid IPv6 endpoint");
            host=s.substring(1,end);
            if(end+1<s.length()) { if(s.charAt(end+1)!=':')throw new IllegalArgumentException("Invalid endpoint"); port=s.substring(end+2); }
        } else if(s.indexOf(':')==s.lastIndexOf(':') && s.indexOf(':')>=0) {
            int colon=s.indexOf(':');host=s.substring(0,colon);port=s.substring(colon+1);
        }
        while(host.endsWith("."))host=host.substring(0,host.length()-1);
        if(host.isEmpty()||host.contains("/")||host.contains("\\")||host.matches(".*\\s.*"))throw new IllegalArgumentException("Invalid host");
        int n=Integer.parseInt(port);if(n<1||n>65535)throw new IllegalArgumentException("Invalid port");
        return (host.indexOf(':')>=0?"["+host+"]":host)+":"+n;
    }
}
