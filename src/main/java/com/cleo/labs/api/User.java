package com.cleo.labs.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cleo.labs.api.constant.HostType;
import com.cleo.labs.util.S;

public class User {
    public static class Description {
        public String             folder;
        public String             group;
        public Map<String,String> group_properties;
        public String             username;
        public String             password;
        public String             typename;
        public HostType           type;
        public String             root;
        public Map<String,String> properties;
        public List<String>       notes = null;

        private static String[] splitFolder(String alias) {
            int slash = alias.lastIndexOf('/');
            return slash>=0 ? new String[] { alias.substring(0, slash).replace('/', '\\'),
                                             alias.substring(slash+1) }
                            : new String[] { null, alias };
        }

        public Description(String[] spec) {
            if (spec.length<2) {
                throw new IllegalArgumentException("invalid user description: folder/username:password@group type:root");
            }

            // [folder/]username[:password]
            String[] folderup = splitFolder(spec[0]);
            folder   = folderup[0];
            String[] usergroup = folderup[1].split("@", 2);
            String[] userpass = usergroup[0].split(":", 2);
            username = userpass[0];
            password = userpass.length>1 ? userpass[1] : null;
            group    = usergroup.length>1 ? usergroup[1] : null;
            
            // group_properties
            int type_index = 1;
            group_properties = new HashMap<String,String>();
            while (type_index<spec.length && spec[type_index].contains("=")) {
                String[] av = spec[type_index].split("=", 2);
                group_properties.put(av[0], av.length>1 ? av[1] : "");
                type_index++;
            }

            // type[:root-folder-or-uri]
            typename = spec[type_index];
            root = null;
            if (typename.contains(":")) {
                String[] tr = typename.split(":", 2);
                typename = tr[0];
                root     = tr[1];
            }
            try {
                type = HostType.valueOf("LOCAL_"+typename.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("invalid user description: unrecognized type (protocol)");
            }

            // properties
            properties = new HashMap<String,String>();
            for (int i=type_index+1; i<spec.length; i++) {
                String[] av = spec[i].split("=", 2);
                properties.put(av[0], av.length>1 ? av[1] : "");
            }

            // notes
            note("parsed user description "+S.join(" ", spec)); 
        }

        public Description(Host h, Mailbox m) throws Exception {
            type       = h.getHostType();
            group      = h.getPath().getAlias();
            group_properties = Defaults.suppressHostDefaults(type, h.getProperties());
            group_properties.remove(homedir(type));
            root       = h.getSingleProperty(homedir(type));
            typename   = type.name().substring("LOCAL_".length()).toLowerCase();
            folder     = h.getSingleProperty("folder");
            // cleanup folder: \ to / and ending in / (unless empty)
            folder     = folder==null?"":folder.replace('\\', '/');
            username   = m.getPath().getAlias();
            properties = Defaults.suppressMailboxDefaults(type, m.getProperties());
            if (username.equals(properties.get("Homedirectory"))) {
                properties.remove("Homedirectory");
            }
            password = properties.remove("Password");
            if (password!=null) {
                password = LexiCom.decode(password);
            } else {
                String hash = properties.get("Pwdhash");
                if (hash!=null && !hash.isEmpty()) {
                    password = LexiCom.crack(username, hash);
                    if (password.equals(hash)) {
                        password = null;
                    } else {
                        properties.remove("Pwdhash");
                    }
                }
            }
            note("user from host "+h.getPath().getAlias()+" mailbox "+username);
        }

        public Description note(String note) {
            if (notes==null) {
                notes = new ArrayList<String>();
            }
//System.err.println(note);
            notes.add(note);
            return this;
        }

        public String[] toStrings() {
            int group_count = (group_properties==null?0:group_properties.size());
            String[] strings = new String[2+group_count
                                           +(properties==null?0:properties.size())];
            strings[0] = S.all(folder,"/")+S.s(username)+S.all(":",password)+S.all("@",group);
            if (group_properties!=null) {
                int i=1;
                for (Map.Entry<String,String> e : group_properties.entrySet()) {
                    strings[i] = S.s(e.getKey())+"="+S.s(e.getValue());
                    i++;
                }
            }
            strings[group_count+1] = S.s(typename);
            if (root!=null && !root.equals(Defaults.getHostDefaults(type).get(homedir(type)))) {
                strings[group_count+1] += ":"+root;
            }
            if (properties!=null) {
                int i=group_count+2;
                for (Map.Entry<String,String> e : properties.entrySet()) {
                    strings[i] = S.s(e.getKey())+"="+S.s(e.getValue());
                    i++;
                }
            }
            return strings;
        }

        public String toString() {
            return S.join(" ", toStrings());
        }
    }

    public interface Filter {
        public boolean accept(Description user);
    }
    public static class RegexFilter implements Filter {
        private String regex;
        public RegexFilter(String regex) {
            this.regex = regex;
        }
        public boolean accept(Description user) {
            return regex==null || user.username.matches(regex);
        }
    }

    private static String homedir(HostType type) {
        if (type == HostType.LOCAL_USER) {
            return "Defaulthomedir";
        } else {
            return "Ftprootpath";
        }
    }

    public static Description[] list(Filter filter) throws Exception {
        List<Description> users = new ArrayList<Description>();
        for (Host h : LexiCom.getHosts()) {
            if (h.isLocal() && h.getHostType()!=null) {
                for (Mailbox m : h.getMailboxes()) {
                    if (!LexiCom.exists(m.getPath())) continue; // I don't know why VL returns non-existing ones, but it does
                    if (m.getSingleProperty("enabled").equalsIgnoreCase("True")) {
                        Description u = new Description(h, m);
                        if (filter==null || filter.accept(u)) {
                            users.add(new Description(h, m));
                        }
                    }
                }
            }
        }
        return users.toArray(new Description[users.size()]);
    }

    private static Host find_host(Description user) throws Exception {
        String hostalias;
        Host host = null;
        if (user.group != null) {
            host = LexiCom.getHost(user.group);
        } else {
            host = LexiCom.findLocalHost(user.type, user.root, user.folder);
        }
        if (host==null) {
            if (user.group != null) {
                hostalias = user.group+S.all(":", user.root);
            } else if (user.type == HostType.LOCAL_USER) {
                hostalias = "users"+S.all(": ", user.root);
            } else {
                hostalias = user.typename+S.all(":", user.root)+" users";
            }
            if (user.folder!=null) {
                String[] folders = user.folder.split("\\\\");
                hostalias = folders[folders.length-1]+"/"+hostalias;
            }
user.note("host not found: proposing "+hostalias);
            //hostalias.replace('/', '#');
            if (LexiCom.exists(new Path(hostalias))) {
                int uniquer = 0;
                String test;
                do {
                    uniquer++;
                    test = hostalias+"["+uniquer+"]";
                } while (LexiCom.exists(new Path(test)));
                hostalias = test;
user.note("had to make it unique as "+hostalias);
            }
            host = LexiCom.activateHost(user.type, hostalias);
            if (user.folder!=null) {
user.note("setting folder "+user.folder);
                host.setProperty("folder", user.folder);
            }
            //host.save();
            for (Mailbox m : host.getMailboxes()) {
                String alias = m.getSingleProperty("alias");
                if (alias.startsWith("myTradingPartner")) {
                    if (user.root!=null && user.root.matches("\\w{2,}:.*")) {
                        // looks like a URI -- kill the template mailbox
                        m.remove();
user.note("deleted template "+alias+": "+m.getPath());
                    } else {
                        m.setProperty("enabled", "False");
                        m.rename(Host.TEMPLATE_MAILBOX);
                        m.save();
user.note("renamed template "+alias+": "+m.getPath());
                    }
                    break;
                }
            }
            //host.save();
            if (user.root!=null) {
user.note("setting "+homedir(host.getHostType())+" "+user.root);
                host.setProperty(homedir(host.getHostType()), user.root);
            }
        } else {
            hostalias = host.getSingleProperty("alias");
user.note("found host "+hostalias);
        }
        return host;
    }

    private static final Pattern USERSET = Pattern.compile("(.*)\\[(\\d+)\\.\\.(\\d+)\\](.*)");

    public static Description update(Description user) throws Exception {
        Host host = find_host(user);
        // see if it is a user set
        String format = user.username.replace("%", "%%");
        int from = 0;
        int to = 0;
        Matcher m = USERSET.matcher(user.username);
        if (m.matches()) {
            format = m.group(1).replace("%", "%%")+"%d"+m.group(4).replace("%", "%%");
            from = Integer.valueOf(m.group(2));
            to = Integer.valueOf(m.group(3));
        }
        // iterate to create users
        for (int i=from; i<=to; i++) {
            String username = String.format(format, i);
            // got host -- find "mailbox"
            Mailbox mailbox = host.findMailbox(username, null); //user.password);
            if (mailbox==null) {
                try {
                    mailbox = host.cloneMailbox(username);
                    user.note("created new mailbox "+username+" from template");
                } catch (Exception e) {
                    // maybe template isn't where we expect it -- just make new
                    mailbox = host.createMailbox(username);
                    user.note("created new mailbox "+username);
                }
            } else {
                user.note("updating existing mailbox "+username);
            }
            if (user.password!=null) {
                mailbox.setProperty("Password", user.password);
                user.note("user "+username+" password set");
            }
            boolean homeset = false;
            for (Map.Entry<String,String> e : user.properties.entrySet()) {
                mailbox.setProperty(e.getKey(), e.getValue());
                if (e.getKey().equalsIgnoreCase("Homedirectory")) {
                    homeset = true;
                }
            }
            if (!homeset) {
                mailbox.setProperty("Homedirectory", username);
            }
            mailbox.setProperty("Enabled", "True");
        }
        // finally save the host
        host.save();
        return user;
    }

    public static Description remove(Description user) throws Exception {
        Host host = LexiCom.findLocalHost(user.type, user.root, user.folder);
        if (host==null) {
            user.note("user not removed: host not found");
        } else {
            Mailbox mailbox = host.findMailbox(user.username, null);
            if (mailbox==null) {
                user.note("user not removed: mailbox not found");
            } else {
                LexiCom.remove(mailbox.getPath());
                user.note("user "+user.username+" removed");
            }
        }
        return user;
    }

    private User() {}
}
