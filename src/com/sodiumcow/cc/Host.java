package com.sodiumcow.cc;

import com.sodiumcow.cc.constant.PathType;
import com.sodiumcow.cc.constant.Protocol;

public class Host {
    private Core core;
    private Path path;

    private Host() {
    }

    static public Host getHost(Core core, String path) throws Exception {
        return getHost(core, new Path(PathType.HOST, path));
    }
    static public Host getHost(Core core, Path path) {
        Host host = new Host();
        host.core = core;
        host.path = path;
        return host;
    }
    
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
