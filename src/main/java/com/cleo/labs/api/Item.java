package com.cleo.labs.api;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.cleo.labs.api.constant.HostType;
import com.cleo.labs.api.constant.PathType;
import com.cleo.labs.util.X;
import com.cleo.lexicom.external.LexiComLogListener;

public class Item {

    protected Core core;
    protected Path path;

    public static Item getItem(Core core, String...path) throws Exception {
        return getItem(core, new Path(path));
    }

    public static Item getItem(Core core, Path path) {
        switch (path.getType()) {
        case HOST:
            return new Host(core, path);
        case MAILBOX:
            return new Mailbox(core, path);
        case ACTION:
            return new Action(core, path);
        case HOST_ACTION:
            return new HostAction(core, path);
        default:
            return null;
        }
    }

    public Path getPath() {
        return path;
    }

    public Item(Core core, Path path) {
        this.core = core;
        this.path = path;
    }

    public boolean hasProperty(String property) throws Exception {
        return core.hasProperty(path, property);
    }

    public String[] getProperty(String property) throws Exception {
        return core.getProperty(path, property);
    }

    public String getSingleProperty(String property) throws Exception {
        return core.getSingleProperty(path, property);
    }

    public boolean matchPropertyIgnoreCase(String property, String match) throws Exception {
        String[] values = getProperty(property);
        if (values!=null) {
            for (String value : values) {
                if (value.equalsIgnoreCase(match)) {
                    return true;
                }
            }
        }
        return false;
    }
    public boolean matchProperty(String property, String match) throws Exception {
        String[] values = getProperty(property);
        if (values!=null) {
            for (String value : values) {
                if (value.equals(match)) {
                    return true;
                }
            }
        }
        return false;
    }
    public void setProperty(String property, String value) throws Exception {
        core.setProperty(path, property, value);
    }

    public void setProperty(String property, String[] values) throws Exception {
        core.setProperty(path, property, values);
    }

    public boolean exists() throws Exception {
        return core.exists(path);
    }

    private Node subnode(String type, String name, Node node) {
        if (node==null || node.getNodeType()!=Node.ELEMENT_NODE) return null;
        Node find = node.getFirstChild();
        while (find != null) {
            NamedNodeMap attrs;
            Node         alias = null;
            if (find.getNodeType()==Node.ELEMENT_NODE         &&
                find.getNodeName().equalsIgnoreCase(type)     &&
                (attrs = find.getAttributes()) != null        &&
                (alias = attrs.getNamedItem("alias")) != null &&
                alias.getNodeValue().equalsIgnoreCase(name)   ) {
                return find;
            }
            find = find.getNextSibling();
        }
        return null;
    }
    public Node getNode() throws Exception {
        Node node = core.getHostDocument(path).getDocumentElement();
        switch (path.getType()) {
        case MAILBOX:
            node = subnode("Mailbox",    path.getPath()[1], node);
            break;
        case ACTION:
            node = subnode("Mailbox",    path.getPath()[1], node);
            node = subnode("Action",     path.getPath()[2], node);
            break;
        case HOST_ACTION:
            node = subnode("HostAction", path.getPath()[1], node);
            break;
        case SERVICE:
            node = subnode("Service",    path.getPath()[1], node);
            break;
        case TRADING_PARTNER:
            node = null; // no such thing any more really
            break;
        case HOST:
            break;
        }
        return node;
    }

    public Map<String, String> getProperties() throws Exception {
        Node node = getNode();
        if (node!=null) {
            return X.flat(X.xml2map(node), 0);
        }
        return null;
    }
    private static final Map<String,Map<String,String>> defaults = new HashMap<String,Map<String,String>>();
    public Map<String,String> getDefaultProperties() throws Exception {
        HostType type = new Host(core, path.getHost()).getHostType();
        synchronized (defaults) {
            if (defaults.isEmpty()) {
                File preconfigured = new File(new File(core.getHome(), "hosts"), "preconfigured");
                for (File f : preconfigured.listFiles()) {
                    if (f.isFile() && f.getName().endsWith(".xml")) {
                        try {
                            Node node = X.file2xml(f).getDocumentElement();
                            String alias = node.getAttributes().getNamedItem("alias").getNodeValue();
                            Map<String,String> map = X.flat(X.xml2map(node), 0);
                            defaults.put(alias, map);
                        } catch (Exception e) {
                            // skip it
                        }
                    }
                }
            }
        }
        return defaults.get(type.template);
    }

    public Item[] getChildren(PathType type) throws Exception {
        Path[] paths    = core.list(type, path);
        Item[] children = new Item[paths.length];
        for (int i=0; i<paths.length; i++) {
            children[i] = getItem(core, paths[i]);
        }
        return children;
    }

    public void save() throws Exception {
        core.save(path);
    }
    public void remove() throws Exception {
        core.remove(path);
    }
    public void rename(String alias) throws Exception {
        core.rename(path, alias);
        path.setAlias(alias);
    }

    public void addLogListener(LexiComLogListener listener) throws Exception {
        core.getLexiCom().addLogListener(listener, path.getPath(), path.getType().id);
    }
    public void removeLogListener(LexiComLogListener listener) throws Exception {
        core.getLexiCom().removeLogListener(listener, path.getPath(), path.getType().id);
    }

    public String decode(String encoded) throws Exception {
        return core.decode(encoded);
    }

    public String encode(String decoded) throws Exception {
        return core.encode(decoded);
    }
}