/*
 * Copyright (c) 2022, 2025 Contributors to the Eclipse Foundation
 * Copyright (c) 2009, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.enterprise.universal.process;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.System.Logger;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;


/**
 * Use this class for painless process spawning.
 * <p>
 * This class was originally written to be compatible with JDK 1.4, using Runtime.exec(),
 * but has been refactored to use ProcessBuilder for better control and configurability.
 *
 * @since JDK 1.4
 * @author bnevins 2005
 */
public final class ProcessManager {
    private static final Logger LOG = System.getLogger(ProcessManager.class.getName());

    private final ProcessBuilder builder;
    private String stdout;
    private String stderr;
    private int timeout;
    private String textToWaitFor;
    private boolean echo = true;
    private String[] stdinLines;

    public ProcessManager(String... cmds) {
        builder = new ProcessBuilder(cmds);
    }


    public ProcessManager(List<String> cmdline) {
        builder = new ProcessBuilder(cmdline);
    }


    public void setTimeoutMsec(int num) {
        if (num > 0) {
            timeout = num;
        }
    }

    public void setEnvironment(String name, String value) {
        Map<String, String> env = builder.environment();
        env.put(name, value);
    }

    public void setWorkingDir(File directory) {
        builder.directory(directory);
    }

    public void setStdinLines(List<String> list) {
        if (list != null && !list.isEmpty()) {
            stdinLines = list.toArray(String[]::new);
        }
    }


    /**
     * Should the output of the process be echoed to stdout?
     *
     * @param newEcho
     */
    public void setEcho(boolean newEcho) {
        echo = newEcho;
    }


    /**
     * If not null, should wait until this text is found in standard output instead of waiting until
     * the process terminates
     *
     * @param textToWaitFor
     */
    public void setTextToWaitFor(String textToWaitFor) {
        this.textToWaitFor = textToWaitFor;
    }


    public String getStdout() {
        return stdout;
    }


    public String getStderr() {
        return stderr;
    }


    /**
     * Executes the command and waits for it to finish while reading its output.
     *
     * @return exit code. Can be overridden internally when we are waiting for a specific text in
     *         output and we succeeded despite the process failed or even did not finish.
     *         If we have found the output, we don't kill the process.
     * @throws ProcessManagerException
     */
    public int execute() throws ProcessManagerException {
        LOG.log(DEBUG, "Executing command:\n  command={0}  \nenv={1}", builder.command(), builder.environment());
        final Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IllegalStateException("Could not execute command: " + builder.command(), e);
        }
        final ReaderThread threadErr = new ReaderThread(process.getErrorStream(), echo, "stderr", textToWaitFor);
        threadErr.start();
        final ReaderThread threadOut = new ReaderThread(process.getInputStream(), echo, "stdout", textToWaitFor);
        threadOut.start();
        try {
            writeStdin(process);
            final int result;
            try {
                result = await(process);
            } catch (InterruptedException | ProcessManagerTimeoutException e) {
                if (threadOut.isTextFound() || threadErr.isTextFound()) {
                    // process did not finish, but we are happy with the output
                    LOG.log(DEBUG, "The process did not finish yet, but text was detected in output: {0}",
                        textToWaitFor);
                    return 0;
                }
                throw e;
            }
            if (textToWaitFor != null) {
                threadErr.finish(1000L);
                threadOut.finish(1000L);
                if(!threadOut.isTextFound() && !threadErr.isTextFound()) {
                    throw new ProcessManagerException("Process finished but text " + textToWaitFor + " not found in output");
                }
            }
            return result;
        } catch (ProcessManagerException pme) {
            throw pme;
        } catch (Exception e) {
            throw new ProcessManagerException(e);
        } finally {
            stderr = threadErr.finish(1000L);
            stdout = threadOut.finish(1000L);
            if (process.isAlive() && textToWaitFor == null) {
                destroy(process);
            }
        }
    }

    private void destroy(Process process) {
        process.destroy();
        // Wait for a while to let the process stop
        try {
            boolean exited = process.waitFor(10, TimeUnit.SECONDS);
            if (!exited) {
                LOG.log(WARNING, "Process did not exit after waiting, attempting to forcibly destroy it: {0}",
                    builder.command());
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            LOG.log(INFO, "Interrupted while waiting for process to terminate", e);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String toString() {
        return builder.command().toString();
    }


    private void writeStdin(Process process) throws ProcessManagerException {
        if (stdinLines == null || stdinLines.length == 0) {
            return;
        }
        try (PrintWriter pipe = new PrintWriter(
            new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), Charset.defaultCharset())))) {
            for (String stdinLine : stdinLines) {
                LOG.log(DEBUG, "InputLine --> {0} <--", stdinLine);
                pipe.println(stdinLine);
                pipe.flush();
            }
        } catch (Exception e) {
            throw new ProcessManagerException(e);
        }
    }


    private int await(Process process) throws InterruptedException, ProcessManagerException {
        if (timeout > 0) {
            if (process.waitFor(timeout, TimeUnit.MILLISECONDS)) {
                return process.exitValue();
            }
            throw new ProcessManagerTimeoutException("Process is still running, timeout " + timeout + " ms exceeded.");
        }
        return process.waitFor();
    }


    private static class ReaderThread extends Thread {
        private final BufferedReader reader;
        private final StringBuilder sb;
        private final boolean echo;
        private final AtomicBoolean stop = new AtomicBoolean();
        private final Thread threadWaitingForProcess;
        private final String textToWaitFor;
        private volatile boolean textFound;

        private ReaderThread(InputStream stream, boolean echo, String threadName, String textToWaitFor) {
            setName(threadName);
            this.reader = new BufferedReader(new InputStreamReader(stream, Charset.defaultCharset()));
            this.sb = new StringBuilder();
            this.echo = echo;
            this.textToWaitFor = textToWaitFor;
            this.threadWaitingForProcess = Thread.currentThread();
        }

        public boolean isTextFound() {
            return textFound;
        }


        @Override
        public void run() {
            try {
                while (true) {
                    String line;
                    if (reader.ready()) {
                        line = reader.readLine();
                    } else if (stop.getAcquire()) {
                        break;
                    } else {
                        Thread.yield();
                        continue;
                    }
                    sb.append(line).append('\n');
                    if (echo) {
                        System.out.println(line);
                    }
                    if (textToWaitFor != null && line.contains(textToWaitFor)) {
                        textFound = true;
                        threadWaitingForProcess.interrupt();
                        return;
                    }
                }
            } catch (Exception e) {
                LOG.log(ERROR, "ReaderThread " + getName() + " broke ...", e);
            } finally {
                try {
                    reader.close();
                } catch (IOException e) {
                    LOG.log(ERROR, "Failed to close BufferedReader", e);
                }
                LOG.log(TRACE, "ReaderThread exiting...");
            }
        }


        /**
         * Asks the thread to finish it's job and waits until the thread dies.
         * <p>
         * @param timeout The maximal time for the waiting.
         *
         * @return the final output of the process.
         */
        public String finish(long timeout) {
            stop.setRelease(true);
            try {
                join(timeout);
            } catch (InterruptedException ex) {
                LOG.log(WARNING, "Interrupted while waiting for ReaderThread to finish", ex);
            }
            return sb.toString();
        }
    }
}
