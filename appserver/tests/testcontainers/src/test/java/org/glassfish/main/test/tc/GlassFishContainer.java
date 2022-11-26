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

import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ulimit;

import java.io.File;
import java.time.Duration;
import java.util.TimeZone;
import java.util.function.Consumer;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * @author David Matejcek
 */
public class GlassFishContainer extends GenericContainer<GlassFishContainer> {

    private static final Consumer<OutputFrame> LOG_CONSUMER = log -> System.out.print("GF_D1: " + log.getUtf8String());
    private static final String GF_DIR = "/home/tck/gf";
    private static final String GF_ADMIN_USER = "admin";
    private static final String GF_ADMIN_PASSWORD_FILE = "/admin-password.txt";

    /**
     * Creates an instance.
     *
     * @param gfZipPath
     */
    public GlassFishContainer(String gfZipPath) {
        super(DockerImageName.parse("dmatej/cts-base:0.4"));
        this
            .withCopyFileToContainer(MountableFile.forHostPath(gfZipPath), "/glassfish.zip")
            // FIXME: parameter adminPassword, generate files inside.
            .withCopyFileToContainer(MountableFile.forClasspathResource("/change-admin-password.txt"), "/change-admin-password.txt")
            .withCopyFileToContainer(MountableFile.forClasspathResource(GF_ADMIN_PASSWORD_FILE), GF_ADMIN_PASSWORD_FILE)
            .withCopyFileToContainer(MountableFile.forClasspathResource("/server-logging.properties"), "/server-logging.properties")
            .withEnv("JAVA_HOME", "/opt/jdk17")
            .withEnv("TZ", TimeZone.getDefault().getID())
            .withEnv("LC_ALL", "en_US.UTF-8")
//            .withEnv("AS_TRACE", "true")
//            .withNetworkMode("host")
            .withCommand("/bin/bash", "-c", "true"
                + " && id && (env | sort) && cat /etc/hosts"
                + " && mkdir " + GF_DIR
                + " && unzip -K -q -o /glassfish.zip -d " + GF_DIR
                + " && cp /server-logging.properties " + GF_DIR + "/glassfish7/glassfish/domains/domain1/config/logging.properties"
                + " && " + GF_DIR + "/glassfish7/bin/asadmin start-domain domain1"
                // We need all lines - if we set too verbose logging, we still need to find the message.
                + " && tail --retry -n 1000000 -F " + GF_DIR + "/glassfish7/glassfish/domains/domain1/logs/server.log"
            )
            .withExposedPorts(4848, 8080, 8181, 7676, 8686, 9009)
            .withCreateContainerCmdModifier(cmd -> {
                cmd.withHostName("glassfish.server.localdomain");
                // see https://github.com/zpapez/docker-java/wiki
                final HostConfig hostConfig = cmd.getHostConfig();
                hostConfig.withMemory(4 * 1024 * 1024 * 1024L);
                hostConfig.withMemorySwappiness(0L);
                hostConfig.withDns();
                // This is to confuse asadmin, incorrect /etc/hosts with duplicit 127.0.0.1 mapping.
                hostConfig.withExtraHosts("localhost2:127.0.0.1");
                hostConfig.withUlimits(new Ulimit[] {new Ulimit("nofile", 4096L, 8192L)});
            })
            .withLogConsumer(LOG_CONSUMER)
            .waitingFor(new LogMessageWaitStrategy().withRegEx(".*Eclipse GlassFish  [0-9\\.]+  \\(.*")
                .withStartupTimeout(Duration.ofSeconds(30L)));
    }


    /**
     * Executes asadmin without need to access to a running domain.
     *
     * @param command - command name
     * @param arguments - arguments. Can be null.
     * @return standard output
     * @throws AsadminCommandException- if the command exit code was not zero; message contains
     *             error output
     */
    public String asLocalAdmin(final String command, final String... arguments) throws AsadminCommandException {
        // FIXME: --echo breaks change-admin-password
        final String[] defaultArgs = new String[] {"--terse"};
        final AsadminCommandExecutor executor = new AsadminCommandExecutor(this, defaultArgs);
        return executor.exec(command, arguments).trim();
    }


    /**
     * Executes asadmin command against running domain instance.
     *
     * @param command - command name
     * @param arguments - arguments. Can be null and should not contain parameters used before
     *            the command.
     * @return standard ouptut
     * @throws AsadminCommandException- if the command exit code was not zero; message contains
     *             error output
     */
    public String asAdmin(final String command, final String... arguments) throws AsadminCommandException {
        final String[] defaultArgs = new String[] {"--terse", "--user", GF_ADMIN_USER, "--passwordfile",
            GF_ADMIN_PASSWORD_FILE};
        final AsadminCommandExecutor executor = new AsadminCommandExecutor(this, defaultArgs);
        return executor.exec(command, arguments).trim();
    }


    /**
     * @return absolute path to asadmin command file in the container.
     */
    public File getAsadmin() {
        return getGlassFishFileStructureInDocker().getAsadmin();
    }


    /**
     * @return instance resolving GlassFish directory structure <b>inside</b> the docker container.
     */
    public GlassFishServerFiles getGlassFishFileStructureInDocker() {
        return new GlassFishServerFiles(new File(GF_DIR, "glassfish7"), "domain1");
    }
}
