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

import java.lang.System.Logger;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertTimeout;

/**
 * @author David Matejcek
 */
@Testcontainers
public class DomainRestartITest {
    private static final Logger LOG = System.getLogger(DomainRestartITest.class.getName());

    private static final Properties CFG = new Properties();

    @Container
    private final GlassFishContainer server = new GlassFishContainer(CFG.getProperty("glassfish.zip"));

    @BeforeAll
    public static void init() throws Exception {
        CFG.load(DomainRestartITest.class.getResourceAsStream("/test.properties"));
    }


    @AfterEach
    public void copyServerLog(TestInfo testInfo) throws Exception {
        server.asAdmin("stop-domain", "--kill");
        server.copyFileFromContainer(server.getGlassFishFileStructureInDocker().getServerLogFile().getAbsolutePath(),
            CFG.getProperty("project.build.directory") + "/server-" + testInfo.getTestMethod().get().getName() + ".log");
        server.execInContainer("rm", server.getGlassFishFileStructureInDocker().getServerLogFile().getAbsolutePath());
    }


    @Test
    @Timeout(threadMode = ThreadMode.SEPARATE_THREAD, unit = TimeUnit.SECONDS, value = 10)
    public void stopDomainWithWrongHostname() throws Exception {
        String stdout = server.asAdmin("--host", "nonexisting.glassfish.ee", "--port", "4848", "stop-domain");
        assertThat(stdout,
            stringContainsInOrder("It appears that server has started, but the communication with it failed.",
                "No remote server named nonexisting.glassfish.ee.", " Is that the correct host name?"));
    }


    @Test
    @Timeout(threadMode = ThreadMode.SEPARATE_THREAD, unit = TimeUnit.MINUTES, value = 1) // FIXME: Nastavit na 3
    public void sequence() throws Exception {
        server.asAdmin("stop-domain");
        server.asAdmin("start-domain");
        server.asLocalAdmin("--user", "admin", "--passwordfile", "/change-admin-password.txt", "change-admin-password");
        assertThat(
            assertTimeout(Duration.ofSeconds(10),
                () -> server.asAdmin("--host", "glassfish.server.localdomain", "--port", "4848", "stop-domain")),
            stringContainsInOrder("Domain domain1 stopped."));
        server.asAdmin("start-domain", "domain1");
        assertThat(
            assertTimeout(Duration.ofSeconds(10),
                () -> server.asAdmin("--host", "localhost", "--port", "4848", "stop-domain")),
            stringContainsInOrder("Domain domain1 stopped."));
        server.asAdmin("start-domain");
        server.asAdmin("stop-domain", "--kill");
    }
}
