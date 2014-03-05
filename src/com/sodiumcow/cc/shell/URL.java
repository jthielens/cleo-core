package com.sodiumcow.cc.shell;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sodiumcow.cc.constant.HostType;

public class URL {
    private HostType type;
    private String   address;
    private int      port;
    
    private String   folder;
    private String   filename;
    
    private String   user;
    private String   password;
    
    private HashMap<String,String> options;
    
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

}