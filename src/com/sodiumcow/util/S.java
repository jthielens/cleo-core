package com.sodiumcow.util;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class S {

    /**
     * Inverts an m x n matrix of Strings to an n x m matrix.
     * @param arrays an m x n matrix (or a varargs list of m rows)
     * @return the inverted n x m matrix
     */
    public static String[][] invert(String[]...arrays) {
        String[][] result = new String[arrays[0].length][arrays.length];
        for (int i=0; i<arrays[0].length; i++) {
            for (int j=0; j<arrays.length; j++) {
                result[i][j] = arrays[j][i];
            }
        }
        return result;
    }

    /**
     * Inverts a Map of m String keys and values to an m x 2 matrix with the
     * keys in column 0 and the values in column 1, each row corresponding to
     * a single Entry in the Map.  Call invert again (see above) to convert to
     * a 2 x m matrix with row 0 consisting of the m keys and row 1 of the m values.
     * @param map
     * @return
     */
    public static String[][] invert(Map<String,String> map) {
        String[][] result = new String[map.size()][2];
        int i = 0;
        for (Map.Entry<String, String> e : map.entrySet()) {
            result[i][0] = e.getKey();
            result[i][1] = e.getValue();
            i++;
        }
        return result;
    }

    /**
     * Inverts a pair of Maps with String keys and values t an m x 3 matrix with the
     * keys in column 0, the value from Map a in column 1, and the value from Map b
     * in column 2.  Maps a and b are aligned by key name, ignoring case.  "Missing"
     * values that are unique to Map a or b are filled with "" (not null) in the
     * corresponding column for Map b or a as needed.
     * @param a Map a
     * @param b Map b
     * @return an m x 3 matrix of Strings
     */
    public static String[][] invert(Map<String,String> a, Map<String,String> b) {
        Map<String,String[]> map = new TreeMap<String,String[]>();
        for (Map.Entry<String, String> e : a.entrySet()) {
            map.put(e.getKey().toLowerCase(), new String[] {e.getKey(), e.getValue(), ""});
        }
        for (Map.Entry<String, String> e : b.entrySet()) {
            String[] entry = map.get(e.getKey().toLowerCase());
            if (entry==null) {
                map.put(e.getKey().toLowerCase(), new String[] {e.getKey(), "", e.getValue()});
            } else {
                entry[2] = e.getValue();
            }
        }
        return map.values().toArray(new String[0][0]);
    }

    /**
     * Returns {@code String}s concatenated with a separator, possibly skipping
     * some strings from the start and the end of the list.
     * @param separator the separator
     * @param from      the starting index in the list, inclusive starting at 0
     * @param to        the ending index in the list, exclusive starting at 0
     * @param a         the list of strings
     * @return          the concatenated strings
     */
    public static String join(String separator, int from, int to, String...a) {
        if (from<0     ) from=0;
        if (to>a.length) to=a.length;
        if (from>=to   ) return "";
        if (a.length==0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i=from; i<to; i++) {
            sb.append(a[i]);
            if (i<to-1) sb.append(separator);
        }
        return sb.toString();
    }

    /**
     * Returns {@code String}s concatenated with a separator, possibly skipping 
     * some strings from the start of the list.
     * @param separator the separator
     * @param from      the number of elements to skip from the start
     * @param a         the list of strings
     * @return          the concatenated strings
     */
    public static String join(String separator, int from, String...a) {
        return join(separator, from, a.length, a);
    }

    /**
     * Returns {@code String}s concatenated with a separator.
     * @param separator the separator
     * @param a         the list of strings
     * @return          the concatenated strings
     */
    public static String join(String separator, String...a) {
        return join(separator, 0, a);
    }

    /**
     * Returns {@code String}s concatenated with a separator.
     * @param separator the separator
     * @param a         the list of strings
     * @return          the concatenated strings
     */
    public static String join(String separator, Collection<String> a) {
        return join(separator, 0, a.toArray(new String[a.size()]));
    }

    /**
     * Special laminating joiner for Maps, formatting each {@code Map.Entry} using the
     * supplied format and converting key and value to Strings with {@code toString}.
     * @param separator the separator
     * @param map       the map
     * @param format    the lamination format
     * @return          the concatenated strings
     */
    public static String join(String separator, Map<?,?> map, final String format) {
        return join(separator, map, new Sprintf(format));
    }


    public interface Inspector<T> {
        public T inspect(String[] group) throws IllegalArgumentException;
    }

    public interface Formatter {
        public String format(Map.Entry<?,?> entry);
    }

    public static class Sprintf implements Formatter {
        private String sprintf;
        public Sprintf(String sprintf) {
            this.sprintf = sprintf;
        }
        public String format(Map.Entry<?,?> entry) {
            return String.format(sprintf, entry.getKey().toString(), entry.getValue().toString());
        }
    }

    // Pattern.compile("(?i)\\s*(!)?\\s*(\\w+)\\s*(?:([><])=\\s*(\\d+)\\s*)?");
    public static <T> List<T> megasplit(Pattern clause, String s, Inspector<T> inspector) {
        ArrayList<T> result = new ArrayList<T>();
        if (s!=null) {
            Matcher m   = clause.matcher(s);
            int     i   = 0;
            String  err = null;
            while (err==null && m.find() && m.start()==i) {
                String[] group = new String[m.groupCount()+1];
                for (i=0; i<group.length; i++) {
                    group[i] = m.group(i);
                }
                try {
                    result.add(inspector.inspect(group));
                } catch (IllegalArgumentException e) {
                    err = e.getMessage();
                }
                i = m.end();
            }
            if (i<s.length() || err!=null) {
                // we didn't make it cleanly to the end
                if (err==null) err="parsing error";
                throw new IllegalArgumentException(err+": "+s.substring(0,i)+"-->"+s.substring(i));
            }
        }
        return result;
    }

    public static String join(String separator, Map<?,?> map, Formatter formatter) {
        StringBuilder s = new StringBuilder();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (s.length()>0) s.append(separator);
            s.append(formatter.format(e));
        }
        return s.toString();
    }

    public static Map<String,String> split(String s, Pattern format) {
        final Map<String,String> result = new LinkedHashMap<String,String>();
        megasplit(format, s, new Inspector<Object> () {
            public Object inspect(String [] group) {
                result.put(group[1], group[2]);
                return null;
            }
        });
        return result;
    }

    /**
     * Returns a {@code List} of the same item repeated a number of times.
     * @param item the item to repeat
     * @param times the number of times to repeat it
     * @return the list
     */
    public static <T> List<T> x(T item, int times) {
        ArrayList<T> result = new ArrayList<T>(times);
        for (int i=0; i<times; i++) {
            result.add(item);
        }
        return result;
    }

    /**
     * Laminates two lists together using a format string, expected to have two %s in it.
     * Empty strings are substituted for the end of the shorter list, if the lists are
     * not the same length.
     * @param a the left list
     * @param b the right list
     * @param format the format string
     * @return the laminated list
     */
    public static String[] lam(String[] a, String[] b, String format) {
        return lam(Arrays.asList(a), Arrays.asList(b), format);
    }

    public static String[] lam(List<String> a, String[] b, String format) {
        return lam(a, Arrays.asList(b), format);
    }

    public static String[] lam(String[] a, List<String> b, String format) {
        return lam(Arrays.asList(a), b, format);
    }

    public static String[] lam(List<String> a, List<String> b, String format) {
        int la = a.size();
        int lb = b.size();
        int l = Math.max(la, lb);
        String[] result = new String[l];
        for (int i=0; i<l; i++) {
            String ai = i>la ? "" : a.get(i);
            String bi = i>lb ? "" : b.get(i);
            result[i] = String.format(format, ai, bi);
        }
        return result;
    }

    /**
     * Like lam(a, new String[]{}, format) but more efficiently.  Of course
     * laminating to nothing is like map-format, but there you go.
     * @param a
     * @param format
     * @return
     */
    public static String[] lam(String[] a, String format) {
        return lam(Arrays.asList(a), format);
    }

    public static String[] lam(List<String> a, String format) {
        int la = a.size();
        String[] result = new String[la];
        for (int i=0; i<la; i++) {
            result[i] = String.format(format, a.get(i));
        }
        return result;
    }

    /**
     * Like {@code lam(a, b, "%s%s")} but more efficiently.
     * @param a
     * @param b
     * @return
     */
    public static String[] lam(String[] a, String[] b) {
        return lam(Arrays.asList(a), Arrays.asList(b));
    }

    public static String[] lam(List<String> a, String[] b) {
        return lam(a, Arrays.asList(b));
    }

    public static String[] lam(String[] a, List<String> b) {
        return lam(Arrays.asList(a), b);
    }

    public static String[] lam(List<String> a, List<String> b) {
        int la = a.size();
        int lb = b.size();
        int l = Math.max(la, lb);
        String[] result = new String[l];
        for (int i=0; i<l; i++) {
            result[i] = i>la ? b.get(i)
                      : i>lb ? a.get(i)
                      : a.get(i)+b.get(i);
        }
        return result;
    }

    public static <T> List<T> cat(List<T> a, List<T> b) {
        int size = a.size() + b.size();
        List<T> result = new ArrayList<T>(size);
        result.addAll(a);
        result.addAll(b);
        return result;
    }

    public static <T> T[] col(T[][] matrix, int c) {
        @SuppressWarnings("unchecked")
        T[] result = (T[]) Array.newInstance(matrix[0][0].getClass(), matrix.length);
        for (int i=0; i<result.length; i++) {
            T[] row = matrix[i];
            result[i] = row.length>c ? row[c] : null;
        }
        return result;
    }

    public interface Filter<T> {
        public boolean accept(T object);
    }

    public static class PatternFilter<T> implements Filter<T> {
        private Pattern pattern;
        public PatternFilter(Pattern pattern) {
            this.pattern = pattern;
        }
        public boolean accept(T object) {
            return pattern.matcher(object.toString()).matches();
        }
    }

    public static class RegexFilter<T> extends PatternFilter<T> {
        public RegexFilter(String regex) {
            super(Pattern.compile(regex));
        }
    }

    public static String glob2re(String glob) {
        return "(?i)"+glob.replaceAll("\\.", "\\.")
                          .replaceAll("\\?", ".")
                          .replaceAll("\\*", ".*");
    }

    public static class GlobFilter<T> extends RegexFilter<T> {
        public GlobFilter(String glob) {
            super(glob2re(glob));
        }
    }

    public static <T> List<T> filter(T[] list, Filter<T> filter) {
        if (list==null || filter==null) return null;
        List<T> filtered = new ArrayList<T>(list.length);
        for (T item : list) {
            if (filter.accept(item)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    public static <V> Map<String,V> filter(Map<String,V> map, Filter<String> filter) {
        try {
            @SuppressWarnings("unchecked")
            Map<String,V> filtered = map.getClass().newInstance();
            for (Map.Entry<String,V> e : map.entrySet()) {
                if (filter.accept(e.getKey())) {
                    filtered.put(e.getKey(), e.getValue());
                }
            }
            return filtered;
        } catch (Exception e) {
            return null;
        }
    }
    public static <V> Map<String,V> prune(Map<String,V> map, Filter<String> filter) {
        for (Iterator<Map.Entry<String,V>> i=map.entrySet().iterator(); i.hasNext();) {
            Map.Entry<String,V> e = i.next();
            if (!filter.accept(e.getKey())) {
                i.remove();
            }
        }
        return map;
    }
}