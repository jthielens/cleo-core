package com.sodiumcow.cc;

import java.util.Arrays;

import com.sodiumcow.cc.constant.HostSource;
import com.sodiumcow.cc.constant.HostType;
import com.sodiumcow.cc.constant.PathType;
import com.sodiumcow.cc.constant.Protocol;

public class Host extends Item {
    public static final String TEMPLATE_MAILBOX = "template mailbox";
    
    private HostSource source = HostSource.NEW;
    public HostSource getSource() {
        return source;
    }
    public void setSource(HostSource source) {
        this.source = source;
    }

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
        Item[] items = getChildren(PathType.MAILBOX);
        return (Mailbox[]) Arrays.copyOf(items, items.length, Mailbox[].class);
    }

    public Mailbox getMailbox(String alias) throws Exception {
        Path test = path.getChild(PathType.MAILBOX, alias);
        if (core.exists(test)) {
            return new Mailbox(core, test);
        }
        return null;
    }

    public Mailbox cloneMailbox(String template, String alias) throws Exception {
        Path template_path = path.getChild(PathType.MAILBOX, template);
        if (template_path!=null) {
            Path mailbox  = core.clone(template_path, alias);
            return new Mailbox(core, mailbox);
        } else {
            return createMailbox(alias);
        }
    }

    public Mailbox cloneMailbox(String alias) throws Exception {
        return cloneMailbox(TEMPLATE_MAILBOX, alias);
    }

    public Mailbox createMailbox(String alias) throws Exception {
        Path mailbox = core.create(path.getChild(PathType.MAILBOX, alias));
        return new Mailbox(core, mailbox);
    }

    public Mailbox findMailbox(String username, String password) throws Exception {
        boolean isHttp = getProtocol()==Protocol.HTTP_CLIENT;
        String userproperty = isLocal() ? "alias" : isHttp ? "Authusername": "Username";
        String passwordproperty = isHttp ? "Authpassword": "Password";
        for (Mailbox m : getMailboxes()) {
            try {
                if (m.matchProperty(userproperty, username) &&
                    m.matchProperty(passwordproperty, core.encode(password))) {
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
    public HostType getHostType() throws Exception {
        if (isLocal()) {
            return HostType.fromTransport(getSingleProperty("transport")+" listener");
        } else {
            return HostType.fromTransport(getSingleProperty("transport"));
        }
    }
}