package com.sodiumcow.cc.migrate;

import java.util.Arrays;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import org.w3c.dom.Document;

import com.sodiumcow.repl.REPL;
import com.sodiumcow.repl.annotation.Command;
import com.sodiumcow.util.F;
import com.sodiumcow.util.X;

public class ST extends REPL {

    private        String      filename = "/Users/john/Documents/work/@customer/fico.com/f_company.xml";
    private        STcrypt     crypt    = null;
    private Map<String,Object> export   = null;

    @Command(name="load", args="[filename]", max=1, comment="load XML export")
    public void load(String...filename) throws Exception {
        if (filename.length>0) {
            this.filename = filename[0];
        }
        Document xml = X.file2xml(this.filename);
        this.export = X.xml2map(xml);
    }

    private void setPassword(String password) {
        crypt = new STcrypt(password);
    }

    @Command(name="password", args="password", comment="set password")
    public void password(String password) throws Exception {
        if (export==null) load();
        // extract encrypted passphrase and verify password
        String ePhrase = (String) X.subobj(export, "exportData", ".encryptedPhrase");
        if (ePhrase==null) {
            error("encryptedPhrase not found in the exportData");
            return;
        } else if (!STcrypt.match(password, ePhrase)) {
            error("password does not match encryptedPhrase");
            return;
        }
        setPassword(password);
    }

    @Command(name="d", args="path", comment="key recovery")
    public void d(String path) {
        if (crypt==null) {
            error("password not set: use password command");
            return;
        }

        // get the ciphertext
        String text = (String) X.subobj(export, path.split("/"));
        if (text==null) {
            error(path+" not found (null)");
            return;
        }
        String plain = crypt.decrypt(text);
        if (text.equals(plain)) {
            error(path+" doesn't look like ciphertext: "+text);
            return;
        }
        report(plain);
    }

    @Command(name="e", args="text", comment="encrypt")
    public void e(String plaintext) {
        if (crypt==null) {
            error("password not set: use password command");
            return;
        }
        byte[] ciphertext = crypt.encrypt(plaintext);
        if (ciphertext!=null) {
            report(F.hex(ciphertext)+" ("+ciphertext.length+" bytes) "+DatatypeConverter.printBase64Binary(ciphertext));
        }
    }

    @Command(name="p", args="path...", comment="print values")
    public void p(String...paths) throws Exception {
        if (export==null) load();
        for (String path : paths) {
            String[] nodes = path.split("/");
            Object o;
            if (path.contains("*") || path.contains("?")) {
                o = X.subprune(export, nodes);
                load();
            } else {
                o = X.subobj(export, nodes);
            }
            String prompt = path+": ";
            if (o==null) {
                report(prompt, "not found");
            } else {
                if (o instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String,Object> out = (Map<String,Object>)o;
                    report(prompt, X.map2tree(out));
                } else {
                    report(prompt, o.toString());
                }
            }
        }
    }
    @Command(name="a", args="a", min=2, comment="test")
    public void a (String x, String y, String z1) {
        String[] z = new String[] {z1};
        report("x="+x+" y="+y+" z="+Arrays.toString(z));
    }

    public static void main(String[] argv) {
        ST repl = new ST();
        try {
            repl.password("Cle0_User1");
        } catch (Exception ignore) { }
        repl.run(argv);
        System.exit(0);
    }
}
