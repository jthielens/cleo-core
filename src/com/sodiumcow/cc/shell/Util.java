package com.sodiumcow.cc.shell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Properties;
import java.util.TreeMap;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.cleo.lexicom.external.ILicense;
import com.sodiumcow.cc.Core;
import com.sodiumcow.repl.REPL;
import com.sodiumcow.util.S;

public class Util {

    public static String licensed_features(ILicense license) {
        ArrayList<String> features = new ArrayList<String>();
        if (license.isTranslatorLicensed()) features.add("integration");
        if (license.isVLProxyLicensed())    features.add("vlproxy");
        if (license.isWebBrowserLicensed()) features.add("browser");
        if (license.isApiLicensed())        features.add("api");
        if (license.isFipsLicensed())       features.add("fips");
        if (license.isSecureEmailLicensed())features.add("secure-email");
        return S.join(", ", features);
    }

    public static String licensed_hosts(int[] hosts) {
        if (hosts.length==0) return "Any";
        ArrayList<String> list = new ArrayList<String>(hosts.length);
        for (int host : hosts) {
            switch (host) {
                case ILicense.MICHAELS: list.add("MICHAELS"); break;
                case ILicense.IBM_IE  : list.add("IBM IE");   break;
                case ILicense.GXS     : list.add("GXS");      break;
                case ILicense.KOHLS   : list.add("KOHLS");    break;
                case ILicense.EDS_ELIT: list.add("EDS ELIT"); break;
                case ILicense.WAL_MART: list.add("WALMART");  break;
                case ILicense.ASDA    : list.add("ASDA");     break;
                default:                list.add("Unknown("+host+")");
            }
        }
        return S.join(", ", list);
    }

    public static String licensed_platform(int platform) {
        if (platform==(ILicense.AS400|ILicense.UNIX|ILicense.WINDOWS)) return "Any";
        ArrayList<String> platforms = new ArrayList<String>();
        if ((platform&ILicense.AS400  ) != 0) platforms.add("AS400");
        if ((platform&ILicense.UNIX   ) != 0) platforms.add("UNIX");
        if ((platform&ILicense.WINDOWS) != 0) platforms.add("WINDOWS");
        return S.join(", ", platforms);
    }

    public static String licensed_product(int product) {
        if (product==ILicense.HARMONY)  return "HARMONY";
        if (product==ILicense.VLTRADER) return "VLTRADER";
        if (product==ILicense.LEXICOM)  return "LEXICOM";
        return "Unknown("+product+")";
    }

    public static String licensed_until(ILicense license) {
        Date expires = license.getKeyExpiration();
        if (license.isTemporary() && expires != null) {
            return "through "+new SimpleDateFormat("yyyy/MM/dd").format(expires);
        } else {
            return "permanently";
        }
    }
    
    public static String xml2string(Node doc) {
        try {
           DOMSource domSource = new DOMSource(doc);
           StringWriter writer = new StringWriter();
           StreamResult result = new StreamResult(writer);
           TransformerFactory tf = TransformerFactory.newInstance();
           Transformer transformer = tf.newTransformer();
           transformer.setOutputProperty(OutputKeys.INDENT, "yes");
           transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
           transformer.transform(domSource, result);
           writer.flush();
           return writer.toString();
        } catch (TransformerException ex) {
           ex.printStackTrace();
           return null;
        }
    }

    public static Map<String,String> attrs2map(Node e) {
        Map<String,String> map = new HashMap<String,String>();
        NamedNodeMap attrs = e.getAttributes();
        for (int i=0; i<attrs.getLength(); i++) {
            map.put(attrs.item(i).getNodeName(),
                    attrs.item(i).getNodeValue());
        }
        return map;
    }

    private static void flat(Map<String,String> result, String prefix, Map<String,Object> map, int depth) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String) {
                result.put(prefix+"."+e.getKey(), (String)v);
            } else if (depth!=0) {
                @SuppressWarnings({"unchecked"})
                Map<String,Object> m = (Map<String,Object>) v;
                flat(result, prefix+"."+e.getKey(), m, depth-1);
            }
        }
    }
    public static Map<String,String> flat(Map<String,Object> map) {
        return flat(map, -1);
    }
    public static Map<String,String> flat(Map<String,Object> map, int depth) {
        Map<String,String> result = new TreeMap<String,String>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String) {
                result.put(e.getKey(), (String)v);
            } else if (depth!=0) {
                @SuppressWarnings({"unchecked"})
                Map<String,Object> m = (Map<String,Object>) v;
                flat(result, e.getKey(), m, depth-1);
            }
        }
        return result;
    }
    public static Map<String,Object> xml2map(Node e) {
        Map<String,Object> map = new TreeMap<String,Object>();
        // e represents <foo attr=value ...>contents</foo>
        //   where contents are
        //     <parameter>value</parameter>
        //     or nested <bar attr=value ...>contents</bar>
        //     or even <bar attr=value .../>
        // xml2map converts the "foo" Node to a flat Map with
        //   each attr=value      converted to .attr     => value
        //   each parameter/value converted to parameter => value
        //   each nested bar      converted to bar       => nested Map

        // Step 1: pull in the attr=value for <foo>
        NamedNodeMap attrs = e.getAttributes();
        if (attrs!=null) {
            for (int i=0; i<attrs.getLength(); i++) {
                map.put("."+attrs.item(i).getNodeName(),
                        attrs.item(i).getNodeValue());
            }
        }
        
        // Step 2: walk the child nodes
        for (Node p = e.getFirstChild(); p!=null; p=p.getNextSibling()) {
            Node child = p.getFirstChild();
            if (child!=null) {
                if (child.getNodeType()==Node.TEXT_NODE &&
                    child.getNextSibling() == null) {
                    // <parameter>value</parameter>
                    String text = child.getNodeValue().trim();
                    if (!text.isEmpty()) {
                        String name = p.getNodeName();
                        if (name.equalsIgnoreCase("Advanced")) {
                            String[] kv = text.split("=", 2);
                            name = name+"."+kv[0];
                            text = kv.length>1 ? kv[1] : "";
                        } else if (name.equalsIgnoreCase("Syntax") || name.equalsIgnoreCase("Header")) {
                            // <Syntax (or Header)>GET stuff</Syntax>
                            String[] kv = text.split(" ", 2);
                            name = name+"["+kv[0]+"]";
                            text = kv.length>1 ? kv[1] : "";
                        } else if (name.equalsIgnoreCase("Contenttypedirs")) {
                            // <Contenttypedirs>type=type</Contenttypedirs>
                            String[] kv = text.split("=", 2);
                            name = name+"["+kv[0]+"]";
                            text = kv.length>1 ? kv[1] : "";
                        }
                        if (map.containsKey(name+"[0]")) {
                            int i;
                            for (i=2; map.containsKey(name+"["+i+"]"); i++);
                            name = name+"["+i+"]";
                        } else if (map.containsKey(name)) {
                            map.put(name+"[0]", map.remove(name));
                            name = name+"[1]";
                        }
                        map.put(name, text);
                    }
                } else {
                    // <bar ...>
                    String name = p.getNodeName();
                    Map<String,Object> pmap = xml2map(p);
                    if (pmap.containsKey(".alias")) {
                        name = name+"["+pmap.get(".alias")+"]";
                    } else if (map.containsKey(name+"[0]")) {
                        int i;
                        for (i=2; map.containsKey(name+"["+i+"]"); i++);
                        name = name+"["+i+"]";
                    } else if (map.containsKey(name)) {
                        map.put(name+"[0]", map.remove(name));
                        name = name+"[1]";
                    }
                    map.put(name, pmap);
                }
            }
        }
    
        // Done
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Map<String,Object> submap(Map<String,Object> map, String...steps) {
        Object o = subobj(map, steps);
        if (o instanceof Map) {
            return (Map<String,Object>) o;
        }
        return null;
    }
    public static Object subobj(Map<String,Object> map, String...steps) {
        Object o = map;
        for (String step : steps) {
            if (!(o instanceof Map)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String,Object> x = (Map<String,Object>) o;
            o = x.get(step);
        }
        return o;
    }

    @SuppressWarnings("unchecked")
    public static Map<String,Object> setmap(final Map<String,Object> map, String[] path, Object value) {
        if (path.length==0) {
            throw new IllegalArgumentException("setmap path can't be empty");
        }
        Map<String,Object> level = map;
        for (int i=0; i<path.length-1; i++) {
            String step = path[i];
            if (!level.containsKey(step) || !(level.get(step) instanceof Map)) {
                Map<String,Object> newmap = new TreeMap<String,Object>();
                level.put(step, newmap);
                level = newmap;
            } else {
                level = (Map<String,Object>)level.get(step);
            }
        }
        if (value==null) {
            level.remove(path[path.length-1]);
        } else {
            level.put(path[path.length-1], value);
        }
        return map;
    }
    public static Map<String,Object> setmap(final Map<String,Object> map, String[] path, Map<String,Object> value) {
        return setmap(map, path, (Object)value);
    }
    public static Map<String,Object> setmap(final Map<String,Object> map, String[] path, String value) {
        return setmap(map, path, (Object)value);
    }

    @SuppressWarnings("unchecked")
    public static String map2tree(Map<String,Object> map) {
        StringBuilder s = new StringBuilder();
        StringBuilder prefix = new StringBuilder();
        Deque<Iterator<Entry<String,Object>>> q = new ArrayDeque<Iterator<Entry<String,Object>>>();
        Iterator<Entry<String,Object>> i = map.entrySet().iterator();
        while (i.hasNext()) {
            Entry<String,Object> e = i.next();
            if (e.getValue() instanceof String) {
                s.append(prefix).append(e.getKey()).append('=').append((String)e.getValue()).append('\n');
            } else {
                s.append(prefix).append(e.getKey()).append(':').append('\n');
                prefix.append(". ");
                q.push(i);
                i = ((Map<String,Object>)e.getValue()).entrySet().iterator();
            }
            while (!i.hasNext() && !q.isEmpty()) {
                i = q.pop();
                prefix.setLength(prefix.length()-2);
            }
        }
        return s.toString();
    }

    private static final Pattern KEY_INDEX = Pattern.compile("(\\w+)(?:\\[(.+)\\]|\\.(.+))?");
    // word (alone), or word[index] (group(2) is index), or word.index (group(3) is index)
    @SuppressWarnings("unchecked")
    public static Document map2xml(Map<String,Object> map) throws ParserConfigurationException {
        Document doc = DocumentBuilderFactory.newInstance()
                                             .newDocumentBuilder()
                                             .newDocument();
        Element elem = null;
        Deque<Iterator<Entry<String,Object>>> qi = new ArrayDeque<Iterator<Entry<String,Object>>>();
        Deque<Element>                        qe = new ArrayDeque<Element>();
        Iterator<Entry<String,Object>> i = map.entrySet().iterator();
        while (i.hasNext()) {
            Entry<String,Object> e = i.next();
            String key = e.getKey();
            if (e.getValue() instanceof String) {
                if (key.startsWith(".")) {
                    elem.setAttribute(key.substring(1), (String)e.getValue());
                } else {
                    Matcher m = KEY_INDEX.matcher(key);
                    m.matches();
                    key = m.group(1);
                    String index = m.group(2)!=null ? m.group(2) : m.group(3);
                    String text = (String)e.getValue();
                    if (index!=null) {
                        // Advanced.key          = value --> Advanced        key=value
                        // Contenttypedirs[type] = value --> Contenttypedirs type=value
                        // Syntax|Header[verb]   = value --> Syntax|Header   verb value
                        if (key.equalsIgnoreCase("Advanced") || key.equalsIgnoreCase("Contenttypedirs")) {
                            text = index + "=" + text;
                        } else if (key.equalsIgnoreCase("Syntax") || key.equalsIgnoreCase("Header")) {
                            if (text.isEmpty()) {
                                text = index;
                            } else {
                                text = index + " " + text;
                            }
                        }
                    }
                    Element param = doc.createElement(key);
                    param.appendChild(doc.createTextNode(text));
                    elem.appendChild(param);
                }
            } else {
                key = key.split("\\[", 2)[0];  // split off key[index] to just key
                Element newelem = doc.createElement(key);
                if (elem==null) {
                    doc.appendChild(newelem);
                    elem = newelem;
                } else {
                    elem.appendChild(newelem);
                }
                qi.push(i);
                qe.push(elem);
                i = ((Map<String,Object>)e.getValue()).entrySet().iterator();
                elem = newelem;
            }
            while (!i.hasNext() && !qi.isEmpty()) {
                i = qi.pop();
                elem = qe.pop();
            }
        }
        return doc;
    }

    public static String xml2tree(Document doc) {
        StringBuilder s = new StringBuilder();
        Deque<Node> tree = new ArrayDeque<Node>();
        Node p = doc.getDocumentElement();
        Node next;
        StringBuilder prefix = new StringBuilder();
        
        do {
            boolean prune = false;
            switch (p.getNodeType()) {
            case Node.ELEMENT_NODE:
                // print out Name
                s.append(prefix)
                 .append(p.getNodeName());
                // append {attrs} if there are any
                Map<String,String> attrs = attrs2map(p);
                if (!attrs.isEmpty()) {
                    s.append(attrs.toString());
                }
                // :, but will it be "text" or nesting deeper?
                s.append(':');
                // if there is a single non-empty TEXT child, it's
                // <thing>stuff</thing>, so make it thing:stuff
                Node child = p.getFirstChild();
                if (child != null &&
                    child.getNodeType()==Node.TEXT_NODE &&
                    child.getNextSibling() == null) {
                    String text = child.getNodeValue().trim();
                    if (!text.isEmpty()) {
                        prune = true; // suppress the child treewalker
                        s.append(text);
                    }
                }
                s.append("\n");
                break;
            case Node.TEXT_NODE:
                // print out the "string", skipping empties
                String text = p.getNodeValue().trim();
                if (!text.isEmpty()) {
                    s.append(prefix)
                     .append('"')
                     .append(text)
                     .append('"')
                     .append("\n");
                }
                break;
            default:
                // don't really expect any of these, so just dump it
                s.append(prefix)
                 .append("type=")
                 .append(p.getNodeType())
                 .append(" ")
                 .append(p.getNodeName())
                 .append("\n");
            }
            if (!prune && (next = p.getFirstChild()) != null) {
                // deeper (remember thing:stuff though)
                tree.push(p);
                p = next;
                prefix.append(". ");
            } else if ((next = p.getNextSibling()) != null) {
                // to the right
                p = next;
            } else {
                // end of the row, pop up and to the right, and up...
                while (!tree.isEmpty() && next == null) {
                    next = tree.pop().getNextSibling();
                    prefix.delete(0,2);
                }
                p = next;
            }
        } while (p != null);
        return s.toString();
    }

    public static void report_bean(REPL repl, Object o) {
        // pass 1: calculate max
        int max = 1;
        String name;
        for (Method method : o.getClass().getMethods()) {
            name = method.getName();
            if ((name.startsWith("get")||name.startsWith("is")) && method.getParameterTypes().length==0) {
                if (name.length()>max) max=name.length();
            }
        }
        // pass 2: report
        for (Method method : o.getClass().getMethods()) {
            name = method.getName();
            if ((name.startsWith("get")||name.startsWith("is")) && method.getParameterTypes().length==0) {
                int drop = name.startsWith("get") ? "get".length() : "is".length();
                char[] attr = Arrays.copyOf((name.substring(drop,drop+1).toLowerCase()+
                                             name.substring(drop+1)).toCharArray(),
                                            max-drop+1);  // -("get"|"is") + " "
                Arrays.fill(attr, name.length()-drop, attr.length, ' ');
                String prefix = new String(attr);
                              
                try {
                    if (method.getReturnType().equals(String.class)) {
                        String value = (String) method.invoke(o);
                        repl.report(prefix, value);
                    } else if (method.getReturnType().equals(String[].class)) {
                        String[] value = (String[]) method.invoke(o);
                        repl.report(prefix, value);
                    } else if (method.getReturnType().equals(Integer.TYPE)) {
                        int value = (Integer) method.invoke(o);
                        repl.report(prefix, String.valueOf(value));
                    } else if (method.getReturnType().equals(Boolean.TYPE)) {
                        boolean value = (Boolean) method.invoke(o);
                        repl.report(prefix, String.valueOf(value));
                    } else if (method.getReturnType().equals(Properties.class)) {
                        Properties value  = (Properties) method.invoke(o);
                        String[]   values = new String[value.size()];
                        int        i      = 0;
                        for (Entry<Object,Object> v : value.entrySet()) {
                            values[i++] = (String)v.getKey()+"="+(String)v.getValue();
                        }
                        repl.report(prefix, values);
                    } else if (method.getReturnType().equals(Object.class)) {
                        Object value = method.invoke(o);
                        if (value==null) {
                            repl.report(prefix, ": null");
                        } else {
                            repl.report(prefix, ": ("+value.getClass().getName()+")");
                            report_bean(repl, value);
                        }
                    }
                } catch (Exception e) {
                    repl.error("error invoking "+method.getName(), e);
                }
            }
        }
    }

   public static String[] xml2pp (Document doc) {
       return xml2pp(doc.getDocumentElement());
   }

   public static String[] xml2pp (Node p) {
        Deque<Node> tree = new ArrayDeque<Node>();
        Node next;
        String[] row = {"","",""};
        String prefix = "";
        ArrayList<String[]> out = new ArrayList<String[]>();
        
        do {
            boolean prune = false;
            switch (p.getNodeType()) {
            case Node.ELEMENT_NODE:
                Map<String,String> attrs = attrs2map(p);
                Node child = p.getFirstChild();
                // if there are no attributes,
                // and there is a single non-empty TEXT child, it's
                // <thing>stuff</thing>, so make it thing:stuff
                if (attrs.isEmpty() &&
                    child != null &&
                    child.getNodeType()==Node.TEXT_NODE &&
                    child.getNextSibling() == null) {
                    String text = child.getNodeValue().trim();
                    if (!text.isEmpty()) {
                        prune = true; // suppress the child treewalker
                        row[row.length-2] = p.getNodeName();
                        if (row[row.length-2].equals("Advanced")) {
                            String[] advanced = text.split("=",2);
                            row[row.length-2] = "adv."+advanced[0];
                            text = advanced.length>1 ? advanced[1] : "";
                        }
                        if (!text.contains("\\n") && text.startsWith("*")) {
                            try {
                                text = "encode("+decode(text)+")";
                            } catch (Exception e) {
                                // just leave it
                            }
                        }
                        for (String line : text.split("\\n")) {
                            row[row.length-1] = line;
                            out.add(Arrays.copyOf(row,row.length));
                            Arrays.fill(row, "");
                        }
                    }
                } else {
                    String alias = attrs.containsKey("alias")?attrs.get("alias"):p.getNodeName();  // as a default
                    if (p.getNodeName().endsWith("Action")) {
                        alias = "<"+alias+">";
                    }
                    row[0]=prefix+alias;
                    if (!attrs.isEmpty()) {
                        for (Entry<String,String> v : attrs.entrySet()) {
                            if (!v.getKey().equals("alias")) {
                                row[row.length-2] = v.getKey();
                                if (//v.getKey().equals("class") &&
                                    v.getValue().startsWith("*")) {
                                    try {
                                        row[row.length-1] = "encode("+
                                                            decode(v.getValue())+
                                                            ")";
                                    } catch (Exception e) {
                                        row[row.length-1] = v.getValue();
                                    }
                                } else {
                                    row[row.length-1] = v.getValue();
                                }
                                out.add(Arrays.copyOf(row,row.length));
                                Arrays.fill(row, "");
                            }
                        }
                    }
                }
                break;
            case Node.TEXT_NODE:
                // print out the "string", skipping empties
                String text = p.getNodeValue().trim();
                if (!text.isEmpty()) {
                    // don't really expect any of these, so just dump it
                    String[] a = new String[row.length];
                    Arrays.fill(a, "!");
                    a[a.length-2] = "text";
                    a[a.length-1] = text;
                    out.add(a);
                }
                break;
            default:
                // don't really expect any of these, so just dump it
                String[] b = new String[row.length];
                Arrays.fill(b, "!");
                b[b.length-2] = "type="+p.getNodeType();
                b[b.length-1] = p.getNodeName();
                out.add(b);
            }
            if (!prune && (next = p.getFirstChild()) != null) {
                // deeper (remember thing:stuff though)
                tree.push(p);
                p = next;
                prefix += "  ";
            } else if ((next = p.getNextSibling()) != null) {
                // to the right
                p = next;
            } else {
                // end of the row, pop up and to the right, and up...
                while (!tree.isEmpty() && next == null) {
                    next = tree.pop().getNextSibling();
                    prefix = prefix.substring(2);
                }
                p = next;
                if (tree.isEmpty()) p = null; // prune at the end
            }
        } while (p != null);
        // next pass: find widths
        int widths[] = new int[row.length] ;
        Arrays.fill(widths, 1);
        for (String[] c : out) {
            for (int i=0; i<widths.length; i++) {
                if (c[i].length()>widths[i]) widths[i]=c[i].length();
            }
        }
        String format = "";
        for (int i=0; i<widths.length-1; i++) {
            format += "%-"+(widths[i]+1)+"s";
        }
        format += "%s";
        // final pass: return the string output
        String[] output = new String[out.size()];
        for (int i=0; i<output.length; i++) {
            output[i] = String.format(format, (Object[])out.get(i));
        }
        return output;
    }

    public static Document string2xml(String xml) throws SAXException, IOException, ParserConfigurationException {
        return DocumentBuilderFactory.newInstance()
                                     .newDocumentBuilder()
                                     .parse(new InputSource(new StringReader(xml)));
    }

    public static Document file2xml(String fn) throws SAXException, IOException, ParserConfigurationException {
        return file2xml(new File(fn));
    }
    public static Document file2xml(File f) throws SAXException, IOException, ParserConfigurationException {
        return DocumentBuilderFactory.newInstance()
                                     .newDocumentBuilder()
                                     .parse(f);
    }

    public static class ReadResult {
        public File    file;
        public String  contents;
        public boolean encrypted;
        public ReadResult (File file, String contents, boolean encrypted) {
            this.file      = file;
            this.contents  = contents;
            this.encrypted = encrypted;
        }
        public ReadResult () {
            this(null,null,false);
        }
    }
    public static ReadResult file2string(String fn) throws IOException {
        return file2string(new File(fn), null);
    }
    public static ReadResult file2string(File f) throws IOException {
        return file2string(f, null);
    }
    public static ReadResult file2string(String fn, Core core) throws IOException {
        return file2string(new File(fn), core);
    }
    public static ReadResult file2string(File f, Core core) throws IOException {
        ReadResult result = new ReadResult();
        byte buf[] = new byte[(int)f.length()];
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            fis.read(buf);
            result.file = f;
            if (core!=null) {
                try {
                    result.contents = core.decrypt(DatatypeConverter.printBase64Binary(buf));
                    result.encrypted = true;
                } catch (Exception e) {
                    // fall through to unencrypted write
                }
            }
        } finally {
            if (fis!=null) fis.close();
        }
        if (!result.encrypted) {
            result.contents = new String(buf);
        }
        return result;
    }
    public static void string2file(String fn, String contents) throws Exception {
        string2file(new ReadResult(new File(fn), contents, false), null);
    }
    public static void string2file(File f, String contents) throws Exception {
        string2file(new ReadResult(f, contents, false), null);
    }
    public static void string2file(ReadResult result, Core core) throws Exception {
        FileOutputStream fos = null;
        try {
            result.file.renameTo(new File(result.file.getAbsolutePath()+".bak"));
            fos = new FileOutputStream(result.file);
            if (result.encrypted) {
                fos.write(DatatypeConverter.parseBase64Binary(core.encrypt(result.contents)));
            } else {
                fos.write(result.contents.getBytes());
            }
        } finally {
           if (fos!=null) fos.close();
        }
    }

    static final String BEAN_PREFIX = "com.cleo.lexicom.beans";
   /**
    * Reverse-engineered version of the VL encode routine.
    * @param s the string to encode
    * @return the encoded string
    */
    public static String encode(String s) {
        if (s==null) return null;
        if (s.startsWith(BEAN_PREFIX+".")) {
            s = new StringBuilder(s.substring(BEAN_PREFIX.length())).reverse().toString();
        }
        byte[] b = s.getBytes();
        for (int i=0; i<b.length; i++) {
            b[i] = (byte) (127-b[i]);
        }
        return "*"+DatatypeConverter.printBase64Binary(b).replace("=", "*");
    }

    /**
     * Reverse-engineered version of the VL decode routine.
     * @param s the string to decode
     * @return the decoded string, or s unchanged if it doesn't look encoded
     */
    public static String decode(String s) {
        if (s==null) return null;
        if (s.startsWith("*")) {
            try {
                byte[] b = DatatypeConverter.parseBase64Binary(s.substring(1).replace("*", "="));
                for (int i=0; i<b.length; i++) {
                    b[i] = (byte) (127-b[i]);
                }
                s = new String(b);
                if (s.matches("[a-zA-Z0-9\\.\\$]+\\.")) {
                    s = BEAN_PREFIX+new StringBuilder(s).reverse().toString();
                }
            } catch (Exception e) {
                // oh well
            }
        }
        return s;
    }
}
