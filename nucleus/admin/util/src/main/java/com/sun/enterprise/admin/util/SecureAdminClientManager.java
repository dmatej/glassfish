/*
 * Copyright (c) 2021, 2025 Contributors to the Eclipse Foundation
 * Copyright (c) 2010, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.admin.util;

import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.SecureAdmin;
import com.sun.enterprise.security.store.AsadminSecurityUtil;
import com.sun.enterprise.util.io.ServerDirs;

import java.io.File;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.internal.api.Globals;
import org.jvnet.hk2.config.ConfigParser;
import org.jvnet.hk2.config.Dom;
import org.jvnet.hk2.config.DomDocument;

import static com.sun.enterprise.util.SystemPropertyConstants.KEYSTORE_TYPE_DEFAULT;

/**
 * Encapsulates the implementation of secure admin.
 * <p>
 * A process that needs to send admin messages to another server and might not have a user-provided
 * username and password should inject this class and invoke
 * {@link #initClientAuthentication(char[], boolean, String, String, String, File)} before it sends
 * a message to the admin listener. The code which actually prepares the message can then retrieve
 * the initialized information from this class in constructing the outbound admin message.
 * <p>
 * The class offers static accessors to the important values so, for example, RemoteAdminCommand
 * (which is not a service and it therefore not subject to injection) can retrieve what it needs to
 * build the outbound admin request.
 * <p>
 * This allows us to support CLI commands which need to connect to the DAS securely but will have
 * neither a user-provided master password nor a human who we could prompt for the master password.
 *
 * @author Tim Quinn
 */
public class SecureAdminClientManager {

    private static final Logger logger = AdminLoggerInfo.getLogger();

    /**
     * the hk2-managed instance - used only by the static accessors
     */
    private static SecureAdminClientManager instance;

    /**
     * is cert-based secure admin enabled?
     */
    private boolean isEnabled;

    /**
     * suitable for passing to SSLContext.init
     */
    private KeyManager[] keyManagers;

    /**
     * suitable for setting as the value in an HTTP header to flag a message source as trusted to submit admin requests
     * (only in the non-secure case)
     */
    private String configuredAdminIndicator;

    private Domain domain;

    private SecureAdmin secureAdmin;

    private String instanceAlias;

    /**
     * Returns KeyManagers which access the SSL key store for use in performing client cert
     * authentication. The returned KeyManagers will most likely be passed to
     * {@link SSLContext#init(KeyManager[], javax.net.ssl.TrustManager[], java.security.SecureRandom)}.
     *
     * @return KeyManagers
     */
    public static KeyManager[] getKeyManagers() {
        return (instance == null ? null : instance.keyManagers());
    }

    /**
     * Prepares the manager so SSL/TLS will provide the correct client cert when connecting to a remote admin port. The main
     * result of invoking this method is to build an array of KeyManagers which can be passed to
     * SSLContext.initClientAuthentication so SSL can use the managers to find certs that meet the requirements of the
     * partner on the other end of the connection.
     * <p>
     * This method opens the keystore, so it will need the master password. The calling command should pass the master
     * password which the user specified in the file specified by the --passwordfile option (if any). Because the
     * user-provided password might be wrong or missing, the caller also indicates whether a human user is present to
     * respond to a prompt for the password. This will not be the case, for example, during an unattended start-up of an
     * instance.
     * <p>
     * The caller also provides at least one of the server name, the node directory, or the node. These are used to locate
     * where the domain.xml file is that contains security config information we need.
     *
     * @param commandMasterPassword master password provided by the user on the command line; null if none
     * @param isInteractive whether the caller is in a context where a human could be prompted to enter a password
     * @param serverName name of the server where domain.xml resides
     * @param nodeDir directory of the node where domain.xml resides
     * @param node name of the node whose directory contains domain.xml
     */
    public synchronized static void initClientAuthentication(final char[] commandMasterPassword, final boolean isInteractive,
            final String serverName, final String nodeDir, final String node, final File nodeDirRoot) {

        /*
         * The client/instance security information is common to a whole domain.
         * So, once this manager is initialized the same settings will be used
         * going forward.  It does not matter whether a different server name
         * or node directory or node is specified.  They should all lead to the
         * same, shared configuration for whether client/instance SSL security
         * should be used or not.
         */
        if (instance == null) {
            instance = new SecureAdminClientManager(commandMasterPassword, isInteractive, serverName, nodeDir, node, nodeDirRoot);
        }
    }

    private SecureAdminClientManager(final char[] commandMasterPassword, final boolean isInteractive, final String serverName,
            final String nodeDir, final String node, final File nodeDirRoot) {
        domain = prepareDomain(serverName, nodeDir, node, nodeDirRoot);
        if (domain == null) {
            return;
        }
        secureAdmin = domain.getSecureAdmin();
        isEnabled = SecureAdmin.isEnabled(secureAdmin);
        configuredAdminIndicator = SecureAdmin.configuredAdminIndicator(secureAdmin);
        if (isEnabled) {
            instanceAlias = SecureAdmin.instanceAlias(secureAdmin);
            logger.fine("SecureAdminClientManager: secure admin is enabled");
        } else {
            logger.fine("SecureAdminClientManager: secure admin is disabled");
        }

        /*
         * Store the static value of the admin indicator header so (for example)
         * RemoteAdminCommand can get it using the static accessor to
         * prepare the outbound admin request.
         */
        configuredAdminIndicator = SecureAdmin.configuredAdminIndicator(secureAdmin);

        if (isEnabled) {
            try {
                /*
                 * The keystore should contain certs for both the
                 * admin (DAS) and the instances (or clients).
                 * If we point SSL at that keystore then it could choose any
                 * public cert that matches what the server is asking for, which
                 * means it might choose to return the DAS cert - or some other
                 * cert the user might have added to the keystore.  Because
                 * the admin code receiving the admin request is expecting us
                 * to use the instance cert, we need to make sure that happens.
                 * So, we'll create a temporary internal keystore containing
                 * the cert for the configured instance alias and we'll use that
                 * keystore for SSL.
                 */
                keyManagers = prepareKeyManagers(commandMasterPassword, isInteractive);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Reports whether the secure admin is enabled, according to the current configuration.
     *
     * @return if secure admin is enabled
     */
    public boolean isEnabled() {
        return isEnabled;
    }

    public KeyManager[] keyManagers() {
        return keyManagers;
    }

    public String configuredAdminIndicatorValue() {
        return configuredAdminIndicator;
    }

    private Domain prepareDomain(final String serverName, final String nodeDir, final String node, final File nodeDirRoot) {
        /*
         * At least one of serverName, nodeDir, or node must be non-null.
         * Otherwise we'll have no way of figuring out which domain.xml to
         * look in to see if we should use client authentication.  This will
         * often be the case, for instance, if create-local-instance is
         * run directly (not from another command).  In such cases, if
         * secure admin is enabled the user should provide --user and
         * --passwordfile on the command line to authenticate to the DAS.
         */
        if (serverName == null && nodeDir == null && node == null) {
            return null;
        }
        final ServerDirsSelector selector;
        try {
            final String nodeDirToUse = (nodeDir != null ? nodeDir : nodeDirRoot.getAbsolutePath());
            selector = ServerDirsSelector.getInstance(null, serverName, nodeDirToUse, node);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        /*
         * If the caller did not pass any of the values we can use to locate
         * the domain.xml, then we cannot run in client-cert mode.
         */
        final ServerDirs dirs = selector.dirs();
        if (dirs == null) {
            return null;
        }

        final File domainXMLFile = dirs.getDomainXml();
        if (!domainXMLFile.exists()) {
            return null;
        }

        try {
            ServiceLocator habitat = Globals.getStaticHabitat();
            ConfigParser parser = new ConfigParser(habitat);
            URL domainURL = domainXMLFile.toURI().toURL();
            DomDocument doc = parser.parse(domainURL);
            Dom domDomain = doc.getRoot();
            Domain d = domDomain.createProxy(Domain.class);
            return d;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private KeyManager[] prepareKeyManagers(final char[] commandMasterPassword, final boolean isPromptable)
        throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        /*
         * The configuration specifies what alias we should use for SSL client
         * authentication.  Because the keystore on disk contains multiple certs,
         * extract the required cert from the on-disk keystore and add it to a
         * temporary key store so it's the only cert there.
         */
        Certificate instanceCert = getCertForConfiguredAlias(commandMasterPassword, isPromptable);

        final KeyStore ks = instanceCertOnlyKS(instanceCert);

        /*
         * The caller will eventually need an array of KeyManagers to pass to
         * SSLContext.initClientAuthentication.  Create that array now from the internal, single-cert
         * keystore so it's available later.
         */

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, new char[] {});
        return kmf.getKeyManagers();
    }

    private KeyStore instanceCertOnlyKS(final Certificate instanceCert) throws KeyStoreException {
        final KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE_DEFAULT);
        ks.setCertificateEntry(instanceAlias, instanceCert);
        return ks;
    }


    private Certificate getCertForConfiguredAlias(final char[] commandMasterPassword, final boolean isPromptable)
        throws KeyStoreException {
        final KeyStore permanentKS = AsadminSecurityUtil.getInstance(commandMasterPassword, isPromptable)
            .getAsadminKeystore();
        Certificate cert = permanentKS.getCertificate(instanceAlias);
        if (cert == null) {
            logger.log(Level.FINER, "Could not find matching cert in keystore for instance alias {0}", instanceAlias);
        } else {
            logger.log(Level.FINER, "Found matching cert in keystore for instance alias {0}", instanceAlias);
        }
        return cert;
    }
}
