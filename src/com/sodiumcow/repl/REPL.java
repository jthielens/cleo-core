package com.sodiumcow.repl;

import static java.lang.Character.isWhitespace;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import com.sodiumcow.repl.annotation.Command;
import com.sodiumcow.repl.annotation.Option;

public class REPL {

    public void error(String e) {
        if (e!=null) {
            System.out.println(e);
        }
    }
    
    public void error(Throwable e) {
        error(e.toString());
    }
    
    public void error(String s, Throwable e) {
        e.printStackTrace();
        if (s!=null) {
            String m = e.getMessage();
            if (m!=null) {
                s = s+": "+m;
            }
            System.out.println(s);
        }
    }

    public void prompt(String s) {
        if (System.console()!=null && s!=null) {
            System.out.print(s);
        }
    }
    
    public void print(String s) {
        if (s!=null) {
            System.out.println(s);
        }
    }
    
    public void report(String s) {
        report(null, s);
    }
    
    public void report(String[] s) {
        report(null, s);
    }
    
    public void report(List<String> s) {
        report(null, s.toArray(new String[s.size()]));
    }
    
    public void report(String prefix, String s) {
        if (prefix==null) prefix="";
        if (     s==null) s="null";

        prefix     = "    "+prefix;
        char[] pad = prefix.toCharArray();
        Arrays.fill(pad,  ' ');
        String spad = new String(pad); // i.e. spad = ' ' x prefix.length

        String[] lines = s.split("\n");
        for (String line : lines) {
            print(prefix+line);
            prefix = spad;
        }
    }
    
    public void report(String prefix, String[] ss) {
        boolean no_prefix = prefix==null;
        if (ss==null) {
            report(prefix, "null");
            return;
        } else if (ss.length==0) {
            report(prefix, "[]");
            return;
        } else if (no_prefix) {
            prefix = "";
        }
        prefix     = "    "+prefix;
        char[] pad = prefix.toCharArray();
        Arrays.fill(pad,  ' ');
        String spad = new String(pad);
        char   c    = '[';
        if (!no_prefix) {
            ss[ss.length-1] = ss[ss.length-1]+"]";
        }

        for (String s : ss) {
            if (s==null) s="null";
            String[] lines = s.split("\n");
            for (String line : lines) {
                if (no_prefix) {
                    print(prefix+line);
                } else {
                    print(prefix+c+line);
                }
                c = ' ';
                prefix = spad;
            }
            c = ',';
        }
    }

    public void report(String prefix, List<String> s) {
        report(prefix, s.toArray(new String[s.size()]));
    }

    public void report(String[] columns, String[][] values) {
        // calculate widths
        int[]     widths = new int[columns.length];
        boolean[] digits = new boolean[columns.length];
        for (int c=0; c<columns.length; c++) {
            widths[c] = columns[c].length();
            digits[c] = true;
            for (int r=0; r<values.length; r++) {
                String x = values[r][c];
                if (x==null) {
                    values[r][c] = "";
                } else {
                    if (x.length() > widths[c]) {
                        widths[c] = x.length();
                    }
                    if (!x.matches("\\s*\\d*")) {
                        digits[c] = false;
                    }
                }
            }
        }
        // create format string
        StringBuilder format = new StringBuilder();
        StringBuilder line   = new StringBuilder();
        for (int c=0; c<columns.length; c++) {
            format.append('%');
            if (!digits[c]) format.append('-');
            format.append(widths[c]).append("s ");
            for (int x=0; x<widths[c]; x++) line.append('-');
            line.append(' ');
        }
        if (format.length()>0) {
            format.setLength(format.length()-1);
            line.setLength(line.length()-1);
        }
        // done
        String f = format.toString();
        report(String.format(f, (Object[]) columns));
        report(line.toString());
        for (int r=0; r<values.length; r++) {
            report(String.format(f, (Object[]) values[r]));
        }
    }

    public boolean connect() {
        return true;
    }
    
    public void disconnect() {
    }
    
    private Method find_option (String name) {
        for (Method method : this.getClass().getMethods()) {
            Option option = method.getAnnotation(Option.class);
            if (option != null && name.equalsIgnoreCase(option.name())) {
                return method;
            }
        }
        return null;
    }

    private Method find_command (String name) {
        for (Method method : this.getClass().getMethods()) {
            Command command = method.getAnnotation(Command.class);
            if (command != null && name.equalsIgnoreCase(command.name())) {
                return method;
            }
        }
        return null;
    }

    private void list_commands () {
        TreeMap<String,Command> commands = new TreeMap<String,Command> ();
        TreeMap<String,Option>  options  = new TreeMap<String,Option>  ();
        int[]                   widths   = {0,0,0,0,0,0};
        // first loop -- find widths
        for (Method method : this.getClass().getMethods()) {
            Command command = method.getAnnotation(Command.class);
            if (command != null) {
                if (command.name   ().length() > widths[0]) widths[0] = command.name   ().length();
                if (command.args   ().length() > widths[1]) widths[1] = command.args   ().length();
                if (command.comment().length() > widths[2]) widths[2] = command.comment().length();
                commands.put(command.name(), command);
            }
            Option option = method.getAnnotation(Option.class);
            if (option != null) {
                // note: option names (slot 3) get a "-" prefix
                if (option.name   ().length() >=widths[3]) widths[3] = option.name   ().length()+1;
                if (option.args   ().length() > widths[4]) widths[4] = option.args   ().length();
                if (option.comment().length() > widths[5]) widths[5] = option.comment().length();
                options.put(option.name(), option);
            }
        }
        // adjust widths for padding
        int width = 0;
        String format = "";
        for (int i=5; i>=0; i--) {
            if (widths[i]>0) {
                if (width>0) widths[i]++; // add a padding space
                width += widths[i];
                format = "%"+(i+1)+"$-"+widths[i]+"s"+format;
            }
        }
        print(String.format("%1$-"+(widths[0]+widths[1]+widths[2])+"s"+(widths[3]>0?"%2$s":""), "Commands", "Options"));
        print(String.format(format,"","","","","","").replaceAll(".", "-"));
        // display
        Iterator<Command> ic = commands.values().iterator();
        Iterator<Option>  io = options .values().iterator();
        while (ic.hasNext() || io.hasNext()) {
            String[] buf = new String[6];
            if (ic.hasNext()) {
                Command c = ic.next();
                buf[0] = c.name();
                buf[1] = c.args();
                buf[2] = c.comment();
            } else {
                buf[0] = "";
                buf[1] = "";
                buf[2] = "";
            }
            if (io.hasNext()) {
                Option o = io.next();
                buf[3] = "-"+o.name();
                buf[4] = o.args();
                buf[5] = o.comment();
            } else {
                buf[3] = "";
                buf[4] = "";
                buf[5] = "";
            }
            print(String.format(format, (Object[]) buf));
        }
    }
    
    private boolean done     = false;
    private boolean confused = false;
    
    @Command(name="exit", comment="exit")
    public void exit_command(String...argv) {
        done = true;
    }
    
    @Command(name="quit", comment="exit")
    public void quit_command(String...argv) {
        done = true;
    }

    private String[] process_options(String[] argv) {
        ArrayList<String> cmd  = new ArrayList<String>();
        
        for (int i=0; i<argv.length; i++) {
            if (argv[i].startsWith("-")) {
                Method option = find_option(argv[i].substring(1));
                if (option != null) {
                    try {
                        if (option.getAnnotation(Option.class).args().length()>0) {
                            option.invoke(this, argv[i+1]);
                            i++;
                        } else {
                            option.invoke(this);
                        }
                    } catch (Exception e) {
                        error("error setting option "+argv[i]+": "+e);
                        confused = true;  // don't keep going in case of error
                    }
                } else {
                    error("error: unrecognized option: "+argv[i]);
                    confused = true;  // don't keep going in case of error
                }
            } else if (argv[i].equals("--")) {
                // stop after -- in case you need arguments like this
                for (int j=i+1; j<argv.length; j++) {
                    cmd.add(argv[j]);
                }
                break;
            } else {
                cmd.add(argv[i]);
            }
        }
        return cmd.toArray(new String[cmd.size()]);
    }

    private String slurp(InputStream is) {
        StringBuilder output = new StringBuilder();
        try {
            InputStreamReader isr = new InputStreamReader(is);
            char[] buffer = new char[1024];
            int length;
            while ((length = isr.read(buffer)) > 0) {
                output.append(buffer, 0, length);
            }
        } catch (Exception e) {
            // so what
        }
        return output.toString();
    }

    public String bang(String[] argv) {
        try {
            ProcessBuilder pb = new ProcessBuilder (argv);
            Process p = pb.start(); // Runtime.getRuntime().exec(argv);
            p.getOutputStream().close();
            String errors = slurp(p.getErrorStream());
            String output = slurp(p.getInputStream());
            return output + errors;
        } catch (IOException e) {
            error("error: ", e);
            return "";
        }
    }

    private void command(String[] argv) {
        // look for !shell
        if (argv.length>0 && argv[0].startsWith("!")) {
            argv[0] = argv[0].substring(1);
            System.out.print(bang(argv));
            return;
        }

        // look for help
        if (argv.length>0 && argv[0].equalsIgnoreCase("help")) {
            list_commands();
            return;
        }

        // splice out options
        argv = process_options(argv);

        Method method  = null;
        // figure out what command to call
        for (int i=argv.length; i>0 && method==null; i--) {
            StringBuilder cmd = new StringBuilder();
            for (int j=0; j<i; j++) {
                if (j>0) cmd.append('_');
                cmd.append(argv[j]);
            }
            method = find_command(cmd.toString());
            if (method != null && !confused) {
                // ok -- pass the rest as arguments
                String[] arguments = new String[argv.length-i];
                for (int j=0; j<arguments.length; j++) {
                    arguments[j] = argv[i+j];
                }
                try {
                    method.invoke(this, new Object[] {arguments});
                } catch (InvocationTargetException e) {
                    error(e);
                    error("caused by", e.getCause());
                } catch (Exception e) {
                    error(e);
                }
            }
        }

        // error if no method
        if (method==null) {
            if (argv.length==0) {
                report("options set");
            } else {
                confused = true;
            }
        }
    }
    
    private String[][] split_commands(String[] argv) {
        ArrayList<String[]> commands = new ArrayList<String[]>();
        ArrayList<String>   command  = new ArrayList<String>();
        
        for (String arg : argv) {
            if (arg.equals(";")) {
                if (command.size()>0) {
                    commands.add(command.toArray(new String[command.size()]));
                    command.clear();
                }
            } else if (arg.length()>0) {
                command.add(arg);
            }
        }
        if (command.size()>0) {
            commands.add(command.toArray(new String[command.size()]));
        }
        return commands.toArray(new String[commands.size()][]);
    }

    private String[] split_string(String s) {
        ArrayList<String> strings = new ArrayList<String>();
        StringBuilder     sb      = new StringBuilder();
        
        char mode  = ' ';
        int  hex_count = -1;
        int  hex_value = 0;
        for (char c : s.toCharArray()) {
            switch (mode) {
            case ' ': // skipping whitespace, anything interesting starts a token
                if (!isWhitespace(c)) {
                    if (c=='\\' || c=='"' || c=='\'') {
                        mode = c;
                    } else {
                        mode = '.';
                        sb.append(c);
                    }
                }
                break;
            case '\\': // append quoted c and go back to collecting a token
            case '(':  // hack for \ inside '
            case '#':  // hack for \ inside "
                if (hex_count >= 0) {
                    int value = Character.digit(c, 16);
                    hex_value *= 16;
                    if (value > 0) { // treat non-hex as 0
                        hex_value += value;
                    }
                    hex_count++;
                    if (hex_count==2) {
                        sb.appendCodePoint(hex_value);
                        hex_count = -1;
                        if (mode=='\\') {
                            mode = '.';
                        } else {
                            mode--; // back to ' or "
                        }
                    }
                } else if (c=='x'||c=='X') {
                    hex_count = 0;
                    hex_value = 0;
                } else {
                    sb.append(c);
                    if (mode=='\\') {
                        mode = '.';
                    } else {
                        mode--; // back to ' or "
                    }
                }
                break;
            case '"':  // append c until a match is found
            case '\'':
                if (c == mode) {
                    mode = '.';
                } else if (c == '\\') {
                    mode++; // '->(, "->#
                } else {
                    sb.append(c);
                }
                break;
            default:
                if (isWhitespace(c)) {
                    strings.add(sb.toString());
                    sb.setLength(0);
                    mode = ' ';
                } else if (c=='\\' || c=='"' || c=='\'') {
                    mode = c;
                } else {
                    sb.append(c);
                }
            }
        }
        if (mode != ' ') {
            // technically an error if in ' " \ mode, but oh well
            strings.add(sb.toString());
        }
        return strings.toArray(new String[strings.size()]);
    }

    /**
     * @param argv
     */
    public void run(String[] argv) {
        if (!connect()) {
            error("Command processor not initialized");
        } else {
            // first pass: process argv
            boolean    commanded = false;
            String[][] commands  = split_commands(argv);
            try {
                for (String[] cmd : commands) {
                    cmd = process_options(cmd);
                    if (cmd.length>0) {
                        command(cmd);
                        commanded = true;
                    }
                }
                if (confused) {
                    error("type \"help\" for help");
                }
                if (!commanded) {
                    // didn't see any commands -- run repl
                    run();
                }
            } catch (Exception e) {
                error(e);
            }
            disconnect();
        }
    }
    
    private void run() {
        BufferedReader i = null;
        try {
            i = new BufferedReader(new InputStreamReader (System.in));
            System.out.println("Command processor initialized");
            while (!done) {
                confused = false;
                StringBuilder command = null;
                prompt("[] ");
                for(;;) {
                    String line = i.readLine();
                    if (line==null) break;
                    if (command==null) command = new StringBuilder();
                    command.append(line);
                    if (line.endsWith("\\")) {
                        command.setLength(command.length()-1);
                        prompt("<> ");
                    } else {
                        break;
                    }
                }
                if (command==null) break;                  // EOF -- done
                String commandString = command.toString();
                if (commandString.matches("\\s*#.*")) continue;  // Comment -- skip
                String[][]commands = split_commands(split_string(commandString));
                if (commands.length==0) {
                    // command(new String[] {"quit"}); // auto-quit?
                } else {
                    for (String[] cmd : commands) {
                        command(cmd);
                    }
                }
                if (confused) {
                    error("type \"help\" for help");
                }
            }
        } catch (Exception e) {
            // quit
            error(e);
        } finally {
            if (i != null) {
                try {
                    i.close();
                } catch (Exception e) {
                    // oh well
                }
            }
        }
        error("Goodbye");
    }
    
    public REPL() {
    }
}
