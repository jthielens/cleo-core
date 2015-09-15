package com.cleo.labs.api;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;

import com.cleo.labs.api.constant.HostSource;
import com.cleo.labs.api.constant.HostType;
import com.cleo.labs.api.constant.Mode;
import com.cleo.labs.api.constant.PathType;
import com.cleo.labs.api.constant.Product;
import com.cleo.labs.api.constant.Protocol;
import com.cleo.lexicom.LexiComException;
import com.cleo.lexicom.beans.Options;
import com.cleo.lexicom.certmgr.external.ICertManagerRunTime;
import com.cleo.lexicom.external.ILexiCom;
import com.cleo.lexicom.external.ILicense;
import com.cleo.lexicom.external.ILicenser;
import com.cleo.lexicom.external.IMailboxController;
import com.cleo.lexicom.external.ISchedule;
import com.cleo.lexicom.external.LexiComFactory;
import com.cleo.lexicom.external.LexiComLogListener;

public class LexiCom {
    private static Product  product;
    private static Mode     mode;
    private static File     home;
    private static ILexiCom lexicom;

    public static Product getProduct() {
        return product;
    }
    public static Mode getMode() {
        return mode;
    }
    public static File getHome() {
        return home;
    }
    public static void setMode(Mode mode) {
        if (connected()) {
            if (mode!=LexiCom.mode) {
                throw new IllegalStateException("mode cannot be changed once connected");
            }
        }
        LexiCom.mode = mode;
    }
    private static Product getProduct(File home) {
        for (Product product : Product.values()) {
            if (new File(home, product.proof).exists()) {
                return product;
            }
        }
        return null;
    }
    public static void setHome(File home) {
        if (connected()) {
            if (home!=LexiCom.home) {
                throw new IllegalStateException("home cannot be changed once connected");
            }
        } else {
            product = getProduct(home);
            if (product==null) {
                throw new IllegalArgumentException("no VersaLex found at "+home);
            }
            LexiCom.home = home;
        }
    }
    public static ILexiCom getLexiCom() throws Exception {
        connect();
        return lexicom;
    }

    private LexiCom() {}

    public static boolean connected() {
        return lexicom!=null;
    }

    public static void connect() throws Exception {
        if (!connected()) {
            if (product==null || mode==null || home==null) {
                lexicom = LexiComFactory.getCurrentInstance();
                home    = lexicom.getAbsolute(new File("."));
                product = getProduct(home);
            } else {
                lexicom = LexiComFactory.getVersaLex(product.id, home.getAbsolutePath(), mode.id);
            }
        }
    }

    public static void disconnect() throws Exception {
        if (lexicom != null) {
            lexicom.close();
            lexicom = null;
        }
    }

    public static File getAbsolute(File file) throws Exception {
        return lexicom.getAbsolute(file);
    }
    public static String getAbsolute(String path) throws Exception {
        return lexicom.getAbsolute(path);
    }

    public static Path[] list(PathType type, Path path) throws Exception {
        connect();
        String[] names = lexicom.list(type.id, path.getPath());
        Path[] paths = new Path[names.length];
        for (int i=0; i<names.length; i++) {
            paths[i] = path.getChild(type, names[i]);
        }
        return paths;
    }

    public static Host[] getHosts() throws Exception {
        Path[]   paths = list(PathType.HOST, new Path());
        Host[]   hosts = new Host[paths.length];
        for (int i=0; i<paths.length; i++) {
            hosts[i] = new Host(paths[i]);
            hosts[i].setSource(HostSource.LIST);
        }
        return hosts;
    }

    public static Host getHost(String alias) throws Exception {
        Path test = new Path(PathType.HOST, alias);
        if (exists(test)) {
            Host host = new Host(test);
            host.setSource(HostSource.GET);
            return host;
        }
        return null;
    }

    public static Host findLocalHost(HostType type, String root, String folder) throws Exception {
        if (folder==null) folder = "";
        for (Host h : getHosts()) {
            if (h.getHostType()==type &&
                (root==null || h.getSingleProperty("Ftprootpath").equals(root))) {
                String hostFolder = h.getSingleProperty("folder");
                if (hostFolder==null) hostFolder = "";
                if (hostFolder.equals(folder)) {
                    return h;
                }
            }
        }
        return null;
    }

    public static Host[] findHosts(HostType type, String address, int port) throws Exception {
        ArrayList<Host> hosts = new ArrayList<Host>();
        for (Host h : getHosts()) {
            if (h.getHostType()==type &&
                h.matchPropertyIgnoreCase("Address", address) &&
                (port<0 || h.matchProperty("Port", String.valueOf(port)))) {
                h.setSource(HostSource.FIND);
                hosts.add(h);
            }
        }
        if (hosts.isEmpty()) {
            return null;
        } else {
            return hosts.toArray(new Host[hosts.size()]);
        }
    }

    public static Document getHostDocument(Path path) throws Exception {
        connect();
        return lexicom.getDocument(path.getPath()[0]);
    }

    public static boolean exists(Path path) throws Exception {
        try {
            hasProperty(path, "alias");
            return true;
        } catch (LexiComException e) {
            if (e.getMessage().matches("Element path '.*' not found")) {
                return false;
            }
            throw (e);
        }
    }

    private static final Pattern KEY_INDEX = Pattern.compile("(\\.?\\w+)(?:\\[(.+)\\]|\\.(.+))?");
    private static class Property {
        public String name;
        public String index          = null;
        public String separator      = null;
        public boolean isIndexed     = false;
        public boolean isMultiValued = false;
        public Property (String property) {
            Matcher m = KEY_INDEX.matcher(property);
            m.matches();
            this.name      = m.group(1);
            this.index     = m.group(2)!=null ? m.group(2) : m.group(3);
            this.isIndexed = this.index!=null;
            if (this.name.equalsIgnoreCase("Advanced") || this.name.equalsIgnoreCase("Contenttypedirs")) {
                this.separator = "=";
                this.isMultiValued = true;
            } else if (this.name.equalsIgnoreCase("Syntax") || this.name.equalsIgnoreCase("Header")) {
                this.separator = " ";
                this.isMultiValued = true;
            } else if (this.name.equalsIgnoreCase("id")) {
                this.isMultiValued = true;
            } else if (this.name.startsWith(".")) {
                // ILexiCom handles XML attribute properties the same as nest element ones
                this.name = property.substring(1);
            } else {
                this.name = property;
            }
        }
    }
    private static class Attribute implements Comparable<Attribute> {
        public String name;
        public String value;
        public int    index;
        public Attribute(String attribute, Property property, int index) {
            String[] pair = attribute.split(property.separator, 2);
            this.name  = pair[0];
            this.value = pair.length==2 ? pair[1] : null;
            this.index = index;
        }
        public Attribute(String name, String value, int index) {
            this.name  = name;
            this.value = value;
            this.index = index;
        }
        @Override
        public int compareTo(Attribute arg0) {
            return this.index-arg0.index;
        }
    }
    private static Map<String,Attribute> valuesToIndex(Property property, String[] values) {
        HashMap<String,Attribute> index = new HashMap<String,Attribute>(values.length);
        for (String value : values) {
            Attribute a = new Attribute(value, property, index.size());
            index.put(a.name.toLowerCase(), a);
        }
        return index;
    }
    private static String[] indexToValues(Property property, Map<String,Attribute> index) {
        Attribute[] attrs = index.values().toArray(new Attribute[index.size()]);
        Arrays.sort(attrs);
        String[] values = new String[index.size()];
        for (int i=0; i<values.length; i++) {
            if (attrs[i].value!=null) {
                values[i] = attrs[i].name+property.separator+attrs[i].value;
            } else {
                values[i] = attrs[i].name;
            }
        }
        return values;
    }
    @SuppressWarnings("unused")
    private static String[] editProperties(Property property, String[] original, String[] edits) {
        // need special handling for multi-valued advanced properties
        // first, build a dictionary of the existing attributes
        Map<String,Attribute> index = valuesToIndex(property, original);
        int counter = index.size();
        // now update the list with edits
        //    attr=value is an addition or replacement
        //    attr=      is too, with a value of ""
        //    attr       means remove the attribute altogether (noop if it doesn't exist)
        for (String edit : edits) {
            String[]  pair     = edit.split(property.separator, 2);
            Attribute existing = index.get(pair[0].toLowerCase());
            if (existing!=null) {
                // edit, either replace or remove the value
                if (pair.length==2) {
                    existing.value = pair[1];
                } else {
                    index.remove(pair[0].toLowerCase());
                }
            } else if (pair.length==2) {
                // add a new one at the end
                Attribute a = new Attribute(edit, property, counter++);
                index.put(a.name.toLowerCase(), a);
            }
        }
        // Now turn it back into a list
        return indexToValues(property, index);
    }
    public static boolean hasProperty(Path path, String property) throws Exception {
        connect();
        return lexicom.hasProperty(path.getType().id, path.getPath(), property);
    }
    public static String[] getProperty(Path path, String name) throws Exception {
        connect();
        Property property = new Property(name);
        if (property.name.equalsIgnoreCase("id")) {
            return lexicom.getID(path.getType().id, path.getPath());
        } else if (property.isIndexed) {
            String[] values = lexicom.getProperty(path.getType().id, path.getPath(), property.name);
            Map<String,Attribute> index = valuesToIndex(property, values);
            Attribute a = index.get(property.index.toLowerCase());
            return a==null ? null : new String[] {a.value};
        } else {
            return lexicom.getProperty(path.getType().id, path.getPath(), property.name);
        }
    }
    public static String getSingleProperty(Path path, String property) throws Exception {
        String[] values = getProperty(path, property);
        if (values!=null) return values[0];
        return null;
    }
    public static void setProperty(Path path, String name, String value) throws Exception {
        connect();
        Property property = new Property(name);
        if (property.isIndexed) {
            // special handling for well-known multi-valued properties to harmonize with Util.xml2map/map2xml
            // set(advanced.index, value)         => set(advanced,        {index=value})
            // set(syntax[index], value)          => set(syntax,          {index value})
            // set(header[index], value)          => set(header,          {index value})
            // set(contenttypedirs[index], value) => set(contenttypedirs, {index=value})
            String[] values = lexicom.getProperty(path.getType().id, path.getPath(), property.name);
            Map<String,Attribute> index = valuesToIndex(property, values);
            Attribute a = index.get(property.index.toLowerCase());
            if (a==null && value!=null) {
                // add new
                a = new Attribute(property.index, value, index.size());
                index.put(property.index, a);
            } else if (a!=null && value!=null){
                // overwrite
                a.value = value;
                index.put(a.name.toLowerCase(), a);
            } else if (a!=null && value==null){
                // delete
                index.remove(a.name.toLowerCase());
            } else {
                // nothing to do
                return;
            }
            lexicom.setProperty(path.getType().id, path.getPath(), property.name, indexToValues(property, index));
        } else if (property.isMultiValued) {
            setProperty(path, property.name, new String[] {value});
        } else {
            lexicom.setProperty(path.getType().id, path.getPath(), property.name, value);
        }
    }
    public static void setProperty(Path path, String property, String[] value) throws Exception {
        connect();
        if (property.equalsIgnoreCase("id")) {
            // use special ID manipulators instead of get/setProperty
            if (path.getType()==PathType.MAILBOX) {
                if (value.length>1) {
                    throw new Exception("Multiple IDs supported for HOST only");
                }
                lexicom.clearID(path.getType().id, path.getPath());
                if (value.length==1) {
                    lexicom.addID(path.getType().id, path.getPath(), value[0]);
                }
            } else if (path.getType()==PathType.HOST){ 
                if (value.length==0) {
                    lexicom.clearID(path.getType().id,  path.getPath());
                } else {
                    for (String id : value) {
                        lexicom.addID(path.getType().id, path.getPath(), id);
                    }
                }
            } else {
                throw new Exception("ID supported for HOST and MAILBOX only");
            }
        } else {
            lexicom.setProperty(path.getType().id, path.getPath(), property, value);
        }
    }

    public static Path lookup(PathType type, String id) throws Exception {
        connect();
        String[] path = lexicom.getPath(type.id, id);
        if (path==null) {
            return null;
        } else {
            return new Path(type, path);
        }
    }

    public static void save(Path path) throws Exception {
        connect();
        if (path.getType() == PathType.HOST) {
            lexicom.save(path.getAlias());
        } else {
            lexicom.save(path.getType().id, path.getPath());
        }
    }

    public static void rename(Path path, String alias) throws Exception {
        connect();
        lexicom.rename(path.getType().id, path.getPath(), alias);
    }

    public static void remove(Path path) throws Exception {
        connect();
        lexicom.remove(path.getType().id, path.getPath());
    }

    public static Path create(Path path) throws Exception {
        connect();
        Path parent = path.getParent();
        String alias = lexicom.create(path.getType().id, parent.getPath(), path.getAlias(), false);
        return parent.getChild(path.getType(), alias);
    }

    public static Path clone(Path path, String alias) throws Exception {
        connect();
        alias = lexicom.clone(path.getType().id, path.getPath(), alias, false);
        return path.getParent().getChild(path.getType(), alias);
    }

    public static Host activateHost (HostType type, String alias) throws Exception {
        connect();
        String hostName = lexicom.activateHost(type.template, alias, false);
        Host host = new Host(hostName);
        host.setSource(HostSource.ACTIVATE);
        return host;
    }

    public static IMailboxController getMailboxController (Path mailbox) throws Exception {
        connect();
        return lexicom.getMailboxController(mailbox.getPath());
    }

    public static ICertManagerRunTime getCertManager() throws Exception {
        connect();
        return lexicom.getCertManager();
    }

    public static ISchedule getSchedule() throws Exception {
        connect();
        return lexicom.getSchedule();
    }

    public static boolean isMultipleIDsAllowed(Path path) throws Exception {
        connect();
        return lexicom.isMultipleIDsAllowed(path.getType().id, path.getPath());
    }

    public static void setMultipleIDsAllowed(Path path, boolean allowed) throws Exception {
        connect();
        lexicom.setMultipleIDsAllowed(path.getType().id, path.getPath(), allowed);
    }

    public static boolean isLocal(Path host) throws Exception {
        connect();
        return lexicom.isLocal(host.getPath()[0]);
    }

    public static Protocol getHostProtocol(Path host) throws Exception {
        connect();
        return Protocol.valueOf(lexicom.getHostProtocol(host.getPath()[0]));
    }

    public static com.cleo.lexicom.external.HostType[] listHostTypes() throws Exception {
        connect();
        return lexicom.listHostTypes();
    }

    public static void addLogListener(LexiComLogListener listener, Path path) throws Exception {
        connect();
        lexicom.addLogListener(listener, path.getPath(), path.getType().id);
    }

    public static void removeLogListener(LexiComLogListener listener, Path path) throws Exception {
        connect();
        lexicom.removeLogListener(listener, path.getPath(), path.getType().id);
    }

    public static Options getOptions() throws Exception {
        connect();
        return lexicom.getOptions();
    }

    public static ILicense getLicense() throws Exception {
        connect();
        return lexicom.getLicense();
    }

    public static ILicenser getLicenser() throws Exception {
        connect();
        return lexicom.getLicenser();
    }

    public static void startService() throws Exception {
        connect();
        lexicom.startService();
    }

    static final String BEAN_PREFIX = "com.cleo.lexicom.beans";
    public static String encode(String s) throws Exception {
        connect();
        if (s.startsWith(BEAN_PREFIX+".")) {
            s = new StringBuilder(s.substring(BEAN_PREFIX.length())).reverse().toString();
        }
        return lexicom.encode(s);
    }
    public static String decode(String s) throws Exception {
        connect();
        String decoded = lexicom.decode(s);
        if (decoded.matches("[a-zA-Z0-9\\.\\$]+\\.")) {
            decoded = BEAN_PREFIX+
                    new StringBuilder(decoded).reverse().toString();
        }
        return decoded;
    }
    public static String encrypt(String s) throws Exception {
        connect();
        return lexicom.encrypt(s);
    }
    public static String decrypt(String s) throws Exception {
        connect();
        return lexicom.decrypt(s);
    }

    public static String getVersion() throws Exception {
        connect();
        return lexicom.getVersion();
    }
}
