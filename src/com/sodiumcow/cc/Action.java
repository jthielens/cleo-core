package com.sodiumcow.cc;

import com.cleo.lexicom.external.ISchedule;
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
    
    public Schedule getSchedule() throws Exception {
        return new Schedule(core.getLexiCom().getSchedule().findItem(getPath().getPath()));
    }

    public void setSchedule(Schedule schedule) throws Exception {
        if (schedule==null) {
            core.getLexiCom().getSchedule().removeItem(getPath().getPath(), true);
        } else {
            ISchedule.Item item = core.getLexiCom().getSchedule().newItem(getPath().getPath());
            schedule.transcribe(item);
            core.getLexiCom().getSchedule().updateItem(item, true);
        }
    }
}
