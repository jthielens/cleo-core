package com.sodiumcow.cc;

import com.cleo.lexicom.external.LexiComLogListener;
import com.sodiumcow.cc.constant.PathType;

public class Node {

    protected Core core;
    protected Path path;

    public static Node getNode(Core core, String...path) throws Exception {
        return getNode(core, new Path(path));
    }

    public static Node getNode(Core core, Path path) {
        switch (path.getType()) {
        case HOST:
            return new Host(core, path);
        case MAILBOX:
            return new Mailbox(core, path);
        case ACTION:
            return new Action(core, path);
        default:
            return null;
        }
    }

    public Node(Core core, Path path) {
        this.core = core;
        this.path = path;
    }

    public boolean hasProperty(String property) throws Exception {
        return core.hasProperty(path, property);
    }

    public String[] getProperty(String property) throws Exception {
        return core.getProperty(path, property);
    }

    public void setProperty(String property, String value) throws Exception {
        core.setProperty(path, property, value);
    }

    public void setProperty(String property, String[] values) throws Exception {
        core.setProperty(path, property, values);
    }

    public Node[] getChildren(PathType type) throws Exception {
        Path[] paths    = core.list(type, path);
        Node[] children = new Node[paths.length];
        for (int i=0; i<paths.length; i++) {
            children[i] = getNode(core, paths[i]);
        }
        return children;
    }

    public void save() throws Exception {
        core.save(path);
    }

    public void addLogListener(LexiComLogListener listener) throws Exception {
        core.getLexiCom().addLogListener(listener, path.getPath(), path.getType().id);
    }
    public void removeLogListener(LexiComLogListener listener) throws Exception {
        core.getLexiCom().removeLogListener(listener, path.getPath(), path.getType().id);
    }
}