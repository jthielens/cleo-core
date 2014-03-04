package com.sodiumcow.cc.constant;

import com.cleo.lexicom.external.ILexiCom;

public enum PathType {
    HOST            (ILexiCom.HOST),
    MAILBOX         (ILexiCom.MAILBOX),
    TRADING_PARTNER (ILexiCom.TRADING_PARTNER),
    ACTION          (ILexiCom.ACTION),
    SERVICE         (ILexiCom.SERVICE),
    HOST_ACTION     (ILexiCom.HOST_ACTION);
    
    public final int id;
    private PathType(int id) {
        this.id = id;
    }
}
