package com.sodiumcow.cc.shell;

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

import com.sodiumcow.util.F;
import com.sodiumcow.util.S;

public class URI {
    public String             id           = null;
    public String             file         = null;
    public String             inputStream  = null;
    public String             outputStream = null;
    public String[]           classPath    = null;
    public String[]           addPath      = null;
    public Map<String,String> properties   = new HashMap<String,String>();

    private static final Pattern URI_PROPERTY = Pattern.compile("cleo\\.uri\\.(\\w+)\\.file");
    private static final String  COLON        = String.valueOf(File.pathSeparatorChar);

    public static Properties removeScheme(Properties props, URI scheme) {
        return removeScheme(props, scheme.id);
    }
    public static Properties removeScheme(Properties props, String id) {
        String prefix = "cleo.uri."+id+".";
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                props.remove(key);
            }
        }
        return props;
    }

    public static Properties setScheme(Properties props, URI scheme) {
        removeScheme(props, scheme);
        props.setProperty("cleo.uri."+scheme.id+".file",         scheme.file);
        props.setProperty("cleo.uri."+scheme.id+".inputstream",  scheme.inputStream);
        props.setProperty("cleo.uri."+scheme.id+".outputstream", scheme.outputStream);
        if (scheme.classPath!=null && scheme.classPath.length>0) {
        	props.setProperty("cleo.uri."+scheme.id+".classpath",    S.join(COLON, scheme.classPath));
        }
        for (Map.Entry<String,String> e : scheme.properties.entrySet()) {
            props.setProperty("cleo.uri."+scheme.id+"."+e.getKey(), e.getValue());
        }
        if (scheme.addPath!=null && scheme.addPath.length>0) {
            Set<String> classSet = new HashSet<String>();
            String existing = props.getProperty("cleo.additional.classpath");
            if (existing!=null && !existing.isEmpty()) {
                classSet.addAll(Arrays.asList(existing.split(COLON)));
            }
            classSet.addAll(Arrays.asList(scheme.addPath));
            props.setProperty("cleo.additional.classpath", S.join(COLON, classSet));
        }
        return props;
    }

    public static URI getScheme(Properties props, String id) {
        String             file         = null;
        String             inputstream  = null;
        String             outputstream = null;
        String             classpath    = null;
        Map<String,String> properties   = new HashMap<String,String>();

        String prefix = "cleo.uri."+id+".";
        if (props.containsKey(prefix+"file")) {
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith(prefix)) {
                    String prop = key.substring(prefix.length());
                    if (prop.equals("file")) {
                        file = props.getProperty(key);
                    } else if (prop.equals("inputstream")) {
                        inputstream = props.getProperty(key);
                    } else if (prop.equals("outputstream")) {
                        outputstream = props.getProperty(key);
                    } else if (prop.equals("classpath")) {
                        classpath = props.getProperty(key);
                    } else {
                        properties.put(prop, props.getProperty(key));
                    }
                }
            }
            return new URI(id, file, inputstream, outputstream, classpath, properties);
        } else {
            return null;
        }
    }

    public static String[] getSchemeIds(Properties props) {
        List<String> ids = new ArrayList<String>();
        for (String key : props.stringPropertyNames()) {
            Matcher m = URI_PROPERTY.matcher(key);
            if (m.matches()) {
                ids.add(m.group(1));
            }
        }
        return ids.toArray(new String[ids.size()]);
    }

    public static URI[] getSchemes(Properties props) {
        String[] ids  = getSchemeIds(props);
        URI[]    uris = new URI[ids.length];
        for (int i=0; i<ids.length; i++) {
            uris[i] = getScheme(props, ids[i]);
        }
        return uris;
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

    public static void store(File home, Properties props) throws Exception {
        FileOutputStream out = new FileOutputStream(new File(home, "conf/system.properties"));
        props.store(out, "Properties updated by "+URI.class.getCanonicalName());
        out.close();
    }

    public URI (String scheme, String schemeFile, String schemeInputStream, String schemeOutputStream, String schemeClassPath, Map<String,String> properties) {
        if (properties==null) {
            properties = new HashMap<String,String>();
        }
        this.id           = scheme;
        this.file         = schemeFile;
        this.inputStream  = schemeInputStream;
        this.outputStream = schemeOutputStream;
        this.classPath    = schemeClassPath==null ? null : schemeClassPath.split(COLON);
        this.properties   = properties;
        if (classPath==null) {
            properties.put("file",         this.file);
            properties.put("inputstream",  this.inputStream);
            properties.put("outputstream", this.outputStream);
        }
    }

    private static String relativize (File home, File jar) {
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

    private File homeFile(File home, String name) {
        File f = new File(name);
        if (home!=null && !f.isAbsolute()) {
            return new File(home, name);
        }
        return f;
    }

    private synchronized void inspectJars (File home, final String...jars) throws Exception {
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
                    this.file = kv[1];
                } else if (kv[0].equals("inputstream")) {
                    this.inputStream = kv[1];
                } else if (kv[0].equals("outputstream")) {
                    this.outputStream = kv[1];
                } else {
                    this.properties.put(kv[0], kv[1]);
                }
                continue;
            } else if (file.endsWith(":")) {
                this.id = file.substring(0, file.length()-1);
                continue;
            }
            // ok, it's a file
            File jarfile = homeFile(home, file);
            if (jarfile.canRead()) {
                schemeFiles.add(relativize(home, jarfile));
            } else {
                throw new Exception("can not read file: "+jarfile);
            }
        }
        // Second: run through the actual files in two passes
        if (!schemeFiles.isEmpty()) {
            final ClassLoader    classLoader = Util.class.getClassLoader();
            final java.net.URL[] urls = new java.net.URL[schemeFiles.size()];
            for (int i=0; i<schemeFiles.size(); i++) {
                urls[i] = new java.net.URL("jar:file:"+homeFile(home, schemeFiles.get(i)).getCanonicalPath()+"!/");
            }
            final URLClassLoader ucl = new URLClassLoader(urls, classLoader);
            // First pass: parse out manifest attributes to find:
            // - scheme                Cleo-URI-Scheme
            // - file class            Cleo-URI-File-Class
            // - input class           Cleo-URI-InputStream-Class
            // - output class          Cleo-URI-OutputStream-Class
            // - classpath             Cleo-URI-Depends
            // - additional.classpath  Cleo-URI-Additional
            // Note that first setting wins (for scheme and file classes that aren't lists).
            // Note also that the JARs themselves are added to classpath unless "this" appears in
            //      Additional, in which case the current JAR is added to additional instead
            for (String f : schemeFiles) {
                final JarFile jarfile = new JarFile(homeFile(home, f));
                // first check manifest
                Manifest manifest = jarfile.getManifest();
                if (manifest!=null) {
                    Attributes attrs = manifest.getMainAttributes();
                    if (attrs!=null) {
                        if (this.id==null && attrs.containsKey(new Attributes.Name("Cleo-URI-Scheme"))) {
                            this.id = attrs.getValue("Cleo-URI-Scheme").toLowerCase();
                        }
                        if (this.file==null && attrs.containsKey(new Attributes.Name("Cleo-URI-File-Class"))) {
                            this.file = attrs.getValue("Cleo-URI-File-Class");
                            if (this.id==null) {
                                this.id = file.toLowerCase();
                                if (this.id.endsWith("file")) {
                                    this.id = this.id.substring(this.id.lastIndexOf(".")+1, this.id.length()-"file".length());
                                }
                            }
                        }
                        if (this.inputStream==null && attrs.containsKey(new Attributes.Name("Cleo-URI-InputStream-Class"))) {
                            this.inputStream = attrs.getValue("Cleo-URI-InputStream-Class");
                        }
                        if (this.outputStream==null && attrs.containsKey(new Attributes.Name("Cleo-URI-OutputStream-Class"))) {
                            this.outputStream = attrs.getValue("Cleo-URI-OutputStream-Class");
                        }
                        if (attrs.containsKey(new Attributes.Name("Cleo-URI-Depends"))) {
                            classpath.addAll(Arrays.asList(attrs.getValue("Cleo-URI-Depends").split("\\s+")));
                        }
                        if (attrs.containsKey(new Attributes.Name("Cleo-URI-Additional"))) {
                            additional.addAll(Arrays.asList(attrs.getValue("Cleo-URI-Additional").split("\\s+")));
                        }
                    }
                }
                jarfile.close();
                if (additional.contains("this")) {
                    additional.remove("this");
                    additional.add(f);
                } else {
                    classpath.add(f);
                }
            }
            // Second pass: if the classes were not set in manifest atttributes,
            // read through the JAR files again trying on each class to find ones
            // that match the necessary types.
            if (file==null || inputStream==null || outputStream==null) {
                // look for compatible classes
                for (String f : schemeFiles) {
                    final JarFile jarfile = new JarFile(homeFile(home, f));
                    for (Enumeration<JarEntry> entries = jarfile.entries(); entries.hasMoreElements();) {
                        JarEntry entry = entries.nextElement();
                        if (entry.getName().endsWith(".class")) {
                            String classname = entry.getName().replaceAll("/", "\\.");
                            classname = classname.substring(0, classname.length()-".class".length());
                            if (!classname.contains("$")) {
                                try {
                                    final Class<?> test = Class.forName(classname, true, ucl);
                                    if (file==null && com.cleo.lexicom.beans.LexURIFile.class.isAssignableFrom(test)) {
                                        this.file = classname;
                                        if (this.id==null) {
                                            this.id = classname.toLowerCase();
                                            if (this.id.endsWith("file")) {
                                                this.id = this.id.substring(this.id.lastIndexOf(".")+1, this.id.length()-"file".length());
                                            }
                                        }
                                    }
                                    if (inputStream==null) {
                                        try {
                                            test.getConstructor(com.cleo.lexicom.beans.LexURIFile.class);
                                            if (java.io.InputStream.class.isAssignableFrom(test)) {
                                                this.inputStream = classname;
                                            }
                                        } catch (Exception ignore) {}
                                    }
                                    if (outputStream==null) {
                                        try {
                                            test.getConstructor(com.cleo.lexicom.beans.LexURIFile.class, boolean.class);
                                            if (java.io.OutputStream.class.isAssignableFrom(test)) {
                                                this.outputStream = classname;
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
        if (this.file==null || this.inputStream==null || this.outputStream==null) {
            throw new Exception("classes missing from classpath: invalid URI");
        }
        if (!classpath.isEmpty()) {
            this.classPath = classpath.toArray(new String[classpath.size()]);
        }
        if (!additional.isEmpty()) {
            this.addPath = additional.toArray(new String[additional.size()]);
        }
    }

    private String mvn2(String path) {
        String[] gav = path.split("%");
        if (gav.length==3) {
            StringBuilder s = new StringBuilder("http://central.maven.org/maven2/")
                                  .append(gav[0].replace('.','/'))   // group, changing . to /
                                  .append('/').append(gav[1])        // artifact
                                  .append('/').append(gav[2])        // version
                                  .append('/').append(gav[1])        // artifact-version.jar
                                  .append('-').append(gav[2]).append(".jar");
            return s.toString();
        }
        return null;
    }

    private void copyToLib(String[] list, File home, File lib, Shell shell) throws Exception {
        if (list==null) return;
        for (int i=0; i<list.length; i++) {
            String path = list[i];
            String mvn2 = mvn2(path);
            if (mvn2!=null) {
                byte[] sha1 = F.hex(S.s(F.download(mvn2+".sha1")));
                F.Clobbered result = F.download(mvn2, lib, "SHA-1", sha1, F.ClobberMode.UNIQUE);
                list[i] = relativize(home, result.file);
                shell.report(result.matched ? path+" matched to existing "+result.file
                                            : path+" downloaded to "+result.file);
            } else {
                F.Clobbered result = F.copy(homeFile(home, path), lib, F.ClobberMode.UNIQUE);
                list[i] = relativize(home, result.file);
                shell.report(result.matched ? path+" matched to existing "+result.file
                                            : path+" copied to "+result.file);
            }
        }
    }

    public void install(File home, File lib, Shell shell) throws Exception {
        copyToLib(classPath, home, lib, shell);
        copyToLib(addPath,   home, lib, shell);
    }

    public URI (String scheme, String schemeFile, String schemeInputStream, String schemeOutputStream, String[] schemeClassPath, Map<String,String> properties) {
        this(scheme, schemeFile, schemeInputStream, schemeOutputStream, S.join(COLON, schemeClassPath), properties);
    }

    public URI(String...jars) throws Exception {
        this(null, jars);
    }

    public URI(File home, final String...jars) throws Exception {
        inspectJars(home, jars);
    }

    public String[] toStrings() {
        List<String> list = new ArrayList<String>();
        list.add("cleo.uri."+this.id+".file="+this.file);
        list.add("cleo.uri."+this.id+".inputstream="+this.inputStream);
        list.add("cleo.uri."+this.id+".outputStream="+this.outputStream);
        if (this.classPath!=null) {
            list.add("cleo.uri."+this.id+".classpath="+S.join(File.pathSeparator, this.classPath));
        }
        for (Map.Entry<String,String> e : this.properties.entrySet()) {
            list.add("cleo.uri."+this.id+"."+e.getKey()+"="+e.getValue());
        }
        return list.toArray(new String[list.size()]);
    }
    public String toString() {
        return S.join("\n", this.toStrings());
    }
    public String[] deconstruct() {
        List<String> list = new ArrayList<String>();
        list.add(this.id+":");
        if (this.classPath!=null) {
            list.addAll(Arrays.asList(this.classPath));
        }
        for (Map.Entry<String,String> e : this.properties.entrySet()) {
            list.add(e.getKey()+"="+e.getValue());
        }
        return list.toArray(new String[list.size()]);
    }
}
