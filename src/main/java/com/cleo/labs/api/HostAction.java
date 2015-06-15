package com.cleo.labs.api;

import com.cleo.labs.api.constant.PathType;
import com.cleo.lexicom.external.ISchedule;

public class HostAction extends Item {
    public HostAction(Core core, Path path) {
        super(core, path);
        if (path.getType() != PathType.HOST_ACTION) {
            throw new IllegalArgumentException();
        }
    }

    public HostAction(Core core, String host, String action) {
        super(core, new Path(PathType.HOST_ACTION, host, action));
    }
    
    public Schedule getSchedule() throws Exception {
        ISchedule.Item s = core.getLexiCom().getSchedule().findItem(getPath().getPath());
        if (s==null) {
            return null;
        } else {
            return new Schedule(s);
        }
    }

    public void setSchedule(Schedule schedule) throws Exception {
        ISchedule scheduler = core.getLexiCom().getSchedule();
        if (schedule==null) {
            scheduler.removeItem(getPath().getPath(), true);
        } else {
            ISchedule.Item item = scheduler.newItem(getPath().getPath());
            schedule.transcribe(item);
            scheduler.updateItem(item, true);
        }
    }
}
