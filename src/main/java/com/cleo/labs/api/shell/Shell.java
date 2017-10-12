package com.cleo.labs.api.shell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.rmi.RemoteException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.DatatypeConverter;

import com.cleo.labs.api.Action;
import com.cleo.labs.api.Defaults;
import com.cleo.labs.api.Host;
import com.cleo.labs.api.Item;
import com.cleo.labs.api.LexiCom;
import com.cleo.labs.api.Mailbox;
import com.cleo.labs.api.Path;
import com.cleo.labs.api.Schedule;
import com.cleo.labs.api.URL;
import com.cleo.labs.api.User;
import com.cleo.labs.api.constant.HostSource;
import com.cleo.labs.api.constant.HostType;
import com.cleo.labs.api.constant.Mode;
import com.cleo.labs.api.constant.Packaging;
import com.cleo.labs.api.constant.PathType;
import com.cleo.labs.api.constant.Protocol;
import com.cleo.labs.api.exception.URLResolutionException;
import com.cleo.labs.api.shell.CleoSpiConfig.APIHookClass;
import com.cleo.labs.util.LDAP;
import com.cleo.labs.util.S;
import com.cleo.labs.util.S.Inspector;
import com.cleo.labs.util.X;
import com.cleo.labs.util.repl.REPL;
import com.cleo.labs.util.repl.annotation.Command;
import com.cleo.labs.util.repl.annotation.Option;
import com.cleo.lexicom.beans.LexBean;
import com.cleo.lexicom.beans.Options;
import com.cleo.lexicom.beans.Options.DBConnection;
import com.cleo.lexicom.certmgr.ImportedCertificate;
import com.cleo.lexicom.certmgr.external.ICertManagerRunTime;
import com.cleo.lexicom.external.DirectoryEntry;
import com.cleo.lexicom.external.ILicense;
import com.cleo.lexicom.external.ILicenser;
import com.cleo.lexicom.external.ISchedule;
import com.cleo.lexicom.external.LexiComLogEvent;
import com.cleo.lexicom.external.LexiComLogListener;
import com.cleo.lexicom.external.RegistrationInfo;
import com.cleo.security.encryption.ConfigEncryption;

public class Shell extends REPL {
    LexiComLogListener reporter = new LexiComLogListener() {
        public void log(LexiComLogEvent e) {
            if (!e.getEvent().getNodeName().equals("Stop")) {
                report(e.getMessage());
            }
        }
    };

    @Option(name="h", args="home", comment="installation directory")
    public void home_option(String arg) {
        LexiCom.setHome(new File(arg));
        URI.setHome(LexiCom.getHome());
    }
    
    @Option(name="p", args="product", comment="H | VLT | LC")
    public void product_option(String arg) {
        error("product is now automatically detected: "+arg);
    }
    
    @Option(name="m", args="mode", comment="STANDALONE | DISTRIBUTED")
    public void mode_option(String arg) {
        try {
            LexiCom.setMode(Mode.valueOf(arg.toUpperCase()));
        } catch (Exception e) {
            error("unrecognized mode: "+arg);
        }
    }

    @Override
    public void disconnect() {
        try {
            vldb.disconnect();
            h2db.disconnect();
            LexiCom.disconnect();
        } catch (Exception e) {
            error("error disconnecting", e);
        }
    }

    @Command(name="echo", args="message", comment="print message")
    public void echo(String...argv) {
        print(S.join(" ", argv));
    }

    /**
     * Canned formatter that produces a key=value string, quoting both key and
     * value independently, i.e. qq(key)=qq(value).
     */
    private static S.Formatter qqequals = new S.Formatter () {
        public String format(Map.Entry<?,?> entry) {
            return qq(entry.getKey().toString())+"="+qq(entry.getValue().toString());
        }
    };

    /**
     * Quotes a String of the form a[=b], quoting a and b independently.
     * @param s a (possibly null) String of the form a[=b]
     * @return qq(a)[=qq(b)], or "null"
     */
    static String qqequals(String s) {
        if (s==null) return "null";
        return S.join("=", qq(s.split("=", 2)));
    }

    /**
     * Quotes an array of Strings of the form a[=b].
     * @param ss a (possibly null) array of Strings
     * @return a formatted result, or null
     */
    static String[] qqequals(String[] ss) {
        if (ss==null) return null;
        String[] result = new String[ss.length];
        for (int i=0; i<ss.length; i++) {
            result[i] = qqequals(ss[i]);
        }
        return result;
    }

    @Command(name="quote", args="message", comment="print message")
    public void quote(String...argv) {
        print(S.join(" ", qq(argv)));
    }

    private void demo_registration(RegistrationInfo reg) {
        reg.setFirstName("Cleo");
        reg.setLastName("Demonstration");
        reg.setTitle("Demonstration");
        reg.setCompany("Cleo");
        reg.setAddress1("4203 Galleria Dr");
        reg.setCity("Loves Park");
        reg.setState("IL");
        reg.setZip("61111");
        reg.setCountry("US");
        reg.setEmail("sales@cleo.com");
        reg.setPhone("+1.815.654.8110");
    }

    private void report_registration(RegistrationInfo reg) {
        StringBuilder sb = new StringBuilder();
        String        s;
        sb.append(reg.getFirstName());
        s = reg.getLastName();
        if (s.length()>0) {
            if (sb.length()>0) sb.append(" ");
            sb.append(s);
        }
        s = reg.getTitle();
        if (s.length()>0) {
            if (sb.length()>0) sb.append(", ");
            sb.append(s);
        }
        s = reg.getCompany();
        if (s.length()>0) {
            if (sb.length()>0) sb.append(", ");
            sb.append(s);
        }
        if (sb.length()>0) {
            report(sb.toString());
            sb.setLength(0);
        }
        s = reg.getAddress1();
        if (s.length()>0) report (s);
        s = reg.getAddress2();
        if (s.length()>0) report (s);
        sb.append(reg.getCity());
        s = reg.getState();
        if (s.length()>0) {
            if (sb.length()>0) sb.append(", ");
            sb.append(s);
        }
        s = reg.getZip();
        if (s.length()>0) {
            if (sb.length()>0) sb.append("  ");
            sb.append(s);
        }
        if (sb.length()>0) {
            report(sb.toString());
            sb.setLength(0);
        }
        sb.append(reg.getPhone());
        s = reg.getExtension();
        if (s.length()>0) {
            if (sb.length()>0) sb.append(" x");
            sb.append(s);
        }
        if (sb.length()>0) {
            report(sb.toString());
            sb.setLength(0);
        }
        s = reg.getEmail();
        if (s.length()>0) report (s);
    }

    private void report_license(ILicense license) {
        try {
            report(Util.licensed_product(license.getProduct())
                   +" ["+license.getSerialNumber()+"]"
                   +" on "+Util.licensed_hosts(license.getAllowedHosts())
                   +" "+Util.licensed_until(license));
            report("  platforms: "+Util.licensed_platform(license.getPlatform()));
            report("  features:  "+Util.licensed_features(license));
        } catch (RemoteException e) {
            error(e);
        }
    }

    @Command(name="report", args="[serial]", comment="license or serial # information")
    public void report_command(String...argv) {
        if (argv.length==0) {
            try {
                report("attempting to retrieve license");
                report_license(LexiCom.getLicense());
            } catch (Exception e) {
                error("license retrieval failed", e);
            }
        } else {
            if (LexiCom.getMode() != Mode.STANDALONE) {
                report("switching to -m STANDALONE");
                LexiCom.setMode(Mode.STANDALONE);
            }
            for (String serial : argv) {
                try {
                    report("attempting to retrieve registration for "+serial);
                    ILicenser license = LexiCom.getLicenser();
                    RegistrationInfo reg = license.registrationQuery(serial);
                    report_registration(reg);
                    Util.report_bean(this, reg);
                } catch (Exception e) {
                    error("report failed", e);
                }
            }
        }
    }

    @Command(name="register", args="serial|file", comment="register license")
    public void register_command(String...argv) {
        if (argv.length==1) {
            try {
                if (LexiCom.getMode() != Mode.STANDALONE) {
                    report("switching to -m STANDALONE");
                    LexiCom.setMode(Mode.STANDALONE);
                }
                if (argv[0].matches("\\w{6}-\\w{6}")) {
                    String serial = argv[0];
                    report("attempting to retrieve registration for "+serial);
                    ILicenser license = LexiCom.getLicenser();
                    RegistrationInfo current_reg = license.registrationQuery(serial);
                    demo_registration(current_reg);
                    report_registration(current_reg);
                    license.register(current_reg);
                    report(serial+" registered, retreiving license information");
                } else {
                    String fn = argv[0];
                    ILicenser license = LexiCom.getLicenser();
                    @SuppressWarnings("unused")
                    ILicense content = license.licenseFile(fn, true);
                }
                report_license(LexiCom.getLicense());
            } catch (Exception e) {
                error("register failed", e);
            }
        } else {
            error("register requires a single serial number xxxxxx-yyyyyy or a filename");
        }
    }

    @Command(name="unregister", comment="unregister license")
    public void unregister_command(String...argv) {
        try {
            if (LexiCom.getMode() != Mode.STANDALONE) {
                report("switching to -m STANDALONE");
                LexiCom.setMode(Mode.STANDALONE);
            }
            ILicenser license = LexiCom.getLicenser();
            license.unregister();
        } catch (Exception e) {
            error("unregister failed", e);
        }
    }

    @Command(name="close", comment="close LexiCom session")
    public void close_command(String...argv) {
        try {
            LexiCom.disconnect();
        } catch (Exception e) {
            error("close failed", e);
        }
    }

    @Command(name="list_types", comment="list host types")
    public void list_types() throws Exception {
        com.cleo.lexicom.external.HostType[] types = LexiCom.listHostTypes();
        String[] columns = new String[] {"Type", "Protocol", "Packaging", "Local"};
        String[][] values = new String[types.length][4];
        for (int i=0; i<types.length; i++) {
            com.cleo.lexicom.external.HostType t = types[i];
            values[i][0] = t.getName();
            values[i][1] = Protocol.valueOf(t.getHostProtocol()).name();
            values[i][2] = Packaging.valueOf(t.getMailboxPackaging()).name();
            values[i][3] = String.valueOf(t.isLocal());
        }
        report(columns, values);
    }
    
    @SuppressWarnings("unused")
    private class OldCrypt implements LDAP.Crypt {
        @Override public String encrypt(String s) throws Exception {
            return LexiCom.encrypt(s);
        }
        @Override public String decrypt(String s) throws Exception {
            if (s.matches("#.*#")) {
                StringBuffer sb = new StringBuffer(s.subSequence(1, s.length()-1));
                while (sb.length()%4 > 0) sb.append('=');
                s = LexiCom.decrypt(sb.toString());
            } else if (s.startsWith("vlenc:") || s.startsWith("*")) {
                s = LexBean.vlDecrypt((ConfigEncryption) null, s);
            }
            return s;
        }
    }

    private class VLCrypt implements LDAP.Crypt {
        @Override public String encrypt(String s) throws Exception {
            return vlenc(s);
        }
        @Override public String decrypt(String s) throws Exception {
            return vldec(s);
        }
    }

    private class Reporter implements URI.Reporter {
        private Shell shell;
        @Override public void report(String s) {
            shell.report(s);
        }
        public Reporter(Shell shell) {
            this.shell = shell;
        }
    }

    private LDAP get_ldap() throws Exception {
        return new LDAP(X.submap(read_xml_file("Users.xml").map, "Ldapserver"),
                        this.new VLCrypt());
    }

    private class DB extends com.cleo.labs.util.DB {
        private DBOptions                       options = null;
        private Map<String,Map<String,Integer>> tables  = null;

        public DB() {
            super();
        }
        public DB(String connection, String user, String password) throws SQLException {
            super(connection, user, password);
        }

        @Override
        public void connect() throws SQLException {
            if (!connected()) {
                if (options==null) {
                    try {
                        Options o = LexiCom.getOptions();
                        DBConnection c = o.getTransferLoggingDBConnection();
                        if (c!=null) {
                            options = new DBOptions(c);
                        }
                    } catch (Exception e) {
                        // error will fall out
                    }
                }
                if (options==null) {
                    throw new SQLException("use db set or db find first");
                } else {
                    connect(options.connection, options.user, options.password);
                }
                if (tables==null || tables.isEmpty()) {
                    tables = tables();
                }
            }
        }

        public DBOptions getOptions() {
            return this.options;
        }
        public DB setOptions(DBOptions options) {
            this.options = options;
            return this;
        }

        /**
         * Converts a table name from case-insensitive to case-sensitive.
         * @param table the case-insensitive table name
         * @return the tble name in its proper case-sensitive form
         * @throws Exception
         */
        public String table(String table) throws Exception {
            connect();
            if (tables!=null) {
                for (String t : tables.keySet()) {
                    if (t.equalsIgnoreCase(table)) {
                        return t;
                    }
                }
            }
            return null;
        }
        /**
         * Converts table.column from case-insensitive to case-sensitive.
         * @param table the table
         * @param column the case-insensitive column name
         * @return the column name in its proper case-sensitive form
         */
        public String column(String table, String column) throws SQLException {
            connect();
            if (tables!=null) {
                Map<String,Integer> columns = tables.get(table);
                for (String c : columns.keySet()) {
                    if (c.equalsIgnoreCase(column)) {
                        return c;
                    }
                }
            }
            return null;
        }
        /**
         * Returns true if table.column has a string type.
         * @param table
         * @param column
         * @return true if table.column has a string type (VARCHAR)
         */
        public boolean string(String table, String column) throws SQLException {
            connect();
            if (tables!=null && tables.get(table)!=null && tables.get(table).get(column)!=null) {
                int type = tables.get(table).get(column);
                return type==Types.VARCHAR;
            }
            return false;
        }
    }
    private DB vldb = new DB();
    private DB h2db = new DB().setOptions(new DBOptions("h2:viewonly:VersaLex@127.0.0.1:9092/data/hibernate"));
    private VLNav get_vlnav() throws Exception {
        return new VLNav(vldb);
    }
    @Command(name="list_certs", comment="list user certificate aliases")
    public void list_certs() throws Exception {
        ICertManagerRunTime certManager = LexiCom.getCertManager();
        for (@SuppressWarnings("unchecked")
             Enumeration<String> e=certManager.getUserAliases();
             e.hasMoreElements();) {
            String alias = e.nextElement();
            report(alias);
        }
    }

    @SuppressWarnings("unchecked")
    @Command(name="list_cas", comment="list certificate authority aliases")
    public void list_cas() throws Exception {
        ICertManagerRunTime certManager = LexiCom.getCertManager();
        String certPath = LexiCom.getAbsolute("certs");
        Map<String,String> cas = new TreeMap<String,String>();
        Map<String,String> caseMap = new HashMap<String,String>();
        for (File ca : new File(certPath).listFiles()) {
            if (!ca.isFile()) continue;
            caseMap.put(ca.getName().toLowerCase(), ca.getName());
            /*
            report(ca.getName().toLowerCase()+" -> "+ca.getName());
            try {
                Collection<X509Certificate> certs = certManager.readCert(ca);
                for (X509Certificate x : certs) {
                    cas.put(certManager.getFriendlyName(x), "certs/"+ca.getName());
                }
            } catch (Exception e) {
                error("error listing cert", e);
            }
            */
        }
        //report(new String[] {"CA", "File"}, S.invert(cas));
        /* this way doesn't work because getCAFiles seems to lowercase everything */
        for (Enumeration<String> e=certManager.getCAFiles(); e.hasMoreElements();) {
            String ca = e.nextElement();
            if (ca.endsWith("/")) {
                String fn = ca.replaceAll("/$", "");
                if (caseMap.containsKey(fn)) fn = caseMap.get(fn);
                Collection<X509Certificate> certs = certManager.readCert(certPath+"/"+fn);
                for (X509Certificate x : certs) {
                    cas.put(certManager.getFriendlyName(x), ca);
                }
            } else {
                X509Certificate x = certManager.getCACertificate(ca);
                cas.put(certManager.getFriendlyName(x), ca);
            }
        }
        report(new String[] {"CA", "Id"}, S.invert(cas));
    }
    @Command(name="trust", args="file...", comment="trust CA(s)")
    public void trust(String...argv) {
        ICertManagerRunTime certManager;
        try {
            certManager = LexiCom.getCertManager();
        } catch (Exception e) {
            error("error getting cert manager", e);
            return;
        }
        for (String arg : argv) {
            File f = new File(arg);
            if (!f.isFile()) {
                error("can't read file: "+arg);
            } else {
                try {
                    @SuppressWarnings("unchecked")
                    Collection<X509Certificate> certs = certManager.readCert(f);
                    certManager.trustCACert(certs.toArray(new X509Certificate[certs.size()]));
                    report("CA \""+arg+"\" trusted");
                } catch (Exception e) {
                    error("error reading "+arg, e);
                }
            }
        }
    }
    @Command(name="import_key", args="alias key cert password", comment="import key")
    public void import_key(String alias, String key, String cert, String password) throws Exception {
        File keyf  = new File(key);
        File certf = new File(cert);
        alias = alias.toUpperCase();
        if (!keyf.isFile()) {
            error("key file not found: "+key);
        } else if (!certf.isFile()) {
            error("cert file not found: "+cert);
        } else {
            ICertManagerRunTime certManager = LexiCom.getCertManager();
            certManager.importUserCertKey(alias, certf, keyf, password, true/*replace*/, false/*addPassword*/);
            report("key \""+alias+"\" imported");
        }
    }
    @Command(name="import_ssh", args="alias key", comment="import SSH public key")
    public void import_ssh(String alias, String key) throws Exception {
        ImportedCertificate imported = new ImportedCertificate(alias, key);
        ICertManagerRunTime certManager = LexiCom.getCertManager();
        certManager.importCaStoreCert(alias, imported.getX509(), false);
    }

    @Command(name="uri_parse", args="uri", comment="parse URI")
    public void uri_parse(String s) {
        try {
            java.net.URI u = new java.net.URI(s);
            Util.report_bean(this, u);
        } catch (Exception e) {
            error("error parsing uri", e);
        }
    }
    @Command(name="uri_list", args="[id ...]", comment="list URI drivers")
    public void uri_list(String...argv) {
        try{
            CleoSpiConfig config = new CleoSpiConfig(LexiCom.getHome());
            if (argv.length==0) {
                // dump entire configuration
                report(config.toString());
                Map<MvnJar,Set<MvnJar>> dependencies = config.dependencies();
                if (!dependencies.isEmpty()) {
                    report("dependencies");
                    for (Map.Entry<MvnJar,Set<MvnJar>> e : dependencies.entrySet()) {
                        report("  "+e.getKey().jar());
                        for (MvnJar depend : e.getValue()) {
                            report("    "+depend.jar());
                        }
                    }
                }
                Set<MvnJar> referenced = config.referenced_jars();
                if (!referenced.isEmpty()) {
                    report("referenced");
                    for (MvnJar mj : referenced) {
                        report("  "+mj.fileName());
                    }
                }
                Set<MvnJar> unreferenced = config.unreferenced_jars();
                if (!unreferenced.isEmpty()) {
                    report("unreferenced");
                    for (MvnJar mj : unreferenced) {
                        report("  "+mj.fileName());
                    }
                }
                Set<MvnJar> missing = config.missing_jars();
                if (!missing.isEmpty()) {
                    report("missing");
                    for (MvnJar mj : missing) {
                        report("  "+mj.fileName());
                    }
                }
            } else {
                // report on specific jars
                for (String arg : argv) {
                    MvnJar  mj = config.factory().get(arg);
                    report(mj.toString());
                }
            }
        } catch (Exception e) {
            error("failed", e);
        }
    }
    @Command(name="uri_install", args="[no] file.jar ...", min=1, comment="install URI drivers")
    public void uri_install(String...argv) throws Exception {
        URI scheme = null;
        if (argv.length>0 && argv[0].endsWith(":")) {
            // usage: uri install scheme: property=value... jar...
            // TODO: deprecate this mode of install
            scheme = URI.inspectJars(argv);
            if (scheme!=null) {
                scheme.install();
                report(scheme.scheme, scheme.toStrings());
            } else {
                error("invalid URI: "+S.join(" ", argv));
            }
        } else if (argv.length>0) {
            // usage: uri install [no] jar-with-manifest...
            boolean preflight = argv[0].equalsIgnoreCase("no");
            if (preflight) {
                String[] rest = new String[argv.length-1];
                System.arraycopy(argv, 1, rest, 0, rest.length);
                argv = rest;
            }
            CleoSpiConfig config = new CleoSpiConfig(LexiCom.getHome());
            for (String jar : argv) {
                config.install_jar(config.factory().get(jar));
            }
            if (!preflight) {
                config.save();
            }
            report(config.toString());
        }
    }
    @Command(name="uri_inspect", args="jar ...", min=1, comment="inspect a jar")
    public void uri_inspect(String...argv) {
        MvnJar.Factory factory = new MvnJar.Factory(LexiCom.getHome(), LexiCom.getHome("lib", "uri"));
        for (String jar : argv) {
            report(new MvnJar(jar, factory).toString());
        }
    }
    @Command(name="uri_remove", args="id [no] ...", min=1, comment="remove URI drivers")
    public void uri_remove(String...argv) throws Exception {
        CleoSpiConfig config = new CleoSpiConfig(LexiCom.getHome());
        boolean preflight = argv[0].equalsIgnoreCase("no");
        if (preflight) {
            String[] rest = new String[argv.length-1];
            System.arraycopy(argv, 1, rest, 0, rest.length);
            argv = rest;
        }
        for (String uri : argv) {
            // choices are uri:scheme, auth:scheme, any APIHook, g%a%v, or *.jar
            if (uri.matches("(?i)uri:.*")) {
                String name = uri.substring("uri:".length());
                report("removing uri scheme "+name);
                config.remove_scheme(name);
            } else if (uri.matches("(?i)auth:.*")) {
                String name = uri.substring("auth:".length());
                report("removing auth scheme "+name);
                config.remove_authenticator(name);
            } else {
                try {
                    APIHookClass hook = APIHookClass.valueOf(uri.toUpperCase());
                    report("removing API Hook "+hook.name());
                    config.remove_hook(hook);
                } catch (Exception ignore) {
                    MvnJar jar = config.factory().get(uri);
                    if (!jar.exists()) {
                        report("warning: file does not exist: "+uri);
                    }
                    report("removing "+jar.file().getPath());
                    config.remove_jar(jar);
                }
            }
        }
        if (!preflight) {
            config.save();
        }
        report(config.toString());
    }

    @Command(name="list_nodes", args="path with a *", comment="list objects")
    public void list_nodes(String...argv) {
        Path path;
        if (argv.length==0) {
            path = new Path(PathType.HOST, "*");
        } else if (argv.length==1) {
            path = Path.parsePath(argv[0]);
        } else {
            path = new Path(argv);
        }
        try {
            if (!path.getPath()[path.getPath().length-1].equals("*")) {
                error("warning: it's supposed to end in a *");
            }
            report(path.toString()+" ("+path.getType()+": "+Arrays.toString(path.getPath())+")");
            for (Path item : LexiCom.list(path.getType(), path.getParent())) {
                String name = item.toString();
                if (item.getType()==PathType.HOST) {
                    String folder = LexiCom.getSingleProperty(item, "folder");
                    if (folder!=null) {
                        folder = folder.replace('\\', '/');
                        name = folder+"/"+name;
                    }
                }
                report(name);
            }
        } catch (Exception e) {
            error("could not list", e);
        }
    }

    private static final Pattern DUMP_PATTERN = Pattern.compile("(.*?)(?:,(\\d+))?(?::(.*))?");
    @Command(name="dump", args="(type:audit|path[:props|[,depth]:table])", comment="dump types or nodes")
    public void dump(String...argv) {
        try {
            for (String arg : argv) {
                Matcher m = DUMP_PATTERN.matcher(arg);
                m.matches();
                String   name  = m.group(1);
                int      depth = m.group(2)==null ? -1 : Integer.valueOf(m.group(2));
                String   fmt   = m.group(3);
                if ("audit".equalsIgnoreCase(fmt)) {
                    if (name.equals("*")) {
                        // usage: dump *:audit
                        // Calculates and prints new Defaults for all types
                        Defaults.printAllDefaults(System.out);
                    } else {
                        // usage: dump type:audit
                        // Calculates and prints new Defaults for <type>
                        Object o = Defaults.printDefaults(System.out, HostType.valueOf(name.toUpperCase()));
                        Util.report_bean(this, o);
                    }
                } else {
                    Path     path = Path.parsePath(name);
                    Item     item = Item.getItem(path);
                    HostType type = new Host(path.getHost()).getHostType();
                    if ("props".equalsIgnoreCase(fmt)) {
                        // usage: dump path:props
                        // Dumps the non-default properties of <path>
                        Map<String,String> props = item.getProperties();
                        if (path.getType()==PathType.HOST) {
                            Defaults.suppressHostDefaults(type, props);
                        } else if (path.getType()==PathType.MAILBOX) {
                            Defaults.suppressMailboxDefaults(type, props);
                        }
                        if (props!=null) {
                            report(new String[] {"Attribute", "Value"}, S.invert(props));
                        } else {
                            error("not found");
                        }
                    } else if ("table".equalsIgnoreCase(fmt)) {
                        // usage: dump path[,depth]:table
                        // Dumps the segment of the XML host file for <path> [to <depth>]
                        String[][] description = S.invert(X.flat(X.xml2map(item.getNode()), depth));
                        report(new String[] {"Attribute", "Value"}, description);
                    } else if ("defaults".equalsIgnoreCase(fmt)) {
                        Map<String,String> defaults = Defaults.getDefaults(item);
                        report(new String[] {"Attribute", "Value"}, S.invert(defaults));
                    } else {
                        // usage: dump path
                        // Dumps the raw XML pretty-print of the XML host file segment for <path>
                        report(Util.xml2pp(item.getNode()));
                    }
                }
            }
        } catch (Exception e) {
            error("could not dump hosts", e);
        }
    }
    
    @Command(name="has", args="path property ...", min=1, comment="does path have property...")
    public void has(String pathname, String...props) throws Exception {
        Path path = Path.parsePath(pathname);
        for (String prop : props) {
            boolean has;
            try {
                has = LexiCom.hasProperty(path, prop);
                report(qq(pathname)+(has?" has    ":" has no ")+prop);
            } catch (Exception e) {
                report(qq(pathname)+" error  "+prop+": "+e.getMessage());
            }
        }
    }
    @Command(name="get", args="path property ...", min=1, comment="get property...")
    public void get(String pathname, String...props) {
        if (pathname.isEmpty()) {
            // system option
            for (String prop : props) {
                try {
                    report(prop+"="+Util.get_bean(LexiCom.getOptions(), prop));
                } catch (Exception e) {
                    report(prop+" not found: "+e.toString());
                }
            }
        } else {
            // path property
            Path path = Path.parsePath(pathname);
            for (String prop : props) {
                String[] values;
                try {
                    values = LexiCom.getProperty(path, prop);
                    if (values==null) {
                        report(path+"."+prop+" not found");
                    } else if (values.length==1) {
                        report(path+"."+prop+"=", values[0]);
                    } else {
                        report(path+"."+prop+"=", values);
                    }
                } catch (Exception e) {
                    error("error getting "+prop, e);
                }
            }
        }
    }
    @Command(name="set", args="path property [value...]", comment="set value(s)")
    public void set(String pathname, String property, String...value) throws Exception {
        if (pathname.isEmpty()) {
            try {
                Options o = LexiCom.getOptions();
                Util.set_bean(o, property,
                              value==null||value.length==0 ? null : value[0]);
                o.save();
            } catch (Exception e) {
                report(property+" not found: "+e.toString());
            }
        } else {
            Path path = Path.parsePath(pathname);
            if (value.length==0) {
                LexiCom.setProperty(path, property, (String) null);
            } else if (value.length==1) {
                LexiCom.setProperty(path, property, value[0]);
            } else {
                LexiCom.setProperty(path, property, value);
            }
            if (autosave) {
                LexiCom.save(path);
            }
        }
    }
    @Command(name="exists", args="path...", comment="check for object")
    public void exists(String...argv) {
        for (String arg : argv) {
            try {
                boolean exists = LexiCom.exists(Path.parsePath(arg));
                report(arg+(exists?" exists":" doesn't exist"));
            } catch (Exception e) {
                error("error checking "+arg, e);
            }
        }
    }
    @Command(name="rename", args="path alias", comment="rename object")
    public void rename(String pathname, String alias) throws Exception {
        Path path = Path.parsePath(pathname);
        LexiCom.rename(path, alias);
    }
    @Command(name="lookup_host", args="id", comment="lookup host by id")
    public void lookup_host(String id) throws Exception {
        Path found = LexiCom.lookup(PathType.HOST, id);
        if (found==null) {
            error("lookup["+id+"] not found");
        } else {
            report("lookup["+id+"]=", found.toString());
        }
    }
    @Command(name="lookup_mailbox", args="id", comment="lookup mailbox by id")
    public void lookup_mailbox(String id) throws Exception {
        Path found = LexiCom.lookup(PathType.MAILBOX, id);
        if (found==null) {
            error("lookup["+id+"] not found");
        } else {
            report("lookup["+id+"]=", found.toString());
        }
    }
    private boolean autosave = false;
    @Command(name="autosave", args="on|off", comment="set autosave on/off")
    public void autosave(String...argv) {
        if (argv.length>1) {
            error("usage: autosave [on|off]");
        } else if (argv.length==0) {
            report("autosave is "+(autosave?"on":"off"));
        } else if (argv[0].equalsIgnoreCase("on")) {
            autosave = true;
        } else if (argv[0].equalsIgnoreCase("off")) {
            autosave = false;
        } else {
            error("usage: autosave [on|off]");
        }
    }
    @Command(name="save", args="path...", comment="save changes")
    public void save(String...argv) {
        if (argv.length==0) {
            error("usage: save path...");
        } else {
            for (String arg : argv) {
                Path path = Path.parsePath(arg);
                try {
                    LexiCom.save(path);
                } catch (Exception e) {
                    error("error saving "+arg, e);
                }
            }
        }
    }
    @Command(name="encode", args="[reverse] string...", comment="encode string(s) [reversed]")
    public void encode(String...argv) {
        boolean reverse = false;
        for (String arg : argv) {
            if (arg.equalsIgnoreCase("reverse")) {
                reverse = true;
            } else {
                try {
                    if (reverse) {
                        arg = new StringBuilder(arg).reverse().toString();
                    }
                    report(arg+" => "+Util.encode(arg));
                } catch (Exception e) {
                    error("could not encode", e);
                }
            }
        }
    }
    @Command(name="decode", args="[reverse] string...", comment="decode string(s) [reversed]")
    public void decode(String...argv) {
        boolean reverse = false;
        for (String arg : argv) {
            if (arg.equalsIgnoreCase("reverse")) {
                reverse = true;
            } else {
                try {
                    String decoded = Util.decode(arg);
                    if (reverse) {
                        decoded = new StringBuilder(decoded).reverse().toString();
                    }
                    report(arg+" => "+decoded);
                } catch (Exception e) {
                    error("could not decode", e);
                }
            }
        }
    }
    private String vlenc(String s) {
        try {
            String alias = UUID.randomUUID().toString();
            Host host = LexiCom.activateHost(HostType.FTP, alias);
            host.save();
            Mailbox mailbox = host.getMailboxes()[0];
            mailbox.setProperty("Password", s);
            host.save();
            String result = mailbox.getSingleProperty("Password");
            host.remove();
            return result;
        } catch (Exception e) {
            return "";
        }
    }
    private static String vldec(String s) {
        if (s.startsWith("vlenc:") || s.startsWith("*")) {
            s = LexBean.vlDecrypt((ConfigEncryption) null, s);
        }
        return s;
    }
    @Command(name="encrypt", args="string|file [file]", comment="encrypt data")
    public void encrypt(String...argv) {
        if (argv.length<1 || argv.length>2) {
            error("usage: encrypt string|file [file]");
            return;
        }
        try {
            String src = argv[0];
            File f = new File(src);
            if (f.exists()) {
                byte buf[] = new byte[(int)f.length()];
                FileInputStream fis = new FileInputStream(f);
                fis.read(buf);
                fis.close();
                src = new String(buf);
            }
            String enc = LexiCom.encrypt(src);
            if (argv.length>1) {
                File out = new File(argv[1]);
                FileOutputStream fos = new FileOutputStream(out);
                fos.write(DatatypeConverter.parseBase64Binary(enc));
                fos.close();
            } else {
                report("core:     "+enc);
                enc = LexBean.vlEncrypt((ConfigEncryption) null, src);
                report("LexBean:  "+enc);
                enc = vlenc(src);
                report("vlenc:    "+enc);
                enc = LexiCom.hash(src);
                report("vlpbkdf2: "+enc);
            }
        } catch (Exception e) {
            error("could not encrypt", e);
        }
    }
    @Command(name="decrypt", args="string|file [file]", comment="decrypt data")
    public void decrypt(String...argv) {
        if (argv.length<1 || argv.length>2) {
            error("usage: decrypt string|file [file]");
            return;
        }
        try {
            String src = argv[0];
            String d2  = null;
            XmlReadResult xml = null;
            try {
                xml = read_xml_document(src);
                src = xml.file.contents;
            } catch (Exception e) {
                // not an xml filename -- just process the string
                if (src.startsWith("#") && src.endsWith("#")) {
                    StringBuffer sb = new StringBuffer(src.subSequence(1, src.length()-1));
                    while (sb.length()%4 > 0) {
                        sb.append('=');
                    }
                    src = sb.toString();
                }
                d2 = vldec(src);
                try {
                    src = LexiCom.decrypt(src);
                } catch (Exception ignore) {}
            }
            if (argv.length>1) {
                Util.string2file(argv[1], src);
            } else if (xml!=null) {
                report(X.map2tree(xml.map));
                Map<String,Object> ldapmap = X.submap(xml.map, "Users", "Ldapserver");
                if (ldapmap==null) {
                    ldapmap = X.submap(xml.map, "Options", "Ldapserver");
                }
                if (ldapmap!=null) {
                    LDAP ldap = new LDAP(ldapmap, this.new VLCrypt());
                    report(ldap.toString());
                }
            } else {
                if (d2==null) {
                    report(src);
                } else {
                    report("core:  "+src);
                    report("vldec: "+d2);
                }
            }
        } catch (Exception e) {
            error("could not decrypt", e);
        }
    }
    @Command(name="url", args="url...", comment="parse url string(s)")
    public void url(String...argv) {
        for (String arg : argv) {
            try {
                java.net.URL u = new java.net.URL(arg);
                report("path = "+u.getPath());
                /*
                URL url = URL.parseURL(arg);
                if (url==null) {
                    error("cannot parse "+arg);
                } else if (url.getType()==null) {
                    error("unrecognized protocol: "+arg);
                } else {
                    report(url.toString());
                    report("host ", S.join(" ", url.getHostProperties(), qqequals));
                    report("mailbox ", S.join(" ", url.getMailboxProperties(), qqequals));
                }
                */
            } catch (Exception e) {
                error("parsing error", e);
            }
        }
    }
    @Command(name="send", args="file url", comment="send a file")
    public void send(String...argv) {
        if (argv.length!=2) {
            error("usage: send file url");
        } else {
            String fn  = argv[0];
            String url = argv[1];
            File   f   = new File(fn);
            URL    u   = URL.parseURL(url);
            if (!f.exists()) {
                error("file not found: "+fn);
            } else if (!f.isFile()) {
                error("regular file expected: "+fn);
            } else if (u==null) {
                error("could not parse URL: "+url);
            } else {
                try {
                    u.resolve();
                    Host host = u.getHost();
                    if (host.getSource()==HostSource.ACTIVATE) {
                        report("created host "+host.getPath());
                    } else {
                        report("reusing host "+host.getPath());
                    }
                    // Now setup mailbox
                    Mailbox mailbox = u.getMailbox();
                    if (mailbox!=null) {
                        report("mailbox exists: "+mailbox.getSingleProperty("alias"));
                    } else {
                        report("mailbox being created for "+u.getUser()+":"+u.getPassword());
                        mailbox = host.createMailbox(u.getUser());
                        mailbox.setProperty("Username", u.getUser());
                        mailbox.setProperty("Password", u.getPassword());
                        mailbox.setProperty("enabled", "True");
                    }
                    // Now send
                    boolean ok = mailbox.send(f, u.getFolder(), u.getFilename(), reporter);
                    report("send "+(ok?"succeeded":"failed"));
                    report("result ", u.getMailbox().getLastResult());

                    // clean up if a temporary host was created
                    if (host.getSource()==HostSource.ACTIVATE) {
                        host.remove();
                    }
                } catch (URLResolutionException e) {
                    error(e.getMessage());
                } catch (Exception e) {
                    error("trouble activating host", e);
                }
            }
        }
    }
    @Command(name="list", args="url", comment="list files")
    public void list(String...argv) {
        if (argv.length!=1) {
            error("usage: list url");
        } else {
            String url = argv[0];
            URL    u   = URL.parseURL(url);
            if (u==null) {
                error("could not parse URL: "+url);
            } else {
                try {
                    u.resolve();
                    if (u.getHost().getSource()==HostSource.NEW) {
                        report("created host "+u.getHost().getPath());
                    } else {
                        report("reusing host "+u.getHost().getPath());
                    }

                    // Get the listing (uses a temp action internally)
                    u.getMailbox().addLogListener(reporter);
                    DirectoryEntry[] files = u.getMailbox().list(u.getFolder(), u.getFilename(), reporter);
                    u.getMailbox().removeLogListener(reporter);
                    for (DirectoryEntry file : files) {
                        report(file.relativePath()+" -> "+file.name());
                    }
                    report("result ", u.getMailbox().getLastResult());

                    // clean up if a temporary host was created
                    if (u.getHost().getSource()==HostSource.ACTIVATE) {
                        u.getHost().remove();
                    }
                } catch (URLResolutionException e) {
                    error(e.getMessage());
                } catch (Exception e) {
                    error("trouble activating host", e);
                }
            }
        }
    }

    @Command(name="remove", args="path...", comment="remove node(s)")
    public void remove(String...argv) {
        for (String arg : argv) {
            try {
                LexiCom.remove(Path.parsePath(arg));
            } catch (Exception e) {
                error("error removing "+arg, e);
            }
        }
    }

    private static class XmlReadResult {
        public Util.ReadResult    file;
        public Map<String,Object> map;
    }
    private XmlReadResult read_xml_document(String fn) throws Exception {
        // returns XML file including the top-level Document
        return read_xml(fn, true);
    }
    private XmlReadResult read_xml_file(String fn) throws Exception {
        // returns XML file after a getDocumentElement
        return read_xml(fn, false);
    }
    private XmlReadResult read_xml(String fn, boolean document) throws Exception {
        XmlReadResult xml = new XmlReadResult();
        try {
            xml.file = Util.file2string(fn);
        } catch (FileNotFoundException e1) {
            try {
                xml.file = Util.file2string(LexiCom.getHome(fn));
            } catch (FileNotFoundException e2) {
                try {
                    xml.file = Util.file2string(LexiCom.getHome("conf", fn));
                } catch (FileNotFoundException e3) {
                    try {
                        xml.file = Util.file2string(LexiCom.getHome("hosts", fn));
                    } catch (FileNotFoundException e4) {
                        return null;
                    }
                }
            }
        }
        try {
            if (document) {
                xml.map = X.xml2map(X.string2xml(xml.file.contents));
            } else {
                xml.map = X.xml2map(X.string2xml(xml.file.contents).getDocumentElement());
            }
        } catch (Exception e) {
            xml.map = null; /// must not be XML
        }
        return xml;
    }
    private void write_xml_file(XmlReadResult xml) throws Exception {
        if (xml.map!=null) {
            xml.file.contents = X.xml2string(Util.map2xml(xml.map));
        }
        Util.string2file(xml.file);
    }

    @Command(name="opts", args="[file|table[(query)] [path[=value]]]", comment="display/update options")
    public void opts(String...argv) {
        try {
            DB db = vldb;
            if (argv.length>0 && argv[0].equalsIgnoreCase("h2")) {
                db = h2db;
                argv = Arrays.copyOfRange(argv, 1, argv.length);
            }
            if (argv.length==0) {
                // usage: opts
                // just dumps out core iLexCom options
                Options o = LexiCom.getOptions();
                Util.report_bean(this, o);
            } else {
                String name = argv[0];
                String table = name;
                Map<String,String> where = null;
                boolean err = false;
                int paren = name.indexOf('(');
                if (paren>=0 && name.endsWith(")")) {
                    table = name.substring(0, paren);
                    where = S.split(name.substring(paren+1, name.length()-1), S.COMMA_EQUALS);
                }
                table = db.table(table);
                if (table!=null) {
                    // usage: opts table[(query)] [column...]
                    //   runs query and lists selected columns.  * or empty means all columns
                    // usage: opts table[(query)] [column=value...]
                    //   if any columns have = updates the selected columns before reporting
                    //   update only updates the first row matching the query
                    //   you can combine update and select
                    String[] qcolumns = null;
                    Object[] qargs    = null;
                    if (where!=null) {
                        qcolumns = new String[where.keySet().size()];
                        int i = 0;
                        for (String c : where.keySet()) {
                            qcolumns[i] = db.column(table, c);
                            i++;
                        }
                        qargs = where.values().toArray();
                    }
                    boolean star = false;
                    List<String> select_columns = new ArrayList<String>();
                    List<String> update_columns = new ArrayList<String>();
                    List<Object> update_values  = new ArrayList<Object>();
                    for (int i=1; i<argv.length; i++) {
                        String   arg  = argv[i];
                        if (arg.equals("*")) {
                            star = true;
                        } else {
                            String[] kv   = arg.split("=", 2);
                            String column = db.column(table, kv[0]);
                            if (column==null) {
                                error("no such column "+table+"."+kv[0]);
                                err=true;
                                continue;
                            }
                            if (kv.length>1) {
                                update_columns.add(column);
                                if (db.string(table, column)) {
                                    update_values.add(kv[1]);
                                } else {
                                    update_values.add(Integer.valueOf(kv[1]));
                                }
                            }
                            select_columns.add(column);
                        }
                    }
                    if (!err) {
                        String[] columns = star ? null : select_columns.toArray(new String[select_columns.size()]);
                        DB.Selection selection = db.new Selection(table, columns, qcolumns, qargs);
                        int updated = 0;
                        if (update_columns.size()>0) {
                            updated = selection.update(update_columns, update_values);
                            if (updated==0) {
                                // convert to insert
                                db.insert(table,
                                            S.cat(Arrays.asList(qcolumns), update_columns),
                                            S.cat(Arrays.asList(qargs), update_values));
                                selection = db.new Selection(table, columns, qcolumns, qargs);
                            }
                        }
                        DB.Selection.Result r = selection.rows();
                        if (r.count==1 && r.columns.length>4) {
                            report(new String[] {"Column", "Value"}, S.invert(r.columns, r.rows[0]));
                        } else {
                            report(r.columns, r.rows);
                            report(r.count+" rows selected"+(updated>0 ? ", "+updated+" rows updated" : ""));
                        }
                    }
                } else if (name.matches("(?i).*xml")) {
                    XmlReadResult xml = read_xml_document(name);
                    if (xml==null) {
                        error("file not found");
                        return;
                    }
                    report("(reading "+xml.file.file.getPath()+")");
                    if (xml.file.encrypted) {
                        report("(contents were encrypted)");
                    }
                    // ok -- got us a map of the XML file
                    // now work through the arguments for display/update
                    boolean updated = false;
                    for (int i=1; i<argv.length; i++) {
                        String   arg  = argv[i];
                        String[] kv   = arg.split("=", 2);
                        String[] path = kv[0].split("/");
                        if (kv.length>1) {
                            updated=true;
                            if (kv[1].isEmpty()) {
                                X.setmap(xml.map, path, (Object)null); // null means remove it
                            } else {
                                try {
                                    LDAP ldap = new LDAP(kv[1]);
                                    X.setmap(xml.map, path, ldap.toMap(this.new VLCrypt()));
                                } catch (Exception e) {
                                    // no big deal -- not LDAP, but see if it's DB
                                    try {
                                        DBOptions dbo = new DBOptions(kv[1]);
                                        X.setmap(xml.map, path, dbo.connection);
                                    } catch (Exception f) {
                                        // no big deal -- not LDAP or DB -- just a String
                                        X.setmap(xml.map, path, kv[1]);
                                    }
                                }
                            }
                        } else {
                            Object o = X.subprune(xml.map, path);
                            //if (o==null) o = X.subprune(xml.map, path);
                            if (o!=null) {
                                if (o instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String,Object> out = (Map<String,Object>)o;
                                    report(X.map2tree(out));
                                } else {
                                    //String out = (String)o;
                                    report(o.toString());
                                }
                            }
                        }
                    }
                    if (updated) {
                        write_xml_file(xml);
                        report("file "+xml.file.file.getPath()+" updated");
                    } else if (argv.length<=1) {
                        report(X.map2tree(xml.map));
                        //report(X.xml2string(Util.map2xml(xml.map)));
                    }
                } else {
                    // just try to dump a file
                    report(Util.file2string(name).contents);
                }
            }
        } catch (Error r) {
            error("error: "+ r);
        } catch (Exception e) {
            error("error getting options", e);
        }
    }
    public static class DBOptions {
        public enum Vendor { MYSQL, ORACLE, SQLSERVER, DB2, H2; }
        public Vendor vendor;
        public String user;
        public String password;
        public String db;
        public String driver;
        public String type;
        public String rawconnection;
        public String connection;
        public void init(String vendor, String user, String password, String host, String port, String db) {
            this.vendor     = Vendor.valueOf(vendor.toUpperCase());
            this.user       = user;
            this.password   = password;
            this.db         = db;
            switch (this.vendor) {
            case MYSQL:
                if (port==null) port = "3306";
                this.driver        = "com.mysql.jdbc.Driver";
                this.type          = DBConnection.DB_CONTYPE_MYSQL;
                this.rawconnection = "jdbc:mysql://"+host+":"+port;
                this.connection    = this.rawconnection+"/"+this.db;
                break;
            case ORACLE:
                if (port==null) port = "1521";
                this.driver        = "oracle.jdbc.driver.OracleDriver";
                this.type          = DBConnection.DB_CONTYPE_OTHER;
                this.rawconnection = "jdbc:oracle:thin:@"+host+":"+port;
                this.connection    = this.rawconnection+":"+this.db;
                break;
            case SQLSERVER:
                if (port==null) port = "1433";
                this.driver        = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
                this.type          = DBConnection.DB_CONTYPE_OTHER;
                this.rawconnection = "jdbc:sqlserver://"+host+":"+port;
                this.connection    = this.rawconnection+";databaseName="+this.db;
                break;
            case DB2:
                if (port==null) port = "5000";
                this.driver        = "com.ibm.db2.jcc.DB2Driver";
                this.type          = DBConnection.DB_CONTYPE_OTHER;
                this.rawconnection = "jdbc:db2://"+host+":"+port;
                this.connection    = this.rawconnection+"/"+this.db;
                break;
            case H2:
                if (port==null) port = "9092";
                this.driver        = "org.h2.Driver";
                this.type          = DBConnection.DB_CONTYPE_OTHER;
                this.rawconnection = "jdbc:h2:tcp://"+host+":"+port;
                this.connection    = this.rawconnection+"/"+this.db;
                break;
            }
        }
        // vendor:user:pass@host[:port]/database
        static final Pattern DB_PATTERN = Pattern.compile("(\\w+):(.*):(.*)@(.*?)(?::(\\d+))?/(.*)");
        public DBOptions(String string) {
            Matcher m = DB_PATTERN.matcher(string);
            if (m.matches()) {
                this.init(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), m.group(6));
            } else {
                throw new IllegalArgumentException("invalid database descriptor: "+string);
            }
        }
        public DBOptions (DBConnection c) {
            this.connection = c.getConnectionString();
            this.driver     = c.getDriverString();
            this.type       = c.getConnectionType();
            this.user       = c.getUserName();
            this.password   = vldec(c.getPassword());
            this.vendor     = Vendor.valueOf(this.connection.split(":")[1].toUpperCase()); // jdbc:vendor:stuff
        }
        public Options.DBConnection getDBConnection() throws Exception {
            Options.DBConnection c = LexiCom.getOptions().new DBConnection(); 
            c.setConnectionType(this.type);
            c.setConnectionString(this.connection);
            c.setDriverString(this.driver);
            c.setUserName(this.user);
            c.setPassword(this.password);
            return c;
        }
    }
    @Command(name="db", args="find|set|use|remove|create|drop string", comment="find/set db connection")
    public void db(String...argv) {
        if (argv.length!=2) {
            error("usage: db find|set|use|remove|create|drop string");
        } else {
            String command = argv[0];
            String string  = argv[1];
            try {
                Options o = LexiCom.getOptions();
                if (command.equalsIgnoreCase("find")) {
                    Options.DBConnection c = o.findDBConnection(string);
                    if (c==null) {
                        error("not found");
                    } else {
                        vldb.setOptions(new DBOptions(c));
                        Util.report_bean(this, c);
                    }
                } else if (command.equalsIgnoreCase("set")) {
                    try {
                        vldb.setOptions(new DBOptions(string));
                        Options.DBConnection c = vldb.getOptions().getDBConnection();
                        o.updateDBConnection(c);
                        o.save();
                        c = o.findDBConnection(vldb.getOptions().connection);
                        Util.report_bean(this, c);
                    } catch (Exception e) {
                        error("usage: db set type:user:password@host[:port]/database");
                    }
                } else if (command.equalsIgnoreCase("use")) {
                    if (string.contains(":")) {
                        vldb.setOptions(new DBOptions(string));
                    } else {
                        // just setting the password
                        try {
                            DBConnection c = o.getTransferLoggingDBConnection();
                            if (c!=null) {
                                DBOptions options = new DBOptions(c);
                                options.password = string;
                                vldb.setOptions(options);
                            }
                        } catch (Exception e) {
                            // error will fall out
                        }
                    }
                } else if (command.equalsIgnoreCase("remove")) {
                    o.removeDBConnection(string);
                    o.save();
                } else if (command.equalsIgnoreCase("create") || command.equalsIgnoreCase("drop")) {
                    try {
                        DBOptions dbo = new DBOptions(string);
                        try {
                            DB db = new DB(dbo.rawconnection, dbo.user, dbo.password);
                            db.execute(command+" database "+dbo.db);
                            report("database "+dbo.db+" "+(command.equalsIgnoreCase("create")?"created":"dropped"));
                        } catch (SQLException e) {
                            error("can not "+command+" database "+dbo.db, e);
                        }
                    } catch (Exception e) {
                        error("usage: db (create|drop) type:user:password@host[:port]/database");
                    }
                } else {
                    error("usage: db find|set string");
                }
            } catch (Exception e) {
                error("error getting options", e);
            }
        }
    }
    // ldap[s][(type|opt|map...)]://[user:pass@]host[:port]/basedn[?filter]
    // type = ad|apache|domino|novell|dirx
    // opt  = starttls|default
    // map  = (user|mail|name|home|first|last)=attr
    @Command(name="ldap", args="[url|* [user...]]", comment="ldap query")
    public void ldap(String...argv) {
        try {
            LDAP ldap;
            int  start;
            if (argv.length==0) {
                report(get_ldap().toString());
                return;
            } else if (argv[0].equals("*")) {
                ldap = get_ldap();
                start = 1;
            } else {
                try {
                    ldap = new LDAP(argv[0]);
                    start = 1;
                } catch (IllegalArgumentException e) {
                    // treat it like implied *
                    ldap = get_ldap();
                    start = 0;
                }
            }
            for (int i=start; i<argv.length; i++) {
                Map<LDAP.Attr, String> found = ldap.find(argv[i]);
                report(S.join("\n", found, "%s = %s"));
            }
        } catch (Exception e) {
            error("error", e);
        }
    }
    @Command(name="ldap_add", args="[url|*] user...", min=1, comment="ldap add")
    public void ldapadd(String...argv) {
        try {
            LDAP ldap;
            int  start;
            if (argv[0].equals("*")) {
                ldap = get_ldap();
                start = 1;
            } else {
                try {
                    ldap = new LDAP(argv[0]);
                    start = 1;
                } catch (IllegalArgumentException e) {
                    // treat it like implied *
                    ldap = get_ldap();
                    start = 0;
                }
            }
            for (int i=start; i<argv.length; i++) {
                String arg = argv[i];
                final Map<LDAP.Attr,String> entry = new HashMap<LDAP.Attr,String>();
                S.megasplit(S.COMMA_EQUALS, arg, new Inspector<Object> () {
                    public Object inspect(String [] group) {
                        LDAP.Attr a = LDAP.Attr.valueOf(group[1].toUpperCase());
                        entry.put(a, group[2]);
                        return null;
                    }
                });
                ldap.add(entry);
            }
        } catch (Exception e) {
            error("error adding user", e);
        }
    }
    @Command(name="xferlog_off", comment="disable transfer logging")
    public void xferlog_off() throws Exception {
        Options o = LexiCom.getOptions();
        o.setTransferLogging(Options.TRANSFER_LOG_OFF);
        o.setTransferLoggingEnabled(false);
        o.save();
    }
    @Command(name="xferlog_xml", comment="log transfers to XML")
    public void xferlog_xml() throws Exception {
        Options o = LexiCom.getOptions();
        o.setTransferLogging(Options.TRANSFER_LOG_XML);
        o.setTransferLoggingEnabled(true);
        o.save();
    }
    @Command(name="xferlog", args="type:user:password@host[:port]/db", comment="log transfers to database")
    public void xferlog(String db) throws Exception {
        Options o = LexiCom.getOptions();
        DBOptions dbo = new DBOptions(db);
        o.setTransferLogging(Options.TRANSFER_LOG_DATABASE);
        o.setTransferLoggingDBConnectionStr(dbo.connection);
        o.setTransferLoggingEnabled(true);
        o.save();
    }

    private static Map<String,String> crackPasswords(Map<String,String> props) {
        if (props!=null) {
            Iterator<Map.Entry<String,String>> i = props.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry<String,String> e = i.next();
                if (e.getKey().matches("(?i).*password")) {
                    props.put(e.getKey(), vldec(e.getValue()));
                }
            }
        }
        return props;
    }
    private static String[] splitFolder(String alias) {
        int slash = alias.lastIndexOf('/');
        if (slash >= 0) {
            return new String[] { alias.substring(0, slash).replace('/', '\\'),
                                  alias.substring(slash+1) };
        
        } else {
            return new String[] { null, alias };
        }
    }
    @Command(name="host", args="[alias|* [url]]", comment="create a new remote host")
    public void host(String...argv) {
        if (argv.length<=1) {
            // usage: host [*|alias]
            // export [all] remote host[s]
            // output format is the host commands to set the hosts up
            try {
                Host[] hosts = (argv.length==0 || argv[0].equals("*"))
                               ? LexiCom.getHosts()
                               : new Host[] {LexiCom.getHost(argv[0])};
                for (Host h : hosts) {
                    if (h==null) {
                        error("host "+qq(argv[0])+" not found");
                    } else if (!h.isLocal()) {
                        HostType           type   = h.getHostType();
                        Map<String,String> props  = Defaults.suppressHostDefaults(type, h.getProperties());
                        URL                url    = new URL(type).extractHost(props);
                        String             alias  = h.getPath().getAlias();
                        String             folder = props.remove(".folder");
                        // cleanup folder: \ to / and ending in / (unless empty)
                        folder = folder==null?"":folder.replace('\\', '/').replaceFirst("(?<=[^/])$", "/");
                        // note: above regex adds terminal slash unless empty or already there
                        List<String>       output = new ArrayList<String>();
                        if (alias.equals(url.getProtocol()+"://"+url.getAddress())) {
                            alias = "*";
                        }
                        if (props!=null && !props.isEmpty()) {
                            output.add("host "+qq(folder+alias)+" "+qq(url.toString())+" "+S.join(" \\\n    ", props, qqequals));
                        } else {
                            output.add("host "+qq(folder+alias)+" "+qq(url.toString()));
                        }
                        for (Mailbox m : h.getMailboxes()) {
                            if (!LexiCom.exists(m.getPath())) continue; // I don't know why VL returns non-existing ones, but it does
                            if (m.getSingleProperty("enabled").equalsIgnoreCase("True")) {
                                // get the properties
                                Map<String,String> mprops = Defaults.suppressMailboxDefaults(type, m.getProperties());
                                crackPasswords(mprops);
                                // figure out how to display alias vs. user:password
                                url.extractMailbox(mprops);
                                String malias = url.formatMailbox(m.getPath().getAlias());
                                // format the remaining mailbox attributes
                                if (mprops!=null && !mprops.isEmpty()) {
                                    output.add("  mailbox "+qq(malias)+" "+S.join(" \\\n    ", mprops, qqequals));
                                } else {
                                    output.add("  mailbox "+qq(malias));
                                }
                                // put it all together
                            }
                        }
                        report(S.join(" \\\n", output));
                    }
                }
            } catch (Exception e) {
                error("could not list hosts", e);
            }
            return;
        }
        // usage: host [folder/]alias url [property=value...] [mailbox alias[(user:password)] [property=value...]...]
        // create new host <alias> and associated mailbox from parsed <url>
        // add mailbox[es] with <alias> and associated properties
        //    alias:password is acceptable shorthand for alias(user:password) when alias==user
        // reuse existing host (if found and appropriate) and mailbox (if found)
        if (argv.length<2) {
            error("usage: host alias url [properties...]");
            return;
        }
        // parsing command line:
        // first, get alias and url parsed, defaulting alias if requested (*)
        String alias = argv[0];
        String url   = argv[1];
        URL    u     = URL.parseURL(url);
        if (u==null) {
            error("could not parse URL: "+url);
            return;
        } else if (u.getType()==null) {
            error("unrecognized protocol: "+url);
            return;
        }
        // parse folder/ off of alias
        String[] fa = splitFolder(alias);
        String folder = fa[0];
        alias = fa[1];
        // default alias if *
        if (alias.equals("*")) {
            alias = u.getProtocol()+"://"+u.getAddress();
        }
        // second, now start collecting properties
        // if there was a mailbox in the url, queue it up as the first mailbox
        Map<String,String> hostProps    = u.getHostProperties();
        Map<String,String> mailboxProps = u.getMailboxProperties();
        Map<String,Map<String,String>> mailboxes = new HashMap<String,Map<String,String>>();
        if (mailboxProps!=null && !mailboxProps.isEmpty()) {
            mailboxes.put(u.getUser(), mailboxProps);
            mailboxProps = null;
        }
        // default in the folder property
        if (folder!=null && !folder.isEmpty()) {
            hostProps.put(".folder", folder);
        }
        // now loop through the remaining properties, starting with the host properties
        boolean hostmode = true;
        String malias = null;
        for (int i=2; i<argv.length; i++) {
            if (argv[i].equalsIgnoreCase("mailbox")) {
                // if we were collecting a mailbox, queue it up
                // then pick off the next token as the alias and keep going
                if (!hostmode) {
                    mailboxes.put(malias, mailboxProps);
                    mailboxProps = null;
                }
                hostmode = false;
                i++;
                if (i<argv.length) {
                    mailboxProps = u.parseMailbox(argv[i]);
                    malias = mailboxProps.remove(".alias");
                }
            } else {
                String[] kv = argv[i].split("=", 2);
                if (hostmode) {
                    hostProps.put(kv[0], kv.length>1 ? kv[1] : null);
                } else {
                    mailboxProps.put(kv[0], kv.length>1 ? kv[1] : null);
                }
            }
        }
        // at the end, queue up the last mailbox
        if (mailboxProps!=null && !mailboxProps.isEmpty()) {
            mailboxes.put(malias, mailboxProps);
        }
        // interlude to debug this
        if (alias.equalsIgnoreCase("test")) {
            report("host "+alias+" "+S.join(" ", hostProps, qqequals));
            for (Map.Entry<String,Map<String,String>> mbx : mailboxes.entrySet()) {
                report("mailbox "+mbx.getKey()+" "+S.join(" ", mbx.getValue(), qqequals));
            }
            return;
        }
        try {
            // create (or update) the host
            Host host = LexiCom.getHost(alias);
            if (host==null) {
                host = LexiCom.activateHost(u.getType(), alias);
                report("created new host "+alias);
                // change "myMailbox" to "template mailbox" - just because
                Mailbox template = host.getMailbox("myMailbox");
                if (template!=null) {
                    template.setProperty("enabled", "False");
                    template.rename(Host.TEMPLATE_MAILBOX);
                    report("renamed template: "+template.getPath());
                }
            } else if (host.getHostType() != u.getType()) {
                error("you can't change the host type from "+host.getHostType()+" to "+u.getType()+" (yet)");
            } else {
                report("updating existing host "+alias);
            }
            for (Map.Entry<String, String> prop : hostProps.entrySet()) {
                host.setProperty(prop.getKey(), prop.getValue());
            }
            host.save();
            // create (or update) mailboxes
            for (Map.Entry<String,Map<String,String>> mbx : mailboxes.entrySet()) {
                malias = mbx.getKey();
                Mailbox mailbox = host.getMailbox(malias);
                if (mailbox==null) {
                    try {
                        mailbox = host.cloneMailbox(malias);
                        report("created new mailbox "+malias+" from template");
                    } catch (Exception e) {
                        // maybe template isn't where we expect it -- just make new
                        mailbox = host.createMailbox(malias);
                        report("created new mailbox "+malias);
                    }
                } else {
                    report("updating existing mailbox "+malias);
                }
                for (Map.Entry<String, String> prop : mbx.getValue().entrySet()) {
                    mailbox.setProperty(prop.getKey(), prop.getValue());
                }
                mailbox.setProperty("enabled", "True");
                host.save();
            }
        } catch (Exception e) {
            error("trouble activating host", e);
        }
    }
    @Command(name="user", args="prot:user:pass ...", comment="create users")
    public void user(String...argv) {
        if (argv.length<=1 && (argv.length==0 || !argv[0].contains(":"))) {
            // usage: user [*|user]
            // export [all] user[s]
            // output format is the user commands to set the users up
            try {
                String user = (argv.length==0 || argv[0].equals("*")) ? null : argv[0];
                User.Filter filter = new User.RegexFilter(user);
                for (User.Description u : User.list(filter)) {
                    report("  user "+S.join(" ", qqequals(u.toStrings())));
                }
                // repeat the same thing with vlusers
                try {
                    for (VLNav.UserDescription u : get_vlnav().list_users()) {
                        if (user==null || u.username.matches(user)) {
                            report("  user "+S.join(" ", qqequals(u.toStrings())));
                        }
                    }
                } catch (SQLException e) {
                    // probably VLNav is not set up?
                }
            } catch (Exception e) {
                error("error listing users", e);
            }
            return;
        }
        // now in create user mode
        // user [folder/]name[:password] type {property=value ...}
        if (argv.length<2) {
            error("missing type");
            return;
        }
        String typename = argv[1];
        // check for vluser
        if (typename.equalsIgnoreCase(VLNav.UserDescription.VLUSER)) {
            try {
                VLNav.UserDescription vluser = get_vlnav().new UserDescription(argv);
                report("add/update user "+S.join(" ", qqequals(vluser.toStrings())));
                vluser = get_vlnav().update_user(vluser);
                report("user "+S.join(" ", qqequals(vluser.toStrings())));
            } catch (Exception e) {
                error("can not create vluser", e);
            }
            return;
        } else {
            try {
                User.Description user = new User.Description(argv);
                report("add/update/delete user "+S.join(" ", qqequals(user.toStrings())));
                user = User.update(user);
                report(S.join("\n", user.notes));
                report("user "+S.join(" ", qqequals(user.toStrings())));
            } catch (Exception e) {
                error("can not add/update/delete user", e);
            }
        }
    }
    @Command(name="action", args="[path [command...]]", comment="export/create action(s)")
    public void action(String...argv) {
        if (argv.length==0) {
            // usage: action
            // export all scheduled actions
            // output format is the action commands to set the actions up
            try {
                for (ISchedule.Item schedule : LexiCom.getSchedule().listItems()) {
                    Path path = new Path(schedule.getAction());
                    String[] commands = LexiCom.getProperty(path, "Commands")[0].split("\n");
                    report("action "+qq(path.toString())+" ", S.join(" \\\n",qq(commands)));
                }
            } catch (Exception e) {
                error("error listing scheduled actions", e);
            }
        } else if ((argv.length==1 && argv[0].equals("*"))) {
            // usage: action *
            // export all (and I mean all) actions
            try {
                for (Host h : LexiCom.getHosts()) {
                    for (Item i : h.getChildren(PathType.HOST_ACTION)) {
                        report("got item "+i.getPath());
                        String[] commands = S.s(LexiCom.getSingleProperty(i.getPath(), "Commands")).split("\n");
                        report("action "+qq(i.getPath().toString())+" \\\n  "+S.join(" \\\n  ",qq(commands)));
                    }
                    for (Mailbox m : h.getMailboxes()) {
                        if (!LexiCom.exists(m.getPath())) continue; // I don't know why VL returns non-existing ones, but it does
                        for (Item i : m.getChildren(PathType.ACTION)) {
                            String[] commands = S.s(LexiCom.getSingleProperty(i.getPath(), "Commands")).split("\n");
                            report("action "+qq(i.getPath().toString())+" \\\n  "+S.join(" \\\n  ",qq(commands)));
                        }
                    }
                }
            } catch (Exception e) {
                error("error listing all actions", e);
            }
        } else if (argv.length==1) {
            // usage: action path
            // export the scheduled action <action>
            // output format is the action command to set the action up
            String name = argv[0];
            try {
                Path path = Path.parsePath(name);
                List<Path> actions = new ArrayList<Path>();
                switch (path.getType()) {
                case HOST:
                    actions.addAll(Arrays.asList(LexiCom.list(PathType.HOST_ACTION, path)));
                    break;
                case MAILBOX:
                    actions.addAll(Arrays.asList(LexiCom.list(PathType.ACTION, path)));
                    break;
                case ACTION:
                case HOST_ACTION:
                    actions.add(path);
                    break;
                default:
                    error("this type of path does not have actions");
                }
                for (Path action : actions) {
                    String[] commands = LexiCom.getProperty(action, "Commands")[0].split("\n");
                    report("action "+qq(action.toString())+" ", S.join("\\\n",qq(commands)));
                }
            } catch (Exception e) {
                error("error listing action commands for "+name, e);
            }
        } else {
            String name = argv[0];
            Path path = Path.parsePath(name);
            if (path.getType() != PathType.ACTION && path.getType() != PathType.HOST_ACTION) {
                error("<action>user@host or <action>host expected: "+argv[0]);
            } else {
                String commands = S.join("\n", 1, argv);
                try {
                    if (!LexiCom.exists(path)) {
                        path = LexiCom.create(path);
                    }
                    LexiCom.setProperty(path, "Commands", commands);
                    LexiCom.save(path);
                    report(name, commands);
                } catch (Exception e) {
                    error("error creating action "+name, e);
                }
            }
        }
    }
    @Command(name="schedule", args="[action [null|schedule...]]", comment="export/create action schedule(s)")
    public void schedule(String...argv) {
        if (argv.length==0 || (argv.length==1 && argv[0].equals("*"))) {
            // usage: schedule [*]
            // export all configured schedules
            // output format is the schedule commands to set the schedules up
            try {
                for (ISchedule.Item schedule : LexiCom.getSchedule().listItems()) {
                    report("schedule "+qq(new Path(schedule.getAction()).toString())+" "+
                           new Schedule(schedule).toString());
                }
            } catch (Exception e) {
                error("error listing schedules", e);
            }
        } else if (argv.length==1) {
            // usage: schedule <action>
            // export schedule for <action>
            // output format is the schedule command to set the schedule up
            Path path = Path.parsePath(argv[0]);
            if (path.getType() != PathType.ACTION && path.getType() != PathType.HOST_ACTION) {
                error("schedule applicable for action or host action only");
            } else {
                try {
                    Action action = new Action(path);
                    Schedule schedule = action.getSchedule();
                    report("schedule "+qq(path.toString())+" "+schedule);
                } catch (Exception e) {
                    error("error getting schedule", e);
                }
            }
        } else if (argv[0].equalsIgnoreCase("test")) {
            // usage: schedule test <schedule>
            // parse <schedule> for syntax but don't do anything with it
            Schedule schedule = new Schedule(S.join(" ",  1, argv));
            report(schedule.toString());
        } else if (argv.length==2 && argv[1].equalsIgnoreCase("null")) {
            // usage: schedule <action> null
            // delete schedule for <action>
            Path path = Path.parsePath(argv[0]);
            Action action = new Action(path);
            try {
                action.setSchedule(null);
            } catch (Exception e) {
                error("error setting schedule", e);
            }
        } else {
            // usage: schedule <action> <schedule>
            // create new schedule for <action>
            Schedule schedule = new Schedule(S.join(" ",  1, argv));
            Path     path = Path.parsePath(argv[0]);
            Action   action = new Action(path);
            try {
                action.setSchedule(schedule);
            } catch (Exception e) {
                error("error setting schedule", e);
            }
        }
    }
    @Command(name="scheduler", args="(autostart [on|off] | start)", comment="scheduler control")
    public void scheduler(String...argv) {
        try {
            if (argv.length==2 && argv[0].equalsIgnoreCase("autostart") && argv[1].equalsIgnoreCase("on")) {
                LexiCom.getSchedule().setAutoStartup(true);
                LexiCom.getSchedule().save();
                report("scheduler autostart set to on");
            } else if (argv.length==2 && argv[0].equalsIgnoreCase("autostart") && argv[1].equalsIgnoreCase("off")) {
                LexiCom.getSchedule().setAutoStartup(false);
                report("scheduler autostart set to off");
                LexiCom.getSchedule().save();
            } else if (argv.length==1 && argv[0].equalsIgnoreCase("autostart")) {
                report("scheduler autostart is "+(LexiCom.getSchedule().isAutoStartup()?"on":"off"));
            } else if (argv.length==1 && argv[0].equalsIgnoreCase("start")) {
                LexiCom.startService();
            } else {
                error("usage: scheduler (autostart [on|off] | start)");
            }
        } catch (Exception e) {
            error("error managing scheduler", e);
        }
    }
    @Command(name="group", args="[name [definition]]", comment="export/create VLNav group(s)")
    public void group(String...argv) {
        if (argv.length==0 || (argv.length==1 && argv[0].equals("*"))) {
            // usage: group [*]
            // export all user-defined groups
            // output format is the group command to set the group up
            try {
                for (String group : get_vlnav().list_groups().values()) {
                    report("group "+qq(group)+" "+S.join(" \\\n    ", get_vlnav().find_group(group).toMap(), qqequals));
                    for (VLNav.UserDescription user : get_vlnav().list_users(group)) {
                        report("  user "+S.join(" ", qq(user.toStrings())));
                    }
                }
            } catch (Exception e) {
                error("could not list groups", e);
            }
        } else if (argv[0].equalsIgnoreCase("test")) {
            // usage: group test <definition>
            // parse <definition> for syntax but don't do anything with it
            if (argv.length==1) {
                error("usage: group test description");
            } else {
                try {
                    VLNav.GroupDescription desc = get_vlnav().new GroupDescription("test", Arrays.copyOfRange(argv, 1, argv.length));
                    report(desc.toString());
                } catch (Exception e) {
                    error("error parsing description", e);
                }
            }
        } else if (argv.length==1) {
            // usage: group <group>
            // export definition of <group>
            // output format is the group command to set the group up
            try {
                String group = argv[0];
                report("group "+qq(group)+" = "+get_vlnav().find_group(group));
            } catch (Exception e) {
                error("could not list groups", e);
            }
        } else {
            // usage: group <group> <definition>
            // create new <group> with <definition>
            String name = argv[0];
            try {
                VLNav.GroupDescription exist = get_vlnav().find_group(name);
                if (exist!=null) {
                    error("group "+name+" exists: "+exist);
                    VLNav.GroupDescription update = get_vlnav().new GroupDescription(name, Arrays.copyOfRange(argv, 1, argv.length));
                    report("update to "+update);
                } else {
                    VLNav.GroupDescription desc = get_vlnav().new GroupDescription(name, Arrays.copyOfRange(argv, 1, argv.length));
                    get_vlnav().create_group(desc);
                    report("group "+name+" created");
                }
            } catch (Exception e) {
                error("can not create group "+name, e);
            }
        }
    }
    @Command(name="describe", args="tables", comment="describe tables")
    public void describe(String...argv) {
        try {
            DB db = vldb;
            if (argv.length>0 && argv[0].equalsIgnoreCase("h2")) {
                db = h2db;
                argv = Arrays.copyOfRange(argv, 1, argv.length);
            }
            if (argv.length>0) {
                Set<String> tables     = new TreeSet<String>();
                Set<String> all_tables = db.tables().keySet();
                for (String arg : argv) {
                    // convert to regex pattern (at least add ?i)
                    String   pattern = S.glob2re(arg);
                    boolean found = false;
                    for (String table : all_tables) {
                        if (table.matches(pattern)) {
                            tables.add(table);
                            found = true;
                        }
                    }
                    if (!found) {
                        error("no tables found matching "+arg);
                    }
                }
                for (String table : tables) {
                    try {
                        if (db.table(table)==null) {
                            error("no such table: "+table);
                            continue;
                        }
                        table = db.table(table);
                        String[][] description = S.invert(db.describe(table));
                        report(new String[] {"", table}); // blank line before
                        report(new String[] {"Column", "Type"}, description);
                        //report(S.col(description, 0), new String[][] {S.col(description, 1)});
                    } catch (Exception e) {
                        error("cannot describe "+table, e);
                    }
                }
            } else {
                Map<String,Map<String,Integer>> t = db.tables();
                for (Map.Entry<String,Map<String,Integer>> e : t.entrySet()) {
                    for (Map.Entry<String,Integer> f : e.getValue().entrySet()) {
                        report(e.getKey()+"."+f.getKey()+" : "+f.getValue());
                    }
                }
            }
        } catch (Exception e) {
            error("cannot connect to database", e);
        }
    }

    public Shell() {
        // TODO: move this out of URI into a context or something so I can kill URI
        URI.setReporter(new Reporter(this));
        URI.setHome(new File("."));
    }

    public static void main(String[] argv) {
        Shell repl = new Shell();
        repl.run(argv);
        System.exit(0);
    }
}
