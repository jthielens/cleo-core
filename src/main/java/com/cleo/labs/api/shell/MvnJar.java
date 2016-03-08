package com.cleo.labs.api.shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import com.cleo.labs.util.F;
import com.cleo.labs.util.S;
import com.cleo.labs.util.X;

/**
 * MvnJar wraps a filename or SBT-style group%artifact%version string.
 * In the latter case, where there is really a Maven reference:
 * <ul><li>{@code gav()} returns {@code true}</li>
 *     <li>{@code sbt()} returns {@code group%artifact%version}</li>
 *     <li>{@code url()} returns a URL String where the artifact may be found</li>
 *     <li>{@code jar()} returns {@code artifact-version.jar}</li>
 *     <li>{@code resolve()} locates {@code artifact-version.jar} in {@code lib}
 *         and updates {@code file()} to be {@code lib/artifact-version.jar}</li>
 *     <li>{@code update()} downloads the file from {@code url()} to {@code lib}
 *         if its hash differs from what is already there</li>
 * </ul>
 * Otherwise
 * <ul><li>{@code gav()} returns {@code false}</li>
 *     <li>{@code sbt()} returns {@code null}</li>
 *     <li>{@code url()} returns {@code null}</li>
 *     <li>{@code jar()} returns the original {@code jar} filename passed to the constructor</li>
 *     <li>{@code resolve()} locates the jar file in {@code lib}
 *         and updates {@code file()} to be {@code lib/jar}</li>
 *     <li>{@code update()} copies the file to {@code lib} if it is not already there</li>
 * </ul>
 */
public class MvnJar {
    private String  group    = null;
    private String  artifact = null;
    private String  version  = null;
    private File    file     = null;
    private String  jar      = null;
    private Factory factory  = null;

    // read only
    public String  group   () { return this.group   ; }
    public String  artifact() { return this.artifact; }
    public String  version () { return this.version ; }
    public File    file    () { return this.file    ; }
    public String  jar     () { return this.jar     ; }
    public Factory factory () { return this.factory ; }

    // cleaned up filename
    public String  fileName() {
        return factory().relativize(file());
    }

    // cleojar attributes, parsed from manifest
    private boolean     cleojar      = false; // did we see any manifest entries

    private String      scheme       = null;  // Cleo-URI-Scheme
    private String      fileclass    = null;  // Cleo-URI-File-Class
    private String      inputstream  = null;  // Cleo-URI-InputStream-Class
    private String      outputstream = null;  // Cleo-URI-OutputStream-Class
    private Set<MvnJar> classpath    = null;  // Cleo-URI-Depends
    private Set<MvnJar> additional   = null;  // Cleo-URI-Additional

    private String      listener     = null;  // Cleo-API-LexiComLogListener
    private String      incoming     = null;  // Cleo-API-ILexiComIncoming
    private String      outgoing     = null;  // Cleo-API-LexiComOutgoingThread
    private String      usergroups   = null;  // Cleo-API-ExtendedUserGroups
    private String      portalauth   = null;  // Cleo-API-IPortalUserAuthentication

    private String      authscheme   = null;  // Cleo-Authenticator-Scheme
    private String      authclass    = null;  // Cleo-Authenticator-Class

    private Set<String> providers    = null;  // com.cleo.labs.uri.vfs.provider.*.class

    public  boolean     cleojar     () { return this.cleojar     ; }
    public  String      scheme      () { return this.scheme      ; }
    public  String      fileclass   () { return this.fileclass   ; }
    public  String      inputstream () { return this.inputstream ; }
    public  String      outputstream() { return this.outputstream; }
    public  Set<MvnJar> classpath   () { return this.classpath   ; }
    public  Set<MvnJar> additional  () { return this.additional  ; }
    public  String      listener    () { return this.listener    ; }
    public  String      incoming    () { return this.incoming    ; }
    public  String      outgoing    () { return this.outgoing    ; }
    public  String      usergroups  () { return this.usergroups  ; }
    public  String      portalauth  () { return this.portalauth  ; }
    public  String      authscheme  () { return this.authscheme  ; }
    public  String      authclass   () { return this.authclass   ; }
    public  Set<String> providers   () { return this.providers   ; }

    public static class Factory {
        private static Map<String,MvnJar> library = new HashMap<>();
        private File                      home    = null;
        private File                      lib     = null;
        public  File home() { return this.home; }
        public  File lib () { return this.lib ; }
        /**
         * Return an already-known jar when referenced by short name,
         * resolved name, or group%artifact%version, or create, parse,
         * and register a new jar.
         * @param jar short name, resolved name, or g%a%v
         * @return a {@link MvnJar}
         */
        public MvnJar get(String jar) {
            MvnJar mvnjar = library.get(jar);
            if (mvnjar==null) {
                mvnjar = new MvnJar(jar, this);
                library.put(jar, mvnjar);
                library.put(mvnjar.jar(), mvnjar);
                if (mvnjar.gav()) {
                    library.put(mvnjar.sbt(), mvnjar);
                }
            }
            return mvnjar;
        }
        /**
         * Used for special handling of "this" in Cleo-URI-Additional.
         */
        public final MvnJar THIS; // set in constructor

        /**
         * Converts a {@code separator}-separated {@code list} to a
         * set of resolved {@link MvnJar}.
         * @param list a (possibly {@code null}) String
         * @param separator the separator (not {@code null})
         * @return a (possibly empty but never {@code null}) set
         */
        public Set<MvnJar> of(String list, String separator) {
            return of(list==null ? null : Arrays.asList(list.split(separator)));
        }
        /**
         * Converts a collection of strings to a
         * set of resolved {@link MvnJar}.
         * @param list a (possibly {@code null} or empty) Collection
         * @return a (possibly empty but never {@code null}) set
         */
        public Set<MvnJar> of(Collection<String> list) {
            Set<MvnJar> jars = new HashSet<>(list==null ? 0 : list.size());
            if (list!=null) {
                for (String name : list) {
                    jars.add(get(name));
                }
            }
            return jars;
        }
        /**
         * Returns a path, converted to a relative path from the
         * factory home directory if the path is (a) absolute and
         * (b) resides within the home directory.  Otherwise the
         * argument is returned unchanged.
         * @param file the file to clean up
         * @return the relativized path, or just file
         */
        public String relativize(File file) {
            Path path = file.toPath().normalize();
            Path home = home().getAbsoluteFile().toPath();
            if (path.isAbsolute() && path.startsWith(home)) {
                path = home.relativize(path);
            }
            return path.toString();
        }
        public Factory(File home, File lib) {
            this.home = home;
            this.lib  = lib;
            THIS      = new MvnJar("this", this);
        }
    }
    /**
     * Sets a new {@code jar}, and proceeds to resolve and parse it.
     * @param jar a filename, or group%artifact%version
     * @return {@code this}
     */
    public MvnJar jar(String jar, Factory factory) {
        this.jar     = jar;
        this.factory = factory;
        if (jar!=null) {
            String[] gav = jar.split("%");
            if (gav.length==3) {
                this.group    = gav[0];
                this.artifact = gav[1];
                this.version  = gav[2];
                this.jar = artifact()+"-"+version()+".jar";
            }
            this.file = new File(factory.lib(), new File(jar()).getName());
            if (Paths.get(jar()).isAbsolute() && Paths.get(jar()).startsWith(factory.home().getAbsoluteFile().toPath())) {
                this.jar = factory.home().getAbsoluteFile().toPath().relativize(Paths.get(jar())).toString();
            }
            // was relativize(file().toURI())
            // this.jar = factory.home().toURI().relativize(new File(jar()).toURI()).getPath();
            // this.jar = factory.home().toPath().relativize(Paths.get(jar())).toString();
            parse();
        }
        return this;
    }

    /**
     * Creates a {@link MvnJar} wrapper around a filename or SBT-style
     * group%artifact%version string.
     * @param jar a jar filename or {@code group%artifact%version} string
     */
    public MvnJar(String jar, Factory factory) {
        this.jar(jar, factory);
    }

    /**
     * Returns true if group, artifact, and version are all defined.
     * @return true if there is a GAV
     */
    public boolean gav() {
        return group()!=null && artifact()!=null && version()!=null;
    }
    /**
     * If all three Maven identifiers are present, returns
     * am SBT-encoded GAV (group%artifact%version).  Otherwise
     * simply returns the jar name.
     * @return g%a%v or jar
     */
    public String sbt() {
        return gav() ? group()+"%"+artifact()+"%"+version() : null;
    }
    /**
     * An XPath evaluator for use in Maven metadata.xml parsing.
     */
    private static final XPath xpath = XPathFactory.newInstance().newXPath();
    /**
     * If there is a GAV, returns a URL String from which the file may
     * be downloaded.
     * @return a URL String if {@code gav()}, or {@code null}
     */
    public String url() {
        if (gav()) {
            String repo = "http://central.maven.org/maven2/";
            String dir  = group().replace('.','/')+"/"+ // group, changing . to /
                          artifact()+"/"+               // artifact
                          version()+"/";                // version
            String file = artifact()+"-"+version()+".jar";
            if (group().startsWith("com.cleo")) {
                String contd = "10.10.1.57"; // "10.80.80.156";  // contd.cleo.com behind the VPN
                if (version().contains("SNAPSHOT")) {
                    repo = "http://"+contd+"/nexus/content/repositories/snapshots/";
                    try {
                        String value = xpath.evaluate("/metadata/versioning/snapshotVersions[snapshotVersion/extension='jar']/snapshotVersion/value",
                                X.string2xml(new String(F.download(repo+dir+"maven-metadata.xml"))));
                        file = artifact()+"-"+value+".jar";
                    } catch (Exception ignore) {
                        // usually NPE because file isn't there
                        // URI.report(ignore.toString());
                    }
                } else {
                    // contd.cleo.com
                    repo = "http://"+contd+"/nexus/content/repositories/releases/";
                }
            }
            return repo+dir+file;
        }
        return null;
    }

    /**
     * Downloads or copies a jar file into {@code lib},
     * reporting on its progress.
     * @return {@code this}
     */
    public MvnJar install() {
        return install(new HashSet<MvnJar>());
    }
    /**
     * Downloads or copies a jar file into {@code lib},
     * reporting on its progress, ignoring jars that have
     * already been "touched" to prevent recursive death as
     * it follows dependency links.
     * @param touched the set of already visited jars
     * @return {@code this}
     */
    private MvnJar install(Set<MvnJar> touched) {
        if (jar()!=null && file()!=null && !touched.contains(this)) {
            F.Clobbered result = null;
            if (gav()) {
                String url = url();
                byte[] sha1 = F.hex(S.s(F.download(url+".sha1")));
                try {
                    result = F.download(url, file(), "SHA-1", sha1, F.ClobberMode.OVERWRITE);
                    URI.report(result.matched ? jar()+" matched to existing "+result.file
                                              : jar()+" downloaded to "+result.file);
                } catch (Exception e) {
                    URI.report("error downloading "+jar()+": "+e);
                }
            }
            if (result==null) {
                // gav() wasn't true or didn't work
                File src = new File(jar());
                if (src.equals(file())) {
                    result = new F.Clobbered(src, true);
                    URI.report(jar()+" already installed");
                } else {
                    try {
                        result = F.copy(src, file(), F.ClobberMode.OVERWRITE);
                        URI.report(result.matched ? jar()+" matched to existing "+result.file
                                                  : jar()+" copied to "+result.file);
                    } catch (IOException e) {
                        URI.report("error copying "+jar()+": "+e);
                    }
                }
            }
            if (result!=null) { // && !result.matched) { // TODO: this could be a "force" flag?
                // read the manifest
                parse();
                touched.add(this);
                if (cleojar()) {
                    // if there are dependencies, make sure they are all fetched/updated too
                    for (MvnJar depend : dependencies()) {
                        depend.install(touched);
                    }
                }
            }
        }
        return this;
    }
    /**
     * Returns {@code true} if the JAR exists.
     * @return {@code true} or {@code false}
     */
    public boolean exists() {
        return file()!=null && file().exists();
    }

    /**
     * Resets all parsed cleo attributes.
     * @return {@code this}
     */
    private MvnJar cleo_reset() {
        this.cleojar      = false;
        this.scheme       = null;
        this.fileclass    = null;
        this.inputstream  = null;
        this.outputstream = null;
        this.classpath    = null;
        this.additional   = null;
        this.listener     = null;
        this.incoming     = null;
        this.outgoing     = null;
        this.usergroups   = null;
        this.portalauth   = null;
        this.authscheme   = null;
        this.authclass    = null;
        this.providers    = null;
        return this;
    }

    private static final String PROVIDER = "com/cleo/labs/uri/vfs/provider/";
    /**
     * If the {@code file()} exists, parses its manifest and
     * sets cleo jar attributes, setting {@code cleojar=true} if
     * any are found.
     * @return {@code this}
     */
    private MvnJar parse() {
        cleo_reset();
        File source = new File(jar());
        if (!source.exists()) {
            source = file();
        }
        if (source.exists()) {
            Set<MvnJar> classSet = new HashSet<>();
            Set<MvnJar> addSet   = new HashSet<>();
            try (JarFile jarfile = new JarFile(source)) {
                // parse the manifest
                Manifest manifest = jarfile.getManifest();
                if (manifest!=null) {
                    Attributes attrs = manifest.getMainAttributes();
                    if (attrs!=null) {
                        for (Object o : attrs.keySet()) {
                            String attr = o.toString();
                            if (attr.startsWith("Cleo-URI-Additional")) {
                                addSet.addAll(factory.of(attrs.getValue(attr), "\\s+"));
                            } else if (attr.startsWith("Cleo-URI-Depends")) {
                                classSet.addAll(factory.of(attrs.getValue(attr), "\\s+"));
                            } else {
                                switch (attr) {
                                case "Cleo-URI-Scheme":
                                    this.scheme = attrs.getValue(attr);
                                    this.cleojar = true;
                                    break;
                                case "Cleo-URI-File-Class":
                                    this.fileclass = attrs.getValue(attr);
                                    this.cleojar = true;
                                    break;
                                case "Cleo-URI-InputStream-Class":
                                    this.inputstream = attrs.getValue(attr);
                                    this.cleojar = true;
                                    break;
                                case "Cleo-URI-OutputStream-Class":
                                    this.outputstream = attrs.getValue(attr);
                                    this.cleojar = true;
                                    break;
                                case "Cleo-API-LexiComLogListener":
                                    this.listener = attrs.getValue(attr);
                                    this.cleojar = true;
                                    break;
                                case "Cleo-API-ILexiComIncoming":
                                    this.incoming = attrs.getValue(attr);
                                    this.cleojar = true;
                                    break;
                                case "Cleo-API-LexiComOutgoingThread":
                                    this.outgoing = attrs.getValue(attr);
                                    this.cleojar = true;
                                    break;
                                case "Cleo-API-ExtendedUserGroups":
                                    this.usergroups = attrs.getValue(attr);
                                    this.cleojar = true;
                                    break;
                                case "Cleo-API-IPortalUserAuthentication":
                                    this.portalauth = attrs.getValue(attr);
                                    this.cleojar = true;
                                    break;
                                case "Cleo-Authenticator-Scheme":
                                    this.authscheme = attrs.getValue(attr);
                                    this.cleojar = true;
                                    break;
                                case "Cleo-Authenticator-Class":
                                    this.authclass = attrs.getValue(attr);
                                    this.cleojar = true;
                                    break;
                                default:
                                }
                            }
                        }
                        // by default, this jar (filename()) goes in private classpath setting
                        // if "this" appears in "Additional", we put it in additional instead
                        //MvnJar thisjar = new MvnJar("this");
                        if (scheme()==null || addSet.contains(factory.THIS)) {
                            addSet.remove(factory.THIS);
                            addSet.add(this);
                        } else {
                            classSet.add(this);
                        }
                        // promote working Sets to member variables
                        if (!addSet.isEmpty()) {
                            this.additional = addSet;
                            this.cleojar = true;
                        }
                        if (!classSet.isEmpty()) {
                            this.classpath = classSet;
                            this.cleojar = true;
                        }
                    }
                }
                    // parse the Maven pom.properties to find the GAV
                for (Enumeration<JarEntry> entries = jarfile.entries(); entries.hasMoreElements();) {
                    JarEntry entry = entries.nextElement();
                    if (!gav() && entry.getName().matches("META-INF/maven/com\\.cleo\\..*/pom.properties")) {
                        String[] gav = new String[3];
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(jarfile.getInputStream(entry)))) {
                            for (String line=r.readLine(); line!=null; line=r.readLine()) {
                                String[] av = line.split("=", 2);
                                if (av.length==2) {
                                    switch(av[0]) {
                                    case "version":
                                        gav[2] = av[1];
                                        break;
                                    case "groupId":
                                        gav[0] = av[1];
                                        break;
                                    case "artifactId":
                                        gav[1] = av[1];
                                        break;
                                    }
                                }
                            }
                        }
                        if (gav[0]!=null && gav[1]!=null && gav[2]!=null) {
                            this.group    = gav[0];
                            this.artifact = gav[1];
                            this.version  = gav[2];
                        }
                    } else if (entry.getName().matches(PROVIDER+"[^/\\$]*.class")) {
                        if (providers==null) {
                            providers = new HashSet<>();
                        }
                        String provider = entry.getName();
                        provider = provider.substring(PROVIDER.length(), provider.length()-".class".length());
                        providers.add(provider);
                        this.cleojar = true;
                    }
                }
            } catch (IOException abort) {
                // no jar/manifest to parse
            }
        }
        return this;
    }
    /**
     * Returns all JARs upon which this CleoJar depends.  If there
     * are none, returns an empty set, never {@code null}
     * @return a set
     */
    public Set<MvnJar> dependencies() {
        if (classpath()==null && additional()==null) {
            return new HashSet<>();
        } else if (classpath()==null) {
            return additional();
        } else if (additional==null) {
            return classpath();
        } else {
            Set<MvnJar> both = new HashSet<>(classpath());
            both.addAll(additional);
            return both;
        }
    }

    @Override
    public String toString() {
        return jar==null ? "null" :
               jar()+
                S.all(" (",sbt(),")")+(file==null?"":" @ "+fileName())+
                S.all(S.all("\n  uri:",scheme()),
                      S.all("\n    file=",fileclass())+
                      S.all("\n    inputstream=",inputstream())+
                      S.all("\n    outputstream=",outputstream())+
                      S.all("\n    classpath=",S.join("\n      ", MvnJar.relativized(classpath()))))+
                S.all("\n  listener=",listener())+
                S.all("\n  incoming=",incoming())+
                S.all("\n  outgoing=",outgoing())+
                S.all("\n  usergroups=",usergroups())+
                S.all("\n  portalauth=",portalauth())+
                S.all("\n  auth:",authscheme(),"=",authclass())+
                S.all("\n  additional=",S.join("\n    ", MvnJar.relativized(additional())))+
                S.all("\n  providers=",S.join(", ", providers));
    }
    @Override
    public int hashCode() {
        return file!=null ? file.hashCode()
               : jar!=null ? jar.hashCode()
               : 0;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)                  return true;
        if (obj == null)                  return false;
        if (getClass() != obj.getClass()) return false;
        MvnJar other = (MvnJar) obj;
        return file!=null && other.file!=null ? file.equals(other.file)
               : jar==null ? other.jar==null
               : jar.equals(other.jar);
    }

    /**
     * Converts a list of {@code MvnJar}s into a {@code Set} of {@code String}s
     * by listing out their {@link #jar()} names.  Note that {@code null} or
     * empty lists are converted to {@code null}, not an empty {@code Set}.
     * This process is roughly the reverse of {@link MvnJar.Factory#of(Collection)}.
     * @param jars the jar collection
     * @return a (possibly {@code null}) list of String names
     */
    public static Set<String> relativized(Collection<MvnJar> jars) {
        Set<String> strings = null;
        if (jars!=null && !jars.isEmpty()) {
            strings = new HashSet<>();
            for (MvnJar jar : jars) {
                strings.add(jar.fileName());
            }
        }
        return strings;
    }
}
