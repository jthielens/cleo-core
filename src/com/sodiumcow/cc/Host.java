package com.sodiumcow.cc;

import java.util.Arrays;

import com.sodiumcow.cc.constant.PathType;
import com.sodiumcow.cc.constant.Protocol;

public class Host extends Node {
    public Host(Core core, Path path) {
        super(core, path);
        if (path.getType() != PathType.HOST) {
            throw new IllegalArgumentException();
        }
    }

    public Host(Core core, String host) {
        super(core, new Path(PathType.HOST, host));
    }

    public Mailbox[] getMailboxes() throws Exception {
        Node[] nodes = getChildren(PathType.MAILBOX);
        return (Mailbox[]) Arrays.copyOf(nodes, nodes.length, Mailbox[].class);
    }

    // hmm below this line---
    public boolean isMultipleIDsAllowed() throws Exception {
        return core.getLexiCom().isMultipleIDsAllowed(path.getType().id, path.getPath());
    }
    public void setMultipleIDsAllowed(boolean allowed) throws Exception {
        core.getLexiCom().setMultipleIDsAllowed(path.getType().id, path.getPath(), allowed);
    }
    public boolean isLocal() throws Exception {
        return core.getLexiCom().isLocal(path.getPath()[0]);
    }

    public Protocol getProtocol() throws Exception {
        return Protocol.valueOf(core.getLexiCom().getHostProtocol(path.getPath()[0]));
    }
}