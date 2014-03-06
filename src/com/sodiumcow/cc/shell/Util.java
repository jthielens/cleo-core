package com.sodiumcow.cc.shell;

import java.io.StringWriter;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.cleo.lexicom.external.ILicense;
import com.sodiumcow.repl.REPL;

public class Util {

    public static String licensed_features(ILicense license) {
        ArrayList<String> features = new ArrayList<String>();
        if (license.isTranslatorLicensed()) features.add("integration");
        if (license.isVLProxyLicensed())    features.add("vlproxy");
        if (license.isWebBrowserLicensed()) features.add("browser");
        if (license.isApiLicensed())        features.add("api");
        if (license.isFipsLicensed())       features.add("fips");
        if (license.isSecureEmailLicensed())features.add("secure-email");
        return features.toString();
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
        return list.toString();
    }

    public static String licensed_platform(int platform) {
        if (platform==(ILicense.AS400|ILicense.UNIX|ILicense.WINDOWS)) return "Any";
        ArrayList<String> platforms = new ArrayList<String>();
        if ((platform&ILicense.AS400  ) != 0) platforms.add("AS400");
        if ((platform&ILicense.UNIX   ) != 0) platforms.add("UNIX");
        if ((platform&ILicense.WINDOWS) != 0) platforms.add("WINDOWS");
        return platforms.toString();
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
    
    public static String xml2string(Document doc) {
        try {
           DOMSource domSource = new DOMSource(doc);
           StringWriter writer = new StringWriter();
           StreamResult result = new StreamResult(writer);
           TransformerFactory tf = TransformerFactory.newInstance();
           Transformer transformer = tf.newTransformer();
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
            if (name.startsWith("get") && method.getParameterTypes().length==0) {
                if (name.length()>max) max=name.length();
            }
        }
        // pass 2: report
        for (Method method : o.getClass().getMethods()) {
            name = method.getName();
            if (name.startsWith("get") && method.getParameterTypes().length==0) {
                char[] attr = Arrays.copyOf((name.substring(3,4).toLowerCase()+
                                             name.substring(4)).toCharArray(),
                                            max-2);  // -2 = -"get" + " "
                Arrays.fill(attr, name.length()-3, attr.length, ' ');
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
                    } else if (method.getReturnType().equals(Properties.class)) {
                        Properties value  = (Properties) method.invoke(o);
                        String[]   values = new String[value.size()];
                        int        i      = 0;
                        for (Entry<Object,Object> v : value.entrySet()) {
                            values[i++] = (String)v.getKey()+"="+(String)v.getValue();
                        }
                        repl.report(prefix, values);
                    }
                } catch (Exception e) {
                    repl.error("error invoking "+method.getName(), e);
                }
            }
        }
    }
}
