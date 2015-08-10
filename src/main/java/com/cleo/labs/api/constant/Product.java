package com.cleo.labs.api.constant;

import com.cleo.lexicom.external.LexiComFactory;

public enum Product {
    LEXICOM  (LexiComFactory.LEXICOM,  "LexiComc"),
    VLTRADER (LexiComFactory.VLTRADER, "VLTraderc"),
    HARMONY  (LexiComFactory.HARMONY,  "Harmonyc");
    
    public final int    id;
    public final String proof;
    private Product(int id, String proof) {
        this.id    = id;
        this.proof = proof;
    }
}
