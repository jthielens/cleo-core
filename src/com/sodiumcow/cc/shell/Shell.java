package com.sodiumcow.cc.shell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.DatatypeConverter;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

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
import com.sodiumcow.cc.Host;
import com.sodiumcow.cc.LDAP;
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
import com.sodiumcow.cc.shell.VLNav.Privilege;
import com.sodiumcow.repl.REPL;
import com.sodiumcow.repl.annotation.Command;
import com.sodiumcow.repl.annotation.Option;

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
        print(Util.join(" ", argv));
    }

    private void demo_registration(RegistrationInfo reg) {
        reg.setFirstName("Cleo");
        reg.setLastName("Demonstration");
        reg.setTitle("Employee");
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
        return new LDAP(Util.submap(read_xml_file("Users.xml").map, "Users", "Ldapserver"), core);
    }

    private DBOptions db_set = null;
    private DB db = null;
    private Map<String,Map<String,Integer>> db_tables = null;
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
    private boolean db_string(String table, String column) {
        if (db_tables!=null && db_tables.get(table)!=null && db_tables.get(table).get(column)!=null) {
            int type = db_tables.get(table).get(column);
            return type==Types.VARCHAR;
        }
        return false;
    }
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
                for (File ca : new File(certPath).listFiles()) {
                    if (!ca.isFile()) continue;
                    try {
                        Collection<X509Certificate> certs = certManager.readCert(ca);
                        for (X509Certificate x : certs) {
                            report(certManager.getFriendlyName(x));
                        }
                    } catch (Exception e) {
                        error("error listing cert", e);
                    }
                }
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

    private Node subnode(String type, String name, Node node) {
        if (node==null || node.getNodeType()!=Node.ELEMENT_NODE) return null;
        Node find = node.getFirstChild();
        while (find != null) {
            NamedNodeMap attrs;
            Node         alias = null;
            if (find.getNodeType()==Node.ELEMENT_NODE         &&
                find.getNodeName().equalsIgnoreCase(type)     &&
                (attrs = find.getAttributes()) != null        &&
                (alias = attrs.getNamedItem("alias")) != null &&
                alias.getNodeValue().equalsIgnoreCase(name)   ) {
                return find;
            }
            find = find.getNextSibling();
        }
        return null;
    }
    @Command(name="dump", args="path ...", comment="dump hosts")
    public void dump_command(String...argv) {
        try {
            for (String arg : argv) {
                Path     p    = Path.parsePath(arg);
                String[] path = p.getPath();
                Node node = core.getHostDocument(p.getHost()).getDocumentElement();
                switch (p.getType()) {
                case MAILBOX:
                    node = subnode("Mailbox",    path[1], node);
                    break;
                case ACTION:
                    node = subnode("Mailbox",    path[1], node);
                    node = subnode("Action",     path[2], node);
                    break;
                case HOST_ACTION:
                    node = subnode("HostAction", path[1], node);
                    break;
                case SERVICE:
                    node = subnode("Service",    path[1], node);
                    break;
                case TRADING_PARTNER:
                    node = null; // no such thing any more really
                    continue;
                case HOST:
                    break;
                }
                if (node!=null) {
                    report(Util.xml2pp(node));
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
                if (argv.length==3) {
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
                boolean exists = core.exists(new Path(arg));
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
    @Command(name="schedule", args="action", comment="get action schedule")
    public void schedule(String...argv) {
        if (argv.length<1) {
            error("usage: schedule action [null|schedule]");
        } else if (argv.length==1 && argv[0].equals("*")) {
            try {
                for (ISchedule.Item schedule : core.getLexiCom().getSchedule().listItems()) {
                    report(new Path(schedule.getAction()).toString()+":");
                    report("   "+new Schedule(schedule).toString());
                }
            } catch (Exception e) {
                error("error listing schedules", e);
            }
        } else if (argv.length==1) {
            Path path = Path.parsePath(argv[0]);
            if (path.getType() != PathType.ACTION && path.getType() != PathType.HOST_ACTION) {
                error("schedule applicable for action or host action only");
            } else {
                try {
                    Action action = new Action(core, path);
                    Schedule schedule = action.getSchedule();
                    report("   "+schedule.toString());
                } catch (Exception e) {
                    error("error getting schedule", e);
                }
            }
        } else if (argv.length==2 && argv[1].equalsIgnoreCase("null")) {
            Path path = Path.parsePath(argv[0]);
            Action action = new Action(core, path);
            try {
                action.setSchedule(null);
            } catch (Exception e) {
                error("error setting schedule", e);
            }
        } else {
            Path path = Path.parsePath(argv[0]);
            Action action = new Action(core, path);
            Schedule schedule = new Schedule(Util.join(" ",  1, argv));
            try {
                action.setSchedule(schedule);
            } catch (Exception e) {
                error("error setting schedule", e);
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
                    report(arg+" => "+core.encode(arg));
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
                    String decoded = core.decode(arg);
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
    @Command(name="xcode", args="[reverse] string...", comment="encode string(s) [reversed]")
    public void xcode(String...argv) {
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
    @Command(name="zcode", args="[reverse] string...", comment="decode string(s) [reversed]")
    public void zcode(String...argv) {
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
                xml = read_xml_file(src);
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
                report(URL.parseURL(arg).dump());
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
    private XmlReadResult read_xml_file(String fn) throws Exception {
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
            xml.map = Util.xml2map(Util.string2xml(xml.file.contents));
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

    @Command(name="opts", args="[file|table[(query)] [path[=value]]]", comment="display options")
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
                    where = Util.split(name.substring(paren+1, name.length()-1),
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
                    ArrayList<String> select_columns = new ArrayList<String>();
                    ArrayList<String> update_columns = new ArrayList<String>();
                    ArrayList<Object> update_values  = new ArrayList<Object>();
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
                            }
                        }
                        DB.Selection.Result r = selection.rows();
                        if (r.count==1 && r.columns.length>4) {
                            report(new String[] {"Column", "Value"}, Util.invert(r.columns, r.rows[0]));
                        } else {
                            report(r.columns, r.rows);
                            report(r.count+" rows selected"+(updated>0 ? ", "+updated+" rows updated" : ""));
                        }
                    }
                } else if (name.matches("(?i).*xml")) {
                    XmlReadResult xml = read_xml_file(name);
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
                                // no big deal -- not LDAP
                                Util.setmap(xml.map, path, kv[1]);
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
    static final Pattern DB_PATTERN = Pattern.compile("(.*):(.*)@(.*)/(.*)");
    public static class DBOptions {
        public String connection;
        public String user;
        public String password;
        public DBOptions (String connection, String user, String password) {
            this.connection = connection;
            this.user       = user;
            this.password   = password;
        }
        public DBOptions (DBConnection c) {
            this(c.getConnectionString(), c.getUserName(), Util.decode(c.getPassword()));
        }
    }
    @Command(name="db", args="find|set|remove|create string", comment="find/set db connection")
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
                    Matcher m = DB_PATTERN.matcher(string);
                    if (m.matches()) {
                        Options.DBConnection c = o.new DBConnection();
                        c.setConnectionType(Options.DBConnection.DB_CONTYPE_MYSQL);
                        db_set = new DBOptions("jdbc:mysql://"+m.group(3)+"/"+m.group(4), m.group(1), m.group(2));
                        c.setConnectionString(db_set.connection);
                        c.setDriverString("com.mysql.jdbc.Driver");
                        c.setUserName(db_set.user);
                        c.setPassword(db_set.password);
                        o.updateDBConnection(c);
                        o.save();
                        c = o.findDBConnection(db_set.connection);
                        Util.report_bean(this, c);
                    } else {
                        error("usage: db set user:password@host/database");
                    }
                } else if (command.equalsIgnoreCase("remove")) {
                    o.removeDBConnection(string);
                    o.save();
                } else if (command.equalsIgnoreCase("create") || command.equalsIgnoreCase("drop")) {
                    Matcher m = DB_PATTERN.matcher(string);
                    if (m.matches()) {
                        try {
                            DB db = new DB("jdbc:mysql://"+m.group(3), m.group(1), m.group(2));
                            db.execute(command+" database "+m.group(4));
                            report("database "+m.group(4)+" "+(command.equalsIgnoreCase("create")?"created":"dropped"));
                        } catch (SQLException e) {
                            error("can not "+command+" database "+m.group(4), e);
                        }
                    } else {
                        error("usage: db create user:password@host/database");
                    }
                } else {
                    error("usage: db find|set string");
                }
            } catch (Exception e) {
                error("error getting options", e);
            }
        }
    }
    static final Pattern LDAP_PATTERN = Pattern.compile("");
    // ldap[s][(type|opt|map...)]://[user:pass@]host[:port]/basedn[?filter]
    // type = ad|apache|domino|novell|dirx
    // opt  = starttls|default
    // map  = (user|mail|name|home|first|last)=attr
    @Command(name="ldap", args="not sure", comment="not sure")
    public void ldap(String...argv) {
        new LDAP(argv[0]);
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
                    o.setTransferLogging(Options.TRANSFER_LOG_DATABASE);
                    o.setTransferLoggingDBConnectionStr(argv[0]);
                    o.setTransferLoggingEnabled(true);
                }
                o.save();
            } catch (Exception e) {
                error("error setting options", e);
            }
        }
    }
    @Command(name="huh", args="string", comment="testing")
    public void huh(String...argv) {
        if (argv.length>0) {
            try {
                LDAP ldap = get_ldap();
                Map<LDAP.Attribute, String> found = ldap.find(argv[0]);
                report(Util.join("\n", found, "%s = %s"));
                //report("parsing "+Util.join(" ", argv));
                //report(new Schedule(Util.join(" ", argv)).toString());
            } catch (Exception e) {
                error("error: ", e);
            }
            if (argv.length>1) {
                try {
                    report("group "+argv[1]+" = "+new VLNav(get_db()).find_group(argv[1]));
                } catch (Exception e) {
                    error("error: ", e);
                }
            }
        } else {
            for (Provider p : Security.getProviders()) {
                report(p.getName()+" - "+p.getInfo());
            }
        }
    }
    @Command(name="new_host", args="url", comment="create a new remote host")
    public void new_host(String...argv) {
        if (argv.length!=1) {
            error("usage: new host url");
        } else {
            String url = argv[0];
            URL    u   = URL.parseURL(url);
            if (u==null) {
                error("could not parse URL: "+url);
            } else {
                try {
                    u.resolve(core);
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
                        report("mailbox exists: "+mailbox.getProperty("alias")[0]);
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
                } catch (URLResolutionException e) {
                    error(e.getMessage());
                } catch (Exception e) {
                    error("trouble activating host", e);
                }
            }
        }
    }
    @Command(name="new_user", args="prot:user:pass ...", comment="create users")
    public void new_user(String...argv) {
        for (String arg : argv) {
            // validate input
            String[] parts = arg.split(":", 3);
            if (parts.length != 3) {
                error("usage: new user prot:user:pass");
                return;
            }
            HostType prot;
            try {
                prot = HostType.valueOf("LOCAL_"+parts[0].toUpperCase());
            } catch (IllegalArgumentException e) {
                error("protcol must be ftp, http, or sftp");
                return;
            }
            String username = parts[1];
            String password = parts[2];
            try {
                // valid input -- find "host"
                Host host = core.getHost(prot.template);
                if (host==null) {
                    host = core.activateHost(prot, prot.template);
                }
                // got host -- find "mailbox"
                Mailbox mailbox = host.findMailbox(username, password);
                if (mailbox!=null) {
                    report("user "+username+" already exists");
                } else {
                    mailbox = host.createMailbox(username);
                    mailbox.setProperty("Homedirectory", username);
                    report("user "+username+" created");
                }
                mailbox.setProperty("Password", password);
                report("user "+username+" password set");
                host.save();
            } catch (Exception e) {
                error("error creating "+username, e);
            }
        }
    }
    @Command(name="new_action", args="path command...", comment="create new action")
    public void new_action(String...argv) {
        if (argv.length<=1) {
            error("usage: get path property ...");
        } else {
            Path path = Path.parsePath(argv[0]);
            if (path.getType() != PathType.ACTION && path.getType() != PathType.HOST_ACTION) {
                error("<action>user@host or <action>host expected: "+argv[0]);
            } else {
                try {
                    path = core.create(path);
                    report("Commands", Util.join("\\n", 1, argv));
                    core.setProperty(path, "Commands", Util.join("\\n", 1, argv));
                    core.save(path);
                } catch (Exception e) {
                    error("error creating action", e);
                }
            }
        }
    }
    @Command(name="new_group", args="name", comment="create a new VLNav group")
    public void new_group(String...argv) {
        if (argv.length<1) {
            error("usage: new group name [(privilege|filter|application)=value ...]");
        } else {
            String group  = argv[0];
            String privs  = "*=*";
            String filter = null;
            List<String> apps = new ArrayList<String>();
            boolean      err  = false;
            for (int i=1; i<argv.length; i++) {
                String[] kv = argv[i].split("=", 2);
                if (kv.length<2) {
                    error("privilege|filter|application=value expected: "+argv[i]);
                    err = true;
                } else if (kv[0].equalsIgnoreCase("privilege")) {
                    privs = kv[1];
                } else if (kv[0].equalsIgnoreCase("filter")) {
                    filter = kv[1];
                } else if (kv[0].equalsIgnoreCase("application")) {
                    for (String a : kv[1].split(",")) {
                        apps.add(a);
                    }
                } else {
                    err = true;
                    error("privilege|filter|application expected: "+kv[0]);
                }
            }
            if (!err) {
                try {
                    VLNav vln = new VLNav(get_db());
                    vln.create_group(group, Privilege.of(privs), filter, apps);
                    report("group "+group+" created");
                } catch (Exception e) {
                    error("can not create group "+group, e);
                }
            }
        }
    }
    @Command(name="describe", args="tables", comment="describe tables")
    public void describe(String...argv) {
        try {
            if (argv.length>0) {
                if (argv.length==1 && argv[0].matches(".*[\\?\\*\\[\\]].*")) {
                    String   pattern = "(?i)"+argv[0].replaceAll("\\.", "\\.")
                                                     .replaceAll("\\?", ".")
                                                     .replaceAll("\\*", ".*");
                    String[] tables = get_db().tables().keySet().toArray(new String[0]);
                    List<String> matches = new ArrayList<String>();
                    for (String table : tables) {
                        if (table.matches(pattern)) matches.add(table);
                    }
                    argv = matches.toArray(new String[matches.size()]);
                    if (argv.length==0) {
                        error("no tables found matching "+pattern);
                    }
                }
                for (String table : argv) {
                    try {
                        if (db_table(table)==null) {
                            error("no such table: "+table);
                            continue;
                        }
                        table = db_table(table);
                        String[][] description = Util.invert(get_db().describe(table));
                        report(new String[] {"", table}); // blank line before
                        report(new String[] {"Column", "Type"}, description);
                        //report(Util.col(description, 0), new String[][] {Util.col(description, 1)});
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
