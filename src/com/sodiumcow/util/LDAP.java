package com.sodiumcow.util;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import com.sodiumcow.cc.Core; // for encrypt/decrypt -- convert to callback

public class LDAP {

    public enum Type {
        AD     ("Active Directory"),
        APACHE ("Apache Directory Services"),
        DOMINO ("Lotus Domino (IBM)"),
        NOVELL ("Novell eDirectory"),
        DIRX   ("DirX (Siemens)");

        public final String tag;
        private Type (String tag) {
            this.tag = tag;
        }

        private static final HashMap<String,Type> index = new HashMap<String,Type>();
        static { for (Type t : Type.values()) index.put(t.tag.toLowerCase(), t); }
        public static Type lookup(String name) { return index.get(name.toLowerCase()); }
    }

    public enum Option {
        DISABLED, // if config is present but enabled=False
        STARTTLS, // affects security mode
        DEFAULT,  // Maintain Default LDAP User Group
        CHECKPW,  // Check AD password expiration
        WARNUSER; // Email user with AD password expiration warnings
    }

    public enum SecurityMode {
        NONE     ("None"),
        SSL      ("SSL"),
        STARTTLS ("StartTLS");
        
        public String tag;
        private SecurityMode(String tag) {
            this.tag = tag;
        }

        private static final HashMap<String,SecurityMode> index = new HashMap<String,SecurityMode>();
        static { for (SecurityMode s : SecurityMode.values()) index.put(s.tag.toLowerCase(), s); }
        public static SecurityMode lookup(String name) { return index.get(name.toLowerCase()); }
    }

    public enum Attribute {
        // these are for mapping LDAP attributes
        USER    ("Attribute"),
        MAIL    ("Emailaddressattribute"),
        HOME    ("Homedirattribute"),
        NAME    ("Fullnameattribute"),
        FIRST   ("Firstnameattribute"),
        LAST    ("Lastnameattribute"),
        // these are for AD password checking
        DAYS    ("Warningdays",    "7"),
        TIME    ("Checktimeidx",   "0"), // # of 30 minute ticks since midnight :-(
        TO      ("Emailrecipient", "%admin%"),
        FROM    ("Emailsender",    "%admin%"),
        SUBJECT ("Subject",        "Cleo Communications US, LLC Password Expiration Notice"),
        // these are indirect ones
        MODE    ("Security",           SecurityMode.NONE.tag, true),
        TYPE    ("Type",               Type.APACHE.tag,       true),
        DEFAULT ("Defaultldapug",      "False",               true),
        CHECKPW ("Pwdcheckingenabled", "False",               true),
        WARNUSER("Emailusers",         "False",               true),
        ENABLED (".enabled",           "True",                true),
        HOST    ("Address",            "",                    true),
        PORT    ("Port",               "389",                 true),
        USERNAME("Ldapusername",       "",                    true),
        PASSWORD("Ldappassword",       "",                    true),
        BASEDN  ("Ldapdomain",         "",                    true),
        DOMAIN  ("Domain",             "",                    true), // dupe of BASEDN
        FILTER  ("Filter",             "",                    true);

        public final String  tag;
        public final String  dflt;
        public final boolean indirect;
        public final boolean mapped;
        
        private Attribute (String tag) {
            this.tag      = tag;
            this.dflt     = "";
            this.indirect = false;
            this.mapped   = true;
        }
        private Attribute (String tag, String dflt) {
            this.tag      = tag;
            this.dflt     = dflt;
            this.indirect = false;
            this.mapped   = false;
        }
        private Attribute (String tag, String dflt, boolean indirect) {
            this.tag      = tag;
            this.dflt     = dflt;
            this.indirect = indirect;
            this.mapped   = false;
        }

        private static final HashMap<String,Attribute> index = new HashMap<String,Attribute>();
        static { for (Attribute a : Attribute.values()) index.put(a.tag.toLowerCase(), a); }
        public static Attribute lookup(String name) { return index.get(name.toLowerCase()); }
    }
    
    private SecurityMode getMode()            { return SecurityMode.lookup(attrs.get(Attribute.MODE)); }
    private boolean      getBool(Attribute a) { return attrs.get(a).equalsIgnoreCase(Boolean.toString(true)); }
    private void         setBool(Attribute a, boolean b) { attrs.put(a, b?"True":"False"); }
    private void         setIf  (Attribute a, String s)  { if (s!=null && !s.isEmpty()) attrs.put(a,  s); }

    /*------------------------*
     * LDAP Server Definition *
     *------------------------*/
    private EnumMap<Attribute,String> attrs       = new EnumMap<Attribute,String>(Attribute.class);

    public LDAP () {
        // set up defaults
        for (Attribute attr : Attribute.values()) {
            this.attrs.put(attr, attr.dflt);
        }
    }

    // ldap[s][(type|opt|map...)]://[user:pass@]host[:port]/basedn[?filter]
    // type = ad|apache|domino|novell|dirx
    // opt  = starttls|default
    // map  = (user|mail|name|home|first|last)=attr
    static final Pattern LDAP_PATTERN = Pattern.compile("ldap(s)?(?:\\((.*)\\))?://(?:(.*):(.*)@)?(.*)(?::(\\d+))?/(.*?)(?:\\?(.*))?");
    public LDAP (String s) {
        // set up defaults
        this();
        // parse string
        Matcher m = LDAP_PATTERN.matcher(s);
        if (m.matches()) {
            String ssl      = m.group(1);
            String opts     = m.group(2);
            String user     = m.group(3);
            String password = m.group(4);
            String host     = m.group(5);
            String port     = m.group(6);
            String basedn   = m.group(7);
            String filter   = m.group(8);
            // set simple ones
            setIf(Attribute.USERNAME, user);
            setIf(Attribute.PASSWORD, password);
            setIf(Attribute.HOST,     host);
            setIf(Attribute.PORT,     port);
            setIf(Attribute.BASEDN,   basedn);
            setIf(Attribute.DOMAIN,   basedn); // dupe of basedn
            setIf(Attribute.FILTER,   filter);
            // process opts into options and attributes
            EnumSet<Option> options = EnumSet.noneOf(Option.class);
            if (opts!=null && !opts.isEmpty()) {
                for (String opt : opts.split("\\s*,\\s*")) {
                    String[] kv = opt.split("\\s*=\\s*", 2);
                    if (kv.length==2) {
                        // attribute=value: look it up
                        Attribute attr;
                        try {
                            attr = Attribute.valueOf(kv[0].toUpperCase());
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("unrecognized attribute: "+kv[0]);
                        }
                        if (attr.indirect) {
                            throw new IllegalArgumentException("unrecognized attribute: "+kv[0]);
                        }
                        this.attrs.put(Attribute.valueOf(kv[0].toUpperCase()), kv[1]);
                    } else if (!opt.isEmpty()) {
                        // option: look it up as an Option or a Type
                        try {
                            options.add(Option.valueOf(opt.toUpperCase()));
                        } catch (IllegalArgumentException e) {
                            try {
                                this.attrs.put(Attribute.TYPE, Type.valueOf(opt.toUpperCase()).tag);
                            } catch (IllegalArgumentException f) {
                                throw new IllegalArgumentException("unrecognized option: "+opt);
                            }
                        }
                    }
                }
            }
            // figure out if this should be disabled
            if (options.contains(Option.DISABLED)) {
                setBool(Attribute.ENABLED, false);
            }
            // figure out if mode should be set differently from default
            if (options.contains(Option.STARTTLS)) {
                this.attrs.put(Attribute.MODE, SecurityMode.STARTTLS.tag);
            } else if (ssl!=null) {
                this.attrs.put(Attribute.MODE, SecurityMode.SSL.tag);
            }
            // remaining straight up options
            setBool(Attribute.CHECKPW,  options.contains(Option.CHECKPW));
            setBool(Attribute.WARNUSER, options.contains(Option.WARNUSER));
            setBool(Attribute.DEFAULT,  options.contains(Option.DEFAULT));
        } else {
            throw new IllegalArgumentException("can not parse LDAP string: "+s);
        }
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("ldap");
        if (getMode()==SecurityMode.SSL) s.append('s');
        ArrayList<String> list = new ArrayList<String>();
        if (!getBool(Attribute.ENABLED)     ) list.add(Option.DISABLED.name().toLowerCase());
        list.add(Type.lookup(attrs.get(Attribute.TYPE)).name().toLowerCase());
        if (getMode()==SecurityMode.STARTTLS) list.add(Option.STARTTLS.name().toLowerCase());
        if (getBool(Attribute.DEFAULT)      ) list.add(Option.DEFAULT.name().toLowerCase());
        if (getBool(Attribute.CHECKPW)      ) list.add(Option.CHECKPW.name().toLowerCase());
        if (getBool(Attribute.WARNUSER)     ) list.add(Option.WARNUSER.name().toLowerCase());
        for (Map.Entry<Attribute,String> e : attrs.entrySet()) {
            if (!e.getKey().indirect && !e.getValue().equals(e.getKey().dflt)) {
                list.add(e.getKey().name().toLowerCase()+"="+e.getValue());
            }
        }
        if (!list.isEmpty()) {
            s.append('(')
             .append(S.join(",", list))
             .append(')');
        }
        s.append("://");
        if (!attrs.get(Attribute.USERNAME).isEmpty() || !attrs.get(Attribute.PASSWORD).isEmpty()) {
            s.append(attrs.get(Attribute.USERNAME));
            if (!attrs.get(Attribute.PASSWORD).isEmpty()) s.append(':').append(attrs.get(Attribute.PASSWORD));
            s.append('@');
        }
        s.append(attrs.get(Attribute.HOST));
        if (!attrs.get(Attribute.PORT).equals(Attribute.PORT.dflt)) s.append(':').append(attrs.get(Attribute.PORT));
        s.append('/');
        s.append(attrs.get(Attribute.BASEDN));
        if (!attrs.get(Attribute.FILTER).isEmpty()) s.append('?').append(attrs.get(Attribute.FILTER));
        return s.toString();
    }

    public Map<String,Object> toMap(Core core) throws Exception {
        Map<String,Object> map = new TreeMap<String,Object>();
        for (Attribute a : Attribute.values()) {
            if (attrs.containsKey(a)) {
                String value = attrs.get(a);
                if (a==Attribute.PASSWORD) {
                    value = "#"+core.encrypt(value)+"#";
                }
                map.put(a.tag, value);
            } else {
                map.put(a.tag, a.dflt);
            }
        }
        return map;
    }

    public LDAP (Map<String,Object> map, Core core) throws Exception {
        // set up defaults
        this();
        // walk the map
        for (Map.Entry<String,Object> e : map.entrySet()) {
            Attribute a = Attribute.lookup(e.getKey());
            if (a==null) {
                throw new IllegalArgumentException("unrecognized attribute: "+e.getKey());
            }
            if (!(e.getValue() instanceof String)) {
                throw new IllegalArgumentException("String value expected for attribute: "+e.getKey());
            }
            String value = (String)e.getValue();
            if (a==Attribute.PASSWORD && value.matches("#.*#")) {
                StringBuffer sb = new StringBuffer(value.subSequence(1, value.length()-1));
                while (sb.length()%4 > 0) sb.append('=');
                value = core.decrypt(sb.toString());
            }
            attrs.put(a, value);
        }
    }

    public String[] attributes() {
        ArrayList<String> list = new ArrayList<String>();
        for (Attribute a : Attribute.values()) {
            if (a.mapped) {
                if (attrs.get(a)!=null && !attrs.get(a).isEmpty()) {
                    list.add(attrs.get(a));
                }
            }
        }
        return list.toArray(new String[list.size()]);
    }

    /**
     * Searches the LDAP directory for an entry matching {@code Attribute.USER} against
     * the requested {@code alias}.  All connection parameters are derived from the {@code Attributes}
     * set in the {@code attrs} member.  Returns a Map indexed by {@code Attribute} containing the
     * associated values, i.e. the Map maps Attribute.USER => 'alice', not 'cn' => 'alice'.
     * <p>
     * If an entry is not found, the returned Map is empty.  If there is an LDAP problem, an
     * Exception is thrown
     * @param alias the alias to search for
     * @return the attributes of the entry found, organized by {@Attribute}
     * @throws Exception
     */
    public Map<Attribute,String> find(String alias) throws Exception {
        Map<Attribute,String> map = new TreeMap<Attribute,String>();
        Hashtable<String,Object> env = new Hashtable<String,Object>();
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        if (attrs.containsKey(Attribute.USERNAME)) {
            env.put(Context.SECURITY_PRINCIPAL, attrs.get(Attribute.USER)+"="+
                                                attrs.get(Attribute.USERNAME)+","+
                                                attrs.get(Attribute.BASEDN));
        }
        if (attrs.containsKey(Attribute.PASSWORD)) {
            env.put(Context.SECURITY_CREDENTIALS, attrs.get(Attribute.PASSWORD));
        }
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, "ldap://"+attrs.get(Attribute.HOST)+
                                      ":"+attrs.get(Attribute.PORT));
        DirContext ctx = new InitialDirContext(env);
        String base = attrs.get(Attribute.BASEDN);

        SearchControls sc = new SearchControls();
        sc.setReturningAttributes(attributes());
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE);

        String filter = "("+attrs.get(Attribute.USER)+"="+alias+")";
        if (attrs.containsKey(Attribute.FILTER) && !attrs.get(Attribute.FILTER).isEmpty()) {
            filter = "(&"+filter+attrs.get(Attribute.FILTER)+")";
        }

        NamingEnumeration<SearchResult> results = ctx.search(base, filter, sc);
        if (results.hasMore()) {
          SearchResult sr = results.next();
          Attributes found = sr.getAttributes();
          for (Attribute a : Attribute.values()) {
              if (a.mapped) {
                  String mapped = attrs.get(a);
                  if (mapped!=null && !mapped.isEmpty()) {
                      if (found.get(mapped)!=null) {
                          map.put(a, found.get(mapped).get().toString());
                      }
                  }
              }
          }
        }
        ctx.close();
        return map;
    }
}