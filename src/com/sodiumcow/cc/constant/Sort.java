package com.sodiumcow.cc.constant;

import com.cleo.lexicom.external.IDirectoryController;

public enum Sort {
    ALPHABETICAL_ASCENDING  (IDirectoryController.ALPHABETICAL_ASCENDING),
    ALPHABETICAL_DESCENDING (IDirectoryController.ALPHABETICAL_DESCENDING),
    DATE_ASCENDING          (IDirectoryController.DATE_ASCENDING),
    DATE_DESCENDING         (IDirectoryController.DATE_DESCENDING),
    SIZE_ASCENDING          (IDirectoryController.SIZE_ASCENDING),
    SIZE_DESCENDING         (IDirectoryController.SIZE_DESCENDING),
    NONE                    (IDirectoryController.NONE);
    
    public final int id;
    private Sort(int id) {
        this.id = id;
    }
}
