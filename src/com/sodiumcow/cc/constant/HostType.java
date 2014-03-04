package com.sodiumcow.cc.constant;

import java.util.HashMap;

public enum HostType {
    AS2        (Protocol.HTTP_CLIENT,   Packaging.AS2,   false, "AS2/HTTP"),
    AS3        (Protocol.FTP_CLIENT,    Packaging.AS3,   false, "AS3/FTP", "Generic Cleo VersaLex AS3"),
    EBMS       (Protocol.HTTP_CLIENT,   Packaging.EBMS,  false, "ebXML Message Service/HTTP",
                                                                           "Generic ebXML"),
    FTP        (Protocol.FTP_CLIENT,    Packaging.NONE,  false, "FTP"),
    FTPS       (Protocol.FTP_CLIENT,    Packaging.NONE,  false, "FTPs",    "Generic FTPs"),
    HTTP       (Protocol.HTTP_CLIENT,   Packaging.NONE,  false, "HTTP"),
    HTTPS      (Protocol.HTTP_CLIENT,   Packaging.NONE,  false, "HTTPs",   "Generic HTTPs"),
    MQ         (Protocol.MQ_CLIENT,     Packaging.NONE,  false, "IBM MQ"),
    OFTP       (Protocol.OFTP_CLIENT,   Packaging.NONE,  false, "OFTP"),
    SMTP       (Protocol.SMTP_CLIENT,   Packaging.NONE,  false, "SMTP"),
    SMTPS      (Protocol.SMTP_CLIENT,   Packaging.NONE,  false, "SMTPs",   "Generic SMTPs"),
    SFTP       (Protocol.SSHFTP_CLIENT, Packaging.NONE,  false, "SSH FTP", "Generic SSH FTP"),
    MLLP       (Protocol.MLLP_CLIENT,   Packaging.NONE,  false, "MLLP"),
    WS         (Protocol.HTTP_CLIENT,   Packaging.WS,    false, "WS"),
    RNIF       (Protocol.HTTP_CLIENT,   Packaging.RNIF,  false, "RNIF"),
    EBICS      (Protocol.HTTP_CLIENT,   Packaging.EBICS, false, "EBICS"),
    FASP       (Protocol.FASP_CLIENT,   Packaging.NONE,  false, "fasp"),
    LOCAL_FTP  (Protocol.FTP_SERVER,    Packaging.NONE,  true,  "FTP listener"),
    LOCAL_HTTP (Protocol.HTTP_SERVER,   Packaging.NONE,  true,  "HTTP listener"),
    LOCAL_SFTP (Protocol.SSHFTP_SERVER, Packaging.NONE,  true,  "SSH FTP listener");

    public final Protocol  protocol;
    public final Packaging packaging;
    public final boolean   local;
    public final String    transport;
    public final String    template;
    private HostType(Protocol protocol, Packaging packaging, boolean local, String transport, String template) {
        this.protocol  = protocol;
        this.packaging = packaging;
        this.local     = local;
        this.transport = transport;
        this.template  = template;
    }
    private HostType(Protocol protocol, Packaging packaging, boolean local, String transport) {
        this.protocol  = protocol;
        this.packaging = packaging;
        this.local     = local;
        this.transport = transport;
        this.template  = "Generic "+this.name();
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