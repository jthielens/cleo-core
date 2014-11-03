package com.sodiumcow.util;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class X {
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
            String alias = p.hasAttributes() && p.getAttributes().getLength()==1
                           ? p.getAttributes().item(0).getNodeValue()
                           : null;
            if (child!=null &&
                child.getNodeType()==Node.TEXT_NODE &&
                child.getNextSibling() == null &&
                (!p.hasAttributes() || alias!=null)) {
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
                    } else if (alias != null) {
                        name = name+"["+alias+"]";
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
            } else if (p.getNodeType()!=Node.TEXT_NODE) {
                // <bar ...>
                String name = p.getNodeName();
                Map<String,Object> pmap = xml2map(p);
                if (pmap.containsKey(".alias")) { // VersaLex uses alias
                    name = name+"["+pmap.get(".alias")+"]";
                } else if (pmap.containsKey(".key")) { // ST exports use key
                    name = name+"["+pmap.get(".key")+"]";
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
    
        // Done
        return map;
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
    public static Object[] submatch(Map<String,Object>map, String...steps) {
        ArrayList<Object> matches = new ArrayList<Object>();
        matches.add(map);
        for (String step :steps) {
            ArrayList<Object> next = new ArrayList<Object>();
            S.Filter<String> filter = new S.GlobFilter<String>(step);
            for (Object o : matches) {
                if (o instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String,Object> x = S.filter((Map<String,Object>) o, filter);
                    if (x!=null) {
                        next.addAll(x.values());
                    }
                }
            }
            if (next.isEmpty()) {
                return null;
            }
            matches = next;
        }
        return matches.toArray(new Object[matches.size()]);
    }
    public static Map<String,Object> subprune(Map<String,Object>map, String...steps) {
        ArrayList<Object> matches = new ArrayList<Object>();
        matches.add(map);
        for (String step :steps) {
            ArrayList<Object> next = new ArrayList<Object>();
            S.Filter<String> filter = new S.GlobFilter<String>(step);
            for (Object o : matches) {
                if (o instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String,Object> x = (Map<String,Object>) o;
                    S.prune(x, filter);
                    if (!x.isEmpty()) {
                        next.addAll(x.values());
                    }
                }
            }
            if (next.isEmpty()) {
                return null;
            }
            matches = next;
        }
        return map;
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
}
