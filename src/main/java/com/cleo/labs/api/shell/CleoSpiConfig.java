package com.cleo.labs.api.shell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.cleo.labs.api.LexiCom;
import com.cleo.labs.util.F;
import com.cleo.labs.util.F.ClobberMode;
import com.cleo.labs.util.S;
import com.cleo.labs.util.S.Formatter;
import com.cleo.labs.util.X;

public class CleoSpiConfig {
    public enum APIHookClass {
        LISTENER {
            static public final String PROPERTY = "CustomLexiComLogListenerClass";
            @Override
            public String get(CleoSpiConfig config) throws Exception {
                return S.ornull(getOption(config, PROPERTY));
            }
            @Override
            public void set(CleoSpiConfig config, String value) throws Exception {
                setOption(config, PROPERTY, value);
            }
        },
        INCOMING {
            static public final String PROPERTY = "CustomILexiComIncomingClass";
            @Override
            public String get(CleoSpiConfig config) throws Exception {
                return S.ornull(getOption(config, PROPERTY));
            }
            @Override
            public void set(CleoSpiConfig config, String value) throws Exception {
                setOption(config, PROPERTY, value);
            }
        },
        OUTGOING {
            static public final String PROPERTY = "CustomLexiComOutgoingThreadClass";
            @Override
            public String get(CleoSpiConfig config) throws Exception {
                return S.ornull(getOption(config, PROPERTY));
            }
            @Override
            public void set(CleoSpiConfig config, String value) throws Exception {
                setOption(config, PROPERTY, value);
            }
        },
        USERGROUPS {
            static public final String PROPERTY = "cleo.extended.usergroups";
            @Override
            public String get(CleoSpiConfig config) throws Exception {
                return S.ornull(config.property(PROPERTY));
            }
            @Override
            public void set(CleoSpiConfig config, String value) throws Exception {
                config.property(PROPERTY, value);
            }
        };
        public abstract String get(CleoSpiConfig config) throws Exception;
        public abstract void set(CleoSpiConfig config, String value) throws Exception;
        private static final XPath xpath = XPathFactory.newInstance().newXPath();
        private static final String OPTIONS = "conf/Options.xml";
        private static Document opts = null;
        private static String getOption(CleoSpiConfig config, String option) throws Exception {
            // LexiCom.getOptions().getOther(option), but avoiding connect()
            if (opts==null) {
                opts = X.file2xml(new File(config.home(), OPTIONS));
            }
            return (String) xpath.evaluate("substring-after(/Options/Other[starts-with(.,'"+option+"=')],'=')",
                                           opts, XPathConstants.STRING);
        }
        private static boolean setOption(CleoSpiConfig config, String option, String value) throws Exception {
            // LexiCom.getOptions().setOther(option, value), but avoiding connect()
            boolean changed = false;
            Document opts = X.file2xml(new File(config.home(), OPTIONS));
            Node opt = (Node) xpath.evaluate("/Options/Other[starts-with(.,'"+option+"=')]",
                                             opts, XPathConstants.NODE);
            if (opt==null) {
                // nothing there
                if (!S.empty(value)) {
                    // should be something there, so add it after other Other or at the end
                    Element other = opts.createElement("Other");
                    other.appendChild(opts.createTextNode(option+"="+value));
                    Node before = (Node) xpath.evaluate("/Options/Other", opts, XPathConstants.NODE);
                    opts.getFirstChild().insertBefore(other, before);
                    changed = true;
                } // else no change
            } else {
                // something there
                if (S.empty(value)) {
                    // should be nothing, so delete it
                    opt.getParentNode().removeChild(opt);
                    changed = true;
                } else {
                    String current = opt.getFirstChild().getNodeValue().substring(option.length()+1);
                    if (!current.equals(value)) {
                        // should be something else, so update it
                        opt.getFirstChild().setNodeValue(option+"="+value);
                        changed = true;
                    } // else no change
                }
            }
            if (changed) {
                F.write(X.xml2string(opts), OPTIONS, ClobberMode.OVERWRITE);
            }
            return changed;
        }
    }
    public static class APIHook {
        private String hook = null;  // the hook classname
        private MvnJar jar  = null;  // CleoJar, if defined by one

        public APIHook hook(String hook) { this.hook = hook; return this; }
        public String  hook(           ) { return this.hook;              }
        public APIHook jar (MvnJar jar ) { this.jar  = jar ; return this; }
        public MvnJar  jar (           ) { return this.jar ;              }

        @Override
        public String toString() {
            return S.s(hook())+
                   S.all(" (",jar()!=null?jar().jar():null,")");
        }
    }
    public static class UriScheme {
        private String             scheme       = null;  // cleo.uri.scheme
        private String             file         = null;  // cleo.uri.scheme.file
        private String             inputstream  = null;  // cleo.uri.scheme.inputstream
        private String             outputstream = null;  // cleo.uri.scheme.outputstream
        private Set<String>        classpath    = null;  // cleo.uri.scheme.classpath
        private Map<String,String> properties   = null;  // cleo.uri.scheme.*
        private MvnJar             jar          = null;  // CleoJar, if defined by one

        public  UriScheme          scheme      (String             scheme      ) { this.scheme       = scheme      ; return this; }
        public  String             scheme      (                               ) { return this.scheme      ;                      }
        public  UriScheme          file        (String             file        ) { this.file         = file        ; return this; }
        public  String             file        (                               ) { return this.file        ;                      }
        public  UriScheme          inputstream (String             inputstream ) { this.inputstream  = inputstream ; return this; }
        public  String             inputstream (                               ) { return this.inputstream ;                      }
        public  UriScheme          outputstream(String             outputstream) { this.outputstream = outputstream; return this; }
        public  String             outputstream(                               ) { return this.outputstream;                      }
        public  UriScheme          classpath   (Set<String>        classpath   ) { this.classpath    = classpath   ; return this; }
        public  Set<String>        classpath   (                               ) { return this.classpath   ;                      }
        public  UriScheme          properties  (Map<String,String> properties  ) { this.properties   = properties  ; return this; }
        public  Map<String,String> properties  (                               ) { return this.properties  ;                      }
        public  UriScheme          jar         (MvnJar             jar         ) { this.jar          = jar         ; return this; }
        public  MvnJar             jar         (                               ) { return this.jar         ;                      }

        /**
         * Custom setter that
         * parses a :-separated String into a Set, creating a new
         * Set if needed, or adding the entries to the existing Set.
         * @param classPath a :-separated list
         * @return {@code this}
         */
        public UriScheme classpath(String classPath) {
            if (classpath()==null) {
                classpath(new HashSet<String>());
            }
            classpath().addAll(Arrays.asList(classPath.split(":")));
            return this;
        }
        /**
         * Custom setter that
         * sets the {@code value} of property named {@code name},
         * creating a new property Map if needed.
         * @param name the property name
         * @param value the property value
         * @return {@code this}
         */
        public UriScheme property(String name, String value) {
            if (properties==null) {
                properties = new HashMap<>();
            }
            properties.put(name, value);
            return this;
        }
        /**
         * Custom getter that
         * returns the value of property {@code name}, or
         * {@code null} if the property map is null or if
         * the property is not set.
         * @param name the property name
         * @return the property value, or {@code null}
         */
        public String property(String name) {
            return properties==null ? null : properties.get(name);
        }
 
        @Override
        public String toString() {
            return "uri:"+scheme()+S.all(" (",jar()!=null?jar().jar():null,")")+
                   S.all("\n  file=",file())+
                   S.all("\n  inputStream=",inputstream())+
                   S.all("\n  outputStream=",outputstream())+
                   S.all("\n  classPath=",S.join(":", classpath()))+
                   S.all("\n  properties=",S.join("\n    ", properties(), "%s=%s"));
        }
    }
    public static class AuthScheme {
        private String scheme        = null;  // cleo.password.validator.scheme
        private String authenticator = null;  // cleo.password.validator.scheme=authenticator
        private String jar           = null;  // CleoJar, if defined by one
        
        public  AuthScheme scheme       (String scheme       ) { this.scheme        = scheme       ; return this; }
        public  String     scheme       (                    ) { return this.scheme       ;                       }
        public  AuthScheme authenticator(String authenticator) { this.authenticator = authenticator; return this; }
        public  String     authenticator(                    ) { return this.authenticator;                       }
        public  AuthScheme jar          (String jar          ) { this.jar           = jar          ; return this; }
        public  String     jar          (                    ) { return this.jar          ;                       }

        @Override
        public String toString() {
            return "auth:"+scheme()+S.all(" (",jar(),")")+
                   S.all("\n  authenticator=",authenticator());
        }
    }
    private File                          home           = null;
    private File                          lib            = null;
    private Properties                    properties     = null;
    private MvnJar.Factory                factory        = null;
    private Map<String,UriScheme>         schemes        = null;  // cleo.uri.*
    private Map<String,AuthScheme>        authenticators = null;  // cleo.password.validator.*
    private Set<String>                   additional     = null;  // cleo.additional.classpath
    private EnumMap<APIHookClass,APIHook> hooks          = new EnumMap<>(APIHookClass.class); // Options.other.*

    // classical fluent setter/getters
    public  File                          home          (                                            ) { return this.home          ;                        }
    public  File                          lib           (                                            ) { return this.lib           ;                        }
    public  Properties                    properties    (                                            ) { return this.properties    ;                        }
    public  MvnJar.Factory                factory       (                                            ) { return this.factory       ;                        }
    public  CleoSpiConfig                 schemes       (Map<String,UriScheme>         schemes       ) { this.schemes        = schemes       ; return this; }
    public  Map<String,UriScheme>         schemes       (                                            ) { return this.schemes       ;                        }
    public  CleoSpiConfig                 authenticators(Map<String,AuthScheme>        authenticators) { this.authenticators = authenticators; return this; }
    public  Map<String,AuthScheme>        authenticators(                                            ) { return this.authenticators;                        }
    public  CleoSpiConfig                 additional    (Set<String>                   additional    ) { this.additional     = additional    ; return this; }
    public  Set<String>                   additional    (                                            ) { return this.additional    ;                        }
    public  CleoSpiConfig                 hooks         (EnumMap<APIHookClass,APIHook> hooks         ) { this.hooks          = hooks         ; return this; }
    public  EnumMap<APIHookClass,APIHook> hooks         (                                            ) { return this.hooks         ;                        }

    // custom setter/getters
    /**
     * Custom getter that
     * returns the system.properties value for {@code key}, or
     * {@code null} if the properties were not loaded in the
     * constructor or the property is not set.
     * @param key the property to retrieve
     * @return the value, or {@code null}
     */
    public String property(String key) {
        return this.properties()==null ? null : this.properties().getProperty(key);
    }
    /**
     * Custom setter that
     * updates the system.property for {@code key}, if the
     * properties were loaded in the constructor.  If {@code value}
     * is {@code null}, the property is removed.  Otherwise it
     * is set.
     * @param key the property to set
     * @param value the value to set
     * @return {@code this}
     */
    public CleoSpiConfig property(String key, String value) {
        if (this.properties()!=null) {
            if (value==null) {
                this.properties().remove(key);
            } else {
                this.properties().setProperty(key, value);
            }
        }
        return this;
    }
    /**
     * Custom getter that
     * returns the {@link UriScheme} for {@code name}, creating a
     * new one if it does not yet exist.
     * @param name the scheme name
     * @return the (possibly new) {@link UriScheme} object, never {@code null}
     */
    public UriScheme scheme(String name) {
        if (schemes()==null) {
            schemes(new HashMap<String,UriScheme>());
        }
        UriScheme scheme = schemes.get(name);
        if (scheme==null) {
            scheme = new UriScheme().scheme(name);
            schemes.put(name, scheme);
        }
        return scheme;
    }
    /**
     * Custom getter that
     * returns the {@link AuthScheme} for {@code name}, creating a
     * new one if it does not yet exist.
     * @param name the scheme name
     * @return the (possibly new) {@link AuthScheme} object, never {@code null}
     */
    public AuthScheme authenticator(String name) {
        if (authenticators()==null) {
            authenticators(new HashMap<String,AuthScheme>());
        }
        AuthScheme scheme = authenticators.get(name);
        if (scheme==null) {
            scheme = new AuthScheme().scheme(name);
            authenticators.put(name, scheme);
        }
        return scheme;
    }
    /**
     * Custom setter that
     * parses a :-separated String into a Set, creating a new
     * Set if needed, or adding the entries to the existing Set.
     * @param additional a :-separated list
     * @return {@code this}
     */
    public CleoSpiConfig add_additional(String additional) {
        return add_additional(Arrays.asList(additional.split(":")));
    }
    /**
     * Custom setter that
     * adds an existing Set of Strings, creating a new
     * Set if needed, or adding the entries to the existing Set.
     * @param additional a Set of Strings
     * @return {@code this}
     */
    public CleoSpiConfig add_additional(Collection<String> additional) {
        if (additional!=null) {
            if (additional()==null) {
                additional(new HashSet<String>());
            }
            additional().addAll(additional);
        }
        return this;
    }
    public CleoSpiConfig remove_additional(Collection<String> remove) {
        if (remove!=null) {
            if (additional()!=null) {
                additional().removeAll(remove);
            }
        }
        return this;
    }
    /**
     * Custom getter that
     * returns all jars referenced in a classpath of a defined scheme.
     * @return a Set of MvnJar (possibly empty, but not @{code null})
     */
    public Set<MvnJar> classpath_jars() {
        Set<MvnJar> jars = new HashSet<>();
        if (schemes()!=null) {
            for (UriScheme scheme : schemes().values()) {
                if (scheme.classpath()!=null) {
                    jars.addAll(factory.of(scheme.classpath()));
                }
            }
        }
        return jars;
    }
    /**
     * Custom getter that
     * returns all jars referenced in additional or
     * a classpath of a defined scheme.
     * @return a Set of JAR filenames (possibly empty, but not @{code null})
     */
    public Set<MvnJar> jars() {
        Set<MvnJar> jars = factory.of(additional());
        jars.addAll(classpath_jars());
        return jars;
    }
    /**
     * Returns a list of all CleoJar and their dependencies.
     * @return
     */
    public Set<MvnJar> referenced_jars() {
        Set<MvnJar> referenced = new HashSet<>();
        for (MvnJar jar : jars()) {
            if (jar.cleojar()) {
                referenced.add(jar);
                referenced.addAll(jar.dependencies());
            }
        }
        return referenced;
    }
    /**
     * Returns a list of all jars in the additional classpath not
     * referenced by the configuration.
     * @return
     */
    public Set<MvnJar> unreferenced_jars() {
        Set<MvnJar> jars = new HashSet<>();
        if (additional()!=null) {
            jars.addAll(factory.of(additional()));
            jars.removeAll(referenced_jars());
        }
        return jars;
    }

    private static final String URI_PREFIX  = "cleo.uri.";
    private static final String AUTH_PREFIX = "cleo.password.validator.";
    private static final String ADDITIONAL  = "cleo.additional.classpath";

    /**
     * Loads the Service Provider Interface (SPI) plugin configuration
     * for the VersaLex installed in {@code home}.
     * @param home
     */
    public CleoSpiConfig(File home) {
        this.home    = home;
        this.lib     = new File(home(), "lib/uri");
        this.factory = new MvnJar.Factory(home, lib);
        // load property-based configuration
        properties = new Properties();
        try (FileInputStream fis = new FileInputStream(new File(home(), "conf/system.properties"))) {
            properties.load(fis);
            for (String property : properties.stringPropertyNames()) {
                if (property.startsWith(URI_PREFIX)) {
                    String[] uri_property = property.substring(URI_PREFIX.length()).split("\\.", 2);
                    if (uri_property.length==2) {
                        switch (uri_property[1]) {
                        case "file":
                            scheme(uri_property[0]).file(properties.getProperty(property));
                            break;
                        case "inputstream":
                            scheme(uri_property[0]).inputstream(properties.getProperty(property));
                            break;
                        case "outputstream":
                            scheme(uri_property[0]).outputstream(properties.getProperty(property));
                            break;
                        case "classpath":
                            scheme(uri_property[0]).classpath(properties.getProperty(property));
                            break;
                        default:
                            scheme(uri_property[0]).property(uri_property[1], properties.getProperty(property));
                        }
                    }
                } else if (property.startsWith(AUTH_PREFIX)) {
                    authenticator(property.substring(AUTH_PREFIX.length())).authenticator(properties.getProperty(property));
                } else if (property.equals(ADDITIONAL)) {
                    add_additional(properties.getProperty(property));
                }
            }
        } catch (Exception ignore) {
            // no properties
        }
        // load Options-based configuration
        for (APIHookClass hook : APIHookClass.values()) {
            try {
                String value = hook.get(this);
                if (value!=null) {
                    hooks().put(hook, new APIHook().hook(value));
                }
            } catch (Exception ignore) {
                // guess there's no value there
            }
        }
    }

    public void save() {
        // save property-based configuration
        // 1. load file
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(LexiCom.getHome("conf/system.properties"))) {
            properties.load(fis);
        } catch (Exception ignore) {
            // no properties
        }
        // 2. remove managed properties
        for (String property : properties.stringPropertyNames()) {
            if (property.startsWith(URI_PREFIX) || property.startsWith(AUTH_PREFIX)) {
                properties.remove(property);
            }
        }
        properties.remove(ADDITIONAL);
        // 3. update managed properties
        if (schemes()!=null) {
            for (Map.Entry<String,UriScheme> entry : schemes().entrySet()) {
                String    id     = entry.getKey();
                UriScheme scheme = entry.getValue();
                if (scheme.file        ()!=null) properties.put(URI_PREFIX+id+".file",         scheme.file());
                if (scheme.inputstream ()!=null) properties.put(URI_PREFIX+id+".inputstream",  scheme.inputstream());
                if (scheme.outputstream()!=null) properties.put(URI_PREFIX+id+".outputstream", scheme.outputstream());
                if (scheme.classpath   ()!=null) {
                    properties.put(URI_PREFIX+id+".classpath", S.join(":", scheme.classpath()));
                }
                if (scheme.properties()!=null) {
                    for (Map.Entry<String,String> prop : scheme.properties().entrySet()) {
                        properties.put(URI_PREFIX+id+"."+prop.getKey(), prop.getValue());
                    }
                }
            }
        }
        if (authenticators()!=null) {
            for (Map.Entry<String,AuthScheme> entry : authenticators().entrySet()) {
                String     id     = entry.getKey();
                AuthScheme scheme = entry.getValue();
                properties.put(AUTH_PREFIX+id, scheme.authenticator());
            }
        }
        if (additional()!=null) {
            properties.put(ADDITIONAL, S.join(":", additional()));
        }
        // 4. save Options-based configuration (some may be properties)
        for (Map.Entry<APIHookClass,APIHook> hook : hooks().entrySet()) {
            try {
                String value = hook.getValue()==null ? null : hook.getValue().hook();
                hook.getKey().set(this, value);
            } catch (Exception e) {
                URI.report("error saving "+hook.getKey().name()+": "+e);
            }
        }
        // 5. write the file
        try (FileOutputStream out = new FileOutputStream(new File(home(), "conf/system.properties"))) {
            properties.store(out, "Properties updated by "+CleoSpiConfig.class.getCanonicalName());
        } catch (Exception e) {
            URI.report("error saving system.properties: "+e);
        }
    }

    public CleoSpiConfig install_jar(MvnJar mvnjar) {
        mvnjar.install();
        Set<MvnJar> all = jars();
        Deque<MvnJar> toadd = new ArrayDeque<>();
        toadd.add(mvnjar);
        while (!toadd.isEmpty()) {
            MvnJar add = toadd.pop();
            if (add.cleojar() && !all.contains(add)) {
                if (add.scheme()!=null) {
                    scheme(add.scheme())
                        .file(add.fileclass())
                        .inputstream(add.inputstream())
                        .outputstream(add.outputstream())
                        .classpath(MvnJar.relativized(add.classpath()))
                        .jar(add);
                }
                if (add.authscheme()!=null) {
                    authenticator(add.authscheme())
                        .authenticator(add.authclass())
                        .jar(add.jar());
                }
                if (add.incoming()!=null) {
                    hooks().put(APIHookClass.INCOMING,
                            new APIHook().hook(add.incoming()).jar(add));
                }
                if (add.outgoing()!=null) {
                    hooks().put(APIHookClass.OUTGOING,
                            new APIHook().hook(add.outgoing()).jar(add));
                }
                if (add.usergroups()!=null) {
                    hooks().put(APIHookClass.USERGROUPS,
                            new APIHook().hook(add.usergroups()).jar(add));
                }
                if (add.listener()!=null) {
                    hooks().put(APIHookClass.LISTENER,
                            new APIHook().hook(add.listener()).jar(add));
                }
                if (add.additional()!=null) {
                    add_additional(MvnJar.relativized(add.additional()));
                }
                all.add(add);
                toadd.addAll(add.dependencies());
                //TODO: resync the dependencies
            }
        }
        return this;
    }

    public CleoSpiConfig remove_authenticator(String name) {
        if (authenticators().containsKey(name)) {
            authenticators().remove(name);
            remove_jar(null);
        }
        return this;
    }
    public CleoSpiConfig remove_scheme(String name) {
        if (schemes().containsKey(name)) {
            schemes().remove(name);
            remove_jar(null);
        }
        return this;
    }
    public CleoSpiConfig remove_hook(APIHookClass hook) {
        hooks().put(hook, null);
        return this;
    }
    /**
     * removes the specified jar and any piece of configuration depending
     * on it, updating the additional set in the process.  If {@code jar}
     * is {@code null}, the additional set is recalculated, but presumably
     * the configuration has already been edited.
     * @param jar
     * @return
     */
    public CleoSpiConfig remove_jar(MvnJar jar) {
        // TODO: figure out how to be smart about audit and dependency calculation
        audit();
        Map<MvnJar,Set<MvnJar>> dependencies = dependencies();
        Set<MvnJar> unreferenced = unreferenced_jars();
        // reverse dependencies to find all affected jars depending on jar
        Set<MvnJar> affected = new HashSet<>();
        if (jar!=null) {
            affected.add(jar);
            for (Map.Entry<MvnJar,Set<MvnJar>> e : dependencies.entrySet()) {
                if (e.getValue().contains(jar)) {
                    affected.add(e.getKey());
                }
            }
        }
        // now walk through uri scheme, auth schemes, and API hooks, eliminating those affected
        Set<MvnJar> additional = new HashSet<>();
        if (schemes()!=null) {
            Set<String> uris = new HashSet<>(schemes().keySet());
            for (String uri : uris) {
                UriScheme scheme = schemes().get(uri);
                if (scheme.jar()!=null) {
                    MvnJar scheme_jar = scheme.jar();
                    if (affected.contains(scheme_jar)) {
                        schemes().remove(uri);
                        URI.report("removing uri scheme "+uri);
                    } else {
                        if (dependencies.containsKey(scheme_jar)) {
                            additional.addAll(dependencies.get(scheme_jar));
                        }
                        if (additional().contains(scheme_jar.jar())) {
                            additional.add(scheme_jar);
                        }
                    }
                }
            }
        }
        if (authenticators()!=null) {
            Set<String> auths = new HashSet<>(authenticators().keySet());
            for (String auth : auths) {
                AuthScheme scheme = authenticators().get(auth);
                if (scheme.jar()!=null) {
                    MvnJar scheme_jar = factory.get(scheme.jar());
                    if (affected.contains(scheme_jar)) {
                        authenticators().remove(auth);
                        URI.report("removing auth scheme "+auth);
                    } else {
                        if (dependencies.containsKey(scheme_jar)) {
                            additional.addAll(dependencies.get(scheme_jar));
                        }
                        if (additional().contains(scheme_jar.jar())) {
                            additional.add(scheme_jar);
                        }
                    }
                }
            }
        }
        for (APIHookClass hook : APIHookClass.values()) {
            APIHook apihook = hooks().get(hook);
            if (apihook!=null) {
                if (apihook.jar()!=null) {
                    MvnJar hook_jar = apihook.jar();
                    if (affected.contains(hook_jar)) {
                        hooks().put(hook, null);
                        URI.report("removing API Hook "+hook.name());
                    } else {
                        if (dependencies.containsKey(hook_jar)) {
                            additional.addAll(dependencies.get(hook_jar));
                        }
                        if (additional().contains(hook_jar.jar())) {
                            additional.add(hook_jar);
                        }
                    }
                }
            }
        }
        // update additional
        Set<String> removes = new HashSet<>(additional());
        if (!additional.isEmpty()) {
            removes.removeAll(MvnJar.relativized(additional));
        }
        for (String remove : removes) {
            // add back any otherwise unreferenced unaffected jars
            // for now, this means providers, which are not linked to
            // the configuration other than just being there
            // TODO: generalize this "just because" idea as a manifest property?
            MvnJar keeper = factory.get(remove);
            if (keeper.providers()!=null && !affected.contains(keeper)) {
                additional.add(keeper);
            } else {
                URI.report("removing "+remove);
            }
        }
        additional(MvnJar.relativized(additional));
        add_additional(MvnJar.relativized(unreferenced));
        //audit(); // needed for add, but not remove
        return this;
    }

    /**
     * Walks through all the {@link #jars()} looking for {@link CleoJar} jars
     * and then matches the manifest properties again each uri scheme, auth
     * scheme, and {@link APIHook} in the configuration.  For each match, the
     * {@link CleoJar} providing the corresponding manifest property is
     * marked in the {@code jar()} for the configuration (all schemes and
     * {@link APIHook}s carry {@code jar()} properties).
     */
    public CleoSpiConfig audit() {
        if (jars()!=null) {
            for (MvnJar cleojar : jars()) {
                if (cleojar.cleojar()) {
                    UriScheme uri = schemes()==null ? null : schemes().get(cleojar.scheme());
                    if (uri!=null) {
                        uri.jar(cleojar);
                    }
                    AuthScheme auth = authenticators()==null ? null : authenticators().get(cleojar.authscheme());
                    if (auth!=null) {
                        auth.jar(cleojar.jar());
                    }
                    APIHook listener = hooks().get(APIHookClass.LISTENER);
                    if (listener!=null && listener.hook()!=null && listener.hook().equals(cleojar.listener())) {
                        listener.jar(cleojar);
                    }
                    APIHook incoming = hooks().get(APIHookClass.INCOMING);
                    if (incoming!=null && incoming.hook()!=null && incoming.hook().equals(cleojar.incoming())) {
                        incoming.jar(cleojar);
                    }
                    APIHook outgoing = hooks().get(APIHookClass.OUTGOING);
                    if (outgoing!=null && outgoing.hook()!=null && outgoing.hook().equals(cleojar.outgoing())) {
                        outgoing.jar(cleojar);
                    }
                    APIHook extended = hooks().get(APIHookClass.USERGROUPS);
                    if (extended!=null && extended.hook()!=null && extended.hook().equals(cleojar.usergroups())) {
                        extended.jar(cleojar);
                    }
                }
            }
        }
        return this;
    }
    /**
     * Returns an index of dependencies for each jar referenced by the
     * configuration (URI scheme classpaths and additional jars).  Only
     * "cleojars" will have dependencies, which include classpath and
     * additional references from the manifest.
     * @return
     */
    public Map<MvnJar,Set<MvnJar>> dependencies() {
        Map<MvnJar,Set<MvnJar>> map  = new HashMap<>();
        Deque<MvnJar>           todo = new ArrayDeque<>();
        for (MvnJar jar : jars()) {
            todo.add(jar);
        }
        while (!todo.isEmpty()) {
            MvnJar jarfile = todo.pop();
            if (jarfile.exists()) {
                // calculate the set of resolved dependencies, exclusive of "this"
                Set<MvnJar> dependencies = new HashSet<>();
                for (MvnJar depends : jarfile.dependencies()) {
                    if (!depends.equals(jarfile)) {
                        dependencies.add(depends);
                    }
                }
                // if there are some, enter them into the map
                if (!dependencies.isEmpty()) {
                    // merge already known dependencies
                    Set<MvnJar> keys = map.keySet();
                    keys.retainAll(dependencies);
                    for (MvnJar key : keys) {
                        dependencies.addAll(map.get(key));
                    }
                    // enter or extend the current dependency map for this jar
                    if (!map.containsKey(jarfile)) {
                        map.put(jarfile, dependencies);
                    } else {
                        map.get(jarfile).addAll(dependencies);
                        dependencies = map.get(jarfile);
                    }
                    // for reverse dependencies on this jar, queue another pass
                    for (Map.Entry<MvnJar,Set<MvnJar>> e : map.entrySet()) {
                        if (e.getValue().contains(jarfile) &&
                            !e.getValue().containsAll(dependencies)) {
                            e.getValue().addAll(dependencies);
                            todo.add(e.getKey());
                        }
                    }
                }
            }
        }
        return map;
    }

    /**
     * @return a rendering of all the entire SPI configuration.
     */
    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        if (schemes()!=null && !schemes().isEmpty()) {
            s.append(S.join("\n", schemes(), new Formatter() {
                @Override public String format(Map.Entry<?,?> entry) {
                    return entry.getValue().toString();
                }
            })).append('\n');
        }
        if (authenticators()!=null && !authenticators().isEmpty()) {
            s.append(S.join("\n", authenticators(), new Formatter() {
                @Override public String format(Map.Entry<?,?> entry) {
                    return entry.getValue().toString();
                }
            })).append('\n');
        }
        if (additional()!=null) {
            s.append(S.all("additional=",S.join("\n  ",additional()))).append('\n');
        }
        for (APIHookClass hook : APIHookClass.values()) {
            APIHook apihook = hooks().get(hook);
            if (apihook!=null) {
                s.append(hook.name().toLowerCase()).append('=').append(apihook.toString()).append('\n');
            }
        }
        return s.toString();
    }
}
