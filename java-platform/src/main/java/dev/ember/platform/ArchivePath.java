package dev.ember.platform;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;

/** Immutable logical archive path. It never maps entries onto host filesystem paths. */
final class ArchivePath implements Path {
    final ArchiveFileSystem fs; private final String value;
    ArchivePath(ArchiveFileSystem fs, String raw) {
        if (raw.indexOf('\0') >= 0 || raw.indexOf('\\') >= 0) throw new InvalidPathException(raw,"Invalid archive path");
        String path = raw.replaceAll("/+", "/");
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0,path.length()-1);
        this.fs = fs; this.value = path;
    }
    private List<String> names() {
        if (value.equals("/")) return Collections.emptyList();
        return Arrays.asList((isAbsolute() ? value.substring(1) : value).split("/",-1));
    }
    private ArchivePath same(Path other) { if (!(other instanceof ArchivePath) || ((ArchivePath)other).fs != fs) throw new ProviderMismatchException(); return (ArchivePath)other; }
    String key() { String absolute = toAbsolutePath().normalize().toString(); return absolute.equals("/") ? "" : absolute.substring(1); }
    @Override public FileSystem getFileSystem() { return fs; }
    @Override public boolean isAbsolute() { return value.startsWith("/"); }
    @Override public Path getRoot() { return isAbsolute() ? new ArchivePath(fs,"/") : null; }
    @Override public Path getFileName() { List<String> names=names(); return names.isEmpty() ? null : new ArchivePath(fs,names.get(names.size()-1)); }
    @Override public Path getParent() {
        int index=value.lastIndexOf('/'); return index < 0 || value.equals("/") ? null : new ArchivePath(fs,index==0 ? "/" : value.substring(0,index));
    }
    @Override public int getNameCount() { return names().size(); }
    @Override public Path getName(int index) { List<String> names=names(); if(index<0||index>=names.size())throw new IllegalArgumentException(); return new ArchivePath(fs,names.get(index)); }
    @Override public Path subpath(int begin,int end) { List<String> names=names(); if(begin<0||end>names.size()||begin>=end)throw new IllegalArgumentException(); return new ArchivePath(fs,String.join("/",names.subList(begin,end))); }
    @Override public boolean startsWith(Path other) {
        if (!(other instanceof ArchivePath) || ((ArchivePath)other).fs != fs || isAbsolute()!=other.isAbsolute()) return false;
        List<String> a=names(),b=((ArchivePath)other).names(); return a.size()>=b.size() && a.subList(0,b.size()).equals(b);
    }
    @Override public boolean startsWith(String other) { return startsWith(fs.getPath(other)); }
    @Override public boolean endsWith(Path other) {
        if (!(other instanceof ArchivePath) || ((ArchivePath)other).fs!=fs) return false;
        if (other.isAbsolute()) return equals(other);
        List<String> a=names(),b=((ArchivePath)other).names(); return a.size()>=b.size() && a.subList(a.size()-b.size(),a.size()).equals(b);
    }
    @Override public boolean endsWith(String other) { return endsWith(fs.getPath(other)); }
    @Override public Path normalize() {
        List<String> output=new ArrayList<>();
        for(String name:names()) {
            if(name.isEmpty()||name.equals("."))continue;
            if(name.equals("..") && !output.isEmpty() && !output.get(output.size()-1).equals(".."))output.remove(output.size()-1);
            else if(!name.equals("..")||!isAbsolute())output.add(name);
        }
        return new ArchivePath(fs,(isAbsolute()?"/":"")+String.join("/",output));
    }
    @Override public Path resolve(Path other) { ArchivePath path=same(other); if(path.isAbsolute())return path; if(value.isEmpty())return path; if(path.value.isEmpty())return this; return new ArchivePath(fs,value+"/"+path.value); }
    @Override public Path resolve(String other) { return resolve(fs.getPath(other)); }
    @Override public Path resolveSibling(Path other) { same(other); Path parent=getParent(); return parent==null?other:parent.resolve(other); }
    @Override public Path resolveSibling(String other) { return resolveSibling(fs.getPath(other)); }
    @Override public Path relativize(Path other) {
        ArchivePath target=same(other); if(isAbsolute()!=target.isAbsolute())throw new IllegalArgumentException("Absolute/relative mismatch");
        List<String> a=((ArchivePath)normalize()).names(),b=((ArchivePath)target.normalize()).names();
        if(a.size()==1&&a.get(0).isEmpty())return target;
        int common=0;while(common<a.size()&&common<b.size()&&a.get(common).equals(b.get(common)))common++;
        List<String> out=new ArrayList<>();for(int i=common;i<a.size();i++)out.add("..");out.addAll(b.subList(common,b.size()));
        return new ArchivePath(fs,String.join("/",out));
    }
    @Override public URI toUri() {
        try { String entry=new URI(null,null,toAbsolutePath().normalize().toString(),null).getRawPath(); return URI.create("jar:"+fs.archive.toUri().toASCIIString()+"!"+entry); }
        catch(URISyntaxException failure){throw new IllegalArgumentException("Invalid archive URI");}
    }
    @Override public Path toAbsolutePath() { return isAbsolute()?this:new ArchivePath(fs,"/"+value); }
    @Override public Path toRealPath(LinkOption... options)throws IOException { fs.provider().checkAccess(this);return toAbsolutePath().normalize(); }
    @Override public File toFile(){throw new UnsupportedOperationException("Archive entries are not host files");}
    @Override public WatchKey register(WatchService watcher,WatchEvent.Kind<?>[] events,WatchEvent.Modifier... modifiers){throw new UnsupportedOperationException();}
    @Override public WatchKey register(WatchService watcher,WatchEvent.Kind<?>... events){throw new UnsupportedOperationException();}
    @Override public Iterator<Path> iterator(){List<Path> result=new ArrayList<>();for(String name:names())result.add(new ArchivePath(fs,name));return Collections.unmodifiableList(result).iterator();}
    @Override public int compareTo(Path other){return value.compareTo(same(other).value);}
    @Override public boolean equals(Object other){return other instanceof ArchivePath&&((ArchivePath)other).fs==fs&&((ArchivePath)other).value.equals(value);}
    @Override public int hashCode(){return 31*System.identityHashCode(fs)+value.hashCode();}
    @Override public String toString(){return value;}
}
