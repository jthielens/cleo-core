package com.cleo.labs.api.constant;

import java.util.HashMap;

public enum HostType {
    AS2        (Protocol.HTTP_CLIENT,   Packaging.AS2,   "...", "AS2/HTTP"),
    AS3        (Protocol.FTP_CLIENT,    Packaging.AS3,   ".DG", "AS3/FTP", "Generic Cleo VersaLex AS3"),
    EBMS       (Protocol.HTTP_CLIENT,   Packaging.EBMS,  "...", "ebXML Message Service/HTTP",
                                                                           "Generic ebXML"),
    FTP        (Protocol.FTP_CLIENT,    Packaging.NONE,  ".DG", "FTP"),
    FTPS       (Protocol.FTP_CLIENT,    Packaging.NONE,  ".DG", "FTPs",    "Generic FTPs"),
    HTTP       (Protocol.HTTP_CLIENT,   Packaging.NONE,  ".DG", "HTTP"),
    HTTPS      (Protocol.HTTP_CLIENT,   Packaging.NONE,  ".DG", "HTTPs",   "Generic HTTPs"),
    MQ         (Protocol.MQ_CLIENT,     Packaging.NONE,  ".DG", "IBM MQ"),
    OFTP       (Protocol.OFTP_CLIENT,   Packaging.NONE,  "..G", "OFTP"),
    SMTP       (Protocol.SMTP_CLIENT,   Packaging.NONE,  "...", "SMTP"),
    SMTPS      (Protocol.SMTP_CLIENT,   Packaging.NONE,  "...", "SMTPs",   "Generic SMTPs"),
    SFTP       (Protocol.SSHFTP_CLIENT, Packaging.NONE,  ".DG", "SSH FTP", "Generic SSH FTP"),
    MLLP       (Protocol.MLLP_CLIENT,   Packaging.NONE,  "..G", "MLLP"),
    WS         (Protocol.HTTP_CLIENT,   Packaging.WS,    ".DG", "WS"),
    RNIF       (Protocol.HTTP_CLIENT,   Packaging.RNIF,  "...", "RNIF"),
    EBICS      (Protocol.HTTP_CLIENT,   Packaging.EBICS, "..G", "EBICS"),
    FASP       (Protocol.FASP_CLIENT,   Packaging.NONE,  ".DG", "fasp",    "Generic fasp"),
    HSP        (Protocol.HSP,           Packaging.NONE,  "...", "HSP"),
    LOCAL_USER (Protocol.HTTP_SERVER,   Packaging.NONE,  "L..", "FTP, SSH FTP, HTTP listener", "Users"),
    LOCAL_FTP  (Protocol.FTP_SERVER,    Packaging.NONE,  "L..", "FTP listener",                "Local FTP Users"),
    LOCAL_HTTP (Protocol.HTTP_SERVER,   Packaging.NONE,  "L..", "HTTP listener",               "Local HTTP Users"),
    LOCAL_SFTP (Protocol.SSHFTP_SERVER, Packaging.NONE,  "L..", "SSH FTP listener",            "Local SSH FTP Users");

    public final Protocol  protocol;
    public final Packaging packaging;
    public final boolean   local;
    public final boolean   can_dir;
    public final boolean   can_get;
    public final String    transport;
    public final String    template;
    private HostType(Protocol protocol, Packaging packaging, String opts, String transport, String template) {
        this.protocol  = protocol;
        this.packaging = packaging;
        this.local     = opts.charAt(0)=='L';
        this.can_dir   = opts.charAt(1)=='D';
        this.can_get   = opts.charAt(2)=='G';
        this.transport = transport;
        this.template  = template==null?"Generic "+this.name():template;
    }
    private HostType(Protocol protocol, Packaging packaging, String opts, String transport) {
        this(protocol, packaging, opts, transport, null);
    }
    
    private static final HashMap<String,HostType> transport_index = new HashMap<String,HostType>();
    static {
        for (HostType ht : HostType.values()) {
            transport_index.put(ht.transport, ht);
        }
    }
    
    public static HostType fromTransport(String t) {
        return transport_index.get(t);
    }
}