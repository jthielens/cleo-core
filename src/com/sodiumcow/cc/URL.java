package com.sodiumcow.cc;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sodiumcow.cc.constant.HostType;
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
    
    private HashMap<String,String> options;

    private Host    host;
    private Mailbox mailbox;

    public HostType getType() {
        return type;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String getFolder() {
        return folder;
    }

    public String getFilename() {
        return filename;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public HashMap<String, String> getOptions() {
        return options;
    }

    public Host getHost() {
        return host;
    }

    public Mailbox getMailbox() {
        return mailbox;
    }

    public String dump() {
        return (type==null?"null":type.toString())+
               "://"+address+":"+port+
               " options="+options.toString()+
               " folder="+folder+
               " filename="+filename+
               " user="+user+
               " password="+password;
    }
    
    private URL() {}

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
            return url;
        } else {
            return null;
        }
    }

    public void resolve(Core core) throws URLResolutionException, Exception {
        // check basic preconditions
        if (type==null) {
            throw new URLResolutionException("unrecognized protocol: "+raw);
        } else if (type.local) {
            throw new URLResolutionException("local protocol not supported: "+raw);
        } else if (user==null || password==null) {
            throw new URLResolutionException("username and password are required (for now): "+raw);
        }

        // Find matching host/mailbox or clone a preconfigured one
        String hostname = type.toString().toLowerCase()+"://"+address;
        host            = core.findHost(type, address, port);
        mailbox         = null;
        if (host==null) {
            host = core.activateHost(type, hostname);
            host.setProperty("Address", address);
            if (port>=0) {
                host.setProperty("Port", String.valueOf(port));
            }
        } else {
            mailbox = host.findMailbox(user, password);
        }
        host.save();
    }
}