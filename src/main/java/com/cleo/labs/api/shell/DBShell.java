package com.cleo.labs.api.shell;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cleo.labs.api.LexiCom;
import com.cleo.labs.api.shell.DBShell.DBOptions.Vendor;
import com.cleo.labs.util.DB;
import com.cleo.labs.util.repl.REPL;
import com.cleo.labs.util.repl.annotation.Command;
import com.cleo.lexicom.beans.Options;
import com.cleo.lexicom.beans.Options.DBConnection;

public class DBShell extends REPL {
    public static class DBOptions {
        public enum Vendor { MYSQL, ORACLE, SQLSERVER, DB2, H2, POSTGRESQL; }
        public Vendor vendor;
        public String user;
        public String password;
        public String db;
        public String driver;
        public String type;
        public String rawconnection;
        public String connection;
        public void init(String vendor, String user, String password, String host, String port, String db) {
            this.vendor     = Vendor.valueOf(vendor.toUpperCase());
            this.user       = user;
            this.password   = password;
            this.db         = db;
            switch (this.vendor) {
            case MYSQL:
                if (port==null) port = "3306";
                this.driver        = "com.mysql.jdbc.Driver";
                this.type          = DBConnection.DB_CONTYPE_MYSQL;
                this.rawconnection = "jdbc:mysql://"+host+":"+port;
                this.connection    = this.rawconnection+"/"+this.db;
                break;
            case ORACLE:
                if (port==null) port = "1521";
                this.driver        = "oracle.jdbc.driver.OracleDriver";
                this.type          = DBConnection.DB_CONTYPE_OTHER;
                this.rawconnection = "jdbc:oracle:thin:@"+host+":"+port;
                this.connection    = this.rawconnection+":"+this.db;
                break;
            case SQLSERVER:
                if (port==null) port = "1433";
                this.driver        = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
                this.type          = DBConnection.DB_CONTYPE_OTHER;
                this.rawconnection = "jdbc:sqlserver://"+host+":"+port;
                this.connection    = this.rawconnection+";databaseName="+this.db;
                break;
            case POSTGRESQL:
                if (port==null) port = "5432";
                this.driver        = "org.postgresql.Driver";
                this.type          = DBConnection.DB_CONTYPE_OTHER;
                this.rawconnection = "jdbc:postgresql://"+host+":"+port;
                this.connection    = this.rawconnection+"/"+this.db;
                break;
            case DB2:
                if (port==null) port = "5000";
                this.driver        = "com.ibm.db2.jcc.DB2Driver";
                this.type          = DBConnection.DB_CONTYPE_OTHER;
                this.rawconnection = "jdbc:db2://"+host+":"+port;
                this.connection    = this.rawconnection+"/"+this.db;
                break;
            case H2:
                if (port==null) port = "9092";
                this.driver        = "org.h2.Driver";
                this.type          = DBConnection.DB_CONTYPE_OTHER;
                this.rawconnection = "jdbc:h2:tcp://"+host+":"+port;
                this.connection    = this.rawconnection+"/"+this.db;
                break;
            }
        }
        // vendor:user:pass@host[:port]/database
        static final Pattern DB_PATTERN = Pattern.compile("(\\w+):(.*):(.*)@(.*?)(?::(\\d+))?/(.*)");
        public DBOptions(String string) {
            Matcher m = DB_PATTERN.matcher(string);
            if (m.matches()) {
                this.init(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5), m.group(6));
            } else {
                throw new IllegalArgumentException("invalid database descriptor: "+string);
            }
        }
        public DBOptions (DBConnection c) {
            this.connection = c.getConnectionString();
            this.driver     = c.getDriverString();
            this.type       = c.getConnectionType();
            this.user       = c.getUserName();
            this.password   = c.getPassword();
            this.vendor     = Vendor.valueOf(this.connection.split(":")[1].toUpperCase()); // jdbc:vendor:stuff
        }
        public Options.DBConnection getDBConnection() throws Exception {
            Options.DBConnection c = LexiCom.getOptions().new DBConnection(); 
            c.setConnectionType(this.type);
            c.setConnectionString(this.connection);
            c.setDriverString(this.driver);
            c.setUserName(this.user);
            c.setPassword(this.password);
            return c;
        }
    }
    @Command(name="db", args="create|drop string", comment="create/drop db connection")
    public void db(String...argv) {
        if (argv.length!=2) {
            error("usage: db create|drop string");
        } else {
            String command = argv[0];
            String string  = argv[1];
            if (command.equalsIgnoreCase("create") || command.equalsIgnoreCase("drop")) {
                DBOptions dbo = new DBOptions(string);
                try {
                    DB db = new DB(dbo.vendor==Vendor.POSTGRESQL ? dbo.connection : dbo.rawconnection, dbo.user, dbo.password);
                    db.execute(command+" database "+dbo.db);
                    report("database "+dbo.db+" "+(command.equalsIgnoreCase("create")?"created":"dropped"));
                } catch (SQLException e) {
                    error("can not "+command+" database "+dbo.db, e);
                }
            } else {
                error("usage: db (create|drop) type:user:password@host[:port]/database");
            }
        }
    }

    public static void main(String[] argv) {
        DBShell repl = new DBShell();
        repl.run(argv);
        System.exit(0);
    }
}
