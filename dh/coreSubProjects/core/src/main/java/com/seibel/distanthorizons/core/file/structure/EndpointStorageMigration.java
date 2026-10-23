package com.seibel.distanthorizons.core.file.structure;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/** Never combines datasets. A no-replace move preserves originals on failure. */
public final class EndpointStorageMigration {
    private EndpointStorageMigration() { }
    public static Path migrateUnique(Path target,Collection<File> proposed) throws IOException {
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))return null;
        Set<Path> candidates=new LinkedHashSet<>();
        Path parent=target.toAbsolutePath().normalize().getParent();
        for(File f:proposed) {
            Path p=f.toPath().toAbsolutePath().normalize();
            if(p.getParent().equals(parent)&&!p.equals(target.toAbsolutePath().normalize())
                &&Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(p))candidates.add(p);
        }
        if(candidates.size()!=1)return null;
        Path source=candidates.iterator().next();
        Files.move(source,target);return source;
    }
}
