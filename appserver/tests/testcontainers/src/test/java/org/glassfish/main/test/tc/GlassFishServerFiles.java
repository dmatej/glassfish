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

import java.io.File;
import java.nio.file.Paths;

/**
 * Simplified locator of GlassFish domain files, which could be managed by tests.
 * All files are accessed locally not through the docker container.
 * <p>
 * Be careful when changing files used by running instance - changes may not be reflected or even
 * may corrupt the running instance.
 *
 * @author David Matejcek
 */
public class GlassFishServerFiles {

    private final File mainDirectory;
    private final String domainName;

    /**
     * Creates new instance.
     *
     * @param mainDirectory
     * @param domainName
     */
    public GlassFishServerFiles(final File mainDirectory, final String domainName) {
        this.mainDirectory = mainDirectory;
        this.domainName = domainName;
    }


    /**
     * @return GlassFish main directory
     */
    public File getMainDirectory() {
        return this.mainDirectory;
    }


    /**
     * @return domain directory
     */
    public File getDomainDirectory() {
        return Paths.get(getMainDirectory().getAbsolutePath(), "glassfish", "domains", this.domainName).toFile();
    }


    /**
     * @return domain config directory
     */
    public File getDomainConfigDirectory() {
        return new File(getDomainDirectory(), "config");
    }


    /**
     * @return domain lib directory
     */
    public File getDomainLibDirectory() {
        return new File(getDomainDirectory(), "lib");
    }


    /**
     * @return domain log directory
     */
    public File getDomainLogDirectory() {
        return new File(getDomainDirectory(), "logs");
    }


    /**
     * @return domain config directory
     */
    public File getServerLogFile() {
        return new File(getDomainLogDirectory(), "server.log");
    }


    /**
     * @return keystore.jks of the domain
     */
    public File getKeyStoreFile() {
        return getDomainDirectory().toPath().resolve(Paths.get("config", "keystore.jks")).toFile();
    }


    /**
     * @return cacerts.jks of the domain
     */

    public File getTrustStoreFile() {
        return getDomainDirectory().toPath().resolve(Paths.get("config", "cacerts.jks")).toFile();
    }
//
//
//    /**
//     * @return keystore.jks of the domain
//     */
//    // TODO: move passwords to one place!
//    public KeyStoreManager getKeyStore() {
//        return new KeyStoreManager(getKeyStoreFile(), KeyStoreType.JKS, "changeit");
//    }
//
//
//    /**
//     * @return cacerts.jks of the domain
//     */
//    public KeyStoreManager getTrustStore() {
//        return new KeyStoreManager(getTrustStoreFile(), KeyStoreType.JKS, "changeit");
//    }


    /**
     * @return asadmin file (not asadmin.bat, we always run on Linux here!)
     */
    public File getAsadmin() {
        return new File(new File(getMainDirectory(), "bin"), "asadmin");
    }
}
