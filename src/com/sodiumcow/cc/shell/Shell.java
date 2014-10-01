package com.sodiumcow.cc.shell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.DatatypeConverter;

import com.cleo.lexicom.beans.Options;
import com.cleo.lexicom.beans.Options.DBConnection;
import com.cleo.lexicom.certmgr.external.ICertManagerRunTime;
import com.cleo.lexicom.external.DirectoryEntry;
import com.cleo.lexicom.external.ILicense;
import com.cleo.lexicom.external.ILicenser;
import com.cleo.lexicom.external.ISchedule;
import com.cleo.lexicom.external.LexiComLogEvent;
import com.cleo.lexicom.external.LexiComLogListener;
import com.cleo.lexicom.external.RegistrationInfo;
import com.sodiumcow.cc.Action;
import com.sodiumcow.cc.Core;
import com.sodiumcow.cc.Defaults;
import com.sodiumcow.cc.Host;
import com.sodiumcow.cc.Item;
import com.sodiumcow.cc.Mailbox;
import com.sodiumcow.cc.Path;
import com.sodiumcow.cc.Schedule;
import com.sodiumcow.cc.URL;
import com.sodiumcow.cc.constant.HostSource;
import com.sodiumcow.cc.constant.HostType;
import com.sodiumcow.cc.constant.Mode;
import com.sodiumcow.cc.constant.Packaging;
import com.sodiumcow.cc.constant.PathType;
import com.sodiumcow.cc.constant.Product;
import com.sodiumcow.cc.constant.Protocol;
import com.sodiumcow.cc.exception.URLResolutionException;
import com.sodiumcow.repl.REPL;
import com.sodiumcow.repl.annotation.Command;
import com.sodiumcow.repl.annotation.Option;
import com.sodiumcow.util.DB;
import com.sodiumcow.util.F;
import com.sodiumcow.util.LDAP;
import com.sodiumcow.util.S;

public class Shell extends REPL {
    Core             core = new Core();
    
    LexiComLogListener reporter = new LexiComLogListener() {
        public void log(LexiComLogEvent e) {
            if (!e.getEvent().getNodeName().equals("Stop")) {
                report(e.getMessage());
            }
        }
    };

    @Option(name="h", args="home", comment="installation directory")
    public void home_option(String arg) {
        core.setHome(new File(arg));
    }
    
    @Option(name="p", args="product", comment="H | VLT | LC")
    public void product_option(String arg) {
        try {
            core.setProduct(Product.valueOf(arg.toUpperCase()));
        } catch (Exception e) {
            if (arg.equalsIgnoreCase("H")) {
                core.setProduct(Product.HARMONY);
            } else if (arg.equalsIgnoreCase("VLT")) {
                core.setProduct(Product.VLTRADER);
            } else if (arg.equalsIgnoreCase("LC")) {
                core.setProduct(Product.LEXICOM);
            } else {
                error("unrecognized product: "+arg);
            }
        }
    }
    
    @Option(name="m", args="mode", comment="STANDALONE | DISTRIBUTED")
    public void mode_option(String arg) {
        try {
            core.setMode(Mode.valueOf(arg.toUpperCase()));
        } catch (Exception e) {
            error("unrecognized mode: "+arg);
        }
    }

    @Override
    public void disconnect() {
        try {
            disconnect_db();
            core.disconnect();
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
        report(Util.licensed_product(license.getProduct())
               +" ["+license.getSerialNumber()+"]"
               +" on "+Util.licensed_hosts(license.getAllowedHosts())
               +" "+Util.licensed_until(license));
        report("  platforms: "+Util.licensed_platform(license.getPlatform()));
        report("  features:  "+Util.licensed_features(license));
    }

    @Command(name="report", args="[serial]", comment="license or serial # information")
    public void report_command(String...argv) {
        if (argv.length==0) {
            try {
                report("attempting to retrieve license");
                report_license(core.getLexiCom().getLicense());
            } catch (Exception e) {
                error("license retrieval failed", e);
            }
        } else {
            if (core.getMode() != Mode.STANDALONE) {
                report("switching to -m STANDALONE");
                core.setMode(Mode.STANDALONE);
            }
            for (String serial : argv) {
                try {
                    report("attempting to retrieve registration for "+serial);
                    ILicenser license = core.getLexiCom().getLicenser();
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
                if (core.getMode() != Mode.STANDALONE) {
                    report("switching to -m STANDALONE");
                    core.setMode(Mode.STANDALONE);
                }
                if (argv[0].matches("\\w{6}-\\w{6}")) {
                    String serial = argv[0];
                    report("attempting to retrieve registration for "+serial);
                    ILicenser license = core.getLexiCom().getLicenser();
                    RegistrationInfo current_reg = license.registrationQuery(serial);
                    demo_registration(current_reg);
                    report_registration(current_reg);
                    license.register(current_reg);
                    report(serial+" registered, retreiving license information");
                } else {
                    String fn = argv[0];
                    ILicenser license = core.getLexiCom().getLicenser();
                    @SuppressWarnings("unused")
                    ILicense content = license.licenseFile(fn, true);
                }
                report_license(core.getLexiCom().getLicense());
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
            if (core.getMode() != Mode.STANDALONE) {
                report("switching to -m STANDALONE");
                core.setMode(Mode.STANDALONE);
            }
            ILicenser license = core.getLexiCom().getLicenser();
            license.unregister();
        } catch (Exception e) {
            error("unregister failed", e);
        }
    }

    @Command(name="close", comment="close LexiCom session")
    public void close_command(String...argv) {
        try {
            core.disconnect();
        } catch (Exception e) {
            error("close failed", e);
        }
    }

    @Command(name="list_types", comment="list host types")
    public void list_types(String...argv) {
        try {
            for (com.cleo.lexicom.external.HostType t : core.getLexiCom().listHostTypes()) {
                Protocol  prot = Protocol.valueOf(t.getHostProtocol());
                Packaging pack = Packaging.valueOf(t.getMailboxPackaging());
                report(t.getName()+":"+
                       " protocol="+prot+
                       " packaging="+pack+
                       " local="+t.isLocal());
                if (pack==null) {
                    report("   packaging="+t.getMailboxPackaging());
                }
            }
        } catch (Exception e) {
            error("could not list host types", e);
        }
    }
    
    private LDAP get_ldap() throws Exception {
        return new LDAP(Util.submap(read_xml_file("Users.xml").map, "Ldapserver"), core);
    }

    private DBOptions db_set = null;
    private DB db = null;
    private Map<String,Map<String,Integer>> db_tables = null;
    /**
     * Converts a table name from case-insensitive to case-sensitive.
     * @param table the case-insensitive table name
     * @return the tble name in its proper case-sensitive form
     * @throws Exception
     */
    private String db_table(String table) throws Exception {
        get_db();
        if (db_tables!=null) {
            for (String t : db_tables.keySet()) {
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
    private String db_column(String table, String column) {
        if (db_tables!=null) {
            Map<String,Integer> columns = db_tables.get(table);
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
    private boolean db_string(String table, String column) {
        if (db_tables!=null && db_tables.get(table)!=null && db_tables.get(table).get(column)!=null) {
            int type = db_tables.get(table).get(column);
            return type==Types.VARCHAR;
        }
        return false;
    }
    /**
     * If the DB connection is not yet established, attempts to connect
     * to the database last set.  If none has been set, retrieves the DB
     * connection string from the transfer logging configuration.
     * <p>
     * If a successful connection is established, the schema is pulled and
     * cached in db_tables (table names, column names and types).  This
     * allows case-insensitive queries and auto conversion of string/integer
     * types.
     * <p>
     * If the DB connection was already established, the cached db connection
     * is returned.
     * @return
     * @throws Exception
     */
    private DB get_db() throws Exception {
        if (db==null) {
            if (db_set==null) {
                try {
                    Options o = core.getLexiCom().getOptions();
                    DBConnection c = o.getTransferLoggingDBConnection();
                    if (c!=null) {
                        db_set = new DBOptions(c);
                    }
                } catch (Exception e) {
                    // error will fall out
                }
            }
            if (db_set==null) {
                throw new Exception("use db set or db find first");
            } else {
                db = new DB(db_set.connection, db_set.user, db_set.password);
            }
        }
        if (db_tables==null || db_tables.isEmpty()) {
            db_tables = db.tables();
        }
        return db;
    }
    private void disconnect_db() {
        if (db!=null) {
            db.disconnect();
        }
    }
    private VLNav get_vlnav() throws Exception {
        return new VLNav(get_db());
    }
    @Command(name="list_certs", comment="list user certificate aliases")
    public void list_certs(String...argv) {
        if (argv.length>0) {
            error("usage: list certs");
        } else {
            try {
                ICertManagerRunTime certManager = core.getLexiCom().getCertManager();
                for (@SuppressWarnings("unchecked")
                     Enumeration<String> e=certManager.getUserAliases();
                     e.hasMoreElements();) {
                    String alias = e.nextElement();
                    report(alias);
                }
            } catch (Exception e) {
                error("error listing certs", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Command(name="list_cas", comment="list certificate authority aliases")
    public void list_cas(String...argv) {
        if (argv.length>0) {
            error("usage: list cas");
        } else {
            try {
                ICertManagerRunTime certManager = core.getLexiCom().getCertManager();
                String certPath = core.getLexiCom().getAbsolute("certs");
                Map<String,String> cas = new TreeMap<String,String>();
                for (File ca : new File(certPath).listFiles()) {
                    if (!ca.isFile()) continue;
                    try {
                        Collection<X509Certificate> certs = certManager.readCert(ca);
                        for (X509Certificate x : certs) {
                            cas.put(certManager.getFriendlyName(x), "certs/"+ca.getName());
                        }
                    } catch (Exception e) {
                        error("error listing cert", e);
                    }
                }
                report(new String[] {"CA", "File"}, S.invert(cas));
                /* this way doesn't work because getCAFiles seems to lowercase everything
                for (Enumeration<String> e=certManager.getCAFiles();
                     e.hasMoreElements();) {
                    String ca = e.nextElement();
                    report(ca);
                    Collection<X509Certificate> certs = certManager.readCert(certPath+"/"+ca);
                    for (X509Certificate x : certs) {
                        report(". ", certManager.getFriendlyName(x));
                    }
                }
                */
            } catch (Exception e) {
                error("error listing certs", e);
            }
        }
    }
    @Command(name="trust", args="file...", comment="trust CA(s)")
    public void trust(String...argv) {
        ICertManagerRunTime certManager;
        try {
            certManager = core.getLexiCom().getCertManager();
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
    public void import_key(String...argv) {
        if (argv.length != 4) {
            error("usage: import key alias key cert password");
        } else {
            try {
                String alias    = argv[0].toUpperCase();
                File   key      = new File(argv[1]);
                File   cert     = new File(argv[2]);
                String password = argv[3];
                if (!key.isFile()) {
                    error("key file not found: "+argv[1]);
                } else if (!cert.isFile()) {
                    error("cert file not found: "+argv[2]);
                } else {
                    ICertManagerRunTime certManager = core.getLexiCom().getCertManager();
                    certManager.importUserCertKey(alias, cert, key, password, true/*replace*/, false/*addPassword*/);
                    report("key \""+alias+"\" imported");
                }
            } catch (Exception e) {
                error("error importing key", e);
            }
        }
    }

    @Command(name="uri_list", args="[id ...]", comment="install URI drivers")
    public void uri_list(String...argv) {
        try {
            Properties props = URI.load(core.getHome());
            Map<String,URI> uris = new HashMap<String,URI>();
            for (URI uri : URI.getSchemes(props)) {
                uris.put(uri.id, uri);
                if (argv.length==0) {
                    report(uri.id+" ("+S.join(File.pathSeparator, uri.classPath)+")");
                }
            }
            for (String arg : argv) {
                if (uris.containsKey(arg)) {
                    URI uri = uris.get(arg);
                    report(new String[] {
                        uri.id+":",
                       "cleo.uri."+uri.id+".file="+uri.file,
                       "cleo.uri."+uri.id+".inputstream="+uri.inputStream,
                       "cleo.uri."+uri.id+".outputStream="+uri.outputStream,
                       "cleo.uri."+uri.id+".classpath="+S.join(File.pathSeparator, uri.classPath)});
                } else {
                    error("uri not installed: "+arg);
                }
            }
        } catch (Exception e) {
            error("error listing URIs", e);
        }
    }
    @Command(name="uri_install", args="file.jar ...", comment="install URI drivers")
    public void uri_install(String...argv) {
        if (argv.length < 1) {
            error("usage: uri install file.jar ...");
        } else {
            try {
                URI  scheme = new URI(core.getHome(), argv);
                File lib    = new File(core.getHome(), "lib/uri");
                for (int i=0; i<argv.length; i++) {
                    argv[i] = F.copy(new File(argv[i]), lib, F.ClobberMode.UNIQUE).getAbsolutePath();
                }
                scheme = new URI(core.getHome(), argv);
                Properties props = URI.load(core.getHome());
                URI.setScheme(props, scheme);
                URI.store(core.getHome(), props);
                report(new String[] {
                        scheme.id+":",
                       "cleo.uri."+scheme.id+".file="+scheme.file,
                       "cleo.uri."+scheme.id+".inputstream="+scheme.inputStream,
                       "cleo.uri."+scheme.id+".outputStream="+scheme.outputStream,
                       "cleo.uri."+scheme.id+".classpath="+S.join(File.pathSeparator, scheme.classPath)});
            } catch (Exception e) {
                error("error installing URI", e);
            }
        }
    }
    @Command(name="uri_remove", args="id ...", comment="remove URI drivers")
    public void uri_remove(String...argv) {
        if (argv.length<1) {
            error("usage: uri remove id ...");
            return;
        }
        try {
            Properties props = URI.load(core.getHome());
            Map<String,URI> uris = new HashMap<String,URI>();
            for (URI uri : URI.getSchemes(props)) {
                uris.put(uri.id, uri);
            }
            for (String arg : argv) {
                if (uris.containsKey(arg)) {
                    URI.removeScheme(props, arg);
                    // remove files?
                } else {
                    error("uri not installed: "+arg);
                }
            }
            URI.store(core.getHome(), props);
        } catch (Exception e) {
            error("error listing URIs", e);
        }
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
            for (Path item : core.list(path.getType(), path.getParent())) {
                report(item.toString());
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
                        Defaults.printAllDefaults(System.out, core);
                    } else {
                        // usage: dump type:audit
                        // Calculates and prints new Defaults for <type>
                        Defaults.printDefaults(System.out, core, HostType.valueOf(name.toUpperCase()));
                    }
                } else {
                    Path     path = Path.parsePath(name);
                    Item     item = Item.getItem(core, path);
                    HostType type = new Host(core, path.getHost()).getHostType();
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
                        String[][] description = S.invert(Util.flat(Util.xml2map(item.getNode()), depth));
                        report(new String[] {"Attribute", "Value"}, description);
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
    
    @Command(name="has", args="path property ...", comment="does path have property...")
    public void has(String...argv) {
        if (argv.length<=1) {
            error("usage: has path property ...");
        } else {
            Path path = Path.parsePath(argv[0]);
            for (int i=1; i<argv.length; i++) {
                String  prop = argv[i];
                boolean has;
                try {
                    has = core.hasProperty(path, prop);
                    report(path+(has?" has    ":" has no ")+prop);
                } catch (Exception e) {
                    error("error checking "+prop, e);
                }
            }
        }
    }
    @Command(name="get", args="path property ...", comment="get property...")
    public void get(String...argv) {
        if (argv.length<=1) {
            error("usage: get path property ...");
        } else {
            Path path = Path.parsePath(argv[0]);
            for (int i=1; i<argv.length; i++) {
                String   prop = argv[i];
                String[] values;
                try {
                    values = core.getProperty(path, prop);
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
    public void set(String...argv) {
        if (argv.length<2) {
            error("usage: set path property [value ...]");
        } else {
            try {
                Path path = Path.parsePath(argv[0]);
                String property = argv[1];
                if (argv.length==2) {
                    core.setProperty(path, property, (String) null);
                } else if (argv.length==3) {
                    core.setProperty(path, property, argv[2]);
                } else {
                    core.setProperty(path, property, Arrays.copyOfRange(argv, 2, argv.length));
                }
                if (autosave) {
                    core.save(path);
                }
            } catch (Exception e) {
                error("could not set property", e);
            }
        }
    }
    @Command(name="exists", args="path...", comment="check for object")
    public void exists(String...argv) {
        for (String arg : argv) {
            try {
                boolean exists = core.exists(Path.parsePath(arg));
                report(arg+(exists?" exists":" doesn't exist"));
            } catch (Exception e) {
                error("error checking "+arg, e);
            }
        }
    }
    @Command(name="rename", args="path alias", comment="rename object")
    public void rename(String...argv) {
        if (argv.length!=2) {
            error("usage: rename path alias");
        } else {
            try {
                Path path = Path.parsePath(argv[0]);
                String alias = argv[1];
                core.rename(path, alias);
            } catch (Exception e) {
                error("could not rename", e);
            }
        }
    }
    @Command(name="lookup", args="(host|mailbox) id", comment="lookup by id")
    public void lookup(String...argv) {
        if (argv.length!=2) {
            error("usage: lookup (host|mailbox) id");
        } else if (argv[0].equalsIgnoreCase("host")) {
            try {
                Path found = core.lookup(PathType.HOST,  argv[1]);
                if (found==null) {
                    error("lookup["+argv[1]+"] not found");
                } else {
                    report("lookup["+argv[1]+"]=", found.toString());
                }
            } catch (Exception e) {
                error("error looking up mailbox", e);
            }
        } else if (argv[0].equalsIgnoreCase("mailbox")) {
            try {
                Path found = core.lookup(PathType.MAILBOX,  argv[1]);
                if (found==null) {
                    error("lookup["+argv[1]+"] not found");
                } else {
                    report("lookup["+argv[1]+"]=", found.toString());
                }
            } catch (Exception e) {
                error("error looking up mailbox", e);
            }
        } else {
            error("usage: lookup (host|mailbox) id");
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
                    core.save(path);
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
            src = core.encrypt(src);
            if (argv.length>1) {
                File out = new File(argv[1]);
                FileOutputStream fos = new FileOutputStream(out);
                fos.write(DatatypeConverter.parseBase64Binary(src));
                fos.close();
            } else {
                report(src);
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
                src = core.decrypt(src);
            }
            if (argv.length>1) {
                Util.string2file(argv[1], src);
            } else if (xml!=null) {
                report(Util.map2tree(xml.map));
                Map<String,Object> ldapmap = Util.submap(xml.map, "Users", "Ldapserver");
                if (ldapmap!=null) {
                    LDAP ldap = new LDAP(ldapmap, core);
                    report(ldap.toString());
                }
            } else {
                report(src);
            }
        } catch (Exception e) {
            error("could not decrypt", e);
        }
    }
    @Command(name="url", args="url...", comment="parse url string(s)")
    public void url(String...argv) {
        for (String arg : argv) {
            try {
                /*Matcher m = Pattern.compile("(.*)\\?[^\\]/?]*").matcher(arg);
                if (m.matches()) {
                    report("matches: "+m.group(1).length());
                } else {
                    report("does not match: -1");
                    report("null test: "+arg.equalsIgnoreCase(null));
                }
                */
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
                    u.resolve(core);
                    Host host = u.getHost();
                    if (host.getSource()==HostSource.ACTIVATE) {
                        report("created host "+host.getPath());
                    } else {
                        report("reusing host "+host.getPath());
                    }
                    // Now setup mailbox
                    Mailbox mailbox = u.getMailbox();
                    if (mailbox!=null) {
                        report("mailbox exists: "+mailbox.getProperty("alias")[0]);
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
                    u.resolve(core);
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
                core.remove(new Path(arg));
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
            xml.file = Util.file2string(fn, core);
        } catch (FileNotFoundException e1) {
            try {
                xml.file = Util.file2string(new File(core.getHome(), fn), core);
            } catch (FileNotFoundException e2) {
                try {
                    xml.file = Util.file2string(new File(new File(core.getHome(), "conf"), fn), core);
                } catch (FileNotFoundException e3) {
                    try {
                        xml.file = Util.file2string(new File(new File(core.getHome(), "hosts"), fn), core);
                    } catch (FileNotFoundException e4) {
                        return null;
                    }
                }
            }
        }
        try {
            if (document) {
                xml.map = Util.xml2map(Util.string2xml(xml.file.contents));
            } else {
                xml.map = Util.xml2map(Util.string2xml(xml.file.contents).getDocumentElement());
            }
        } catch (Exception e) {
            xml.map = null; /// must not be XML
        }
        return xml;
    }
    private void write_xml_file(XmlReadResult xml) throws Exception {
        if (xml.map!=null) {
            xml.file.contents = Util.xml2string(Util.map2xml(xml.map));
        }
        Util.string2file(xml.file, core);
    }

    @Command(name="opts", args="[file|table[(query)] [path[=value]]]", comment="display/update options")
    public void opts(String...argv) {
        try {
            if (argv.length==0) {
                // usage: opts
                // just dumps out core iLexCom options
                Options o = core.getLexiCom().getOptions();
                Util.report_bean(this, o);
            } else {
                String name = argv[0];
                String table = name;
                Map<String,String> where = null;
                boolean err = false;
                int paren = name.indexOf('(');
                if (paren>=0 && name.endsWith(")")) {
                    table = name.substring(0, paren);
                    where = S.split(name.substring(paren+1, name.length()-1),
                                       Pattern.compile("[\\s,]*(\\w+)=([^,]*)[\\s,]*"));
                }
                table = db_table(table);
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
                            qcolumns[i] = db_column(table, c);
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
                            String column = db_column(table, kv[0]);
                            if (column==null) {
                                error("no such column "+table+"."+kv[0]);
                                err=true;
                                continue;
                            }
                            if (kv.length>1) {
                                update_columns.add(column);
                                if (db_string(table, column)) {
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
                        DB.Selection selection = get_db().new Selection(table, columns, qcolumns, qargs);
                        int updated = 0;
                        if (update_columns.size()>0) {
                            updated = selection.update(update_columns, update_values);
                            if (updated==0) {
                                // convert to insert
                                get_db().insert(table,
                                                S.cat(Arrays.asList(qcolumns), update_columns),
                                                S.cat(Arrays.asList(qargs), update_values));
                                selection = get_db().new Selection(table, columns, qcolumns, qargs);
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
                            try {
                                LDAP ldap = new LDAP(kv[1]);
                                Util.setmap(xml.map, path, ldap.toMap(core));
                            } catch (Exception e) {
                                // no big deal -- not LDAP, but see if it's DB
                                try {
                                    DBOptions dbo = new DBOptions(kv[1]);
                                    Util.setmap(xml.map, path, dbo.connection);
                                } catch (Exception f) {
                                    // no big deal -- not LDAP or DB -- just a String
                                    Util.setmap(xml.map, path, kv[1]);
                                }
                            }
                        } else {
                            Object o = Util.subobj(xml.map, path);
                            if (o instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String,Object> out = (Map<String,Object>)o;
                                report(Util.map2tree(out));
                            } else {
                                String out = (String)o;
                                report(out);
                            }
                        }
                    }
                    if (updated) {
                        write_xml_file(xml);
                        report("file "+xml.file.file.getPath()+" updated");
                    } else if (argv.length<=1) {
                        report(Util.map2tree(xml.map));
                        //report(Util.xml2string(Util.map2xml(xml.map)));
                    }
                } else {
                    report(Util.file2string(name, null).contents);
                }
            }
        } catch (Error r) {
            error("error: "+ r);
        } catch (Exception e) {
            error("error getting options", e);
        }
    }
    public static class DBOptions {
        public enum Vendor { MYSQL, ORACLE, SQLSERVER, DB2; }
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
            this.password   = Util.decode(c.getPassword());
            this.vendor     = Vendor.valueOf(this.connection.split(":")[1].toUpperCase()); // jdbc:vendor:stuff
        }
        public Options.DBConnection getDBConnection(Core core) throws Exception {
            Options.DBConnection c = core.getLexiCom().getOptions().new DBConnection(); 
            c.setConnectionType(this.type);
            c.setConnectionString(this.connection);
            c.setDriverString(this.driver);
            c.setUserName(this.user);
            c.setPassword(this.password);
            return c;
        }
    }
    @Command(name="db", args="find|set|remove|create|drop string", comment="find/set db connection")
    public void db(String...argv) {
        if (argv.length!=2) {
            error("usage: db find|set string");
        } else {
            String command = argv[0];
            String string  = argv[1];
            try {
                Options o = core.getLexiCom().getOptions();
                if (command.equalsIgnoreCase("find")) {
                    Options.DBConnection c = o.findDBConnection(string);
                    if (c==null) {
                        error("not found");
                    } else {
                        db_set = new DBOptions(c);
                        Util.report_bean(this, c);
                    }
                } else if (command.equalsIgnoreCase("set")) {
                    try {
                        db_set = new DBOptions(string);
                        Options.DBConnection c = db_set.getDBConnection(core);
                        o.updateDBConnection(c);
                        o.save();
                        c = o.findDBConnection(db_set.connection);
                        Util.report_bean(this, c);
                    } catch (Exception e) {
                        error("usage: db set type:user:password@host[:port]/database");
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
            if (argv.length==0 || argv[0].equals("*")) {
                ldap = get_ldap();
                report(ldap.toString());
            } else {
                ldap = new LDAP(argv[0]);
            }
            for (int i=1; i<argv.length; i++) {
                Map<LDAP.Attribute, String> found = ldap.find(argv[i]);
                report(S.join("\n", found, "%s = %s"));
            }
        } catch (Exception e) {
            error("error", e);
        }
    }
    @Command(name="xferlog", args="off|xml|db-string", comment="transfer logging")
    public void xferlog(String...argv) {
        if (argv.length>1) {
            error("usage: xferlog off|xml|db-string");
        } else {
            try {
                Options o = core.getLexiCom().getOptions();
                if (argv[0].equalsIgnoreCase("off")) {
                    o.setTransferLogging(Options.TRANSFER_LOG_OFF);
                    o.setTransferLoggingEnabled(false);
                } else if (argv[0].equalsIgnoreCase("xml")) {
                    o.setTransferLogging(Options.TRANSFER_LOG_XML);
                    o.setTransferLoggingEnabled(true);
                } else {
                    try {
                        DBOptions dbo = new DBOptions(argv[0]);
                        o.setTransferLogging(Options.TRANSFER_LOG_DATABASE);
                        o.setTransferLoggingDBConnectionStr(dbo.connection);
                        o.setTransferLoggingEnabled(true);
                    } catch (Exception e) {
                        error("usage: xferlog type:user:password@host[:port]/database");
                    }
                }
                o.save();
            } catch (Exception e) {
                error("error setting options", e);
            }
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
                               ? core.getHosts()
                               : new Host[] {core.getHost(argv[0])};
                for (Host h : hosts) {
                    if (h==null) {
                        error("host "+qq(argv[0])+" not found");
                    } else if (!h.isLocal()) {
                        HostType           type   = h.getHostType();
                        Map<String,String> props  = Defaults.suppressHostDefaults(type, h.getProperties());
                        URL                url    = new URL(type).extractHost(props);
                        String             alias  = h.getPath().getAlias();
                        List<String>       output = new ArrayList<String>();
                        if (alias.equals(url.getProtocol()+"://"+url.getAddress())) {
                            alias = "*";
                        }
                        if (props!=null && !props.isEmpty()) {
                            output.add("host "+qq(alias)+" "+qq(url.toString())+" "+S.join(" \\\n    ", props, qqequals));
                        } else {
                            output.add("host "+qq(alias)+" "+qq(url.toString()));
                        }
                        for (Mailbox m : h.getMailboxes()) {
                            if (m.getSingleProperty("enabled").equalsIgnoreCase("True")) {
                                // get the properties
                                Map<String,String> mprops = Defaults.suppressMailboxDefaults(type, m.getProperties());
                                Defaults.crackPasswords(mprops);
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
        // usage: host alias url [property=value...] [mailbox alias[(user:password)] [property=value...]...]
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
            Host host = core.getHost(alias);
            if (host==null) {
                host = core.activateHost(u.getType(), alias);
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
                    mailbox = host.cloneMailbox(malias);
                    report("created new mailbox "+malias);
                } else {
                    report("updating existing mailbox "+malias);
                }
                for (Map.Entry<String, String> prop : mbx.getValue().entrySet()) {
                    mailbox.setProperty(prop.getKey(), prop.getValue());
                }
                mailbox.setProperty("enabled", "True");
                host.save();
            }
            /* old code
            u.resolve(core, alias);
            Host host = u.getHost();
            if (host.getSource()==HostSource.ACTIVATE) {
                report("created host "+host.getPath());
                Mailbox template = host.getMailbox("myMailbox");
                if (template!=null) {
                    template.setProperty("enabled", "False");
                    template.rename(Host.TEMPLATE_MAILBOX);
                    host.save();
                    report("renamed template: "+template.getPath());
                }
            } else {
                report("reusing host "+host.getPath());
            }
            Mailbox mailbox = u.getMailbox();
            if (mailbox!=null) {
                report("mailbox exists: "+mailbox.getSingleProperty("alias"));
            } else {
                report("mailbox being created for "+u.getUser()+":"+u.getPassword());
                mailbox = host.cloneMailbox(u.getUser());
                report("protocol is "+host.getProtocol());
                if (host.getProtocol()==Protocol.HTTP_CLIENT) {
                    mailbox.setProperty("Authtype", "1"); // BASIC
                    mailbox.setProperty("Authusername", u.getUser());
                    mailbox.setProperty("Authpassword", u.getPassword());
                } else {
                    mailbox.setProperty("Username", u.getUser());
                    mailbox.setProperty("Password", u.getPassword());
                }
                mailbox.setProperty("enabled", "True");
                host.save();
            }
            */
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
                String user = (argv.length==0 || argv[0].equals("*"))
                              ? null : argv[0];
                for (Host h : core.getHosts()) {
                    if (h.isLocal()) {
                        HostType type = h.getHostType();
                        for (Mailbox m : h.getMailboxes()) {
                            String malias = m.getPath().getAlias();
                            if (m.getSingleProperty("enabled").equalsIgnoreCase("True") && (user==null || malias.matches(user))) {
                                Map<String,String> mprops = Defaults.suppressMailboxDefaults(type, m.getProperties());
                                Defaults.crackPasswords(mprops);
                                if (malias.equals(mprops.get("Homedirectory"))) {
                                    mprops.remove("Homedirectory");
                                }
                                String password = mprops.remove("Password");
                                String userpass = qq(malias) + (password!=null ? ":"+qq(password) : "");
                                String typename = type.name().substring("LOCAL_".length()).toLowerCase();
                                report("  user "+userpass+" "+typename+" "+S.join(" \\\n    ", mprops, qqequals));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                error("error listing users", e);
            }
            return;
        }
        // now in create user mode
        // user name[:password] type {property=value ...}
        String[] userpass = argv[0].split(":", 2);
        String username = userpass[0];
        String password = userpass.length>1 ? userpass[1] : null;
        
        String typename = argv[1];
        HostType type;
        try {
            type = HostType.valueOf("LOCAL_"+typename.toUpperCase());
        } catch (IllegalArgumentException e) {
            error("protcol must be ftp, http, or sftp");
            return;
        }
        try {
            // valid input -- find "host"
            Host host = core.getHost(type.template);
            if (host==null) {
                host = core.activateHost(type, type.template);
                Mailbox template = host.getMailbox("myTradingPartner");
                if (template!=null) {
                    template.setProperty("enabled", "False");
                    template.rename(Host.TEMPLATE_MAILBOX);
                    report("renamed template: "+template.getPath());
                }
            }
            // got host -- find "mailbox"
            Mailbox mailbox = host.findMailbox(username, password);
            if (mailbox==null) {
                mailbox = host.cloneMailbox(username);
                report("created new mailbox "+username);
            } else {
                report("updating existing mailbox "+username);
            }
            if (password!=null) {
                mailbox.setProperty("Password", password);
                report("user "+username+" password set");
            }
            boolean homeset = false;
            for (int i=2; i<argv.length; i++) {
                String[] av = argv[i].split("=", 2);
                mailbox.setProperty(av[0], av.length>1 ? av[1] : "");
                if (av[0].equalsIgnoreCase("Homedirectory")) {
                    homeset = true;
                }
            }
            if (!homeset) {
                mailbox.setProperty("Homedirectory", username);
            }
            mailbox.setProperty("Enabled", "True");
            host.save();
        } catch (Exception e) {
            error("error creating "+username, e);
        }
    }
    @Command(name="action", args="[path [command...]]", comment="export/create action(s)")
    public void action(String...argv) {
        if (argv.length==0 || (argv.length==1 && argv[0].equals("*"))) {
            // usage: action [*]
            // export all scheduled actions
            // output format is the action commands to set the actions up
            try {
                for (ISchedule.Item schedule : core.getLexiCom().getSchedule().listItems()) {
                    Path path = new Path(schedule.getAction());
                    String[] commands = core.getProperty(path, "Commands")[0].split("\n");
                    report("action "+qq(path.toString())+" ", S.join(" \\\n",qq(commands)));
                }
            } catch (Exception e) {
                error("error listing scheduled actions", e);
            }
        } else if (argv.length==1) {
            // usage: action <action>
            // export the scheduled action <action>
            // output format is the action command to set the action up
            String name = argv[0];
            try {
                Path path = Path.parsePath(name);
                String[] commands = core.getProperty(path, "Commands")[0].split("\n");
                report("action "+qq(path.toString())+" ", S.join("\\\n",qq(commands)));
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
                    if (!core.exists(path)) {
                        path = core.create(path);
                    }
                    core.setProperty(path, "Commands", commands);
                    core.save(path);
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
                for (ISchedule.Item schedule : core.getLexiCom().getSchedule().listItems()) {
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
                    Action action = new Action(core, path);
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
            Action action = new Action(core, path);
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
            Action   action = new Action(core, path);
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
                core.getLexiCom().getSchedule().setAutoStartup(true);
                core.getLexiCom().getSchedule().save();
                report("scheduler autostart set to on");
            } else if (argv.length==2 && argv[0].equalsIgnoreCase("autostart") && argv[1].equalsIgnoreCase("off")) {
                core.getLexiCom().getSchedule().setAutoStartup(false);
                report("scheduler autostart set to off");
                core.getLexiCom().getSchedule().save();
            } else if (argv.length==1 && argv[0].equalsIgnoreCase("autostart")) {
                report("scheduler autostart is "+(core.getLexiCom().getSchedule().isAutoStartup()?"on":"off"));
            } else if (argv.length==1 && argv[0].equalsIgnoreCase("start")) {
                core.getLexiCom().startService();
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
                String[] groups = get_vlnav().list_groups();
                for (String group : groups) {
                    report("group "+group+" "+S.join(" \\\n    ", get_vlnav().find_group(group).toMap(), qqequals));
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
                report("group "+group+" = "+get_vlnav().find_group(group));
            } catch (Exception e) {
                error("could not list groups", e);
            }
        } else {
            // usage: group <group> <definition>
            // create new <group> with <definition>
            String name = argv[0];
            try {
                VLNav.GroupDescription desc = get_vlnav().new GroupDescription(name, Arrays.copyOfRange(argv, 1, argv.length));
                get_vlnav().create_group(desc);
                report("group "+name+" created");
            } catch (Exception e) {
                error("can not create group "+name, e);
            }
        }
    }
    @Command(name="describe", args="tables", comment="describe tables")
    public void describe(String...argv) {
        try {
            if (argv.length>0) {
                Set<String> tables     = new TreeSet<String>();
                Set<String> all_tables = get_db().tables().keySet();
                for (String arg : argv) {
                    // convert to regex pattern (at least add ?i)
                    String   pattern = "(?i)"+arg.replaceAll("\\.", "\\.")
                                                 .replaceAll("\\?", ".")
                                                 .replaceAll("\\*", ".*");
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
                        if (db_table(table)==null) {
                            error("no such table: "+table);
                            continue;
                        }
                        table = db_table(table);
                        String[][] description = S.invert(get_db().describe(table));
                        report(new String[] {"", table}); // blank line before
                        report(new String[] {"Column", "Type"}, description);
                        //report(S.col(description, 0), new String[][] {S.col(description, 1)});
                    } catch (Exception e) {
                        error("cannot describe "+table, e);
                    }
                }
            } else {
                Map<String,Map<String,Integer>> t = get_db().tables();
                for (Map.Entry<String,Map<String,Integer>> e : t.entrySet()) {
                    for (Map.Entry<String,Integer> f : e.getValue().entrySet()) {
                        report(e.getKey()+"."+f.getKey()+" : "+f.getValue());
                    }
                }
            }
        } catch (Exception e) {
            error("can connect to database");
        }
    }
    public static void main(String[] argv) {
        Shell repl = new Shell();
        repl.run(argv);
        System.exit(0);
    }
}
