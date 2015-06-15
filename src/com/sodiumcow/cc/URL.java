package com.sodiumcow.cc;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cleo.labs.util.S;
import com.sodiumcow.cc.constant.HostType;
import com.sodiumcow.cc.constant.Protocol;
import com.sodiumcow.cc.exception.URLResolutionException;

public class URL {
    private String   raw;

    private HostType type;
    private String   address;
    private int      port;
    
    private String   folder;
    private String   filename;
    
    private String   user;
    private String   password;
    
    private Map<String,String> options;
    private Map<String,String> hostProperties;
    private Map<String,String> mailboxProperties;

    private Host    host;
    private Mailbox mailbox;

    public HostType            getType             () { return type;              } 
    public String              getAddress          () { return address;           } 
    public int                 getPort             () { return port;              } 
    public String              getFolder           () { return folder;            } 
    public String              getFilename         () { return filename;          } 
    public String              getUser             () { return user;              } 
    public String              getPassword         () { return password;          } 
    public Map<String, String> getOptions          () { return options;           } 
    public Map<String, String> getHostProperties   () { return hostProperties;    }
    public Map<String, String> getMailboxProperties() { return mailboxProperties; }
    public Host                getHost             () { return host;              }
    public Mailbox             getMailbox          () { return mailbox;           }

    public String              getProtocol         () {
        return type==null ? "null" : type.name().toLowerCase();
    }

    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(getProtocol());
        if (options!=null && !options.isEmpty()) {
            s.append('(').append(S.join(",", options, "%s=%s")).append(')');
        }
        s.append("://");
        if (user!=null || password!=null) {
            if (user!=null) s.append(user);
            if (password!=null) s.append(':').append(password);
            s.append('@');
        }
        s.append(address);
        if (port>=0) {
            s.append(':').append(port);
        }
        if (folder!=null || filename!=null) {
            s.append('/');
            if (folder!=null) s.append(folder).append('/');
            if (filename!=null) s.append(filename);
        }
        return s.toString();
    }
    
    private URL() {}

    public URL(HostType type) {
        this.type               = type;
        this.address            = null;
        this.port               = -1;
        this.folder             = null;
        this.filename           = null;
        this.user               = null;
        this.password           = null;
        this.options            = new HashMap<String,String>();
        this.hostProperties     = new HashMap<String,String>();
        this.mailboxProperties  = new HashMap<String,String>();
    }

    public URL extractHost(Map<String,String> hostProperties) {
        this.hostProperties.clear();
        this.address = null;
        this.port = -1;
        if (hostProperties!=null) {
            this.address = hostProperties.remove("Address");
            if (this.address!=null) {
                this.hostProperties.put("Address", this.address);
            }
            String port = hostProperties.remove("Port");
            if (port!=null) {
                this.port = Integer.valueOf(port);
                this.hostProperties.put("Port", port);
            }
            this.raw = this.toString();
        }
        return this;
    }
    public URL extractMailbox(Map<String,String> mailboxProperties) {
        this.mailboxProperties.clear();
        this.user = null;
        this.password = null;
        if (mailboxProperties!=null) {
            if (this.type.protocol==Protocol.HTTP_CLIENT) {
                this.user = mailboxProperties.remove("Authusername");
                if (this.user!=null) {
                    this.mailboxProperties.put("Authusername", this.user);
                }
                this.password = mailboxProperties.remove("Authpassword");
                if (this.password!=null) {
                    this.mailboxProperties.put("Authpassword", this.password);
                }
                String authtype = mailboxProperties.get("Authtype");
                if ("1".equals(authtype)) {
                    this.mailboxProperties.put("Authtype", mailboxProperties.remove("Authtype"));
                }
            } else {
                this.user = mailboxProperties.remove("Username");
                if (this.user!=null) {
                    this.mailboxProperties.put("Username", this.user);
                }
                this.password = mailboxProperties.remove("Password");
                if (this.password!=null) {
                    this.mailboxProperties.put("Password", this.password);
                }
            }
            this.raw = this.toString();
        }
        return this;
    }

    // protocol[(options)]://[user[:pass]@]host[:port][/[folder/]filename]
    static final Pattern PARSE = Pattern.compile(
        "(\\w+)(?:\\((.*)\\))?://(?:([^:]+)(?::([^@]+))?@)?([^:/]+)(?::(\\d+))?(?:/(.*/)?(.*))?");
    static public URL parseURL(String s) {
        Matcher m = PARSE.matcher(s);
        if (m.matches()) {
            String protocol = m.group(1);
            String options  = m.group(2);
            String user     = m.group(3);
            String password = m.group(4);
            String host     = m.group(5);
            String port     = m.group(6);
            String folder   = m.group(7);
            String filename = m.group(8);
            
            URL url = new URL();
            url.raw = s;
            try {
                url.type = HostType.valueOf(protocol.toUpperCase());
            } catch (IllegalArgumentException e) {
                url.type = null;
            }
            url.address = host;
            url.port    = port==null?-1:Integer.valueOf(port);
            
            url.folder  = folder;
            url.filename= filename;
            
            url.user    = user;
            url.password= password;
            
            url.options = new HashMap<String,String>();
            if (options != null) {
                for (String opt : options.split("[,\\s]+")) {
                    String[] pair = opt.split("=", 2);
                    if (pair.length==2) {
                        url.options.put(pair[0], pair[1]);
                    } else {
                        url.options.put(pair[0], null);
                    }
                }
            }

            url.hostProperties = new HashMap<String,String>();
            url.hostProperties.put("Address", host);
            if (port!=null) url.hostProperties.put("Port", port);

            url.mailboxProperties = url.stuffUser(user, password);
            if (url.type!=null && url.type.protocol==Protocol.HTTP_CLIENT) {
                if (user!=null) url.mailboxProperties.put("Authusername", user);
                if (password!=null) {
                    url.mailboxProperties.put("Authpassword", password);
                    url.mailboxProperties.put("Authtype", "1");
                }
            } else {
                if (user!=null) url.mailboxProperties.put("Username", user);
                if (password!=null) url.mailboxProperties.put("Password", password);
            }
            return url;
        } else {
            return null;
        }
    }

    private Map<String,String> stuffUser(String user, String password) {
        Map<String,String> result = new HashMap<String,String>();
        if (this.type!=null && this.type.protocol==Protocol.HTTP_CLIENT) {
            if (user!=null) result.put("Authusername", user);
            if (password!=null) {
                result.put("Authpassword", password);
                result.put("Authtype", "1");
            }
        } else {
            if (user!=null) result.put("Username", user);
            if (password!=null) result.put("Password", password);
        }
        return result;
    }

    public Map<String,String> parseMailbox(String alias) {
        String user     = null;
        String password = null;
        if (alias.matches("[^\\(]+\\(.*\\)")) {
            // alias(username[:password])
            String[] au = alias.split("\\(", 2);
            au[1] = au[1].substring(0, au[1].length()-1);
            String[] up = au[1].split(":", 2);
            alias = au[0];
            if (!up[0].isEmpty()) user  = up[0];
            if (!up[1].isEmpty()) password = up[1];
        } else {
            String[] ap = alias.split(":", 2);
            if (ap.length>1) {
                // alias:password
                alias = ap[0];
                user  = alias;
                if (!ap[1].isEmpty()) password = ap[1];
            } else {
                // alias
            }
        }
        Map<String,String> result = stuffUser(user, password);
        result.put(".alias", alias);
        return result;
    }

    public String formatMailbox(String alias) {
        if (alias.equals(getUser())) {
            if (getPassword()!=null) {
                alias += ":"+getPassword();
            }
        } else {
            StringBuilder s = new StringBuilder();
            s.append(alias).append('(');
            if (getUser()!=null) s.append(getUser());
            if (getPassword()!=null) s.append(':').append(getPassword());
            s.append(')');
            alias = s.toString();
        }
        return alias;
    }

    public void resolve(Core core) throws URLResolutionException, Exception {
        resolve(core, options.get("alias"));
    }
    public void resolve(Core core, String alias) throws URLResolutionException, Exception {
        // check basic preconditions
        if (type==null) {
            throw new URLResolutionException("unrecognized protocol: "+raw);
        } else if (type.local) {
            throw new URLResolutionException("local protocol not supported: "+raw);
        } else if (user==null || password==null) {
            throw new URLResolutionException("username and password are required (for now): "+raw);
        }

        // Find matching host/mailbox or clone a preconfigured one
        Host[] hosts;
        if (alias!=null && !alias.equals("*")) {
            // lookup up existing host and enforce consistency if found
            Host host = core.getHost(alias);
            if (host==null) {
                hosts = null;
            } else if (host.getHostType() != type ||
                !host.getSingleProperty("Address").equalsIgnoreCase(address) ||
                Integer.valueOf(host.getSingleProperty("Port")) != port) {
                throw new URLResolutionException("incompatible host for "+alias+": "+raw);
            } else {
                hosts = new Host[] {host};
            }
        } else {
            // just find one by attributes
            hosts = core.findHosts(type, address, port);
            alias = getProtocol()+"://"+address;
        }
        mailbox = null;
        if (hosts==null || hosts.length==0) {
            host = core.activateHost(type, alias);
            host.setProperty("Address", address);
            if (port>=0) {
                host.setProperty("Port", String.valueOf(port));
            }
        } else {
            host = hosts[0];  // default choice is just the first one, unless...
            mailbox = host.findMailbox(user, null);
        }

        // create a mailbox if there wasn't a match
        if (mailbox==null) {
            try {
                mailbox = host.cloneMailbox(user);
            } catch (Exception e) {
                // maybe template isn't where we expect it -- just make new
                mailbox = host.createMailbox(user);
            }
            for (Map.Entry<String,String> e : mailboxProperties.entrySet()) {
                mailbox.setProperty(e.getKey(), e.getValue());
            }
        }
        host.save();
    }
}