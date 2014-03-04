package com.sodiumcow.cc.shell;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sodiumcow.cc.constant.HostType;

public class URL {
    private HostType type;
    private String   address;
    private int      port;
    
    private String   user;
    private String   password;
    
    private HashMap<String,String> options;
    
    public String dump() {
        return type.toString()+"://"+address+":"+port+
               " options="+options.toString()+
               " user="+user+
               " password="+password;
    }
    
    private URL() {}

    // protocol[(options)]://[user[:pass]@]host[:port]
    static final Pattern PARSE = Pattern.compile(
        "(\\w+)(?:\\((.*)\\))?://(?:([^:]+)(?::([^@]+))?@)?([^:]+)(?::(\\d+))?");
    static public URL parseURL(String s) {
        Matcher m = PARSE.matcher(s);
        if (m.matches()) {
            String protocol = m.group(1);
            String options  = m.group(2);
            String user     = m.group(3);
            String password = m.group(4);
            String host     = m.group(5);
            String port     = m.group(6);
            
            URL url = new URL();
            url.type    = HostType.valueOf(protocol.toUpperCase());
            url.address = host;
            url.port    = port==null?-1:Integer.valueOf(port);
            
            url.user    = user;
            url.password=password;
            
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