package com.cleo.labs.api;

import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Proxy {
    public enum Type {
        HTTP(80), FTP(21), SMTP(25), SOCKS4(1080), SOCKS5(1080);
        public int port;
        private Type (int port) {
            this.port = port;
        }
    }
    public static class Description {
        public Type              type;
        public InetSocketAddress socket; // host and port

        // protocol://host[:port]
        static final Pattern PARSE = Pattern.compile("(\\w+)?://([^:]+)(?::(\\d+))?");

        public Description(String s) {
            Matcher m = PARSE.matcher(s);
            if (m.matches()) {
                this.type = Type.valueOf(m.group(1));
                String host = m.group(2);
                int port = m.group(3)==null ? type.port : Integer.valueOf(m.group(3));
                this.socket = new InetSocketAddress(host, port);
            } else {
                throw new IllegalArgumentException("cannot parse proxy URL: "+s);
            }
        }

        @Override
        public String toString() {
            return type.name().toLowerCase()+"://"+socket.getHostString()+":"+socket.getPort();
        }

        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((socket == null) ? 0 : socket.hashCode());
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Description other = (Description) obj;
            if (socket == null) {
                if (other.socket != null) {
                    return false;
                }
            } else if (!socket.equals(other.socket)) {
                return false;
            }
            if (type != other.type) {
                return false;
            }
            return true;
        }
    }
    public Proxy() {
        // TODO Auto-generated constructor stub
    }

}
