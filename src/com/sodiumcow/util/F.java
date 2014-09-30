package com.sodiumcow.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.Arrays;

public class F {
    public enum ClobberMode {NONE, UNIQUE, OVERWRITE;}
    
    public static File copy(File src, File dst) throws IOException {
        return copy(src, dst, ClobberMode.NONE);
    }
    public static File copy(File src, File dst, ClobberMode mode) throws IOException {
        if (!src.exists()) {
            throw new IOException("copy source does not exist");
        } else if (!src.isFile()) {
            throw new IOException("copy source must be a normal file");
        }
        if (dst.isDirectory()) {
            dst = new File(dst, src.getName());
        }
        if (dst.exists()) {
            try {
                if (Arrays.equals(md5(src), md5(dst))) {
                    return dst; // it's the same file anyway -- done
                }
            } catch (Exception e) {}
            if (mode==ClobberMode.UNIQUE) {
                int i = 1;
                while (new File(dst.getAbsolutePath()+"["+i+"]").exists()) i++;
                dst = new File(dst.getAbsolutePath()+"["+i+"]");
            } else if (mode==ClobberMode.NONE) {
                throw new IOException("copy destination already exists");
            }
        }
        if (!dst.exists()) {
            dst.createNewFile();
        }

        FileChannel schannel = null;
        FileChannel dchannel = null;

        try {
            schannel = new FileInputStream(src).getChannel();
            dchannel = new FileOutputStream(dst).getChannel();
            dchannel.transferFrom(schannel, 0, schannel.size());
        } finally {
            if (schannel != null) schannel.close();
            if (dchannel != null) dchannel.close();
        }
        return dst;
    }

    public static byte[] md5(String f) throws Exception {
        return md5(new File(f));
    }
    public static byte[] md5(File f) throws Exception {
        MessageDigest   md5 = MessageDigest.getInstance("MD5");
        FileInputStream fis = null;
        byte[]          buf = new byte[65536];
        try {
            fis = new FileInputStream(f);
            int n;
            while ((n = fis.read(buf)) >= 0) {
                md5.update(buf, 0, n);
            }
        } finally {
            if (fis!=null) fis.close();
        }
        return md5.digest();
    }

    public static String hex(byte[] bytes) {
        StringBuffer s = new StringBuffer();
        for (byte b : bytes) {
            s.append(String.format("%02x", b));
        }
        return s.toString();
    }
}
