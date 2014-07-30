package com.sodiumcow.cc.shell;

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
import java.util.regex.Pattern;

import com.sodiumcow.util.DB;
import com.sodiumcow.util.S;

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
    public enum Privilege {
        SYSTEMS      ("VLTraders tree",                 "E-", "Systems tree", AccessInternal.SYSTEMS_TREE),
        USERS        ("Users tree",                     "E-", "Users tree", AccessInternal.USERS_TREE),
        APPLICATIONS ("Applications tree",              "E-", "Applications tree", AccessInternal.APPS_TREE),
        CERTS        ("certs/*",                        "E-", "Trading partner/CA certificates"),
        DATA         ("data/*",                         "E-", "User certificates/private keys"),
        OPTIONS      ("conf/Options.xml",               "ES", "System options"),
        PROXIES      ("conf/Proxies.xml",               "E-", "Proxy settings"),
        AS400        ("conf/AS400.xml",                 "E-", "AS/400 configuration"),
        FOLDERS      ("conf/WinUnixFolders.xml",        "E-", "Windows/Unix folders configuration"),
        LICENSE      ("License",                        "E-", "License"),
        SCHEDULE     ("conf/Schedule.xml",              "ES", "Schedule"),
        ROUTES       ("conf/Route.xml",                 "ES", "Routes"),
        _ROUTER      ("conf/Router.xml",                "ES", "Routes"), // ROUTES and ROUTER go together :-(
        LISTENER     ("hosts/Local Listener.xml",       "ES", "Local Listener"),
        HOSTS        ("hosts/*.xml",                    "ES", "Hosts", AccessInternal.HOSTS_TREE),
        PARTNERS     ("conf/TradingPartners.xml",       "E-", "Trading partners"),
        REPORT       ("TR",                             "--", "Transfer report");
        public String         token;
        public boolean        editable;
        public boolean        startable;
        public boolean        branched;
        public String         description;
        public AccessInternal tree;
        private Privilege(String token, String flags, String description) {
            this(token, flags, description, null);
        }
        private Privilege(String token, String flags, String description, AccessInternal tree) {
            this.token       = token;
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
                    if (e.getKey()==ROUTES) {
                        sets.get(AccessInternal.STOPSTART).add(_ROUTER.token);
                    }
                }
                if (e.getKey().editable && (e.getValue().access!=Access.READ)) {
                    sets.get(AccessInternal.EDITABLE).add(e.getKey().token);
                    if (e.getKey()==ROUTES) {
                        sets.get(AccessInternal.EDITABLE).add(_ROUTER.token);
                    }
                } else {
                    sets.get(AccessInternal.VIEWONLY).add(e.getKey().token);
                    if (e.getKey()==ROUTES) {
                        sets.get(AccessInternal.VIEWONLY).add(_ROUTER.token);
                    }
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
                boolean stopstart = stopstarts!=null && stopstarts.contains(p.token);
                boolean editable  = editables !=null && editables .contains(p.token);
                boolean viewonly  = viewonlys !=null && viewonlys .contains(p.token);
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

    private DB db;
    public VLNav(DB db) {
        this.db = db;
    }

    private Map<String,Integer> entity_type = null;
    private Map<String,Integer> group_type  = null;
    private Map<String,Integer> appl_type  = null;
    private Map<Integer,String> type_appl  = null;
    private Map<String,String>  appl_lookup = null;
    private void connect() throws SQLException {
        db.connect();
        if (entity_type==null || entity_type.isEmpty()) {
            entity_type = db.loadDictionary("VLEntityNum");
        }
        if (group_type==null || group_type.isEmpty()) {
            group_type = db.loadDictionary("VLGroupNum");
        }
        if (appl_type==null || appl_type.isEmpty()) {
            appl_type = db.loadDictionary("VLApplicationNum", "Application");
            type_appl = new HashMap<Integer,String>();
            appl_lookup = new HashMap<String,String>();
            for (Map.Entry<String, Integer> e : appl_type.entrySet()) {
                type_appl.put(e.getValue(), e.getKey());
                appl_lookup.put(e.getKey().toLowerCase(), e.getKey());
            }
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
        int gtype = group_type.get("VLNavigator Group");
        int etype = entity_type.get("VLNavigator Person");
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
    public String[] list_groups() throws SQLException {
        // so this is SELECT Name FROM VLEntity e join VLEntityGroup g
        //              on e.VLEntityGroupID=g.VLEntityGroupID
        //              where VLGroupNum=1 and IsDefaultEntity=1;
        connect();
        int gtype = group_type.get("VLNavigator Group");
        DB.Selection.Result group = db.new Selection("VLEntityGroup",
                                                     new String[] {"VLEntityGroupID"},
                                                     new String[] {"VLGroupNum"},
                                                     gtype)    .rows();
        DB.Selection.Result entity = db.new Selection("VLEntity",
                                                      new String[] {"Name", "VLEntityGroupID"},
                                                      new String[] {"IsDefaultEntity"},
                                                      IS)    .rows();
        Map<Integer,String> entitygroup = new HashMap<Integer,String>();
        for (String[] row : entity.rows) {
            entitygroup.put(Integer.valueOf(row[1]), row[0]);
        }
        String[] groups = new String[group.rows.length];
        for (int i=0; i<groups.length; i++) {
            groups[i] = entitygroup.get(Integer.valueOf(group.rows[i][0]));
        }
        return groups;
    }
    public GroupDescription find_group(String name) throws SQLException {
        connect();
        GroupDescription result = new GroupDescription(name, null, null, null, null);
        // look up entity and group ids
        DB.Selection.Result id = db.new Selection("VLEntity",
                                                  new String[] {"VLEntityID", "VLEntityGroupID"},
                                                  new String[] {"Name", "IsDefaultEntity"},
                                                  name, IS)    .rows();
        if (id.count==0) return null;  // not found
        int eid = Integer.valueOf(id.rows[0][0]);
        int gid = Integer.valueOf(id.rows[0][1]);
        // look up application assignments
        DB.Selection.Result app = db.new Selection("VLEntityApplication",
                                                   new String[] {"VLApplicationNum"},
                                                   new String[] {"VLEntityID", "IsEnabled"},
                                                   eid, IS)    .rows();
        result.apps = new HashSet<String>(app.rows.length);
        for (int i=0; i<app.rows.length; i++) {
            result.apps.add(type_appl.get(Integer.valueOf(app.rows[i][0])));
        }
        // look up privileges and associated trees
        DB.Selection.Result priv = db.new Selection("VLUserEntityGroupPrivilege",
                                                    new String[] {"VLPrivilegeItem", "VLPrivilegeAccess"},
                                                    new String[] {"VLEntityGroupID"},
                                                    gid)    .rows();
        DB.Selection.Result tree = db.new Selection("VLUserEntityGroupTreeAccess",
                                                    AccessInternal.columns,
                                                    new String[] {"VLEntityGroupID"},
                                                    gid)    .rows();
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
                                                         gid, IS)    .rows();
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
                                                    eid, appl_type.get("Dashboards"))    .rows();
        if (dash.rows.length>0) {
            result.files = new HashSet<String>(dash.rows.length);
            result.files.addAll(Arrays.asList(S.invert(dash.rows)[0]));
        }
        // return result
        return result;
    }
}
