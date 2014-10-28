package com.sodiumcow.util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class DB {
    private Connection conn;
    private String     connection;
    private String     user;
    private String     password;

    public DB() {
        this.conn       = null;
        this.connection = null;
        this.user       = null;
        this.password   = null;
    }

    public DB(String connection, String user, String password) throws SQLException {
        this.conn       = null;
        this.connection = connection;
        this.user       = user;
        this.password   = password;
        connect();
    }

    public boolean connected() {
        return conn!=null;
    }

    private final void _connect() throws SQLException {
        if (!connected()) {
            Properties props = new Properties();
            props.put("user", user);
            props.put("password", password);
            conn = DriverManager.getConnection(connection, props);
        }
    }
    public void connect(String connection, String user, String password) throws SQLException {
        this.connection = connection;
        this.user       = user;
        this.password   = password;
        _connect();
    }

    public void connect() throws SQLException {
        if (!connected()) {
            _connect();
        }
    }

    public void disconnect() {
        if (conn!=null) {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println("SQL error: "+e.getMessage());
                // so what
            }
            conn=null;
        }
    }

    public void execute(String query) throws SQLException {
        Statement stmt = null;
        try {
            connect();
            stmt = conn.createStatement();
            stmt.execute(query);
        } finally {
            if (stmt!=null) stmt.close();
        }
    }

    public Map<String,Map<String,Integer>> tables() throws SQLException {
        Map<String,Map<String,Integer>> result = new HashMap<String,Map<String,Integer>>();
        connect();
        DatabaseMetaData md = conn.getMetaData();
        ResultSet rs = md.getColumns(null, null, "%", "%");
        while (rs.next()) {
            String tab  = rs.getString(3);
            String col  = rs.getString(4);
            int    type = rs.getInt(5);
            if (result.get(tab)==null) {
                result.put(tab, new LinkedHashMap<String,Integer>());
            }
            result.get(tab).put(col,type);
        }
        return result;
    }

    public Map<String,Integer> loadDictionary(String dictionary) throws SQLException {
        return loadDictionary(dictionary, "Description");
    }
    public Map<String,Integer> loadDictionary(String dictionary, String column) throws SQLException {
        Statement stmt = null;
        try {
            connect();
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("select "+dictionary+","+column+" from "+dictionary);
            HashMap<String,Integer> result = new HashMap<String,Integer>();
            while (rs.next()) {
                int    id          = rs.getInt(1);
                String description = rs.getString(2);
                if (description!=null) {
                    result.put(description, id);
                }
            }
            return result;
        } finally {
            if (stmt!=null) stmt.close();
        }
    }

    /**
     * select COLUMNS from TABLE where QUERY =/like ARGS
     * <p>
     * COLUMNS optional defaults to *
     * <p>
     * where QUERY/ARGS options defaults to everything
     * @author john
     *
     */
    public class Selection {
        public String   table;
        public String[] columns;
        public String[] query;
        public Object[] args;

        public Selection(String table) {
            this(table, null, null, (Object[]) null);
        }
        public Selection(String table, String[] query, Object...args) {
            this(table, null, query, args);
        }
        public Selection(String table, String[] columns, String[] query, Object...args) {
            this.table   = table;
            this.columns = columns;
            this.query   = query;
            this.args    = args;
        }
        public Selection(ID id) {
            this(id.table, null, new String[] {id.column}, id.id);
        }
        public Selection(ID id, String[] columns) {
            this(id.table, columns, new String[] {id.column}, id.id);
        }
        
        public class Result {
            public int        count = 0;
            public String[]   columns;
            public String[][] rows;
        }

        public Result rows () throws SQLException {
            PreparedStatement stmt = null;
            Result            result = new Result();
            try {
                connect();
                boolean star = columns==null || columns.length==0;
                stmt = conn.prepareStatement("select "+
                                             (star ? "*" : S.join(",", columns))+
                                             " from "+table+where(query, args));
                setObjects(stmt, args);
                ResultSet rs = stmt.executeQuery();
                ResultSetMetaData m = rs.getMetaData();
                int width = m.getColumnCount();
                ArrayList<String[]> rows = new ArrayList<String[]>();
                while (rs.next()) {
                    result.count++;
                    String[] row = new String[width];
                    for (int i=0; i<row.length; i++) {
                        row[i] = rs.getString(i+1);
                    }
                    rows.add(row);
                }
                result.rows = rows.toArray(new String[rows.size()][]);
                if (star) {
                    result.columns = new String[width];
                    for (int i=0; i<width; i++) {
                        result.columns[i] = m.getColumnName(i+1);
                    }
                } else {
                    result.columns = columns;
                }
                return result;
            } finally {
                if (stmt!=null) stmt.close();
            }
        }

        public int update(String[] update_columns, Object...update_args) throws SQLException {
            return update(Arrays.asList(update_columns), Arrays.asList(update_args));
        }
        public int update(List<String> update_columns, List<Object> update_args) throws SQLException {
            PreparedStatement stmt = null;
            try {
                connect();
                stmt = conn.prepareStatement("update "+table+" set "+S.join(",", S.lam(update_columns, "%s=?"))+
                                             where(query, args));
                setObjects(stmt, update_args);
                setObjects(stmt, args, update_args.size());
                return stmt.executeUpdate();
            } finally {
                if (stmt!=null) stmt.close();
            }
        }
    }


    private static String where(String[] columns, Object...args) {
       if (columns==null || columns.length==0) {
           return "";
       } else {
           String[] ops = new String[columns.length];
           for (int c=0; c<columns.length; c++) {
               ops[c] = "=?";
               if (args[c] instanceof String && ((String)args[c]).matches(".*[%_\\[\\]].*")) {
                   ops[c] = " like ?";
               }
            }
            return " where "+ S.join(" and ", S.lam(columns, ops));
        }
    }
    private static String where(String[] columns) {
        if (columns==null || columns.length==0) {
            return "";
        } else {
            return " where "+ S.join(" and ", S.lam(columns, "%s=?"));
        }
    }

    private static void setObjects(PreparedStatement stmt, Object[] args) throws SQLException {
        setObjects(stmt, args, 0);
    }
    private static void setObjects(PreparedStatement stmt, Object[] args, int offset) throws SQLException {
        if (args!=null && args.length>0) {
            setObjects(stmt, Arrays.asList(args), offset);
        }
    }
    private static void setObjects(PreparedStatement stmt, List<Object> args) throws SQLException {
        setObjects(stmt, args, 0);
    }
    private static void setObjects(PreparedStatement stmt, List<Object> args, int offset) throws SQLException {
        if (args!=null && args.size()>0) {
            for (int i=0; i<args.size(); i++) {
                stmt.setObject(i+1+offset, args.get(i));
            }
        }
    }

    /**
     * Encapsulates a unique reference to a database row by table, id column, and id value.
     * Default constructor fills in nulls and -1 for the id and you are responsible for
     * populating everything else.  There are different constructors depending on the use case.
     * Constructors without the ID column will hunt for the ID column by looking for the first
     * autoincrement column in the schema.  Constructors without the columns/args where clause
     * are designed for tables expected to have a single row, while the where clause lets you
     * select the row of interest.
     * @author john
     *
     */
    public class ID {
        public String table  = null;
        public String column = null;
        public int    id     = -1;
        public boolean hasid() { return id != -1; }
        /**
         * Use this constructor for single row tables with an identified ID column.
         * @param table
         * @param column
         * @throws SQLException
         */
        public ID (String table, String column) throws SQLException {
            this(table, column, new String[0]);
        }
        /**
         * Use this constructor for multi-row tables with an identified ID column with a
         * where clause formatted from {@code columns} and {@code args} (which may be {@code null}
         * or empty, but are expected to be the same length).
         * @param table
         * @param column
         * @param columns
         * @param args
         * @throws SQLException
         */
        public ID (String table, String column, String[] columns, Object...args) throws SQLException {
            this.table = table;
            this.column = column;
            PreparedStatement stmt = null;
            try {
                connect();
                stmt = conn.prepareStatement("select "+column+" from "+table+where(columns));
                setObjects(stmt, args);
                ResultSet rs = stmt.executeQuery();
                if (rs!=null && rs.next()) {
                    this.id = rs.getInt(1);
                }
            } finally {
                if (stmt!=null) stmt.close();
            }
        }
        /**
         * Use this constructor to discover the autoincrement ID column for a table and
         * the associated ID value.
         * @param table
         * @throws SQLException
         */
        public ID (String table) throws SQLException {
            this(table, new String[0]);
        }
        /**
         * Use this constructor to discover the autoincrement ID columns for a table and
         * the ID value associated with the where clause formatted from {@code columns} and
         * {@code args} (which may be {@code null} or empty, but are expected to be the same
         * length).
         * @param table
         * @param columns
         * @param args
         * @throws SQLException
         */
        public ID (String table, String[] columns, Object...args) throws SQLException {
            PreparedStatement stmt = null;
            this.table = table;
            try {
                connect();
                stmt = conn.prepareStatement("select * from "+table+where(columns));
                setObjects(stmt, args);
                ResultSet rs = stmt.executeQuery();
                ResultSetMetaData m = rs.getMetaData();
                for (int i=0; this.column==null && i<m.getColumnCount(); i++) {
                    if (m.isAutoIncrement(i+1)) {
                        this.column = m.getColumnName(i+1);
                    }
                }
                if (rs!=null && rs.next()) {
                    this.id = rs.getInt(1);
                }
                if (this.column==null) {
                    DatabaseMetaData md = conn.getMetaData();
                    ResultSet rp = md.getPrimaryKeys(null, null, table);
                    if (rp!=null && rp.next()) {
                        this.column = rp.getString(4);
                    }
                }
            } finally {
                if (stmt!=null) stmt.close();
            }
        }
    }

    public int insert(String table, String[] columns, Object...args) throws SQLException {
        return insert(table, Arrays.asList(columns), Arrays.asList(args));
    }
    public int insert(String table, List<String> columns, List<Object> args) throws SQLException {
        PreparedStatement stmt = null;
        try {
            connect();
            stmt = conn.prepareStatement("insert into "+table+
                                          "("+S.join(",", columns)+") "+
                                          "values ("+S.join(",", S.x("?", columns.size()))+")",
                                         Statement.RETURN_GENERATED_KEYS);
            setObjects(stmt, args);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            int result = -1;
            if (rs!=null && rs.next()) {
                result = (int)rs.getLong(1);
            }
            return result;
        } finally {
            if (stmt!=null) stmt.close();
        }
    }

    public void delete(String table, List<String> columns, List<Object> args) throws SQLException {
        delete(table, columns.toArray(new String[columns.size()]), args.toArray());
    }
    public void delete(String table, String[] columns, Object...args) throws SQLException {
        PreparedStatement stmt = null;
        try {
            connect();
            stmt = conn.prepareStatement("delete from "+table+where(columns, args));
            setObjects(stmt, args);
            stmt.executeUpdate();
        } finally {
            if (stmt!=null) stmt.close();
        }
    }

    public Map<String,String> describe(String table) throws SQLException {
        Statement stmt = null;
        try {
            Map<String,String> columns = new LinkedHashMap<String,String>();
            connect();
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("select * from "+table+" where false");
            ResultSetMetaData m = rs.getMetaData();
            for (int i=0; i<m.getColumnCount(); i++) {
                columns.put(m.getColumnName(i+1), m.getColumnClassName(i+1));
            }
            return columns;
        } finally {
            if (stmt!=null) stmt.close();
        }
    }

    public String[] star(String table) throws SQLException {
        return describe(table).keySet().toArray(new String[0]);
        
    }
}