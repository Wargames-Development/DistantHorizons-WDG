package com.seibel.distanthorizons.core.file.structure;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
/** Readable labels do not replace identity validation. Never merge two worlds with the same name. */
public final class ReadableWorldStorage {
    private ReadableWorldStorage() { }
    public static void validateName(String name) {
        if(name==null||name.isEmpty()||name.length()>150||name.equals(".")||name.equals("..")
            ||name.contains("/")||name.contains("\\")||name.indexOf(':')>=0||name.matches(".*[\\x00-\\x1f].*"))
            throw new IllegalArgumentException("Unsafe world folder name");
    }
    /** Same portable component on every OS, including when packs are exported. */
    public static String endpointFolder(String endpoint) {
        String value=ServerEndpointIdentity.canonical(endpoint);
        int portSeparator=value.lastIndexOf(':');
        String host=value.substring(0,portSeparator);
        StringBuilder folder=new StringBuilder();
        for(int i=0;i<host.length();i++) {
            char c=host.charAt(i);
            // Keep normal DNS/IPv4 names readable. Escape IPv6 colons and literal
            // percent signs distinctly; only the host/port separator becomes '.'.
            if(c=='%' || c<32 || c==127 || "\\/:*?\"<>|".indexOf(c)>=0) {
                folder.append('%').append(Character.toUpperCase(Character.forDigit((c>>>4)&15,16)))
                    .append(Character.toUpperCase(Character.forDigit(c&15,16)));
            } else folder.append(c);
        }
        folder.append('.').append(value.substring(portSeparator+1));
        String result=folder.toString();
        // Windows device names remain reserved even with a filename extension.
        String first=result.substring(0,result.indexOf('.')).toUpperCase(java.util.Locale.ROOT);
        if(first.matches("CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³]")) {
            char c=result.charAt(0);
            result="%"+Integer.toHexString(c).toUpperCase(java.util.Locale.ROOT)+result.substring(1);
        }
        return result;
    }
    /** Retained for callers compiled against the previous OS-dependent helper. */
    public static String endpointFolder(String endpoint,boolean windows) { return endpointFolder(endpoint); }
    public static java.util.List<String> legacyEndpointFolders(String endpoint) {
        String value=ServerEndpointIdentity.canonical(endpoint);
        return java.util.Arrays.asList(value,value.replace("%","%25").replace(":","%3A"));
    }
    public static synchronized File resolve(File root,String worldName,String levelKey) {
        validateName(worldName);
        if(levelKey==null||!levelKey.matches(com.seibel.distanthorizons.core.network.messages.base.LevelInitMessage.LEVEL_KEY_REGEX))
            throw new IllegalArgumentException("Invalid world identity");
        Path target=root.toPath().resolve(worldName);
        Path old=root.toPath().resolve(levelKey.replace(":","@@"));
        try {
            Files.createDirectories(root.toPath());
            if(Files.isSymbolicLink(target)||Files.isSymbolicLink(old))throw new IOException("Symlink world folder");
            if(!Files.exists(target)&&Files.isDirectory(old)&&!old.equals(target)) {
                verifyOrWriteIdentity(old,levelKey,true);
                Files.move(old,target);
            }
            if(Files.exists(target))verifyOrWriteIdentity(target,levelKey,false);
            else {Files.createDirectory(target);verifyOrWriteIdentity(target,levelKey,true);}
            return target.toFile();
        } catch(IOException failure) {throw new IllegalStateException("DH world storage could not be resolved without risking another dataset: "+target,failure);}
    }
    private static void verifyOrWriteIdentity(Path dir,String key,boolean allowCreate) throws IOException {
        Path marker=dir.resolve(".dh-level-key");
        if(Files.isSymbolicLink(marker))throw new IOException("Symlink identity marker");
        if(Files.exists(marker)) {
            if(!new String(Files.readAllBytes(marker),StandardCharsets.UTF_8).trim().equals(key))throw new IOException("World name already belongs to another level key; original preserved");
        } else if(allowCreate)Files.write(marker,(key+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);
        else throw new IOException("Existing world folder has no identity record; original preserved");
    }
}
