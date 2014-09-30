package com.sodiumcow.cc.shell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sodiumcow.util.S;

public class URI {
    public String   id           = null;
    public String   file         = null;
    public String   inputStream  = null;
    public String   outputStream = null;
    public String[] classPath    = null;

    private static final Pattern URI_PROPERTY = Pattern.compile("cleo\\.uri\\.(\\w+)\\.file");
    private static final String  COLON        = String.valueOf(File.pathSeparatorChar);

    public static Properties removeScheme(Properties props, URI scheme) {
        return removeScheme(props, scheme.id);
    }
    public static Properties removeScheme(Properties props, String id) {
        props.remove("cleo.uri."+id+".file");
        props.remove("cleo.uri."+id+".inputstream");
        props.remove("cleo.uri."+id+".outputstream");
        props.remove("cleo.uri."+id+".classpath");
        return props;
    }

    public static Properties setScheme(Properties props, URI scheme) {
        removeScheme(props, scheme);
        props.setProperty("cleo.uri."+scheme.id+".file",         scheme.file);
        props.setProperty("cleo.uri."+scheme.id+".inputstream",  scheme.inputStream);
        props.setProperty("cleo.uri."+scheme.id+".outputstream", scheme.outputStream);
        props.setProperty("cleo.uri."+scheme.id+".classpath",    S.join(COLON, scheme.classPath));
        return props;
    }

    public static URI getScheme(Properties props, String id) {
        if (props.containsKey("cleo.uri."+id+".file")) {
            return new URI(id,
                           props.getProperty("cleo.uri."+id+".file"),
                           props.getProperty("cleo.uri."+id+".inputstream"),
                           props.getProperty("cleo.uri."+id+".outputstream"),
                           props.getProperty("cleo.uri."+id+".classpath"));
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

    public static Properties load(File home) throws Exception {
        Properties props = new Properties();
        FileInputStream in = new FileInputStream(new File(home, "conf/system.properties"));
        props.load(in);
        in.close();
        return props;
    }

    public static void store(File home, Properties props) throws Exception {
        FileOutputStream out = new FileOutputStream(new File(home, "conf/system.properties"));
        props.store(out, "Properties updated by "+URI.class.getCanonicalName());
        out.close();
    }

    public URI (String scheme, String schemeFile, String schemeInputStream, String schemeOutputStream, String schemeClassPath) {
        this.id             = scheme;
        this.file         = schemeFile;
        this.inputStream  = schemeInputStream;
        this.outputStream = schemeOutputStream;
        this.classPath    = schemeClassPath==null ? null : schemeClassPath.split(COLON);
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

    private synchronized void inspectJars (File home, final String...jars) throws Exception {
        List<String> schemeFiles  = new ArrayList<String>();
        for (String file : jars) {
            File jarfile = new File(file);
            schemeFiles.add(relativize(home, jarfile));
            if (jarfile.canRead()) {
                final ClassLoader    classLoader = Util.class.getClassLoader();
                final String         canonical   = jarfile.getCanonicalPath();
                final java.net.URL   url         = new java.net.URL("jar:file:"+canonical+"!/");
                final URLClassLoader ucl         = new URLClassLoader(new java.net.URL[] { url }, classLoader);
                final JarInputStream jar         = new JarInputStream(new FileInputStream(jarfile));
                JarEntry entry;
                while ((entry = jar.getNextJarEntry()) != null) {
                    if (entry.getName().endsWith(".class")) {
                        String classname = entry.getName().replaceAll("/", "\\.");
                        classname = classname.substring(0, classname.length()-".class".length());
                        if (!classname.contains("$")) {
                            try {
                                final Class<?> test = Class.forName(classname, true, ucl);
                                if (com.cleo.lexicom.beans.LexURIFile.class.isAssignableFrom(test)) {
                                    this.file = classname;
                                    this.id = classname.toLowerCase();
                                    if (this.id.endsWith("file")) {
                                        this.id = this.id.substring(this.id.lastIndexOf(".")+1, this.id.length()-"file".length());
                                    }
                                }
                                try {
                                    test.getConstructor(com.cleo.lexicom.beans.LexURIFile.class);
                                    if (java.io.InputStream.class.isAssignableFrom(test)) {
                                        this.inputStream = classname;
                                    }
                                } catch (Exception e) {}
                                try {
                                    test.getConstructor(com.cleo.lexicom.beans.LexURIFile.class, boolean.class);
                                    if (java.io.OutputStream.class.isAssignableFrom(test)) {
                                        this.outputStream = classname;
                                    }
                                } catch (Exception e) {}
                            } catch (final ClassNotFoundException e) {
                                // so what
                            } 
                        }
                    }
                }
                jar.close();
            }
        }
        if (this.file==null || this.inputStream==null || this.outputStream==null) {
            throw new Exception("classes missing from classpath: invalid URI");
        }
        this.classPath = schemeFiles.toArray(new String[schemeFiles.size()]);
    }

    public URI (String scheme, String schemeFile, String schemeInputStream, String schemeOutputStream, String[] schemeClassPath) {
        this(scheme, schemeFile, schemeInputStream, schemeOutputStream, S.join(COLON, schemeClassPath));
    }

    public URI(String...jars) throws Exception {
        this(null, jars);
    }

    public URI(File home, final String...jars) throws Exception {
        inspectJars(home, jars);
    }
}
