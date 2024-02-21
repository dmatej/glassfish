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

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.glassfish.main.test.tc.GlassFishContainer.GF_ADMIN_PASSWORD_FILE;
import static org.glassfish.main.test.tc.GlassFishContainer.GF_ADMIN_USER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;

/**
 * @author David Matejcek
 */
@Testcontainers
public class DomainRestartITest {
    private static final Properties CFG = new Properties();

    @Container
    private final GlassFishContainer server = new GlassFishContainer(CFG.getProperty("glassfish.zip"))
        .withAsadminTerse(false).withAsadminTrace(true);

    @BeforeAll
    public static void init() throws Exception {
        CFG.load(DomainRestartITest.class.getResourceAsStream("/test.properties"));
    }


    @AfterEach
    public void copyServerLog(TestInfo testInfo) throws Exception {
        server.asLocalAdmin("stop-domain", "--kill");
        server.copyFileFromContainer(server.getGlassFishFileStructureInDocker().getServerLogFile().getAbsolutePath(),
            CFG.getProperty("project.build.directory") + "/server-" + testInfo.getTestMethod().get().getName() + ".log");
        server.execInContainer("rm", server.getGlassFishFileStructureInDocker().getServerLogFile().getAbsolutePath());
    }


    @Test
    @Timeout(threadMode = ThreadMode.SEPARATE_THREAD, unit = TimeUnit.SECONDS, value = 10)
    public void stopDomainWithWrongHostname() throws Exception {
        assertThrows(AsadminCommandException.class,
            () -> server.asAdmin("--host", "nonexisting.glassfish.ee", "--port", "4848", "stop-domain"));
    }


    /**
     * Reproducer. stop-domain randomly ended with timeout.
     * <p>
     * Context:
     * <ul>
     * <li>/etc/hosts contains two lines with 127.0.0.1 (bad practice)
     * <li>change-admin-password immediately followed by the stop-domain in a bash script.
     * <li>Always used --user and --password
     * </ul>
     */
    @Test
    @Timeout(threadMode = ThreadMode.SEPARATE_THREAD, unit = TimeUnit.MINUTES, value = 5)
    public void sequence() throws Exception {
        List<String> asadminArgs = List.of("--user", GF_ADMIN_USER, "--passwordfile", GF_ADMIN_PASSWORD_FILE);
        asadmin(10, stringContainsInOrder("Command stop-domain executed successfully."), asadminArgs, "stop-domain");

        // Redundant args are here intentionally
        asadmin(30, stringContainsInOrder("Command start-domain executed successfully."), asadminArgs, "start-domain");

        asadmin(10, stringContainsInOrder("Command change-admin-password executed successfully"),
            List.of("--user", "admin", "--passwordfile", "/change-admin-password.txt"), "change-admin-password");

        // Correct host name, port, user and password.
        asadmin(10,
            stringContainsInOrder(
                "The directory /home/tck/.gfclient/cache/glassfish.server.localdomain_4848 has been created.",
                "Command stop-domain executed successfully."),
            List.of("--host", "glassfish.server.localdomain", "--port", "4848", "--user", GF_ADMIN_USER,
                "--passwordfile", GF_ADMIN_PASSWORD_FILE),
            "stop-domain");

        // Standard
        asadmin(30, stringContainsInOrder("Command start-domain executed successfully."), List.of(), "start-domain",
            "domain1");

        // Using localhost should be okay too
        asadmin(10, stringContainsInOrder("Command stop-domain executed successfully."),
            List.of("--host", "localhost", "--port", "4848"), "stop-domain");

        // Standard
        asadmin(30, stringContainsInOrder("Command start-domain executed successfully."), List.of(), "start-domain");

        // Local server can be killed by local asadmin.
        asadmin(10, stringContainsInOrder("Command stop-domain executed successfully."), List.of(), "stop-domain",
            "--kill");
    }


    private void asadmin(int timeoutInSeconds, Matcher<String> outputMatcher, List<String> asadminArgs,
        String commandName, String... commandArgs) {
        AsadminCommandExecutor asadmin = new AsadminCommandExecutor(server, asadminArgs.toArray(String[]::new));
        assertThat(assertTimeout(Duration.ofSeconds(timeoutInSeconds), () -> asadmin.exec(commandName, commandArgs)),
            outputMatcher);
    }
}
