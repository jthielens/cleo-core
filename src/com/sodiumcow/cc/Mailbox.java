package com.sodiumcow.cc;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import com.cleo.lexicom.external.IMailboxController;
import com.cleo.lexicom.external.LexiComOutgoing;
import com.cleo.lexicom.external.RemoteLexiComOutgoing;
import com.sodiumcow.cc.constant.PathType;

public class Mailbox extends Node {
    private IMailboxController controller = null;

    public Mailbox(Core core, Path path) {
        super(core, path);
        if (path.getType() != PathType.MAILBOX) {
            throw new IllegalArgumentException();
        }
    }

    public Mailbox(Core core, String host, String mailbox) {
        super(core, new Path(PathType.MAILBOX, host, mailbox));
    }

    private synchronized void connect() throws Exception {
        if (controller==null) {
            controller = core.getMailboxController(path);
        }
    }

    public Action newTempAction(String alias) throws Exception {
        connect();
        return new Action(core, new Path(controller.createTempAction(alias)));
    }

    public boolean send(File f, String folder, String as) throws Exception {
        Properties props = new Properties();
        if (folder!=null) {
            props.setProperty(IMailboxController.PUT_DESTINATION, folder);
        }
        FileInputStream fis = new FileInputStream(f);
        LexiComOutgoing tx = new LexiComOutgoing(fis);
        tx.setFilename(as!=null?as:f.getName());
        return controller.send(new RemoteLexiComOutgoing(tx), props, false);
    }
}
