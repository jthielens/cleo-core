package com.cleo.labs.api.shell;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cleo.labs.api.LexiCom;
import com.cleo.labs.util.DB;
import com.cleo.labs.util.S;

public class VLNav {
    public static final int IS = 1;
    public static final int ISNT = 0;

    private enum AccessInternal {
        VIEWONLY, EDITABLE, STOPSTART,
        SYSTEMS_TREE ("VLPoolTreeSubset"),
        USERS_TREE   ("UserGroupTreeSubset"),
        APPS_TREE    ("ApplicationTreeSubset"),
        HOSTS_TREE   ("HostFolderTreeSubset");
        public final String column;
        private AccessInternal() { this.column = null; }
        private AccessInternal(String column) { this.column = column;}
        static public final String[] columns;
        static public final AccessInternal[] index;
        static {
            int count = 0;
            for (AccessInternal a : AccessInternal.values()) {
                if (a.column!=null) count++;
            }
            columns = new String[count];
            index = new AccessInternal[count];
            count = 0;
            for (AccessInternal a : AccessInternal.values()) {
                if (a.column!=null) {
                    columns[count] = a.column;
                    index[count] = a;
                    count++;
                }
            }
        }
    }
    public enum Access {
        READ ("ro"),  // VIEWONLY
        CTRL ("c"),   // VIEWONLY + STOPSTART if startable
        EDIT ("rw"),  // EDITABLE if editable else VIEWONLY
        FULL ("*");   // EDITABLE if editable else VIEWONLY + STOPSTART if startable
        public String token;
        private Access(String token) {
            this.token = token;
        }
        private static final HashMap<String,Access> index = new HashMap<String,Access>();
        static { for (Access a : Access.values()) index.put(a.token.toLowerCase(), a); }
        public static Access of(String name) { return index.get(name.toLowerCase()); }
    }
    public static class ScopedAccess {
        public Access   access;
        public String[] scope;
        public ScopedAccess(Access access, String[] scope) {
            this.access = access;
            this.scope  = scope;
        }
        public ScopedAccess(Access access) {
            this(access, null);
        }
    }
    // VLTree;UsrTree;AppTree;CACrt;UsrCrt;Optns;Prxy;AS400;WinUnx;Lic;Sched;Route;LL;Hosts;SYS;SCR;TP EDITABLE
    // Optns;Sched;Route;LL;Hosts;SYS;SCR STARTSTOP
    public enum Privilege {
        SYSTEMS      ("VLTree",  "VLTraders tree",                 "E-", "Systems tree", AccessInternal.SYSTEMS_TREE),
        USERS        ("UsrTree", "Users tree",                     "E-", "Users tree", AccessInternal.USERS_TREE),
        APPLICATIONS ("AppTree", "Applications tree",              "E-", "Applications tree", AccessInternal.APPS_TREE),
        CERTS        ("CACrt",   "certs/*",                        "E-", "Trading partner/CA certificates"),
        DATA         ("UsrCrt",  "data/*",                         "E-", "User certificates/private keys"),
        OPTIONS      ("Optns",   "conf/Options.xml",               "ES", "System options"),
        PROXIES      ("Prxy",    "conf/Proxies.xml",               "E-", "Proxy settings"),
        AS400        ("AS400",   "conf/AS400.xml",                 "E-", "AS/400 configuration"),
        FOLDERS      ("WinUnx",  "conf/WinUnixFolders.xml",        "E-", "Windows/Unix folders configuration"),
        LICENSE      ("Lic",     "License",                        "E-", "License"),
        SCHEDULE     ("Sched",   "conf/Schedule.xml",              "ES", "Schedule"),
        ROUTES       ("Route",   "conf/Route.xml",                 "ES", "Routes"),
        _ROUTER      ("",        "conf/Router.xml",                "ES", "Routes"), // ROUTES and ROUTER go together :-(
        LISTENER     ("LL",      "hosts/Local Listener.xml",       "ES", "Local Listener"),
        HOSTS        ("Hosts",   "hosts/*.xml",                    "ES", "Hosts", AccessInternal.HOSTS_TREE),
        SYS          ("SYS",     "",                               "ES", "System Actions"),
        SCR          ("SCR",     "",                               "ES", "Script Actions"),
        PARTNERS     ("TP",      "conf/TradingPartners.xml",       "E-", "Trading partners"),
        REPORT       ("TR",      "TR",                             "--", "Transfer report");
        public String         token;
        public String         legacy;
        public boolean        editable;
        public boolean        startable;
        public boolean        branched;
        public String         description;
        public AccessInternal tree;
        private Privilege(String token, String legacy, String flags, String description) {
            this(token, legacy, flags, description, null);
        }
        private Privilege(String token, String legacy, String flags, String description, AccessInternal tree) {
            this.token       = token;
            this.legacy      = legacy;
            this.editable    = flags.contains("E");
            this.startable   = flags.contains("S");
            this.branched    = tree!=null;
            this.description = description;
            this.tree        = tree;
        }

        /**
         * Convert the internal mapping of Privileges to Access levels to the external
         * representation of a trio of Strings by AccessInternal levels, plus an encoding
         * of the scope subtrees as the fourth pseudo-AccessInternal.SUBTREE.
         * @param privs the internal mapping
         * @return the external mapping
         */
        public static EnumMap<AccessInternal,Set<String>> of(EnumMap<Privilege,ScopedAccess> privs) {
            EnumMap<AccessInternal,Set<String>> sets = new EnumMap<AccessInternal,Set<String>>(AccessInternal.class);
            for (AccessInternal i : AccessInternal.values()) sets.put(i, new HashSet<String>());
            for (Map.Entry<Privilege,ScopedAccess> e : privs.entrySet()) {
                if (e.getKey().startable && (e.getValue().access==Access.CTRL || e.getValue().access==Access.FULL)) {
                    sets.get(AccessInternal.STOPSTART).add(e.getKey().token);
                    /* this is gone now
                    if (e.getKey()==ROUTES) {
                        sets.get(AccessInternal.STOPSTART).add(_ROUTER.token);
                    }
                    */
                }
                if (e.getKey().editable && (e.getValue().access!=Access.READ)) {
                    sets.get(AccessInternal.EDITABLE).add(e.getKey().token);
                    /* this is gone now
                    if (e.getKey()==ROUTES) {
                        sets.get(AccessInternal.EDITABLE).add(_ROUTER.token);
                    }
                    */
                } else {
                    sets.get(AccessInternal.VIEWONLY).add(e.getKey().token);
                    /* this is gone now
                    if (e.getKey()==ROUTES) {
                        sets.get(AccessInternal.VIEWONLY).add(_ROUTER.token);
                    }
                    */
                }
                if (e.getKey().branched && e.getValue().scope!=null) {
                    sets.get(e.getKey().tree).addAll(Arrays.asList(e.getValue().scope));
                }
            }
            return sets;
        }

        /**
         * Convert the external representation of a trio of String by AccessInternal levels and the
         * scope subtrees back into the internal mapping of Privileges to Access levels.
         * @param sets the external mapping
         * @return the internal mapping
         */
        public static EnumMap<Privilege,ScopedAccess> fo(EnumMap<AccessInternal,Set<String>> sets) {
            EnumMap<Privilege,ScopedAccess> privs = new EnumMap<Privilege,ScopedAccess>(Privilege.class);
            Set<String> stopstarts = sets.get(AccessInternal.STOPSTART);
            Set<String> editables  = sets.get(AccessInternal.EDITABLE );
            Set<String> viewonlys  = sets.get(AccessInternal.VIEWONLY );
            for (Privilege p : Privilege.values()) {
                boolean stopstart = stopstarts!=null && (stopstarts.contains(p.token) || stopstarts.contains(p.legacy));
                boolean editable  = editables !=null && (editables .contains(p.token) || editables .contains(p.legacy));
                boolean viewonly  = viewonlys !=null && (viewonlys .contains(p.token) || viewonlys .contains(p.legacy));
                Access  access    = null;
                if (p.startable) {
                    if  (stopstart && (!p.editable || editable)) {
                        access = Access.FULL;
                    } else if (stopstart) {
                        access = Access.CTRL;
                    } else if (editable) {
                        access = Access.EDIT;
                    } else if (viewonly) {
                        access = Access.READ;
                    }
                } else if (p.editable) {
                    if (editable) {
                        access = Access.FULL;
                    } else if (viewonly) {
                        access = Access.CTRL;
                    }
                } else {
                    if (viewonly) {
                        access = Access.FULL;
                    }
                }
                if (access != null) {
                    Set<String> tree = sets.get(p.tree);
                    if (p.branched && tree!=null) {
                        privs.put(p, new ScopedAccess(access, tree.toArray(new String[tree.size()])));
                    } else {
                        privs.put(p, new ScopedAccess(access));
                    }
                }
            }
            return privs;
        }

        private static final String[] STAR_SCOPE = {"*"};
        private static final Pattern CLAUSE = Pattern.compile("[\\s,;]*(\\*|\\w+)=(\\*|\\w+)(?:\\(([^\\)]*)\\))?[\\s,;]*");
        // CLAUSE = privilege=access[(scope,...)],...
        // privilege and scope are a token or *, scopes are whatever
        // ,; or whitespace may separate CLAUSEs, ,; may separate scopes (in case scopes have embedded whitespace)
        private static class Clause {
            public Privilege privilege;
            public Access    access;
            public String[]  scope;
            public Clause (Privilege privilege, Access access, String[] scope) {
                this.privilege = privilege;
                this.access    = access;
                this.scope     = scope;
            }
        }
        private static class Inspector implements S.Inspector<Clause> {
            public Clause inspect(String[] group) {
                String p = group[1];
                String a = group[2];
                String s = group.length>3 ? group[3] : null; // optional
                Privilege privilege;
                Access    access;
                String[]  scope;
                if (p.equals("*")) {
                    privilege = null; // code for "all"
                } else {
                    try {
                        privilege = Privilege.valueOf(p.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("unrecognized privilege: "+p);
                    }
                }
                access = Access.of(a);
                if (access==null) {
                    try {
                        access = Access.valueOf(a.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("unrecognized access level: "+a);
                    }
                }
                if (s!=null && !s.isEmpty()) {
                    if (privilege!=null && !privilege.branched) {
                        throw new IllegalArgumentException("/subtree not allowed for privilege "+p);
                    }
                    scope = s.split("[;,]");
                } else if (privilege==null || privilege.branched) {
                    scope = STAR_SCOPE;
                } else {
                    scope = null;
                }
                return new Clause(privilege, access, scope);
            }
        }
        public static EnumMap<Privilege,ScopedAccess> of(String s) {
            EnumMap<Privilege,ScopedAccess> result = new EnumMap<Privilege,ScopedAccess>(Privilege.class);
            // s      = clause,...
            // clause = priv=access[(trees)]
            // priv   = one of the Privilege names, case insensitive, not _ROUTER, or *
            // access = one of the Access names, case insensitive, or its token
            // trees  = tree;...
            if (s!=null) {
                List<Clause> clauses = S.megasplit(CLAUSE, s, new Inspector());
                for (Clause clause : clauses) {
                    if (clause.privilege==null) {
                        for (Privilege p : Privilege.values()) {
                            if (p!=Privilege._ROUTER) { // have to skip this crazy one
                                result.put(p, new ScopedAccess(clause.access, p.branched ? clause.scope : null));
                            }
                        }
                    } else {
                        result.put(clause.privilege, new ScopedAccess(clause.access, clause.scope));
                    }
                }
            }
            return result;
        }
        public static String format(EnumMap<Privilege,ScopedAccess> privileges) {
            ArrayList<String> clauses = new ArrayList<String>();
            ArrayList<String> excepts = new ArrayList<String>();
            boolean star = privileges.keySet().containsAll(EnumSet.complementOf(EnumSet.of(Privilege._ROUTER)));    // candidate for *
            Access  starWhat = null;   // *=what? (null before we find the first one) -- could be improved to to find most likely candidate
            for (Map.Entry<Privilege, ScopedAccess> e : privileges.entrySet()) {
                Access access = e.getValue().access;
                if (star) {
                    if (starWhat==null) {
                        starWhat = access;
                    } else if (access != starWhat) {
                        star = false;
                    }
                }
                StringBuilder clause = new StringBuilder(e.getKey().name().toLowerCase()).append('=').append(access.token);
                String[] scope = e.getValue().scope;
                if (scope!=null && scope.length>0) {
                    if (scope.length>1 || !scope[0].equals("*")) {
                        clause.append('(').append(S.join(",", scope)).append(')');
                        excepts.add(clause.toString());
                    }
                }
                clauses.add(clause.toString());
            }
            if (star) {
                excepts.add(0, "*="+starWhat.token);
                return S.join(",", excepts);
            }
            return S.join(",", clauses);
        }
    }

    private DB   db;
    public VLNav(DB db) {
        this.db   = db;
    }

    private Map<String,Integer> entity_type  = null;
    private Map<String,Integer> group_type   = null;
    private Map<String,Integer> contact_type = null;
    private Map<String,Integer> appl_type    = null;
    private Map<Integer,String> type_appl    = null;
    private Map<String,String>  appl_lookup  = null;

    private int                 gtype        = -1;    // group_type.get("VLNavigator Group");
    private int                 etype        = -1;    // entity_type.get("VLNavigator Person");
    private int                 emailContact = -1;    // contact_type.get("Work Email");
    private int                 phoneContact = -1;    // contact_type.get("Work Phone");
    private boolean             hasUnify     = false; // appl_type.containsKey("Unify"); (or "Trust")

    private void connect() throws SQLException {
        db.connect();
        if (entity_type==null || entity_type.isEmpty()) {
            entity_type = db.loadDictionary("VLEntityNum");
            if (entity_type!=null) {
                if (entity_type.containsKey("VLNavigator Person")) etype = entity_type.get("VLNavigator Person");
            }
        }
        if (group_type==null || group_type.isEmpty()) {
            group_type = db.loadDictionary("VLGroupNum");
            if (group_type!=null) {
                if (group_type.containsKey("VLNavigator Group")) gtype = group_type.get("VLNavigator Group");
            }
        }
        if (contact_type==null || contact_type.isEmpty()) {
            contact_type = db.loadDictionary("VLContactNum");
            if (contact_type!=null) {
                if (contact_type.containsKey("Work Email")) emailContact = contact_type.get("Work Email");
                if (contact_type.containsKey("Work Phone")) phoneContact = contact_type.get("Work Phone");
            }
        }
        if (appl_type==null || appl_type.isEmpty()) {
            appl_type = db.loadDictionary("VLApplicationNum", "Application", "IsEnabled=1");
            type_appl = new HashMap<Integer,String>();
            appl_lookup = new HashMap<String,String>();
            for (Map.Entry<String, Integer> e : appl_type.entrySet()) {
                type_appl.put(e.getValue(), e.getKey());
                appl_lookup.put(e.getKey().toLowerCase(), e.getKey());
            }
            hasUnify = appl_type.containsKey("Unify") || appl_type.containsKey("Trust");
        }
    }

    private static final String[] COLUMNS = {
        "StartDT",
        "Folder",
        "Host",
        "Mailbox",
        "Direction",
        "Status",
        "OrigName",
        "TrackedType",
        "Transport",
        "RunType",
        "TransferID",
        "ExternalID",
        "MessageID",
        "MailboxID",
        "Username",
        "Action",
        "EndDT",
        "IsReceipt",
        "OrigPath",
        "OrigFileDT",
        "FileSize",
        "TransferTime",
        "TransferBytes",
        "CRC",
        "ResultText",
        "FileHeader",
        "VLSerial",
        "CopyPath",
        "PreviousTransferID",
        "Command",
        "InteractiveUsername",
        "StartNDT",
        "EndNDT",
        "TradingPartnerAlias",
        "FileType",
        "Sender",
        "SenderID",
        "Receiver",
        "ReceiverID",
        "DocumentID",
        "DocumentType",
        "AckStatus",
    };

    public class GroupDescription {
        public String                          name;
        public EnumMap<Privilege,ScopedAccess> privileges;
        public String                          filter;
        public Set<String>                     apps;
        public Set<String>                     files;
        public int                             eid = -1;
        public int                             gid = -1;
        public int                             utgid = -1;
        public GroupDescription(String name, String privileges, String filter, Set<String> apps, Set<String> files) {
            this.name       = name;
            this.privileges = Privilege.of(privileges);
            this.filter     = filter;
            this.apps       = apps;
            this.files      = files;
        }
        public GroupDescription(String name, String[] argv) throws Exception {
            connect();
            String privs  = "*=*";
            String filter = null;
            Set<String> apps = new HashSet<String>();
            Set<String> files = new HashSet<String>();
            for (String arg : argv) {
                String[] kv = arg.split("=", 2);
                if (kv.length<2) {
                    throw new IllegalArgumentException("privilege|filter|application|file=value expected: "+arg);
                } else if (kv[0].equalsIgnoreCase("privilege")) {
                    privs = kv[1];
                } else if (kv[0].equalsIgnoreCase("filter")) {
                    filter = kv[1];
                } else if (kv[0].equalsIgnoreCase("application")) {
                    for (String a : kv[1].split(",")) {
                        String app = appl_lookup.get(a.toLowerCase());
                        if (app==null) {
                            throw new IllegalArgumentException("no such application: "+a);
                        }
                        apps.add(app);
                    }
                } else if (kv[0].equalsIgnoreCase("file")) {
                    files.addAll(Arrays.asList(kv[1].split(",")));
                } else {
                    throw new IllegalArgumentException("privilege|filter|application|file expected: "+kv[0]);
                }
            }
            this.name       = name;
            this.privileges = Privilege.of(privs);
            this.filter     = filter;
            this.apps       = apps;
            this.files      = files;
        }
        public Map<String,String> toMap() {
            Map<String,String> map = new HashMap<String,String>();
            if (this.privileges!=null) {
                map.put("privilege", Privilege.format(this.privileges));
            }
            if (this.filter!=null && !this.filter.isEmpty()) {
                map.put("filter", this.filter);
            }
            if (this.apps!=null && !this.apps.isEmpty()) {
                map.put("application", S.join(",", this.apps.toArray(new String[this.apps.size()])));
            }
            if (this.files!=null && !this.files.isEmpty()) {
                map.put("file", S.join(",", this.files.toArray(new String[this.files.size()])));
            }
            return map;
        }
        public String toString() {
            StringBuilder s = new StringBuilder("group ").append(this.name);
            if (this.privileges!=null) {
                s.append(" privilege=").append(Privilege.format(this.privileges));
            }
            if (this.filter!=null && !this.filter.isEmpty()) {
                s.append(" filter='").append(this.filter).append('\'');
            }
            if (this.apps!=null && !this.apps.isEmpty()) {
                s.append(" application='").append(S.join(",", this.apps.toArray(new String[this.apps.size()]))).append('\'');
            }
            if (this.files!=null && !this.files.isEmpty()) {
                s.append(" file='").append(S.join(",", this.files.toArray(new String[this.files.size()]))).append('\'');
            }
            return s.toString();
        }
    }

    private void create_group_privilege(int gid, EnumMap<AccessInternal,Set<String>> dbmap, AccessInternal access) throws SQLException {
        Set<String> plist = dbmap.get(access);
        if (!plist.isEmpty()) {
            db.insert("VLUserEntityGroupPrivilege",
                      new String[] {"VLEntityGroupID", "VLPrivilegeItem", "VLPrivilegeAccess"},
                      gid, S.join(";", plist), access.name());
        }
    }
    private void create_group_tree(int gid, EnumMap<AccessInternal,Set<String>> dbmap) throws SQLException {
        List<String> columns = new ArrayList<String>();
        List<Object> values  = new ArrayList<Object>();
        columns.add("VLEntityGroupID");
        values.add(gid);
        for (AccessInternal access : AccessInternal.values()) {
            if (access.column!=null) {
                Set<String> subtrees = dbmap.get(access);
                String value = "";
                if (subtrees!=null) {
                    value = S.join("\n", subtrees);
                }
                values.add(value);
                columns.add(access.column);
            }
        }
        db.insert("VLUserEntityGroupTreeAccess", columns, values);
    }

    public void create_group(GroupDescription group) throws SQLException {
        EnumMap<AccessInternal,Set<String>>  dbmap = Privilege.of(group.privileges);
        connect();
        int gid   = db.insert("VLEntityGroup", new String[] {"VLGroupNum"}, gtype);
        int eid   = db.insert("VLEntity",
                              new String[] {"Name", "VLEntityGroupID", "VLEntityNum", "IsEnabled", "IsDefaultEntity", "IsSystemAdmin"},
                              group.name, gid, etype, true, true, false); // IS, IS, ISNT);
        db.insert("VLUserEntityGroupAccess",
                  new String[] {"VLEntityGroupID", "VLPools", "VLSerials"},
                  gid, "mySystem", "");
        create_group_privilege(gid, dbmap, AccessInternal.VIEWONLY);
        create_group_privilege(gid, dbmap, AccessInternal.EDITABLE);
        create_group_privilege(gid, dbmap, AccessInternal.STOPSTART);
        create_group_tree(gid, dbmap);
        for (int i=0; i<COLUMNS.length; i++) {
            db.insert("VLUserEntityGroupTRColumns",
                      new String[] {"VLEntityGroupID", "ColumnName", "CustomColumnName", "Enabled", "ColumnNumber"},
                      gid, COLUMNS[i], "", IS, i);
        }
        db.insert("VLUserEntityGroupTRAccess",
                  new String[] {"VLEntityGroupID", "AccessibleFileTypes", "TransactionsAccessible"},
                  gid, "EDI,XML,Text", ISNT);
        if (group.filter!=null) {
            db.insert("VLUserEntityGroup",
                      new String[] {"VLEntityGroupID", "LdapUserGroup", "OverrideDomain", "OverrideFilter", "ExtendFilter"},
                      gid, IS, ISNT, ISNT, group.filter);
        }
        if (group.apps!=null && !group.apps.isEmpty()) {
            boolean trust = false;
            boolean unify = false;
            for (String app : group.apps) {
                if (appl_lookup.containsKey(app.toLowerCase())) {
                    db.insert("VLEntityApplication",
                              new String[] {"VLEntityID", "VLApplicationNum", "IsEnabled"},
                              eid, appl_type.get(appl_lookup.get(app.toLowerCase())), IS);
                    if (app.equalsIgnoreCase("trust")) trust=true;
                    if (app.equalsIgnoreCase("unify")) unify=true;
                } else {
                    throw new SQLException("no such application: "+app);
                }
            }
            if (trust||unify) {
                int sid = db.insert("UTSearchableEntity",
                                    new String[] {"entityType", "searchableCriteria"},
                                    "UserGroup", group.name);
                db.insert("UTUserGroup",
                          new String[] {"isUsingParentsConfiguration", "isUsingParentsLDAP", "name",
                                        "trustEnabled", "unifyEnabled", "entity_id"},
                          ISNT, ISNT, group.name, trust?IS:ISNT, unify?IS:ISNT, sid);
            }
        }
        if (group.files!=null && !group.files.isEmpty()) {
            int dash = appl_type.get("Dashboards");
            int seq  = 0;
            db.delete("VLEntityApplicationFile",
                      new String[] {"VLEntityID", "VLApplicationNum"},
                      eid, dash);
            for (String file : group.files) {
                db.insert("VLEntityApplicationFile",
                          new String[] {"VLEntityID", "VLApplicationNum", "Sequence", "Path"},
                          eid, dash, seq, file);
                seq++;
            }
        }
    }
    public Map<Integer,String> list_groups() throws SQLException {
        connect();
        // make a set of all VLEntityGroupIDs that are "VLNavigator Group"s
        DB.Selection.Result group = db.new Selection("VLEntityGroup",
                                                     new String[] {"VLEntityGroupID"},
                                                     new String[] {"VLGroupNum"},
                                                     gtype)    .rows();
        Set<Integer> groups = new HashSet<Integer>();
        for (String[] row : group.rows) {
            groups.add(Integer.valueOf(row[0]));
        }
        // now make a map of VLEntityGroupID => Name for that set
        DB.Selection.Result entity = db.new Selection("VLEntity",
                                                      new String[] {"Name", "VLEntityGroupID"},
                                                      new String[] {"IsDefaultEntity"},
                                                      IS)    .rows();
        Map<Integer,String> map = new HashMap<Integer,String>();
        for (String[] row : entity.rows) {
            int gid = Integer.valueOf(row[1]);
            if (groups.contains(gid)) {
                map.put(gid, row[0]);
            }
        }
        return map;
    }
    public GroupDescription find_group_ids(String name) throws SQLException {
        connect();
        GroupDescription result = new GroupDescription(name, null, null, null, null);
        // look up entity and group ids
        DB.Selection.Result id = db.new Selection("VLEntity",
                                                  new String[] {"VLEntityID", "VLEntityGroupID"},
                                                  new String[] {"Name", "IsDefaultEntity"},
                                                  name, IS)    .rows();
        if (id.count==0) return null;  // not found
        result.eid = Integer.valueOf(id.rows[0][0]);
        result.gid = Integer.valueOf(id.rows[0][1]);
        if (hasUnify) {
            id = db.new Selection("UTUserGroup", S.a("entity_id"), S.a("name"), name).rows();
            if (id.count>0) {
                result.utgid = Integer.valueOf(id.rows[0][0]);
            }
        }
        return result;
    }
    public String find_group_name(int gid) {
        try {
            DB.Selection.Result id = db.new Selection("VLEntity",
                                                      new String[] {"Name"},
                                                      new String[] {"VLEntityGroupID", "IsDefaultEntity"},
                                                      gid, IS)    .rows();
            return id.count==0 ? null : id.rows[0][0];
        } catch (Exception ignore) {
            return null;
        }
    }
    public GroupDescription find_group(String name) throws SQLException {
        GroupDescription result = find_group_ids(name);
        if (result==null) return null;  // not found
        // look up application assignments
        DB.Selection.Result app = db.new Selection("VLEntityApplication",
                                                   new String[] {"VLApplicationNum"},
                                                   new String[] {"VLEntityID", "IsEnabled"},
                                                   result.eid, IS)    .rows();
        result.apps = new HashSet<String>(app.rows.length);
        for (int i=0; i<app.rows.length; i++) {
            result.apps.add(type_appl.get(Integer.valueOf(app.rows[i][0])));
        }
        // look up privileges and associated trees
        DB.Selection.Result priv = db.new Selection("VLUserEntityGroupPrivilege",
                                                    new String[] {"VLPrivilegeItem", "VLPrivilegeAccess"},
                                                    new String[] {"VLEntityGroupID"},
                                                    result.gid)    .rows();
        DB.Selection.Result tree = db.new Selection("VLUserEntityGroupTreeAccess",
                                                    AccessInternal.columns,
                                                    new String[] {"VLEntityGroupID"},
                                                    result.gid)    .rows();
        EnumMap<AccessInternal,Set<String>> sets = new EnumMap<AccessInternal,Set<String>>(AccessInternal.class);
        for (String[] row : priv.rows) {
            if (row[0]!=null && !row[0].isEmpty()) {
                sets.put(AccessInternal.valueOf(row[1]), new HashSet<String>(Arrays.asList(row[0].split(";"))));
            }
        }
        for (int a=0; a < AccessInternal.index.length; a++) {
            String trees = tree.rows[0][a];
            if (trees!=null && !trees.isEmpty()) {
                sets.put(AccessInternal.index[a], new HashSet<String>(Arrays.asList(trees.split("\n"))));
            }
        }
        result.privileges = Privilege.fo(sets);
        // lookup filter string (if unify/trust)
        try {
            DB.Selection.Result vlueg = db.new Selection("VLUserEntityGroup",
                                                         new String[] {"ExtendFilter"},
                                                         new String[] {"VLEntityGroupID", "LdapUserGroup"},
                                                         result.gid, IS)    .rows();
            if (vlueg.rows.length>0) {
                result.filter = vlueg.rows[0][0];
            }
        } catch (SQLException e) {
            // assume this is because U&T tables don't exist -- no filter
        }
        // lookup dashboard files assignments
        DB.Selection.Result dash = db.new Selection("VLEntityApplicationFile",
                                                    new String[] {"Path"},
                                                    new String[] {"VLEntityID", "VLApplicationNum"},
                                                    result.eid, appl_type.get("Dashboards"))    .rows();
        if (dash.rows.length>0) {
            result.files = new HashSet<String>(dash.rows.length);
            result.files.addAll(Arrays.asList(S.invert(dash.rows)[0]));
        }
        // return result
        return result;
    }
    public int find_user_id(String name) {
        int eid = -1;
        try {
            connect();
            // select VLEntityID from VLUser where UserName=name
            DB.Selection.Result id = db.new Selection("VLUser",
                                                      new String[] {"VLEntityID"},
                                                      new String[] {"UserName"},
                                                      name)    .rows();
            if (id.count>0) {
                eid = Integer.valueOf(id.rows[0][0]);
            }
        } catch (SQLException ignore) {}
        return eid;
    }

    public class UserDescription {
        public int     eid      = -1;
        public int     gid      = -1;
        public int     utgid    = -1;
        public String  first    = null;
        public String  last     = null;
        public String  fullname = null;
        public String  username = null;
        public String  alias    = null;
        public String  password = null;
        public String  email    = null;
        public String  phone    = null;
        public boolean build    = false;

        public UserDescription() {
            // defaults ok
        }

        public static final String  VLUSER = "vluser";

        public UserDescription(String[] spec) {
            // group/alias(user:password) vluser attributes
            if (spec==null || spec.length<2 || !spec[1].equalsIgnoreCase(VLUSER)) {
                throw new IllegalArgumentException("invalid arguments: group/alias(user:password) "+VLUSER+" attibutes");
            }
            // group/[alias(]user:password[)]
            // this technically matches things like group/user:password), but so what...
            Matcher m = Pattern.compile("(.*)/(?:(.*)\\()?(.*?)(?::(.*?))?\\)?").matcher(spec[0]);
            if (!m.matches()) {
                throw new IllegalArgumentException("invalid argument: "+spec[0]+" doesn't match group/alias(user:password)");
            }
            GroupDescription group = null;
            try {
                group = find_group_ids(m.group(1));
            } catch (SQLException ignore) {}
            if (group==null) {
                throw new IllegalArgumentException("unknown group: "+m.group(1));
            }
            this.gid = group.gid;
            this.utgid = group.utgid;
            this.username = m.group(3);
            this.password = m.group(4);
            this.alias = S.empty(m.group(2)) ? username : m.group(2);
            for (int i=2; i<spec.length; i++) {
                String[] kv = spec[i].split("=", 2);
                String value = kv.length>1 ? kv[1] : "";
                if      (kv[0].equalsIgnoreCase("first"   )) this.first    = value;
                else if (kv[0].equalsIgnoreCase("last"    )) this.last     = value;
                else if (kv[0].equalsIgnoreCase("fullname")) this.fullname = value;
                else if (kv[0].equalsIgnoreCase("email"   )) this.email    = value;
                else if (kv[0].equalsIgnoreCase("phone"   )) this.phone    = value;
                else if (kv[0].equalsIgnoreCase("username")) this.username = value;
                else if (kv[0].equalsIgnoreCase("alias"   )) this.alias    = value;
                else if (kv[0].equalsIgnoreCase("password")) this.password = value;
                else
                    throw new IllegalArgumentException("unknown attribute: "+kv[0]);
            }
            String built = null;
            if (!S.empty(first) || !S.empty(last)) {
                if (S.empty(first)) {
                    built = last;
                } else if (S.empty(last)) {
                    built = first;
                } else {
                    built = first+" "+last;
                }
                
            }
            if (fullname==null && built!=null) {
                this.build = true;
                this.fullname = built;
            } else {
                this.build = S.equal(fullname, built);
            }
        }

        public void set(String[] columns, String[] values) {
            for (int i=0; i<columns.length; i++) {
                String column = columns[i];
                if (column.equalsIgnoreCase("VLEntityID")) {
                    this.eid = Integer.valueOf(values[i]);
                } else if (column.equalsIgnoreCase("VLEntityGroupID")) {
                    this.gid = Integer.valueOf(values[i]);
                } else if (column.equalsIgnoreCase("userGroup_entity_id")) {
                    this.utgid = Integer.valueOf(values[i]);
                } else if (column.equalsIgnoreCase("Name")) {
                    this.fullname = values[i];
                } else if (column.equalsIgnoreCase("FirstName")) {
                    this.first = values[i];
                } else if (column.equalsIgnoreCase("LastName")) {
                    this.last = values[i];
                } else if (column.equalsIgnoreCase("UserName")) {
                    this.username = values[i];
                } else if (column.equalsIgnoreCase("Alias")) {
                    this.alias = values[i];
                } else if (column.equalsIgnoreCase("UserPassword")) {
                    try {
                        this.password = LexiCom.decode(values[i]);
                    } catch (Exception e) {
                        this.password = values[i];
                    }
                    this.password = LexiCom.crack(this.alias, this.password);
                } else if (column.equalsIgnoreCase("BuildFullName")) {
                    this.build = values[i].equals("1");
                }
            }
        }

        public Map<String,String> toMap() {
            Map<String,String> map = new HashMap<String,String>();
            if (!S.empty(first   )          ) map.put("first"   , first);
            if (!S.empty(last    )          ) map.put("last"    , last);
            if (!S.empty(fullname) && !build) map.put("fullname", fullname);
            if (!S.empty(username)          ) map.put("username", username);
            if (!S.empty(alias   )          ) map.put("alias"   , alias);
            if (!S.empty(password)          ) map.put("password", password);
            if (!S.empty(email   )          ) map.put("email"   , email);
            if (!S.empty(phone   )          ) map.put("phone"   , phone);
            return map;
        }

        public String[] toStrings() {
            // group/alias(username:password) attributes
            List<String> strings = new ArrayList<String>(7);
            String userpass = S.s(username)+(S.empty(password)?"":(":"+password));
            if (!S.empty(alias) && !S.equal(alias, username)) {
                userpass = alias+"("+userpass+")";
            }
            strings.add(S.s(find_group_name(gid))+"/"+userpass);
            strings.add(VLUSER);  // fixed typename
            if (!S.empty(first   )          ) strings.add("first="+first);
            if (!S.empty(last    )          ) strings.add("last=" +last);
            if (!S.empty(fullname) && !build) strings.add("fullname="+fullname);
            if (!S.empty(email   )          ) strings.add("email="+email);
            if (!S.empty(phone   )          ) strings.add("phone="+phone);
            return strings.toArray(new String[strings.size()]);
        }

        public String toString() {
            return S.join(" ", toStrings());
        }
    }

    private static final String[] CONTACT_COLUMNS = new String[] {"VLEntityID","VLContactNum","Value","IsPrimary"};
    private String update_user_contact(int eid, String before, String after, int num) throws SQLException {
        if (after!=null && !S.equal(before, after)) {
            if (before==null) {
                // add
                db.insert("VLContact", CONTACT_COLUMNS, eid, emailContact, after, false);
            } else if (after.isEmpty()) {
                // delete
                db.delete("VLContact", S.a("VLEntityID","VLContactNum"), eid, num);
            } else {
                //update
                db.new Selection("VLContact", S.a("VLEntityID","VLContactNum"), eid, num)
                  .update(S.a("Value"), after);
            }
        } else {
            after = before;
        }
        return after;
    }
    public UserDescription update_user(UserDescription after) throws Exception {
        // insert into VLEntity (Name, VLEntityGroupID, VLEntityNum, IsEnabled, IsDefaultEntity, IsSystemAdmin) values (null, 5, 1, 1, 0, 0)
        // insert into VLUser (VLEntityID, FirstName, LastName, BuildFullName, UserName, Alias, LDAPUser, UserPassword) values (30, null, null, 0, 'demo1', 'demo1', 0, '*HBMaEA**')
        // insert into VLContact (VLEntityID, VLContactNum, Value, IsPrimary) values (30, 1, '1234', 0)
        // insert into VLContact (VLEntityID, VLContactNum, Value, IsPrimary) values (30, 0, 'demo!@cleo.demo', 0)
        // insert into UTSearchableEntity (entityType, searchableCriteria) values ('User', 'Joe Demonstration <demo!@cleo.demo>')
        // insert into UTUser (company, email, firstName, image, isExternalContact, isTemporaryPassword, lastFailedLogin, lastName, lastSuccessfulLogin, password, phoneNumber, purl, salt, userGroup_entity_id, entity_id)
        //   values (null, 'demo!@cleo.demo', 'Joe', '', 0, null, null, 'Demonstration', null, '', '1234', null, '', 25, 303)
        int eid = find_user_id(after.username);
        if (eid < 0) {
            // add
            eid = db.insert("VLEntity",
                            S.a("Name", "VLEntityGroupID", "VLEntityNum", "IsEnabled", "IsDefaultEntity", "IsSystemAdmin"),
                            after.fullname, after.gid, etype, true, false, false); // IS, IS, ISNT);
            db.insert("VLUser",
                      S.a("VLEntityID","FirstName","LastName","BuildFullName","UserName","Alias","LDAPUser","UserPassword"),
                      eid, after.first, after.last, after.build, after.username, after.alias, false, LexiCom.hash(after.password));
            if (!S.empty(after.email)) {
                db.insert("VLContact", CONTACT_COLUMNS, eid, emailContact, after.email, false);
            }
            if (!S.empty(after.phone)) {
                db.insert("VLContact", CONTACT_COLUMNS, eid, phoneContact, after.phone, false);
            }
            if (hasUnify && !S.empty(after.email)) {
                String search = (S.empty(after.fullname)?"":after.fullname+" ")+"<"+after.email+">";
                int sid = db.insert("UTSearchableEntity", S.a("entityType","searchableCriteria"), "User", search);
                db.insert("UTUser",
                          S.a("email","firstName","isExternalContact","lastName","phoneNumber","userGroup_entity_id","entity_id"),
                          after.email, after.first, 0, after.last, after.phone, after.utgid, sid);
            }
        } else {
            // update
            UserDescription before = list_user(eid);
            // VLEntity: did Name or VLEntityGroupID change?
            if (!S.equal(before.fullname,after.fullname) || before.gid!=after.gid) {
                DB.Selection entity = db.new Selection("VLEntity", S.a("VLEntityID"), eid);
                entity.update(S.a("Name","VLEntityGroupID"), after.fullname, after.gid);
            }
            // VLUser: check if anything changed
            boolean user_updated = false;
            if (after.first   ==null || S.equal(before.first   , after.first   )) after.first    = before.first   ;
            else                                                                  user_updated = true;
            if (after.last    ==null || S.equal(before.last    , after.last    )) after.last     = before.last    ;
            else                                                                  user_updated = true;
            if (after.username==null || S.equal(before.username, after.username)) after.username = before.username;
            else                                                                  user_updated = true;
            if (after.alias   ==null || S.equal(before.alias   , after.alias   )) after.alias    = before.alias   ;
            else                                                                  user_updated = true;
            if (after.password==null || S.equal(before.password, after.password)) after.password = before.password;
            else                                                                  user_updated = true;
            // VLUser: update it if anything changed
            if (user_updated) {
                DB.Selection user = db.new Selection("VLUser", S.a("VLEntityID"), eid);
                user.update(S.a("FirstName","LastName","BuildFullName","UserName","Alias","UserPassword"),
                            after.first, after.last, after.build, after.username, after.alias, LexiCom.encode(after.password));
            }
            // VLContact: add/modify/update
            after.email = update_user_contact(eid, before.email, after.email, emailContact);
            after.phone = update_user_contact(eid, before.phone, after.phone, phoneContact);
            if (hasUnify) {
                // find the existing UTUser and UTSearchableEntity information
                String utcriteria = null;
                int utid = -1;
                if (!S.empty(before.email)) {
                    DB.Selection.Result id = db.new Selection("UTUser", S.a("entity_id"), S.a("email"), before.email).rows();
                    if (id.count>0) {
                        utid = Integer.valueOf(id.rows[0][0]);
                    }
                    id = db.new Selection("UTSearchableEntity", S.a("searchableCriteria"), S.a("entity_id"), utid).rows();
                    if (id.count>0) {
                        utcriteria = id.rows[0][0];
                    }
                }
                // now figure out how to update it
                String search = (S.empty(after.fullname)?"":after.fullname+" ")+"<"+after.email+">";
                if (utid==-1 && S.empty(after.email)) {
                    // done -- nothing there, nothing supposed to be
                } else if (utid==-1) {
                    // add new info
                    int sid = db.insert("UTSearchableEntity", S.a("entityType","searchableCriteria"), "User", search);
                    db.insert("UTUser",
                              S.a("email","firstName","isExternalContact","lastName","phoneNumber","userGroup_entity_id","entity_id"),
                              after.email, after.first, 0, after.last, after.phone, after.utgid, sid);
                } else if (S.empty(after.email)) {
                    // delete existing
                    db.delete("UTSearchableEntity", S.a("entity_id"), utid);
                    db.delete("UTUser", S.a("entity_id"), utid);
                } else {
                    // update whatever's there
                    if (!search.equals(utcriteria)) {
                        db.new Selection("UTSearchableEntity", S.a("entity_id"), utid)
                          .update(S.a("searchableCriteria"), search);
                    }
                    db.new Selection("UTUser", S.a("entity_id"), utid)
                      .update(S.a("email","firstName","lastName","phoneNumber","userGroup_entity_id"),
                              after.email, after.first, after.last, after.phone, after.utgid);
                }
            }
        }
        return after;
    }
    public UserDescription list_user(int eid) throws SQLException {
        connect();
        UserDescription result = new UserDescription();
        String[] entityColumns = new String[] {"VLEntityID", "VLEntityGroupID", "Name"};
        DB.Selection.Result entity = db.new Selection("VLEntity",
                                                      entityColumns,
                                                      new String[] {"VLEntityID"},
                                                      eid)    .rows();
        result.set(entityColumns, entity.rows[0]);
        String[] userColumns;
        if (hasUnify) {
            userColumns = S.a("VLEntityID","FirstName","LastName","UserName","Alias","UserPassword","BuildFullName");
        } else {
            userColumns = S.a("VLEntityID","null","null","UserName","Alias","UserPassword","null");
        }
        DB.Selection.Result user = db.new Selection("VLUser",
                                                    userColumns,
                                                    new String[] {"VLEntityID", "LDAPUser"},
                                                    result.eid, ISNT)    .rows();
        if (user.count==0) return null;
        result.set(userColumns, user.rows[0]);
        DB.Selection.Result contact = db.new Selection("VLContact",
                                                       new String[] {"VLContactNum","Value"},
                                                       new String[] {"VLEntityID"},
                                                       result.eid)    .rows();
        for (String[] crow : contact.rows) {
            int key = Integer.valueOf(crow[0]);
            if (key==emailContact) result.email=crow[1];
            if (key==phoneContact) result.phone=crow[1];
        }
        return result;
    }
    public UserDescription[] list_users() throws SQLException {
        return list_users(null);
    }
    public UserDescription[] list_users(String group) throws SQLException {
        connect();
        List<String> queryColumns = new ArrayList<String>(Arrays.asList("VLEntityNum","IsEnabled","IsDefaultEntity")); //,"IsSystemAdmin"));
        List<Object> queryValues  = new ArrayList<Object>(Arrays.asList(etype, IS, ISNT)); //, ISNT));
        if (group!=null) {
            GroupDescription g = find_group(group);
            queryColumns.add("VLEntityGroupID");
            queryValues.add(g.gid);
        }
        DB.Selection.Result entity = db.new Selection("VLEntity",
                                                      new String[] {"VLEntityID"},
                                                      queryColumns.toArray(new String[queryColumns.size()]),
                                                      queryValues.toArray())    .rows();
        List<UserDescription> results = new ArrayList<UserDescription>(entity.count);
        for (String[] row : entity.rows) {
            UserDescription result = list_user(Integer.valueOf(row[0]));
            if (result!=null) {
                results.add(result);
            }
        }
        return results.toArray(new UserDescription[results.size()]);
    }
}
