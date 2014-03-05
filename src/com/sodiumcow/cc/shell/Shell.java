package com.sodiumcow.cc.shell;


import java.io.File;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;

import org.w3c.dom.Document;

import com.cleo.lexicom.certmgr.external.ICertManagerRunTime;
import com.cleo.lexicom.external.HostType;
import com.cleo.lexicom.external.ILicense;
import com.cleo.lexicom.external.ILicenser;
import com.cleo.lexicom.external.LexiComLogEvent;
import com.cleo.lexicom.external.LexiComLogListener;
import com.cleo.lexicom.external.RegistrationInfo;
import com.sodiumcow.cc.Action;
import com.sodiumcow.cc.Core;
import com.sodiumcow.cc.Host;
import com.sodiumcow.cc.Mailbox;
import com.sodiumcow.cc.Path;
import com.sodiumcow.cc.constant.Mode;
import com.sodiumcow.cc.constant.Packaging;
import com.sodiumcow.cc.constant.PathType;
import com.sodiumcow.cc.constant.Product;
import com.sodiumcow.cc.constant.Protocol;
import com.sodiumcow.repl.REPL;
import com.sodiumcow.repl.annotation.Command;
import com.sodiumcow.repl.annotation.Option;

public class Shell extends REPL {
    static Core core = new Core();
    
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
            core.disconnect();
        } catch (Exception e) {
            error("error disconnecting", e);
        }
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
                } catch (Exception e) {
                    error("report failed", e);
                }
            }
        }
    }

    @Command(name="register", args="serial", comment="register license")
    public void register_command(String...argv) {
        if (argv.length==1) {
            try {
                String serial = argv[0];
                report("attempting to retrieve registration for "+serial);
                ILicenser license = core.getLexiCom().getLicenser();
                RegistrationInfo reg = license.registrationQuery(serial);
                report_registration(reg);
                license.register(reg);
                report(serial+" registered, retreiving license information");
                report_license(core.getLexiCom().getLicense());
            } catch (Exception e) {
                error("register failed", e);
            }
        } else {
            error("register requires a single serial number xxxxxx-yyyyyy");
        }
    }

    @Command(name="unregister", comment="unregister license")
    public void unregister_command(String...argv) {
        try {
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
    public void list_types_command(String...argv) {
        try {
            for (HostType t : core.getLexiCom().listHostTypes()) {
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

    @Command(name="list", args="path with a *", comment="list objects")
    public void list(String...argv) {
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

    @Command(name="dump", args="host ...", comment="dump hosts")
    public void dump_host_command(String...argv) {
        try {
            for (String arg : argv) {
                report(arg);
                Document host = core.getHostDocument(new Path(arg));
                report(Util.xml2tree(host));
            }
        } catch (Exception e) {
            error("could not dump hosts", e);
        }
    }
    
    @Command(name="has", args="path property ...", comment="does path have property...")
    public void has_command(String...argv) {
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
    public void get_command(String...argv) {
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
    @Command(name="multi", args="path [boolean]", comment="get/set multiple IDs")
    public void multi_command(String...argv) {
        if (argv.length==0 || argv.length>2) {
            error("usage: multi path [true|false]");
        } else {
            try {
                Host h = new Host(core, argv[0]);
                report("multi ID currently "+h.isMultipleIDsAllowed());
                if (argv.length==2) {
                    if (argv[1].equalsIgnoreCase("false")) {
                        h.setMultipleIDsAllowed(false);
                        report("multi ID updated to "+h.isMultipleIDsAllowed());
                    } else if (argv[1].equalsIgnoreCase("true")) {
                        h.setMultipleIDsAllowed(true);
                        report("multi ID updated to "+h.isMultipleIDsAllowed());
                    } else {
                        error("true or false expected: "+argv[1]);
                    }
                }
            } catch (Exception e) {
                error("host lookup failed", e);
            }
        }
    }
    @Command(name="encode", args="[reverse] string...", comment="encode string(s) [reversed]")
    public void encode_command(String...argv) {
        boolean reverse = false;
        for (String arg : argv) {
            if (arg.equalsIgnoreCase("reverse")) {
                reverse = true;
            } else {
                try {
                    if (reverse) {
                        arg = new StringBuilder(arg).reverse().toString();
                    }
                    report(arg+" => "+core.getLexiCom().encode(arg));
                } catch (Exception e) {
                    error("could not encode", e);
                }
            }
        }
    }
    @Command(name="decode", args="[reverse] string...", comment="decode string(s) [reversed]")
    public void decode_command(String...argv) {
        boolean reverse = false;
        for (String arg : argv) {
            if (arg.equalsIgnoreCase("reverse")) {
                reverse = true;
            } else {
                try {
                    String decoded = core.getLexiCom().decode(arg);
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
    @Command(name="encrypt", args="string...", comment="encrypt string(s)")
    public void encrypt_command(String...argv) {
        for (String arg : argv) {
            try {
                report(arg+" => "+core.getLexiCom().encrypt(arg));
            } catch (Exception e) {
                error("could not encrypt", e);
            }
        }
    }
    @Command(name="decrypt", args="string...", comment="decrypt string(s)")
    public void decrypt_command(String...argv) {
        for (String arg : argv) {
            try {
                report(arg+" => "+core.getLexiCom().decrypt(arg));
            } catch (Exception e) {
                error("could not decrypt", e);
            }
        }
    }
    @Command(name="huh", comment="show stuff")
    public void huh(String...argv) {
        //report("bean separator="+LexUtil.beanSeparator);
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
            } else if (u.getType()==null) {
                error("unrecognized protocol: "+url);
            } else if (u.getType().local) {
                error("local protocol not supported: "+url);
            } else if (u.getUser()==null || u.getPassword()==null) {
                error("username and password are required (for now): "+url);
            } else {
                try {
                    // Clone and save a preconfigured host/mailbox
                    String  hostName    = u.getType().toString().toLowerCase()+"://"+u.getAddress();
                    Host    host        = core.activateHost(u.getType(), hostName);
                    Mailbox mailbox     = host.getMailboxes()[0];
                    host.setProperty("Address", u.getAddress());
                    if (u.getPort()>=0) {
                        host.setProperty("Port", String.valueOf(u.getPort()));
                    }
                    mailbox.setProperty("Username", u.getUser());
                    mailbox.setProperty("Password", u.getPassword());
                    host.save();
                    // Now create a temp action for sending
                    Action  action      = mailbox.newTempAction("send");
                    action.addLogListener(reporter);
                    
                    boolean ok = mailbox.send(f, u.getFolder(), u.getFilename());
                    report("send "+(ok?"succeeded":"failed"));
                    
                    action.removeLogListener(reporter);
                } catch (Exception e) {
                    error("trouble activating host", e);
                }
            }
        }
    }

    @Command(name="remove", args="path...", comment="remove nodes")
    public void remove(String...argv) {
        for (String arg : argv) {
            try {
                core.remove(new Path(arg));
            } catch (Exception e) {
                error("error removing "+arg, e);
            }
        }
    }

    public static void main(String[] argv) {
        Shell repl = new Shell();
        repl.run(argv);
        System.exit(0);
    }
}
