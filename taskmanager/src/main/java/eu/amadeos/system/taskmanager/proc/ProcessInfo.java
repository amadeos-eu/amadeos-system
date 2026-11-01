package eu.amadeos.system.taskmanager.proc;

import java.util.List;

/**
 * One process as {@code /proc/<pid>} describes it at one moment.
 *
 * @param pid        the process id
 * @param parentPid  the parent's process id (0 for the first process)
 * @param name       the name to show: the program's file name, or the name the program gave itself
 * @param command    the command line, arguments separated by spaces (empty for kernel threads)
 * @param uid        the real user id
 * @param state      the state letter of {@code /proc/<pid>/stat} (R running, S sleeping, T stopped, Z zombie, ...)
 * @param cpuTicks   user and system time used so far, in clock ticks
 * @param rssBytes   resident memory
 * @param ioBytes    bytes read from and written to storage so far, or -1 if not readable (other users)
 * @param nice       the nice value (-20 .. 19)
 * @param threads    the number of threads
 * @param startTicks the start time in clock ticks after boot
 * @param kernelThread true for the kernel's own threads (no command line, child of kthreadd)
 */
public record ProcessInfo(int pid, int parentPid, String name, String command, int uid, char state, long cpuTicks,
                          long rssBytes, long ioBytes, int nice, int threads, long startTicks,
                          boolean kernelThread) {

    /** The program file name of a command line: the file name of its first word. */
    static String programName(List<String> argv) {
        if (argv.isEmpty()) return "";
        String first = argv.get(0);
        int slash = first.lastIndexOf('/');
        return slash >= 0 ? first.substring(slash + 1) : first;
    }

    /**
     * The name to show for a process. The kernel keeps the first 15 characters of the program's name
     * ({@code comm}); a program may also have renamed itself ("Web Content"). The program's file name is
     * used when {@code comm} is its beginning; for Java programs the jar or the main class.
     */
    static String displayName(String comm, List<String> argv) {
        String program = programName(argv);
        if (program.isEmpty() || !program.startsWith(comm)) return comm;
        if (program.equals("java")) {
            String java = javaProgram(argv);
            if (!java.isEmpty()) return java;
        }
        return program;
    }

    /** The jar of {@code java -jar x.jar}, or the simple name of the main class; "" if none is found. */
    static String javaProgram(List<String> argv) {
        for (int i = 1; i < argv.size(); i++) {
            String a = argv.get(i);
            if (a.equals("-jar") && i + 1 < argv.size()) {
                String jar = argv.get(i + 1);
                jar = jar.substring(jar.lastIndexOf('/') + 1);
                if (jar.endsWith(".jar")) jar = jar.substring(0, jar.length() - 4);
                // amadeos-wm-1.2.0.jar, amadeos-wm-0.0.0-dev.0.jar: the name without the version
                return jar.replaceFirst("-\\d+(\\.\\d+)*([-~.+].*)?$", "");
            }
            if (a.equals("-cp") || a.equals("-classpath") || a.equals("--class-path") || a.equals("-p")
                    || a.equals("--module-path") || a.equals("--add-opens") || a.equals("--add-exports")) {
                i++;
                continue;
            }
            if (a.equals("-m") || a.equals("--module")) {
                if (i + 1 < argv.size()) return simpleClass(argv.get(i + 1).replaceFirst("^.*/", ""));
                return "";
            }
            // @argfiles hold more options
            if (!a.startsWith("-") && !a.startsWith("@")) return simpleClass(a);
        }
        return "";
    }

    private static String simpleClass(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
