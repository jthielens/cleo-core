package com.sodiumcow.cc;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cleo.lexicom.external.ISchedule;

public class Schedule {
    /*
     * A Schedule is a list of Items.
     * An Item has a Type:
     *   ONE_TIME: Calendar with a single Date yyyy/mm/dd          "ON yyyy/mm/dd"
     *   WEEKLY:   Calendar with a list of Days                    "ON d,d-d,..."
     *   MONTHLY:  Calendar with a list of Months, two variants    "OF m,m-m,..."
     *      Recurrent: a Recurrence and a list of Days             "EVERY|ON THE nth d|DAY"
     *      Non-recurrent: a list of day numbers 1-31              "ON THE n,n-n,..."
     * Every Calendar also has a list of Times.
     * A Time has a Start hh:mm[:ss],                              "@hh:mm[:ss]"
     *   and optional Recurring and End hh:mm[:ss]                 "hh:mm-hh:mmxhh:mm"
     */

    public enum Type {
        ONE_TIME (ISchedule.ONE_TIME),
        WEEKLY   (ISchedule.WEEKLY),
        MONTHLY  (ISchedule.MONTHLY);
            
        public final String id;
        private Type(String id) {
            this.id = id;
        }

        private static final HashMap<String,Type> index = new HashMap<String,Type>();
        static {
            for (Type t : Type.values()) {
                index.put(t.id.toLowerCase(),  t);
            }
        }

        public static Type lookup(String id) {
            Type t = index.get(id.toLowerCase());
            if (t==null) {
                throw new IllegalArgumentException("invalid type: "+id);
            }
            return t;
        }
    }

    public enum Day {
        SUNDAY    (ISchedule.SUNDAY),
        MONDAY    (ISchedule.MONDAY),
        TUESDAY   (ISchedule.TUESDAY),
        WEDNESDAY (ISchedule.WEDNESDAY),
        THURSDAY  (ISchedule.THURSDAY),
        FRIDAY    (ISchedule.FRIDAY),
        SATURDAY  (ISchedule.SATURDAY);
            
        public final String id;
        private Day(String id) {
            this.id = id;
        }

        private static final HashMap<String,Day> index = new HashMap<String,Day>();
        static {
            for (Day d : Day.values()) {
                index.put(d.id.toLowerCase(),  d);
            }
        }

        public static Day lookup(String id) {
            Day d = index.get(id.toLowerCase());
            if (d==null) {
                throw new IllegalArgumentException("invalid day: "+id);
            }
            return d;
        }
        
        public static EnumSet<Day> days(String s) {
            EnumSet<Day> set = EnumSet.noneOf(Day.class);
            int i = 0;
            while (i<s.length()) {
                if (s.length()-i < 2) {
                    throw new IllegalArgumentException("invalid day list @"+i+": "+s);
                }
                Day from = lookup(s.substring(i,i+2));
                i += 2;
                if (i<s.length() && s.charAt(i) == '-') {
                    i++;
                    Day to = lookup(s.substring(i,i+2));
                    i += 2;
                    set.addAll(EnumSet.range(from, to));
                } else {
                    set.add(from);
                }
            }
            return set;
        }

        public static String days(EnumSet<Day> s) {
            StringBuilder sb = new StringBuilder(36);
            Day range_start = null;
            Day range_end   = null;
            for (Day d : s) {
                if (range_start != null) {
                    if (d.ordinal() != range_end.ordinal()+1) {
                        sb.append(range_start.id);
                        if (range_end.ordinal() == range_start.ordinal()+1) {
                            sb.append(range_end.id);
                        } else if (range_end != range_start) {
                            sb.append('-').append(range_end.id);
                        }
                        range_start = d;
                    }
                } else {
                    range_start = d;
                }
                range_end   = d;
            }
            if (range_start != null) {
                sb.append(range_start.id);
                if (range_end.ordinal() == range_start.ordinal()+1) {
                    sb.append(range_end.id);
                } else if (range_end != range_start) {
                    sb.append('-').append(range_end.id);
                }
            }
            return sb.toString();
        }
    }

    public enum DayOfMonth {
        FIRST          (1),
        SECOND         (2),
        THIRD          (3),
        FOURTH         (4),
        FIFTH          (5),
        SIXTH          (6),
        SEVENTH        (7),
        EIGHTH         (8),
        NINTH          (9),
        TENTH         (10),
        ELEVENTH      (11),
        TWELFTH       (12),
        THIRTEENTH    (13),
        FOURTEENTH    (14),
        FIFTEENTH     (15),
        SIXTEENTH     (16),
        SEVENTEENTH   (17),
        EIGHTEENTH    (18),
        NINETEENTH    (19),
        TWENTIETH     (20),
        TWENTYFIRST   (21),
        TWENTYSECOND  (22),
        TWENTYTHIRD   (23),
        TWENTYFOURTH  (24),
        TWENTYFIFTH   (25),
        TWENTYSIXTH   (26),
        TWENTYSEVENTH (27),
        TWENTYEIGHTH  (28),
        TWENTYNINTH   (29),
        THIRTIETH     (30),
        THIRTYFIRST   (31);
            
        public final int id;
        private DayOfMonth(int id) {
            this.id = id;
        }
    
        private static final HashMap<Integer,DayOfMonth> index = new HashMap<Integer,DayOfMonth>();
        static {
            for (DayOfMonth d : DayOfMonth.values()) {
                index.put(d.id,  d);
            }
        }
    
        public static DayOfMonth lookup(int id) {
            DayOfMonth d = index.get(id);
            if (d==null) {
                throw new IllegalArgumentException("invalid day of month: "+id);
            }
            return d;
        }

        public static DayOfMonth lookup(String id) {
            return lookup(parse(id));
        }

        public static EnumSet<DayOfMonth> daysofmonth(String s) {
            EnumSet<DayOfMonth> set = EnumSet.noneOf(DayOfMonth.class);
            for (String item : s.split(",")) {
                String[] range = item.split("-", 2);
                if (range.length>1) {
                    set.addAll(EnumSet.range(lookup(range[0]), lookup(range[1])));
                } else {
                    set.add(lookup(range[0]));
                }
            }
            return set;
        }
    
        public static String daysofmonth(EnumSet<DayOfMonth> s) {
            StringBuilder sb = new StringBuilder(36);
            DayOfMonth range_start = null;
            DayOfMonth range_end   = null;
            for (DayOfMonth d : s) {
                if (range_start != null) {
                    if (d.ordinal() != range_end.ordinal()+1) {
                        if (sb.length()>0) sb.append(',');
                        sb.append(range_start.id);
                        if (range_end.ordinal() == range_start.ordinal()+1) {
                            sb.append(',').append(range_end.id);
                        } else if (range_end != range_start) {
                            sb.append('-').append(range_end.id);
                        }
                        range_start = d;
                    }
                } else {
                    range_start = d;
                }
                range_end   = d;
            }
            if (range_start != null) {
                if (sb.length()>0) sb.append(',');
                sb.append(range_start.id);
                if (range_end.ordinal() == range_start.ordinal()+1) {
                    sb.append(',').append(range_end.id);
                } else if (range_end != range_start) {
                    sb.append('-').append(range_end.id);
                }
            }
            return sb.toString();
        }
    }

    public enum Month {
        JANUARY   (ISchedule.JANUARY),
        FEBRUARY  (ISchedule.FEBRUARY),
        MARCH     (ISchedule.MARCH),
        APRIL     (ISchedule.APRIL),
        MAY       (ISchedule.MAY),
        JUNE      (ISchedule.JUNE),
        JULY      (ISchedule.JULY),
        AUGUST    (ISchedule.AUGUST),
        SEPTEMBER (ISchedule.SEPTEMBER),
        OCTOBER   (ISchedule.OCTOBER),
        NOVEMBER  (ISchedule.NOVEMBER),
        DECEMBER  (ISchedule.DECEMBER);
            
        public final String id;
        private Month(String id) {
            this.id = id;
        }

        private static final HashMap<String,Month> index = new HashMap<String,Month>();
        static {
            for (Month m : Month.values()) {
                index.put(m.id.toLowerCase(),  m);
            }
        }

        public static Month lookup(String id) {
            Month m = index.get(id.toLowerCase());
            if (m==null) {
                throw new IllegalArgumentException("invalid month: "+id);
            }
            return m;
        }
        
        public static EnumSet<Month> months(String s) {
            EnumSet<Month> set = EnumSet.noneOf(Month.class);
            int i = 0;
            while (i<s.length()) {
                if (s.length()-i < 3) {
                    throw new IllegalArgumentException("invalid month list @"+i+": "+s);
                }
                Month from = lookup(s.substring(i,i+3));
                i += 3;
                if (i<s.length() && s.charAt(i) == '-') {
                    i++;
                    Month to = lookup(s.substring(i,i+3));
                    i += 3;
                    set.addAll(EnumSet.range(from, to));
                } else {
                    set.add(from);
                }
            }
            return set;
        }

        public static String months(EnumSet<Month> s) {
            StringBuilder sb = new StringBuilder(36);
            Month range_start = null;
            Month range_end   = null;
            for (Month m : s) {
                if (range_start != null) {
                    if (m.ordinal() != range_end.ordinal()+1) {
                        sb.append(range_start.id);
                        if (range_end.ordinal() == range_start.ordinal()+1) {
                            sb.append(range_end.id);
                        } else if (range_end != range_start) {
                            sb.append('-').append(range_end.id);
                        }
                        range_start = m;
                    }
                } else {
                    range_start = m;
                }
                range_end   = m;
            }
            if (range_start != null) {
                sb.append(range_start.id);
                if (range_end.ordinal() == range_start.ordinal()+1) {
                    sb.append(range_end.id);
                } else if (range_end != range_start) {
                    sb.append('-').append(range_end.id);
                }
            }
            return sb.toString();
        }
    }

    public enum Recurrence {
        EVERY     (ISchedule.EVERY),
        FIRST     (ISchedule.FIRST),
        SECOND    (ISchedule.SECOND),
        THIRD     (ISchedule.THIRD),
        FOURTH    (ISchedule.FOURTH),
        LAST      (ISchedule.LAST);
            
        public final String id;
        private Recurrence(String id) {
            this.id = id;
        }

        private static final HashMap<String,Recurrence> index = new HashMap<String,Recurrence>();
        static {
            for (Recurrence r : Recurrence.values()) {
                index.put(r.id.toLowerCase(),  r);
            }
        }

        public static Recurrence lookup(String id) {
            Recurrence r = index.get(id.toLowerCase());
            if (r==null) {
                throw new IllegalArgumentException("invalid recurrence: "+id);
            }
            return r;
        }
    }

    private static final int SECONDS = 1;
    private static final int MINUTES = SECONDS*60;
    private static final int HOURS   = MINUTES*60;

    /**
     * Parses a (possibly null) string into an int.  Throws an
     * IllegalArgumentException in the string doesn't parse.
     * @param n a number or null
     * @return 0 for null, otherwise the parsed int.
     */
    private static int parse(String n) {
        if (n!=null) {
            try {
                return Integer.valueOf(n);
            } catch (Exception e) {
                throw new IllegalArgumentException("number expected: "+n);
            }
        }
        return 0;
    }

    private static final String DATE_PATTERN  = "(?:(\\d{4})/(\\d{1,2})/(\\d{1,2}))";
    private static final String CLOCK_PATTERN = "(?:([01]\\d?|2[0-4]?|[3-9])(?::([0-5]\\d?|[6-9]))?(?::([0-5]\\d?|[6-9]))?)";
    private static final String INTERVAL_PATTERN = "(\\d+)|(?:(?:(\\d+)[hH])?(?:(\\d+)[mM])?(?:(\\d+)[sS])?)"+
                                                   "|"+CLOCK_PATTERN;

    /**
     * A Clock represents a time of day between midnight (00:00) and midnight (24:00)
     * with a resolution of 1 second.  While the internal representation is the number
     * of seconds since 00:00 midnight, externally clocks are represented as
     *    hh:mm:ss        on a 24-hour clock
     *    hh:mm           if ss would be 00
     *    
     * On input, the parser is somewhat permissive in that leading 0 may be omitted,
     * and :mm (and further :ss) are optional.  So 5, for example, is understood to
     * be 05:00 or 05:00:00.
     * @author john
     */
    public static class Clock {
        private static final Pattern CLOCK = Pattern.compile(CLOCK_PATTERN);

        private int   seconds;

        /**
         * Parses an [hh:]mm[:ss] string into a Clock object.
         * @param s [hh:]mm[:ss]
         */
        public Clock(String s) {
            // parse
            Matcher m = CLOCK.matcher(s);
            if (m.matches()) {
                int hh = parse(m.group(1));
                int mm = parse(m.group(2));
                int ss = parse(m.group(3));
                if (hh==24 && (mm>0 || ss>0)) {
                    throw new IllegalArgumentException("hh:mm:ss expected (24:00:00 max): "+s);
                }
                seconds = HOURS*hh + MINUTES*mm + SECONDS*ss;
            } else {
                throw new IllegalArgumentException("hh:mm:ss expected: "+s);
            }
        }

        public int getSeconds() {
            return seconds;
        }

        @Override
        public String toString() {
            int hh = seconds / HOURS;
            int mm = (seconds % HOURS) / MINUTES;
            int ss = (seconds % MINUTES) / SECONDS;
            if (ss == 0) {
                return String.format("%02d:%02d", hh, mm);
            } else {
                return String.format("%02d:%02d:%02d", hh, mm, ss);
            }
        }
    }

    /**
     * An Interval represents a recurrence interval, described in terms of some
     * combination of hours, minutes, and seconds.  While represented internally
     * in seconds, the string representation follows one of two formats.  The
     * interval format uses unit suffixes HMS as follows:
     *    <number>h<number>m<number>s
     * where all <number>x are optional, but at least one must appear, and they must
     * appear in HMS order.  The clock format uses hh:mm:ss notation as for a Clock.
     * 
     * The toString() method generally represents intervals in the Clock format
     * unless the interval can be represented by a single HMS unit (with the slight
     * cheat that a single hour or minute will be added to the minutes/seconds value,
     * so 1m30s will be represented as 90s, but 2m30s will be 00:02:30).
     * @author john
     */
    public static class Interval {
        private static final Pattern INTERVAL = Pattern.compile(INTERVAL_PATTERN);

        private int   seconds;

        /**
         * Parses a [hhH][mmM][ssS] or CLOCK string into an Interval object.
         * A simple bare number is interpreted as minutes; while technically
         * this matches the CLOCK pattern, for clocks a bare number 0-23 is
         * interpreted as an hour.  Seems weird, but I think it's more intuitive
         * this way, so 5/5 means "every 5 minutes starting at 5am".
         * @param s [hhH][mmM][ssS] or [hh:]mm[:ss]
         */
        public Interval(String s) {
            Matcher m = INTERVAL.matcher(s);
            if (m.matches()) {
                if (m.group(1)!=null) { // simple pattern: group 1
                    seconds = MINUTES*parse(m.group(1));
                } else if (m.group(5)!=null) { // CLOCK pattern: groups 5,6,7
                    seconds = HOURS*parse(m.group(5)) +
                              MINUTES*parse(m.group(6)) +
                              SECONDS*parse(m.group(7));
                } else { // INTERVAL pattern: groups 2,3,4
                    seconds = HOURS*parse(m.group(2)) +
                              MINUTES*parse(m.group(3)) +
                              SECONDS*parse(m.group(4));
                }
            } else {
                throw new IllegalArgumentException("hHmMsS or hh:mm:ss expected: "+s);
            }
        }

        public Interval(int seconds) {
            this.seconds = seconds;
        }

        public int getSeconds() {
            return seconds;
        }

        @Override
        public String toString() {
            int hh = seconds / HOURS;
            int mm = (seconds % HOURS) / MINUTES;
            int ss = (seconds % MINUTES) / SECONDS;
            if (mm==0 && ss==0) {
                return String.format("%dh", hh);
            } else if (hh<=1 && ss==0) {
                return String.format("%dm", 60*hh + mm);
            } else if (hh==0 && mm<=1) {
                return String.format("%ds", 60*mm + ss);
            } else if (ss == 0) {
                return String.format("%02d:%02d", hh, mm);
            } else {
                return String.format("%02d:%02d:%02d", hh, mm, ss);
            }
        }

        public String transcribe() {
            int hh = seconds / HOURS;
            int mm = (seconds % HOURS) / MINUTES;
            int ss = (seconds % MINUTES) / SECONDS;
            if (ss == 0) {
                return String.format("%02d:%02d", hh, mm);
            } else {
                return String.format("%02d:%02d:%02d", hh, mm, ss);
            }
        }
    }

    /**
     * A Time object represents a single time 00:00-24:00, or a time period with
     * start and end times and a recurrence interval, where a recurrence of 0 is
     * understood to mean "continuously".  The string encodings are:
     *    start               a single point in time
     *    start-end           a time range with continuous recurrence
     *    start/interval-end  a time range with periodic recurrence
     * Both start and end times are Clock's.  the interval is an Interval.
     * @author john
     */
    public static class Time {
        private Clock    start;
        private Clock    stop;
        private Interval interval;

        public Time(String s) {
            String[] start_stop = s.split("-", 2);
            String[] start_interval = start_stop[0].split("/", 0);
            start    = new Clock(start_interval[0]);
            stop     = null;
            interval = null;
            if (start_stop.length>1) {
                stop = new Clock(start_stop[1]);
                if (start_interval.length>1) {
                    interval = new Interval(start_interval[1]);
                } else {
                    interval = new Interval(0);
                }
            } else if (start_interval.length>1) {
                throw new IllegalArgumentException("/interval requires -stop time");
            }
            if (stop!=null && start.getSeconds()>stop.getSeconds()) {
                throw new IllegalArgumentException("start time is after stop time");
            }
        }

        public Time(ISchedule.Item.Calendar.Time t) {
            start    = new Clock(t.getStart());
            stop     = null;
            interval = null;
            String recur = t.getRecurring();
            if (recur!=null) {
                stop = new Clock(t.getUntil());
                interval = new Interval(recur);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(30);
            sb.append(start.toString());
            if (stop!=null) {
                if (interval.getSeconds()>0) {
                    sb.append('/').append(interval.toString());
                }
                sb.append('-').append(stop.toString());
            }
            return sb.toString();
        }

        public void transcribe(ISchedule.Item.Calendar.Time itime) throws Exception {
            itime.setStart(start.toString());
            if (interval!=null) {
                itime.setRecurring(interval.transcribe());
                itime.setUntil(stop.toString());
            }
        }
    }

    /**
     * A Date is a simple syntax checking wrapper around a yyyy/mm/dd string,
     * with the constructor throwing an IllegalArgumentException if it fails.
     * 1-digit mm and dd are accepted on input, but the leading 0 is added on
     * output.  dd value must be appropriate for mm (and yyyy for 02/29).
     * @author john
     */
    public static class Date {
        private String date;
        private static final Pattern DATE = Pattern.compile(DATE_PATTERN);

        public Date(String s) {
            boolean error = true;
            Matcher m     = DATE.matcher(s);
            if (m.matches()) {
                int yyyy = Integer.valueOf(m.group(1));
                int mm   = Integer.valueOf(m.group(2));
                int dd   = Integer.valueOf(m.group(3));
                boolean leap = (yyyy % 4 == 0) &&
                               ((yyyy % 100 != 0) || (yyyy % 400 == 0));
                int maxd = (mm==4|mm==6|mm==9|mm==11) ? 30
                         : (mm==2 && leap)            ? 29
                         : (mm==2)                    ? 28
                         : 31;
                if (mm>=1 && mm<=12 && dd>=1 && dd<=maxd) {
                    this.date = String.format("%04d/%02d/%02d", yyyy, mm, dd);
                    error = false;
                }
            }
            if (error) {
                throw new IllegalArgumentException("invalid date yyyy/mm/dd: "+s);
            }
        }
        
        @Override
        public String toString() {
            return date;
        }
    }

    /**
     * A Calendar represents a single instance of one of the Type's:
     *    ONE_TIME          - a single date yyyy/mm/dd
     *    WEEKLY            - a list of one or more Day's of the week
     *    MONTHLY recurring - a Recurrence (EVERY, FIRST, ...) of a single Day
     *                        in a list of Month's
     *    MONTHLY by day    - a list of one or more DayOfMonth's
     *                        in a list of Month's
     * Regardless of the Type and Recurrence, each Calendar also has a list of
     * execution Time's.  String representations are:
     *    ONE_TIME          - on yyyy/mm/dd                       at Time,...
     *    WEEKLY            - on DdDd...                          at Time,...
     *    MONTHLY recurring - every ([nth] Dd|day) [in MmmMmm...] at Time,...
     *    MONTHLY by day    - on d,d,... [in MmmMmm...]           at Time,...
     * 
     * Notes:
     *    [in MmmMmm...] is optional and means in Jan-Dec if absent.
     *    MONTH recurring "onday" may be null, meaning "every day"
     * @author john
     */
    public static class Calendar {
        private Type                type;
        private Date                date;        // type==ONE_TIME
        private EnumSet<Day>        days;        // type==WEEKLY
        private Recurrence          recurrence;  // type==MONTHLY, may be null
        private Day                 onday;       // type==MONTHLY && recurrence!=null
        private EnumSet<DayOfMonth> ondays;      // type==MONTHLY && recurrence==null
        private EnumSet<Month>      in;          // type==MONTHLY
        private ArrayList<Time>     times;
        
        public Calendar(String s) {
            String[] date_time = s.trim().split("(?i)\\s+at\\s+", 2);
            if (date_time.length<2) {
                throw new IllegalArgumentException("missing \"at time\": "+s);
            }
            String parse = date_time[0];
            // parse the date part
            if (parse.matches("(?i)on\\s.*")) {
                parse = parse.replaceFirst("^(?i)on\\s+","");
                if (parse.matches(DATE_PATTERN)) {
                    // on date
                    this.type = Type.ONE_TIME;
                    this.date = new Date(parse);
                } else if (parse.matches("\\d.*")) {
                    // on d,d,d [in mmm...]
                    String[] d_in = parse.split("(?i)\\s+in\\s+", 2);
                    this.type = Type.MONTHLY;
                    this.recurrence = null;
                    this.ondays = DayOfMonth.daysofmonth(d_in[0]);
                    if (d_in.length>1) {
                        this.in = Month.months(d_in[1]);
                    } else {
                        this.in = EnumSet.allOf(Month.class);
                    }
                } else {
                    // on dd...
                    this.type = Type.WEEKLY;
                    this.days = Day.days(parse);
                }
            } else if (date_time[0].matches("(?i)every\\s.*")) {
                // every (day|[nth] dd) [in mmm...]
                String[] d_in = parse.split("(?i)\\s+in\\s+", 2);
                this.type = Type.MONTHLY;
                String[] phrase = d_in[0].split("\\s+");
                if (phrase.length==2) {
                    // every (day|dd)
                    this.recurrence = Recurrence.EVERY;
                    if (phrase[1].equalsIgnoreCase("day")) {
                        // every day
                        this.onday = null;
                    } else {
                        this.onday = Day.lookup(phrase[1]);
                    }
                } else if (phrase.length==3) {
                    // every nth dd
                    this.recurrence = Recurrence.lookup(phrase[1]);
                    this.onday      = Day.lookup(phrase[2]);
                } else {
                    throw new IllegalArgumentException("invalid recurrence every: "+s);
                }
                // [in mmm...]
                if (d_in.length>1) {
                    this.in = Month.months(d_in[1]);
                } else {
                    this.in = EnumSet.allOf(Month.class);
                }
            } else {
                throw new IllegalArgumentException("on or every expected: "+s);
            }
            // now parse the times from date_time[1]
            String[] list = date_time[1].split("\\s*,\\s*");
            this.times = new ArrayList<Time>(list.length);
            for (String t : list) {
                this.times.add(new Time(t));
            }
        }

        public Calendar(Type type, ISchedule.Item.Calendar cal) {
            this.type = type;
            switch (type) {
            case ONE_TIME:
                this.date = new Date(cal.getDate());
                break;
            case WEEKLY:
                this.days = Day.days(cal.getDays());
                break;
            case MONTHLY:
                if (cal.getRecurrence()==null) {
                    this.recurrence = null;
                    this.ondays     = DayOfMonth.daysofmonth(cal.getDaysOfMonth());
                } else {
                    this.recurrence = Recurrence.lookup(cal.getRecurrence());
                    String onday = cal.getRecurrentDay();
                    if (onday!=null) {
                        this.onday = Day.lookup(onday);
                    } else {
                        this.onday = null;
                    }
                }
                this.in = Month.months(cal.getMonths());
                break;
            }
            List<ISchedule.Item.Calendar.Time> list = cal.listTimes();
            this.times = new ArrayList<Time>(list.size());
            for (ISchedule.Item.Calendar.Time t : list) {
                this.times.add(new Time(t));
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            switch (type) {
            case ONE_TIME:
                sb.append("on ").append(date);
                break;
            case WEEKLY:
                sb.append("on ").append(Day.days(days));
                break;
            case MONTHLY:
                if (recurrence==null) {
                    sb.append("on ").append(DayOfMonth.daysofmonth(ondays));
                } else {
                    sb.append("every ");
                    if (onday==null) {
                        sb.append("day");
                    } else {
                        if (recurrence!=Recurrence.EVERY) {
                            sb.append(recurrence.toString().toLowerCase()).append(' ');
                        }
                        sb.append(onday.id);
                    }
                }
                if (!in.containsAll(EnumSet.allOf(Month.class))) {
                    sb.append(" in ").append(Month.months(in));
                }
                break;
            }
            // at times
            sb.append(" at ");
            for (Time t : times) {
                sb.append(t.toString()).append(',');
            }
            sb.setLength(sb.length()-1);  // remove trailing ,

            // all done
            return sb.toString();
        }

        public void transcribe(ISchedule.Item.Calendar ical) throws Exception {
            switch (this.type) {
            case ONE_TIME:
                ical.setDate(date.toString());
                break;
            case WEEKLY:
                ical.setDays(Day.days(days));
                break;
            case MONTHLY:
                if (recurrence==null) {
                    ical.setDaysOfMonth(DayOfMonth.daysofmonth(ondays));
                } else {
                    if (onday==null) {
                        ical.setDaysOfMonth(recurrence.id, null);
                    } else {
                        ical.setDaysOfMonth(recurrence.id, onday.id);
                    }
                }
                ical.setMonths(Month.months(in));
                break;
            }
            for (Time t : times) {
                t.transcribe(ical.addTime());
            }
        }
    }

    /**
     * A Schedule corresponds to the entire schedule that can be attached to
     * an action (an ISchedule.Item).  It consists of the "on file" flag and
     * a list of Calendar's.  The "continuous" flag is shorthand for the WEEKLY
     * Calendar "on Su-Sa at 00:00-24:00".  The following syntax is parsed in
     * the constructor:
     *    on file continuously
     *    [on file] calendar+calendar+...
     *
     * The "on file continuously" syntax implicitly creates the implied WEEKLY
     * Calendar, which can also be entered manually and will be recognized.
     *
     * Note that each Calendar in the list must be of the same Type.
     */
    private boolean             onfile;
    private boolean             continuous;
    private Type                type;
    private ArrayList<Calendar> calendars;
    
    public Schedule(String s) {
        boolean putativelycontinuous = false;
        s = s.trim();
        this.onfile = s.matches("(?i)on\\s+file\\s+.*");
        if (this.onfile) {
            this.onfile = true;
            s = s.replaceFirst("^(?i)on\\s+file\\s+","");
        }
        if (s.matches("(?i)continuously\\b.*")) {
            s = s.replaceFirst("^(?i)continuously\\s*","");
            if (s.isEmpty()) s = "on Su-Sa at 0-24";
            putativelycontinuous = true;
        }
        String[] cals = s.split("\\s*\\+\\s*");
        this.calendars = new ArrayList<Calendar>(cals.length);
        this.type      = null;
        for (String cal : cals) {
            Calendar c = new Calendar(cal);
            if (this.type==null) {
                this.type = c.type;
            } else if (this.type != c.type) {
                throw new IllegalArgumentException("all calendars must have the same type: "+this.type+" not "+c.type);
            }
            this.calendars.add(c);
        }
        // set "continuous" for WEEKLY Su-Sa at 00:00/0s-24:00
        this.continuous = false;
        if (this.calendars.size()==1) {
            Calendar test = this.calendars.get(0);
            if (test.type==Type.WEEKLY &&
                test.days.containsAll(EnumSet.allOf(Day.class)) &&
                test.times.size()==1) {
                Time at = test.times.get(0);
                if (at.start.seconds==0 &&
                    at.interval != null &&
                    at.stop.seconds==24*HOURS &&
                    at.interval.seconds == 0) {
                    this.continuous = true;
                }
            }
        }
        // check putative continuosity
        if (putativelycontinuous && !this.continuous) {
            throw new IllegalArgumentException("must be on Su-Sa at 0-24 or equivalent to be continuous");
        }
    }

    public Schedule(ISchedule.Item schedule) {
        this.onfile     = schedule.isOnlyIfFile();
        this.continuous = schedule.isContinous();
        this.type       = Schedule.Type.lookup(schedule.getPeriod());
        List<ISchedule.Item.Calendar> cals = schedule.listCalendars();
        this.calendars  = new ArrayList<Calendar>(cals.size());
        for (ISchedule.Item.Calendar cal : cals) {
            this.calendars.add(new Calendar(this.type, cal));
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (this.onfile) {
            sb.append("on file ");
        }
        if (this.continuous) {
            sb.append("continuously ");
        } else {
            for (Calendar c : this.calendars) {
                sb.append(c.toString()).append("+");
            }
        }
        sb.setLength(sb.length()-1);
        return sb.toString();
    }

    public void transcribe(ISchedule.Item item) throws Exception {
        item.setOnlyIfFile(this.onfile, this.continuous);
        item.setPeriod(this.type.id);
        for (Calendar c : this.calendars) {
            c.transcribe(item.addCalendar());
        }
    }
}