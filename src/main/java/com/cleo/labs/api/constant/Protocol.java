package com.cleo.labs.api.constant;

import java.util.HashMap;

import com.cleo.lexicom.external.ILexiCom;

public enum Protocol {
    HTTP_CLIENT   (ILexiCom.HTTP_CLIENT),
    HTTP_SERVER   (ILexiCom.HTTP_SERVER),
    FTP_CLIENT    (ILexiCom.FTP_CLIENT),
    FTP_SERVER    (ILexiCom.FTP_SERVER),
    OFTP_CLIENT   (ILexiCom.OFTP_CLIENT),
    SSHFTP_CLIENT (ILexiCom.SSHFTP_CLIENT),
    SSHFTP_SERVER (ILexiCom.SSHFTP_SERVER),
    MQ_CLIENT     (ILexiCom.MQ_CLIENT),
    SMTP_CLIENT   (ILexiCom.SMTP_CLIENT),
    MLLP_CLIENT   (ILexiCom.MLLP_CLIENT),
    FASP_CLIENT   (ILexiCom.FASP_CLIENT),
    HSP           (ILexiCom.HSP);
    
    public final int id;
    private Protocol(int id) {
        this.id = id;
    }

    private static final HashMap<Integer,Protocol> index = new HashMap<Integer,Protocol>();
    static {
        for (Protocol p : Protocol.values()) {
            index.put(p.id,  p);
        }
    }
    
    public static Protocol valueOf(int id) {
        return index.get(id);
    }
}