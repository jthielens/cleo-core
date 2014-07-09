package com.sodiumcow.cc.shell;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
         * representation of a trio of Strings by AccessInternal levels, plus and encoding
         * of the scope subtrees as the fourth pseudo-AccessInternal.SUBTREE.
         * @param privs the internal mapping
         * @return the external mapping
         */
        public static EnumMap<AccessInternal,List<String>> of(EnumMap<Privilege,ScopedAccess> privs) {
            EnumMap<AccessInternal,List<String>> lists = new EnumMap<AccessInternal,List<String>>(AccessInternal.class);
            for (AccessInternal i : AccessInternal.values()) lists.put(i, new ArrayList<String>());
            for (Map.Entry<Privilege,ScopedAccess> e : privs.entrySet()) {
                if (e.getKey().startable && (e.getValue().access==Access.CTRL || e.getValue().access==Access.FULL)) {
                    lists.get(AccessInternal.STOPSTART).add(e.getKey().token);
                    if (e.getKey()==ROUTES) {
                        lists.get(AccessInternal.STOPSTART).add(_ROUTER.token);
                    }
                }
                if (e.getKey().editable && (e.getValue().access!=Access.READ)) {
                    lists.get(AccessInternal.EDITABLE).add(e.getKey().token);
                    if (e.getKey()==ROUTES) {
                        lists.get(AccessInternal.EDITABLE).add(_ROUTER.token);
                    }
                } else {
                    lists.get(AccessInternal.VIEWONLY).add(e.getKey().token);
                    if (e.getKey()==ROUTES) {
                        lists.get(AccessInternal.VIEWONLY).add(_ROUTER.token);
                    }
                }
                if (e.getKey().branched && e.getValue().scope!=null) {
                    lists.get(e.getKey().tree).addAll(Arrays.asList(e.getValue().scope));
                }
            }
            return lists;
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
        private static class Inspector implements Util.Inspector<Clause> {
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
                List<Clause> clauses = Util.megasplit(CLAUSE, s, new Inspector());
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
    }

    private DB db;
    public VLNav(DB db) {
        this.db = db;
    }

    private Map<String,Integer> entity_type = null;
    private Map<String,Integer> group_type  = null;
    private Map<String,Integer> appl_type  = null;
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

    private void create_group_privilege(int gid, EnumMap<AccessInternal,List<String>> dbmap, AccessInternal access) throws SQLException {
        List<String> plist = dbmap.get(access);
        if (!plist.isEmpty()) {
            db.insert("VLUserEntityGroupPrivilege",
                      new String[] {"VLEntityGroupID", "VLPrivilegeItem", "VLPrivilegeAccess"},
                      gid, Util.join(";", plist), access.name());
        }
    }
    private void create_group_tree(int gid, EnumMap<AccessInternal,List<String>> dbmap) throws SQLException {
        List<String> columns = new ArrayList<String>();
        List<Object> values  = new ArrayList<Object>();
        columns.add("VLEntityGroupID");
        values.add(gid);
        for (AccessInternal access : AccessInternal.values()) {
            if (access.column!=null) {
                List<String> subtrees = dbmap.get(access);
                String value = "";
                if (subtrees!=null) {
                    value = Util.join("\n", subtrees);
                }
                values.add(value);
                columns.add(access.column);
            }
        }
        db.insert("VLUserEntityGroupTreeAccess", columns, values);
    }

    public void create_group(String name, EnumMap<Privilege,ScopedAccess> privileges) throws SQLException {
        create_group(name, privileges, null, null);
    }
    public void create_group(String name, EnumMap<Privilege,ScopedAccess> privileges, String filter, List<String> apps) throws SQLException {
        EnumMap<AccessInternal,List<String>>  dbmap = Privilege.of(privileges);
        connect();
        int gtype = group_type.get("VLNavigator Group");
        int etype = entity_type.get("VLNavigator Person");
        int gid   = db.insert("VLEntityGroup", new String[] {"VLGroupNum"}, gtype);
        int eid   = db.insert("VLEntity",
                              new String[] {"Name", "VLEntityGroupID", "VLEntityNum", "IsEnabled", "IsDefaultEntity", "IsSystemAdmin"},
                              name, gid, etype, true, true, false); // IS, IS, ISNT);
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
        if (filter!=null) {
            db.insert("VLUserEntityGroup",
                      new String[] {"VLEntityGroupID", "LdapUserGroup", "OverrideDomain", "OverrideFilter", "ExtendFilter"},
                      gid, IS, ISNT, ISNT, filter);
        }
        if (apps!=null && !apps.isEmpty()) {
            boolean trust = false;
            boolean unify = false;
            for (String app : apps) {
                if (appl_type.containsKey(app)) {
                    db.insert("VLEntityApplication",
                              new String[] {"VLEntityID", "VLApplicationNum", "IsEnabled"},
                              eid, appl_type.get(app), IS);
                    if (app.equalsIgnoreCase("trust")) trust=true;
                    if (app.equalsIgnoreCase("unify")) unify=true;
                } else {
                    throw new SQLException("no such application: "+app);
                }
            }
            if (trust||unify) {
                int sid = db.insert("UTSearchableEntity",
                                    new String[] {"entityType", "searchableCriteria"},
                                    "UserGroup", name);
                db.insert("UTUserGroup",
                          new String[] {"isUsingParentsConfiguration", "isUsingParentsLDAP", "name",
                                        "trustEnabled", "unifyEnabled", "entity_id"},
                          ISNT, ISNT, name, trust?IS:ISNT, unify?IS:ISNT, sid);
            }
        }
    }
    public int find_group(String name) throws SQLException {
        connect();
        return db.new ID("VLEntity", "VLEntityGroupID", new String[] {"Name", "IsDefaultEntity"},  name, true).id;
    }
}
