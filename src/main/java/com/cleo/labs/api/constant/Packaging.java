package com.cleo.labs.api.constant;

import java.util.HashMap;

import com.cleo.lexicom.external.ILexiCom;

public enum Packaging {
    NONE           (ILexiCom.NONE),
    AS2            (ILexiCom.AS2),
    AS3            (ILexiCom.AS3),
    EBMS           (ILexiCom.ebMS),
    OPENPGP        (ILexiCom.OPENPGP),
    XML_ENCRYPTION (ILexiCom.XML_ENCRYPTION),
    RNIF           (ILexiCom.RNIF),
    EBICS          (ILexiCom.EBICS),
    WS             (ILexiCom.WS);
   
    public final int id;
    private Packaging(int id) {
        this.id = id;
    }

    private static final HashMap<Integer,Packaging> index = new HashMap<Integer,Packaging>();
    static {
        for (Packaging p : Packaging.values()) {
            index.put(p.id,  p);
        }
    }
    
    public static Packaging valueOf(int id) {
        return index.get(id);
    }
}