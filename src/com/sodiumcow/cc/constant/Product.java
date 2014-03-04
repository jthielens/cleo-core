package com.sodiumcow.cc.constant;

import com.cleo.lexicom.external.LexiComFactory;

public enum Product {
    LEXICOM  (LexiComFactory.LEXICOM),
    VLTRADER (LexiComFactory.VLTRADER),
    HARMONY  (LexiComFactory.HARMONY);
    
    public final int id;
    private Product(int id) {
        this.id = id;
    }
}
