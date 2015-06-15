package com.cleo.labs.api;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cleo.labs.api.constant.PathType;

public class Path {
    private String   path[];
    private PathType type;

    public String[] getPath() {
        return path;
    }

    public String getAlias() {
        return path[path.length-1];
    }
    
    public void setAlias(String alias) {
        path[path.length-1] = alias;
    }

    public PathType getType() {
        return type;
    }

    public String toString() {
        if (type==null) {
            return "";
        }
        switch (type) {
        case HOST:
            return path[0];
        case MAILBOX:
            return path[1]+"@"+path[0];
        case ACTION:
            return "<"+path[2]+">"+path[1]+"@"+path[0];
        case HOST_ACTION:
            return "<"+path[1]+">@"+path[0];
        case SERVICE:
            return path[1]+"::"+path[0];
        case TRADING_PARTNER:
            // I don't know what this looks like yet, so let's call it %
            return path[1]+"%"+path[0];
        default:
            // can't happen since that's an exhaustive list, but to keep Eclipse happy:
            return "";
        }
    }

    public Path getHost() {
        return new Path(PathType.HOST, path[0]);
    }

    public Path getParent() {
        if (type==null) {
            return this;
        }
        String[] parent = Arrays.copyOf(path, path.length-1);
        return new Path (parent);
    }

    public Path getChild(PathType type, String name) {
        String[] child = Arrays.copyOf(path,  path.length+1);
        child[child.length-1] = name;
        return new Path (type, child);
    }

    private void setTypeAndPath(PathType type, String...path) {
        if (path.length==0 && type==null ||
            path.length==1 && type==PathType.HOST ||
            path.length==2 && (type==PathType.MAILBOX ||
                               type==PathType.HOST_ACTION ||
                               type==PathType.TRADING_PARTNER ||
                               type==PathType.SERVICE) ||
            path.length==3 && type==PathType.ACTION) {
            this.type = type;
            this.path = path;
        } else if (path.length > 3) {
            throw new IllegalArgumentException("maximum path depth is 3");
        } else {
            throw new IllegalArgumentException("invalid type for path depth");
        }
    }

    /**
     * Parses a path according to the conventions gleaned from moditem attribute
     * encodings in the <Host> element of a host file:
     *     host                    (modtype=Hosts)
     *     mailbox@host            (modtype=Mailboxes)
     *     <action>mailbox@host    (modtype=Actions)
     *     <hostaction>@host       (modtype=Host Actions)
     *     Local Listener          (modtype=Local Listener)
     *     service::Local Listener (modtype=Service)
     *     partner%host            (just guessing)
     * @param path
     */
    private static final Pattern PATH_PARSER =  Pattern.compile("(?:(?:<(.*)>)?(.*)?@|(?:(.*)::)|(?:(.*)%))?(.*)");
    // 1.7  static final Pattern PATH_PARSER =  Pattern.compile("(?:(?:<(?<action>.*)>)?(?<mailbox>.*)?@|(?:(?<service>.*)::)(?:(?<partner>.*)%))?(?<host>.*)");
    public static Path parsePath(String path) {
        if (path==null || path.length()==0) {
            return new Path();
        }
        Matcher m = PATH_PARSER.matcher(path);
        if (m.matches()) {
            String action  = m.group(1);
            String mailbox = m.group(2);
            String service = m.group(3);
            String partner = m.group(4);
            String host    = m.group(5);
            if (service!=null && service.length()>0) {
                return new Path(PathType.SERVICE, host, service);
            } else if (partner!=null && partner.length()>0) {
                return new Path(PathType.TRADING_PARTNER, host, partner);
            } else if (mailbox!=null && mailbox.length()>0) {
                if (action!=null && action.length()>0) {
                    return new Path(PathType.ACTION, host, mailbox, action);
                } else {
                    return new Path(PathType.MAILBOX, host, mailbox);
                }
            } else if (action!=null && action.length()>0) {
                return new Path(PathType.HOST_ACTION, host, action);
            } else {
                return new Path(PathType.HOST, host);
            }
        } else {
            // I think anything should match at least (?<host>.*), but here goes:
            return new Path(PathType.HOST, path);
        }
    }

    public Path(PathType type, String...path) {
        setTypeAndPath(type, path);
    }

    public Path(String...path) {
        if (path==null) path = new String[0];
        this.path = path;
        switch (path.length) {
        case 0:
            this.type = null;
            break;
        case 1:
            this.type = PathType.HOST;
            break;
        case 2:
            this.type = PathType.MAILBOX;
            break;
        case 3:
            this.type = PathType.ACTION;
            break;
        default:
            throw new IllegalArgumentException("maximum path depth is 3");
        }
    }
}