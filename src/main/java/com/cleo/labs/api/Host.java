package com.cleo.labs.api;

import java.util.Arrays;

import com.cleo.labs.api.constant.HostSource;
import com.cleo.labs.api.constant.HostType;
import com.cleo.labs.api.constant.PathType;
import com.cleo.labs.api.constant.Protocol;

public class Host extends Item {
    public static final String TEMPLATE_MAILBOX = "template mailbox";
    
    private HostSource source = HostSource.NEW;
    public HostSource getSource() {
        return source;
    }
    public void setSource(HostSource source) {
        this.source = source;
    }

    public Host(Path path) {
        super(path);
        if (path.getType() != PathType.HOST) {
            throw new IllegalArgumentException();
        }
    }

    public Host(String host) {
        super(new Path(PathType.HOST, host));
    }

    public Mailbox[] getMailboxes() throws Exception {
        Item[] items = getChildren(PathType.MAILBOX);
        return (Mailbox[]) Arrays.copyOf(items, items.length, Mailbox[].class);
    }

    public Mailbox getMailbox(String alias) throws Exception {
        Path test = path.getChild(PathType.MAILBOX, alias);
        if (Core.exists(test)) {
            return new Mailbox(test);
        }
        return null;
    }

    public Mailbox cloneMailbox(String template, String alias) throws Exception {
        Path template_path = path.getChild(PathType.MAILBOX, template);
        if (template_path!=null) {
            Path mailbox  = Core.clone(template_path, alias);
            return new Mailbox(mailbox);
        } else {
            return createMailbox(alias);
        }
    }

    public Mailbox cloneMailbox(String alias) throws Exception {
        return cloneMailbox(TEMPLATE_MAILBOX, alias);
    }

    public Mailbox createMailbox(String alias) throws Exception {
        Path mailbox = Core.create(path.getChild(PathType.MAILBOX, alias));
        return new Mailbox(mailbox);
    }

    public Mailbox findMailbox(String username, String password) throws Exception {
        boolean isHttp = getProtocol()==Protocol.HTTP_CLIENT;
        String userproperty = isLocal() ? "alias" : isHttp ? "Authusername": "Username";
        String passwordproperty = isHttp ? "Authpassword": "Password";
        for (Mailbox m : getMailboxes()) {
            try {
                if (m.matchProperty(userproperty, username) &&
                    (password==null ||   // null means don't match on password
                     m.matchProperty(passwordproperty, Core.encode(password)))) {
                    return m;
                }
            } catch (Exception ignore) {
                // sometimes ILexiCom seems to get out of sync and returns non-existing mailboxes
            }
        }
        return null;
    }

    // hmm below this line---
    public boolean isMultipleIDsAllowed() throws Exception {
        return Core.isMultipleIDsAllowed(path);
    }
    public void setMultipleIDsAllowed(boolean allowed) throws Exception {
        Core.setMultipleIDsAllowed(path, allowed);
    }
    public boolean isLocal() throws Exception {
        return Core.isLocal(path);
    }

    public Protocol getProtocol() throws Exception {
        return Core.getHostProtocol(path);
    }
    public HostType getHostType() throws Exception {
        if (isLocal()) {
            return HostType.fromTransport(getSingleProperty("transport")+" listener");
        } else {
            return HostType.fromTransport(getSingleProperty("transport"));
        }
    }
}