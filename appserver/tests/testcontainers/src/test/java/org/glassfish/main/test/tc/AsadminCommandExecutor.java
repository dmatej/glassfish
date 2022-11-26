/*
 * Copyright (c) 2022 Eclipse Foundation and/or its affiliates. All rights reserved.
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

package org.glassfish.main.test.tc;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.testcontainers.containers.Container.ExecResult;
import static java.util.stream.Stream.of;

/**
 * In container asadmin command executor. Directly uses the asadmin command.
 *
 * @author David Matejcek
 */
public class AsadminCommandExecutor {

    private static final Logger LOG = System.getLogger(AsadminCommandExecutor.class.getName());
    private final GlassFishContainer container;
    private final String[] defaultArgs;


    /**
     * @param container
     * @param defaultArgs - arguments, which will be added before the command name.
     */
    public AsadminCommandExecutor(final GlassFishContainer container, final String... defaultArgs) {
        this.container = container;
        this.defaultArgs = defaultArgs == null ? new String[0] : defaultArgs;
    }


    /**
     * Executes the asadmin command.
     *
     * @param command - command name - must not be null.
     * @param arguments - command arguments - nullable.
     * @return standard output
     * @throws AsadminCommandException - if the command exit code was not zero; message contains
     *             error output
     */
    public String exec(final String command, final String... arguments) throws AsadminCommandException {
        LOG.log(Level.INFO, "exec(command={0}, arguments={1})", command, Arrays.toString(arguments));
        Objects.requireNonNull(command, "command");
        final String asadmin = container.getAsadmin().getAbsolutePath();
        final String[] args = concat(of(asadmin), of(defaultArgs), of(command), of(arguments));
        try {
            final ExecResult result = container.execInContainer(StandardCharsets.UTF_8, args);
            final int exitCode = result.getExitCode();
            LOG.log(Level.DEBUG, "args={0}, exitCode={1},\nstdout:\n{2}\nstderr:\n{3}", args, exitCode,
                result.getStdout(), result.getStderr());
            if (exitCode == 0) {
                return result.getStdout();
            }
            throw new AsadminCommandException(
                "Execution of command '" + command + "' failed with: \n" + result.getStderr());
        } catch (final UnsupportedOperationException | InterruptedException | IOException e) {
            throw new IllegalStateException("Could not execute asadmin command " + command, e);
        }
    }


    @SafeVarargs
    private static String[] concat(final Stream<String>... streams) {
        return of(streams).flatMap(Function.identity()).toArray(String[]::new);
    }
}
