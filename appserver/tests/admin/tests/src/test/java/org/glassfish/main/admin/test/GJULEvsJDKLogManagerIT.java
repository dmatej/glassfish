/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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

package org.glassfish.main.admin.test;

import java.nio.file.Path;

import org.glassfish.main.itest.tools.GlassFishTestEnvironment;
import org.glassfish.main.itest.tools.asadmin.Asadmin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.glassfish.main.itest.tools.asadmin.AsadminResultMatcher.asadminOK;
import static org.hamcrest.MatcherAssert.assertThat;

public class GJULEvsJDKLogManagerIT {

    private static final Asadmin ASADMIN = GlassFishTestEnvironment.getAsadmin(false);
    private static final Path LOGDIR = GlassFishTestEnvironment.getDomain1Directory().resolve("logs").toAbsolutePath();
    private static final String SYSOPT_ERR_LOG = "'-XX:ErrorFile=" + LOGDIR + "/java_error%p.log'";
    private static final String SYSOPT_GC_LOG = "'-Xlog:gc*:file=" + LOGDIR + "/gc-%t.log:time:filecount=10'";

    @BeforeAll
    static void setAndBackup() {
        assertThat(ASADMIN.exec(60000, "stop-domain"), asadminOK());
        assertThat(ASADMIN.exec(60000, "backup-domain"), asadminOK());
        assertThat(ASADMIN.exec(60000, "start-domain"), asadminOK());
    }


    @Test
    void testConfigureAndRestart() {
        assertThat(ASADMIN.exec(10000, "create-jvm-options", SYSOPT_ERR_LOG), asadminOK());
        assertThat(ASADMIN.exec(10000, "create-jvm-options", SYSOPT_GC_LOG), asadminOK());
        assertThat(ASADMIN.exec(60000, "restart-domain"), asadminOK());
    }

    @AfterAll
    static void revert() {
        assertThat(ASADMIN.exec(60000, "stop-domain"), asadminOK());
        assertThat(ASADMIN.exec(60000, "restore-domain", "domain1"), asadminOK());
        assertThat(ASADMIN.exec(60000, "start-domain"), asadminOK());
    }
}
