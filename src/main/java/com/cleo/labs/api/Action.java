package com.cleo.labs.api;

import com.cleo.labs.api.constant.PathType;
import com.cleo.lexicom.external.ISchedule;

public class Action extends Item {
    public Action(Path path) {
        super(path);
        if (path.getType() != PathType.ACTION) {
            throw new IllegalArgumentException();
        }
    }

    public Action(String host, String mailbox, String action) {
        super(new Path(PathType.ACTION, host, mailbox, action));
    }
    
    public Schedule getSchedule() throws Exception {
        ISchedule.Item s = Core.getSchedule().findItem(getPath().getPath());
        if (s==null) {
            return null;
        } else {
            return new Schedule(s);
        }
    }

    public void setSchedule(Schedule schedule) throws Exception {
        ISchedule scheduler = Core.getSchedule();
        if (schedule==null) {
            scheduler.removeItem(getPath().getPath(), true);
        } else {
            ISchedule.Item item = scheduler.newItem(getPath().getPath());
            schedule.transcribe(item);
            scheduler.updateItem(item, true);
            scheduler.save();
        }
    }
}
