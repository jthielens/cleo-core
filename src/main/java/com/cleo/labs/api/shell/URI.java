package com.cleo.labs.api.shell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cleo.labs.util.F;
import com.cleo.labs.util.S;
import com.cleo.labs.util.X;

public class URI {
    public String             scheme       = null;  // Cleo-URI-Scheme
    public String             file         = null;  // Cleo-URI-File-Class
    public String             inputStream  = null;  // Cleo-URI-InputStream-Class
    public String             outputStream = null;  // Cleo-URI-OutputStream-Class
    public String[]           classPath    = null;  // Cleo-URI-Depends
    public String[]           addPath      = null;  // Cleo-URI-Additional

    public String             listener     = null;  // Cleo-API-LexiComLogListener
    public String             incoming     = null;  // Cleo-API-ILexiComIncoming
    public String             outgoing     = null;  // Cleo-API-LexiComOutgoingThread

    public String             self         = null;  // if read from manifest
    public Map<String,String> properties   = new HashMap<String,String>();
    public File               home         = null;
    public Reporter           reporter     = null;

    public interface Reporter {
        void report(String text);
    }

    private void report(String text) {
        if (reporter!=null) {
            reporter.report(text);
        }
    }

    private static final Pattern URI_PROPERTY = Pattern.compile("cleo\\.uri\\.(\\w+)\\.file");
    private static final String  COLON        = String.valueOf(File.pathSeparatorChar);

    public void uninstall() {
        if (scheme!=null) {
            try {
                store(removeProperties(load()));
            } catch (IOException ignore) {}
        }
        // TODO: cleanup JARs
    }

    public Properties removeProperties(Properties props) {
        if (scheme!=null && props!=null) {
            String prefix = "cleo.uri."+scheme+".";
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith(prefix)) {
                    props.remove(key);
                }
            }
        }
        return props;
    }

    public Properties setProperties(Properties props) {
        if (scheme!=null) {
            removeProperties(props);
            props.setProperty("cleo.uri."+scheme+".file",         file);
            props.setProperty("cleo.uri."+scheme+".inputstream",  inputStream);
            props.setProperty("cleo.uri."+scheme+".outputstream", outputStream);
            if (classPath!=null && classPath.length>0) {
                props.setProperty("cleo.uri."+scheme+".classpath", S.join(COLON, classPath));
            }
            for (Map.Entry<String,String> e : properties.entrySet()) {
                props.setProperty("cleo.uri."+scheme+"."+e.getKey(), e.getValue());
            }
        }
        if (addPath!=null && addPath.length>0) {
            Set<String> classSet = new HashSet<String>();
            String existing = props.getProperty("cleo.additional.classpath");
            if (existing!=null && !existing.isEmpty()) {
                classSet.addAll(Arrays.asList(existing.split(COLON)));
            }
            classSet.addAll(Arrays.asList(addPath));
            props.setProperty("cleo.additional.classpath", S.join(COLON, classSet));
        }
        return props;
    }

    public static URI get(File home, Reporter reporter, String id) {
        URI uri = inspectJar(home, reporter, id);
        if (uri==null) {
            uri = new URI(home, reporter);
            Properties props;
            try {
                props = uri.load();
            } catch (IOException ignore) {
                return null;
            }
            String prefix = "cleo.uri."+id+".";
            if (props.containsKey(prefix+"file")) {
                uri.scheme = id;
                for (String key : props.stringPropertyNames()) {
                    if (key.startsWith(prefix)) {
                        String prop = key.substring(prefix.length());
                        if (prop.equals("file")) {
                            uri.file = props.getProperty(key);
                        } else if (prop.equals("inputstream")) {
                            uri.inputStream = props.getProperty(key);
                        } else if (prop.equals("outputstream")) {
                            uri.outputStream = props.getProperty(key);
                        } else if (prop.equals("classpath")) {
                            uri.classPath = props.getProperty(key).split(COLON);
                        } else {
                            if (uri.properties==null) {
                                uri.properties = new HashMap<>();
                            }
                            uri.properties.put(prop, props.getProperty(key));
                        }
                    }
                }
            } else {
                uri= null;
            }
        }
        return uri;
    }

    public static String[] getSchemeIds(File home) {
        List<String> ids = new ArrayList<String>();
        try {
            for (String key : load(home).stringPropertyNames()) {
                Matcher m = URI_PROPERTY.matcher(key);
                if (m.matches()) {
                    ids.add(m.group(1));
                }
            }
        } catch (IOException ignore) { }
        return ids.toArray(new String[ids.size()]);
    }

    public static URI[] getSchemes(File home, Reporter reporter) {
        String[] ids  = getSchemeIds(home);
        URI[]    uris = new URI[ids.length];
        for (int i=0; i<ids.length; i++) {
            uris[i] = get(home, reporter, ids[i]);
        }
        return uris;
    }

    public Properties load() throws IOException {
        return load(home);
    }
    public static Properties load(File home) throws IOException {
        Properties props = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(new File(home, "conf/system.properties"));
            props.load(in);
        } catch (FileNotFoundException e) {
            // that's ok -- otherwise let it bubble up
        } finally {
            if (in!=null) in.close();
        }
        return props;
    }

    public Properties store(Properties props) throws IOException {
        FileOutputStream out = new FileOutputStream(new File(home, "conf/system.properties"));
        props.store(out, "Properties updated by "+URI.class.getCanonicalName());
        out.close();
        return props;
    }

    /**
     * Returns the filename of {@code jar} relative to the {@code home} path.
     * @param jar the JAR
     * @return the relative filename
     */
    private String relativize (File jar) {
        if (home==null) {
            try {
                return jar.getCanonicalPath();
            } catch (Exception e) {
                return jar.getAbsolutePath();
            }
        } else {
            return home.toURI().relativize(jar.toURI()).getPath();
        }
    }

    /**
     * Returns a file relative to the {@code home} path, unless the
     * filename is absolute, in which case this is the same as
     * {@code new File(name)}.
     * @param name
     * @return a resolved relative or an absolute {@code File}
     */
    private File homeFile(String name) {
        File f = new File(name);
        if (home!=null && !f.isAbsolute()) {
            return new File(home, name);
        }
        return f;
    }

    public static URI inspectJar(File home, Reporter reporter, String name) {
        URI uri = new URI(home, reporter);

        File          jar        = new File(name);
        Set<String>   classpath  = new HashSet<String>();   // the ones to add to classpath
        Set<String>   additional = new HashSet<String>();   // the ones to add to additional.classpath
        boolean       manifested = false;                   // did we see a valid Cleo-... manifest

        // allow mvn2 references as file
        if (uri.mvn2(name)!=null) {
            try {
                name = uri.copyToLib(name);
            } catch (Exception e) {
                reporter.report("error downloading "+name+": "+e);
            }
        }
        // now read the jar manifest to parse out manifest attributes to find:
        // - scheme                Cleo-URI-Scheme
        // - file class            Cleo-URI-File-Class
        // - input class           Cleo-URI-InputStream-Class
        // - output class          Cleo-URI-OutputStream-Class
        // - classpath             Cleo-URI-Depends*
        // - additional.classpath  Cleo-URI-Additional*
        // - listener              Cleo-API-LexiComLogListener
        // - incoming              Cleo-API-ILexiComIncoming
        // - outgoing              Cleo-API-LexiComOutgoingThread
        // Note that first setting wins (for scheme and file classes that aren't lists).
        // Depends and Additional can appear multiple times (with unique suffixes, which are
        //      discarded), allowing for lists that would otherwise exceed the length limit
        // Note also that the JARs themselves are added to classpath unless "this" appears in
        //      Additional, in which case the current JAR is added to additional instead
        //
        // Minimal JARs may only have Cleo-URI-Additional (for example a VFS Provider).
        // Full-on URIs will need Cleo-URI-Scheme and the -Class settings at least
        try (JarFile jarfile = new JarFile(home==null || jar.isAbsolute() ? jar : new File(home, name))) {
            Manifest manifest = jarfile.getManifest();
            if (manifest!=null) {
                Attributes attrs = manifest.getMainAttributes();
                if (attrs!=null) {
                    if (uri.scheme==null && attrs.containsKey(new Attributes.Name("Cleo-URI-Scheme"))) {
                        uri.scheme = attrs.getValue("Cleo-URI-Scheme").toLowerCase();
                        manifested = true;
                    }
                    if (uri.file==null && attrs.containsKey(new Attributes.Name("Cleo-URI-File-Class"))) {
                        uri.file = attrs.getValue("Cleo-URI-File-Class");
                        manifested = true;
                    }
                    if (uri.inputStream==null && attrs.containsKey(new Attributes.Name("Cleo-URI-InputStream-Class"))) {
                        uri.inputStream = attrs.getValue("Cleo-URI-InputStream-Class");
                        manifested = true;
                    }
                    if (uri.outputStream==null && attrs.containsKey(new Attributes.Name("Cleo-URI-OutputStream-Class"))) {
                        uri.outputStream = attrs.getValue("Cleo-URI-OutputStream-Class");
                        manifested = true;
                    }
                    if (uri.listener==null && attrs.containsKey(new Attributes.Name("Cleo-API-LexiComLogListener"))) {
                        uri.listener = attrs.getValue("Cleo-API-LexiComLogListener");
                        manifested = true;
                    }
                    if (uri.incoming==null && attrs.containsKey(new Attributes.Name("Cleo-API-ILexiComIncoming"))) {
                        uri.incoming = attrs.getValue("Cleo-API-ILexiComIncoming");
                        manifested = true;
                    }
                    if (uri.outgoing==null && attrs.containsKey(new Attributes.Name("Cleo-API-LexiComOutgoingThread"))) {
                        uri.outgoing = attrs.getValue("Cleo-API-LexiComOutgoingThread");
                        manifested = true;
                    }
                    for (Object o : attrs.keySet()) {
                        if (o.toString().startsWith("Cleo-URI-Additional")) {
                            additional.addAll(Arrays.asList(attrs.getValue(o.toString()).split("\\s+")));
                            manifested = true;
                        } else if (o.toString().startsWith("Cleo-URI-Depends")) {
                            classpath.addAll(Arrays.asList(attrs.getValue(o.toString()).split("\\s+")));
                            manifested = true;
                        }
                    }
                }
            }
        } catch (IOException abort) {
            return null;
        }
        if (manifested) {
            uri.self = name;
            if (additional.contains("this")) {
                additional.remove("this");
                additional.add(name);
            } else {
                classpath.add(name);
            }
            if (!classpath.isEmpty()) {
                uri.classPath = classpath.toArray(new String[classpath.size()]);
            }
            if (!additional.isEmpty()) {
                uri.addPath = additional.toArray(new String[additional.size()]);
            }
            return uri;
        } else {
            return null;
        }
    }

    private URI (File home, Reporter reporter) {
        // use defaults
        this.home = home;
        this.reporter = reporter;
    }

    public static URI inspectJars(File home, Reporter reporter, final String...jars) throws Exception {
        URI uri = new URI(home, reporter);

        List<String> schemeFiles  = new ArrayList<String>(); // JARs on the "command line"
        List<String> classpath    = new ArrayList<String>(); // the ones to add to classpath
        Set<String>  additional   = new HashSet<String>();   // the ones to add to additional.classpath

        // First: look through the "command line" and strip out
        // - properties that look like a=b
        // - the scheme id that looks like scheme:
        // And get upset if anything remaining isn't a readable file
        for (String file : jars) {
            // check if it's a property, not a file
            if (file.contains("=")) {
                String[] kv = file.split("=", 2);
                if (kv[0].equals("file")) {
                    uri.file = kv[1];
                } else if (kv[0].equals("inputstream")) {
                    uri.inputStream = kv[1];
                } else if (kv[0].equals("outputstream")) {
                    uri.outputStream = kv[1];
                } else {
                    uri.properties.put(kv[0], kv[1]);
                }
                continue;
            } else if (file.endsWith(":")) {
                uri.scheme = file.substring(0, file.length()-1);
                continue;
            }
            // ok, it's a file
            if (uri.mvn2(file)!=null) {
                file = uri.copyToLib(file);
            }
            File jarfile = uri.homeFile(file);
            if (jarfile.canRead()) {
                schemeFiles.add(uri.relativize(jarfile));
            } else {
                throw new Exception("can not read file: "+jarfile);
            }
        }
        // Second: run through the actual files in two passes
        if (!schemeFiles.isEmpty()) {
            // read through the JAR files trying on each class to find ones
            // that match the necessary types.
            if (uri.file==null || uri.inputStream==null || uri.outputStream==null) {
                final ClassLoader    classLoader = Util.class.getClassLoader();
                final java.net.URL[] urls = new java.net.URL[schemeFiles.size()];
                for (int i=0; i<schemeFiles.size(); i++) {
                    urls[i] = new java.net.URL("jar:file:"+uri.homeFile(schemeFiles.get(i)).getCanonicalPath()+"!/");
                }
                final URLClassLoader ucl = new URLClassLoader(urls, classLoader);
                // look for compatible classes
                for (String f : schemeFiles) {
                    final JarFile jarfile = new JarFile(uri.homeFile(f));
                    for (Enumeration<JarEntry> entries = jarfile.entries(); entries.hasMoreElements();) {
                        JarEntry entry = entries.nextElement();
                        if (entry.getName().endsWith(".class")) {
                            String classname = entry.getName().replaceAll("/", "\\.");
                            classname = classname.substring(0, classname.length()-".class".length());
                            if (!classname.contains("$")) {
                                try {
                                    final Class<?> test = Class.forName(classname, true, ucl);
                                    if (uri.file==null && com.cleo.lexicom.beans.LexURIFile.class.isAssignableFrom(test)) {
                                        uri.file = classname;
                                        if (uri.scheme==null) {
                                            uri.scheme = classname.toLowerCase();
                                            if (uri.scheme.endsWith("file")) {
                                                uri.scheme = uri.scheme.substring(uri.scheme.lastIndexOf(".")+1, uri.scheme.length()-"file".length());
                                            }
                                        }
                                    }
                                    if (uri.inputStream==null) {
                                        try {
                                            test.getConstructor(com.cleo.lexicom.beans.LexURIFile.class);
                                            if (java.io.InputStream.class.isAssignableFrom(test)) {
                                                uri.inputStream = classname;
                                            }
                                        } catch (Exception ignore) {}
                                    }
                                    if (uri.outputStream==null) {
                                        try {
                                            test.getConstructor(com.cleo.lexicom.beans.LexURIFile.class, boolean.class);
                                            if (java.io.OutputStream.class.isAssignableFrom(test)) {
                                                uri.outputStream = classname;
                                            }
                                        } catch (Exception ignore) {}
                                    }
                                } catch (final ClassNotFoundException e) {
                                    // so what
                                } catch (final NoClassDefFoundError e) {
                                    // so what
                                } 
                            }
                        }
                    }
                    jarfile.close();
                }
            }
        }
        // Finally: check to make sure we got the classes we need
        // and set the classPath and addPath lists
        if (uri.file==null || uri.inputStream==null || uri.outputStream==null) {
            throw new Exception("classes missing from classpath: invalid URI");
        }
        if (!classpath.isEmpty()) {
            uri.classPath = classpath.toArray(new String[classpath.size()]);
        }
        if (!additional.isEmpty()) {
            uri.addPath = additional.toArray(new String[additional.size()]);
        }
        return uri;
    }

    private static class Maven2 {
        public String url;
        public String jar;
        public Maven2(String url, String jar) {
            this.url = url;
            this.jar = jar;
        }
    }
    private Maven2 mvn2(String path) {
        String[] gav = path.split("%");
        if (gav.length==3) {
            String repo = "http://central.maven.org/maven2/";
            String dir  = gav[0].replace('.','/')+"/"+ // group, changing . to /
                          gav[1]+"/"+                  // artifact
                          gav[2]+"/";                  // version
            String jar  = gav[1]+"-"+gav[2]+".jar";
            String as   = jar;                         // local JAR name, initially same as remote
            if (gav[0].startsWith("com.cleo")) {
                String contd = "10.80.80.156";  // contd.cleo.com behind the VPN
                if (gav[2].contains("SNAPSHOT")) {
                    repo = "http://"+contd+"/nexus/content/repositories/snapshots/";
                    try {
                        Map<String,Object> meta = X.xml2map(X.string2xml(new String(F.download(repo+dir+"maven-metadata.xml"))).getDocumentElement());
                        String value = (String)X.subobj(meta, "versioning", "snapshotVersions", "snapshotVersion[0]", "value");
                        jar = gav[1]+"-"+value+".jar";
                    } catch (Exception ignore) {
                        report(ignore.toString());
                    }
                } else {
                    // contd.cleo.com
                    repo = "http://"+contd+"/nexus/content/repositories/releases/";
                }
            }
            return new Maven2(repo+dir+jar, as);
        }
        return null;
    }

    private void copyToLib(String[] list) throws Exception {
        if (list==null) return;
        for (int i=0; i<list.length; i++) {
            list[i] = copyToLib(list[i]);
        }
    }
    private String copyToLib(String path) throws Exception {
        F.Clobbered result;
        Maven2 mvn2 = mvn2(path);
        File lib = new File(home, "lib/uri");
        if (mvn2!=null) {
            byte[] sha1 = F.hex(S.s(F.download(mvn2.url+".sha1")));
            result = F.download(mvn2.url, new File(lib,mvn2.jar), "SHA-1", sha1, F.ClobberMode.OVERWRITE);
            report(result.matched ? path+" matched to existing "+result.file
                                  : path+" downloaded to "+result.file);
        } else {
            File src = homeFile(path);
            File dst = new File(lib, src.getName());
            if (src.equals(dst)) {
                result = new F.Clobbered(src, true);
            } else {
                result = F.copy(src, dst, F.ClobberMode.OVERWRITE);
                report(result.matched ? path+" matched to existing "+result.file
                                      : path+" copied to "+result.file);
            }
        }
        return relativize(result.file);
    }

    public void install() throws Exception {
        copyToLib(classPath);
        copyToLib(addPath);
        store(setProperties(load()));
    }

    public String[] toStrings() {
        List<String> list = new ArrayList<String>();
        if (this.scheme!=null) {
            list.add("cleo.uri."+this.scheme+".file="+this.file);
            list.add("cleo.uri."+this.scheme+".inputstream="+this.inputStream);
            list.add("cleo.uri."+this.scheme+".outputStream="+this.outputStream);
            if (this.classPath!=null) {
                list.add("cleo.uri."+this.scheme+".classpath="+S.join(File.pathSeparator, this.classPath));
            }
            for (Map.Entry<String,String> e : this.properties.entrySet()) {
                list.add("cleo.uri."+this.scheme+"."+e.getKey()+"="+e.getValue());
            }
        }
        if (this.addPath!=null) {
            list.add("cleo.additional.classpath="+S.join(File.pathSeparator, this.addPath));
        }
        return list.toArray(new String[list.size()]);
    }
    public String toString() {
        return S.join("\n", this.toStrings());
    }
    public String[] deconstruct() {
        List<String> list = new ArrayList<String>();
        if (this.self!=null) {
            // should be all manifest driven
            list.add(this.self);
        } else {
            // must be explicit scheme: jars.. property=value...
            if (this.scheme!=null) {
                list.add(this.scheme+":");
            }
            if (this.classPath!=null) {
                list.addAll(Arrays.asList(this.classPath));
            }
            for (Map.Entry<String,String> e : this.properties.entrySet()) {
                list.add(e.getKey()+"="+e.getValue());
            }
        }
        return list.toArray(new String[list.size()]);
    }
}
