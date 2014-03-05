package com.sodiumcow.cc;

import com.sodiumcow.cc.constant.PathType;

public class Action extends Node {
    public Action(Core core, Path path) {
        super(core, path);
        if (path.getType() != PathType.ACTION) {
            throw new IllegalArgumentException();
        }
    }

    public Action(Core core, String host, String mailbox, String action) {
        super(core, new Path(PathType.ACTION, host, mailbox, action));
    }
}
