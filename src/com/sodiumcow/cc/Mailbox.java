package com.sodiumcow.cc;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Properties;

import com.cleo.lexicom.external.DirectoryEntry;
import com.cleo.lexicom.external.IActionController;
import com.cleo.lexicom.external.IMailboxController;
import com.cleo.lexicom.external.LexiComLogListener;
import com.cleo.lexicom.external.LexiComOutgoing;
import com.cleo.lexicom.external.RemoteLexiComOutgoing;
import com.sodiumcow.cc.constant.PathType;
import com.sodiumcow.cc.constant.Sort;
import com.sodiumcow.util.X;

public class Mailbox extends Item {
    private IMailboxController controller = null;
    private String             lastResult = null;

    public Mailbox(Core core, Path path) {
        super(core, path);
        if (path.getType() != PathType.MAILBOX) {
            throw new IllegalArgumentException();
        }
    }

    public Mailbox(Core core, String host, String mailbox) {
        super(core, new Path(PathType.MAILBOX, host, mailbox));
    }

    public String getLastResult() {
        return lastResult;
    }

    public Action[] getActions() throws Exception {
        Item[] nodes = getChildren(PathType.ACTION);
        return (Action[]) Arrays.copyOf(nodes, nodes.length, Action[].class);
    }

    public Action getAction(String alias) throws Exception {
        Path test = path.getChild(PathType.ACTION, alias);
        if (core.exists(test)) {
            return new Action(core, test);
        }
        return null;
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

    public boolean send(File f, String folder, String as, LexiComLogListener listener) throws Exception {
        connect();
        Action action = newTempAction("send");
        if (listener!=null) {
            action.addLogListener(listener);
        }
        Properties props = new Properties();
        if (folder!=null) {
            props.setProperty(IMailboxController.PUT_DESTINATION, folder);
        }
        FileInputStream fis = new FileInputStream(f);
        LexiComOutgoing tx = new LexiComOutgoing(fis);
        tx.setFilename(as!=null?as:f.getName());
        boolean result = controller.send(new RemoteLexiComOutgoing(tx), props, false);
        lastResult = X.xml2string(controller.getLastResult());
        if (listener!=null) {
            action.removeLogListener(listener);
        }
        //action.remove();  // throws a no bean for send
        return result;
    }

    public DirectoryEntry[] list(String path, String glob, LexiComLogListener listener) throws Exception {
        connect();
        Action action = newTempAction("list");
        if (listener!=null) {
            action.addLogListener(listener);
        }
        IActionController actionController = controller.getActionController(action.getPath().getPath());
        DirectoryEntry[]  files = actionController.getDirectoryController().list(path, false, null, Sort.NONE.id);
        lastResult = X.xml2string(controller.getLastResult());
        if (listener!=null) {
            action.removeLogListener(listener);
        }
        action.remove();  // it looks like this is good hygiene for list
        return files;
    }
}
