package com.sodiumcow.cc;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;

import org.w3c.dom.Document;

import com.cleo.lexicom.external.ILexiCom;
import com.cleo.lexicom.external.LexiComFactory;
import com.sodiumcow.cc.constant.Mode;
import com.sodiumcow.cc.constant.PathType;
import com.sodiumcow.cc.constant.Product;

public class Core {
    private Product  product;
    private Mode     mode;
    private File     home;
    private ILexiCom lexicom;
    private Mode     connect_mode;

    public Product getProduct() {
        return product;
    }
    public void setProduct(Product product) {
        this.product = product;
    }
    public Mode getMode() {
        return mode;
    }
    public void setMode(Mode mode) {
        this.mode = mode;
    }
    public File getHome() {
        return home;
    }
    public void setHome(File home) {
        this.home = home;
    }
    public ILexiCom getLexiCom() throws Exception {
        connect();
        return lexicom;
    }

    public Core() {
        this.product      = null;
        this.mode         = null;
        this.home         = null;
        this.lexicom      = null;
        this.connect_mode = null;
    }
    public Core(Product product, Mode mode, File home) throws Exception {
        this.product      = product;
        this.mode         = mode;
        this.home         = home;
        this.lexicom      = null;
        this.connect_mode = null;
        connect();
    }

    public boolean connected() {
        return lexicom!=null;
    }

    public void connect() throws Exception {
        if (this.product==null || this.mode==null || this.home==null) {
            throw new Exception("product, mode, and home must not be null");
        }
        if (connect_mode != null && mode != connect_mode) {
            disconnect();
        }
        if (!connected()) {
            lexicom = LexiComFactory.getVersaLex(product.id, home.getAbsolutePath(), mode.id);
            connect_mode = mode;
        }
    }

    public void disconnect() throws Exception {
        if (this.lexicom != null) {
            lexicom.close();
            lexicom = null;
            connect_mode = null;
        }
    }
    
    public Path[] list(PathType type, Path path) throws Exception {
        connect();
        String[] names = lexicom.list(type.id, path.getPath());
        Path[] paths = new Path[names.length];
        for (int i=0; i<names.length; i++) {
            paths[i] = new Path(PathType.HOST, names[i]);
        }
        return paths;
    }
    
    public Document getHostDocument(Path path) throws Exception {
        connect();
        return lexicom.getDocument(path.getPath()[0]);
    }

    public boolean hasProperty(Path path, String property) throws Exception {
        connect();
        return lexicom.hasProperty(path.getType().id, path.getPath(), property);
    }
    public String[] getProperty(Path path, String property) throws Exception {
        connect();
        if (property.equalsIgnoreCase("id")) {
            return lexicom.getID(path.getType().id, path.getPath());
        } else {
            return lexicom.getProperty(path.getType().id, path.getPath(), property);
        }
    }
    private String[] editProperties(String[] original, String[] edits) {
        // need special handling for multi-valued advanced properties
        // first, build a dictionary of the existing attributes
        class Attribute implements Comparable<Attribute> {
            public String name;
            public String value;
            public int    index;
            public Attribute(String attribute, int index) {
                String[] pair = attribute.split("=", 2);
                this.name  = pair[0];
                this.value = pair.length==2 ? pair[1] : null;
                this.index = index;
            }
            @Override
            public int compareTo(Attribute arg0) {
                return this.index-arg0.index;
            }
        }
        HashMap<String,Attribute> dictionary = new HashMap<String,Attribute>(original.length);
        int counter;
        for (counter=0; counter<original.length; counter++) {
            Attribute a = new Attribute(original[counter], counter);
            dictionary.put(a.name.toLowerCase(), a);
        }
        // now update the list with edits
        //    attr=value is an addition or replacement
        //    attr=      is too, with a value of ""
        //    attr       means remove the attribute altogether (noop if it doesn't exist)
        for (String edit : edits) {
            String[]  pair     = edit.split("=", 2);
            Attribute existing = dictionary.get(pair[0].toLowerCase());
            if (existing!=null) {
                // edit, either replace or remove the value
                if (pair.length==2) {
                    existing.value = pair[1];
                } else {
                    dictionary.remove(pair[0].toLowerCase());
                }
            } else if (pair.length==2) {
                // add a new one at the end
                Attribute a = new Attribute(edit, counter++);
                dictionary.put(a.name.toLowerCase(), a);
            }
        }
        // Now turn it back into a list
        Attribute[] edited = dictionary.values().toArray(new Attribute[dictionary.size()]);
        Arrays.sort(edited);
        String[] values = new String[edited.length];
        for (int i=0; i<edited.length; i++) {
            if (edited[i].value != null) {
                values[i] = edited[i].name+"="+edited[i].value;
            } else {
                values[i] = edited[i].name;
            }
        }
        return values;
    }
    public void setProperty(Path path, String property, String value) throws Exception {
        connect();
        if (property.equalsIgnoreCase("id") || property.equalsIgnoreCase("advanced")) {
            String[] values = {value};
            setProperty(path, property, values);
        } else {
            lexicom.setProperty(path.getType().id, path.getPath(), property, value);
        }
    }
    public void setProperty(Path path, String property, String[] value) throws Exception {
        connect();
        if (property.equalsIgnoreCase("id")) {
            // use special ID manipulators instead of get/setProperty
            if (path.getType()==PathType.MAILBOX) {
                if (value.length>1) {
                    throw new Exception("Multiple IDs supported for HOST only");
                }
                lexicom.clearID(path.getType().id, path.getPath());
                if (value.length==1) {
                    lexicom.addID(path.getType().id, path.getPath(), value[0]);
                }
            } else if (path.getType()==PathType.HOST){ 
                if (value.length==0) {
                    lexicom.clearID(path.getType().id,  path.getPath());
                } else {
                    for (String id : value) {
                        lexicom.addID(path.getType().id, path.getPath(), id);
                    }
                }
            } else {
                throw new Exception("ID supported for HOST and MAILBOX only");
            }
        } else if (property.equalsIgnoreCase("advanced")) {
            String[] advanced = lexicom.getProperty(path.getType().id, path.getPath(), property);
            String[] update   = editProperties(advanced, value);
            lexicom.setProperty(path.getType().id, path.getPath(), property, update);
        } else {
            lexicom.setProperty(path.getType().id, path.getPath(), property, value);
        }
    }

    public Path lookup(PathType type, String id) throws Exception {
        connect();
        String[] path = lexicom.getPath(type.id, id);
        if (path==null) {
            return null;
        } else {
            return new Path(type, path);
        }
    }

    public void save(Path path) throws Exception {
        connect();
        lexicom.save(path.getType().id, path.getPath());
    }

    public void rename(Path path, String alias) throws Exception {
        connect();
        lexicom.rename(path.getType().id, path.getPath(), alias);
    }

    public String getVersion() throws Exception {
        connect();
        return lexicom.getVersion();
    }
}
