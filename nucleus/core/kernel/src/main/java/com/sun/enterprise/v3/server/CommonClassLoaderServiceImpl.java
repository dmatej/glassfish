/*
 * Copyright (c) 2022, 2024 Contributors to the Eclipse Foundation
 * Copyright (c) 2007, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.v3.server;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.kernel.KernelLoggerInfo;
import org.glassfish.main.jdke.cl.GlassfishUrlClassLoader;
import org.jvnet.hk2.annotations.Service;

import static com.sun.enterprise.glassfish.bootstrap.cfg.BootstrapKeys.DERBY_ROOT_PROP_NAME;
import static com.sun.enterprise.glassfish.bootstrap.cfg.BootstrapKeys.INSTALL_ROOT_PROP_NAME;
import static java.util.logging.Level.CONFIG;
import static java.util.logging.Level.INFO;

/**
 * This class is responsible for setting up Common Class Loader. As the
 * name suggests, Common Class Loader is common to all deployed applications.
 * Common Class Loader is responsible for loading classes from
 * following URLs (the order is strictly maintained):
 * lib/*.jar:domain_dir/lib/classes:domain_dir/lib/*.jar:DERBY_DRIVERS.
 * Please note that domain_dir/lib/classes comes before domain_dir/lib/*.jar,
 * just like WEB-INF/classes is searched first before WEB-INF/lib/*.jar.
 * DERBY_DRIVERS are added to this class loader, because GlassFish ships with Derby database by default
 * and it makes them available to users by default. Earlier, they used to be available to applications via
 * launcher classloader, but now they are available via this class loader (see issue 13612 for more details on this).
 *
 * It applies a special rule while handling jars in install_root/lib.
 * In order to maintain file layout compatibility (see  issue #9526),
 * we add jars like javaee.jar and appserv-rt.jar which need to be excluded
 * from runtime classloaders in the server side, as they are already available via
 * PublicAPIClassLoader. So, before we add any jar from install_root/lib,
 * we look at their manifest entry and skip the ones that have an entry
 * GlassFish-ServerExcluded: true
 *
 * @author Sanjeeb.Sahoo@Sun.COM
 */
@Service
public class CommonClassLoaderServiceImpl {

    private static final Logger LOG = KernelLoggerInfo.getLogger();

    @Inject
    private APIClassLoaderServiceImpl acls;

    @Inject
    private ServerEnvironment env;

    /**
     * The common classloader.
     */
    private GlassfishUrlClassLoader commonClassLoader;
    private ClassLoader apiClassLoader;
    private String commonClassPath = "";

    private static final String SERVER_EXCLUDED_ATTR_NAME = "GlassFish-ServerExcluded";

    @PostConstruct
    private void postConstruct() {
        apiClassLoader = Objects.requireNonNull(acls.getAPIClassLoader(), "API ClassLoader is null!");
        createCommonClassLoader();
    }

    private void createCommonClassLoader() {
        List<File> cpElements = new ArrayList<>();
        File domainDir = env.getInstanceRoot();
        final String installRoot = System.getProperty(INSTALL_ROOT_PROP_NAME);
        if (installRoot == null) {
            throw new IllegalStateException("The system property is not set: " + INSTALL_ROOT_PROP_NAME);
        }
        File installDir = new File(installRoot);
        File installLibDir = new File(installDir, "lib");
        if (installLibDir.isDirectory()) {
            Collections.addAll(cpElements, installLibDir.listFiles(new CompiletimeJarFileFilter()));
        }
        final File domainLibDir = new File(domainDir, "lib");
        final File domainClassesDir = new File(domainLibDir, "classes");
        if (domainClassesDir.exists()) {
            cpElements.add(domainClassesDir);
        }
        if (domainLibDir.isDirectory()) {
            Collections.addAll(cpElements, domainLibDir.listFiles(new JarFileFilter()));
        }
        cpElements.addAll(findDerbyJars(installDir));
        List<URL> urls = new ArrayList<>();
        for (File file : cpElements) {
            try {
                urls.add(file.toURI().toURL());
            } catch (IOException e) {
                LOG.log(Level.WARNING, KernelLoggerInfo.invalidClassPathEntry, new Object[] {file, e});
            }
        }
        commonClassPath = urlsToClassPath(urls.stream());
        if (urls.isEmpty()) {
            LOG.logp(Level.FINE, "CommonClassLoaderManager",
                "Skipping creation of CommonClassLoader as there are no libraries available", "urls = {0}", urls);
        } else {
            commonClassLoader = new GlassfishUrlClassLoader("CommonLibs", urls.toArray(URL[]::new), apiClassLoader);
            LOG.log(Level.FINE, "Created common classloader: {0}", commonClassLoader);
        }
    }

    public ClassLoader getCommonClassLoader() {
        return commonClassLoader == null ? apiClassLoader : commonClassLoader;
    }

    /**
     * Adds a classpath element to the common classloader if the classloader supports it.
     * @param url URL of the classpath element to add
     * @throws UnsupportedOperationException If adding not supported by the classloader
     */
    public void addToClassPath(URL url) {
        if (commonClassLoader == null) {
            commonClassLoader = new GlassfishUrlClassLoader("CommonLibs", new URL[] {url}, apiClassLoader);
        } else {
            commonClassLoader.addURL(url);
        }
        commonClassPath = urlsToClassPath(Arrays.stream(commonClassLoader.getURLs()));
    }

    public String getCommonClassPath() {
        return commonClassPath;
    }

    private static String urlsToClassPath(Stream<URL> urls) {
        return urls
                .map(URL::getFile)
                .filter(Predicate.not(String::isBlank))
                .map(File::new)
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator));
    }

    private List<File> findDerbyJars(File installDir) {
        Path derbyHome = getDerbyDir(installDir);
        LOG.log(CONFIG, "Using derby home: {0}", derbyHome);

        final File derbyLib = derbyHome.resolve("lib").toFile();
        if (!derbyLib.exists()) {
            LOG.log(INFO, KernelLoggerInfo.cantFindDerby, derbyLib);
            return Collections.emptyList();
        }

        return Arrays
            .asList(derbyLib.listFiles((dir, name) -> name.endsWith(".jar") && !name.startsWith("derbyLocale_")));
    }

    private static Path getDerbyDir(File installDir) {
        String derbyHomeProperty = System.getProperty(DERBY_ROOT_PROP_NAME);
        if (derbyHomeProperty == null) {
            return installDir.toPath().resolve(Path.of("..", "javadb"));
        }
        Path derbyHome = new File(derbyHomeProperty).toPath();
        if (derbyHome.isAbsolute()) {
            return derbyHome;
        }
        return new File(installDir, "config").toPath().resolve(derbyHome);
    }

    private static class JarFileFilter implements FilenameFilter {

        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(".jar");
        }
    }

    private static class CompiletimeJarFileFilter extends JarFileFilter {

        @Override
        public boolean accept(File dir, String name) {
            if (super.accept(dir, name)) {
                File file = new File(dir, name);
                try (JarFile jar = new JarFile(file)) {
                    Manifest manifest = jar.getManifest();
                    if (manifest != null) {
                        String exclude = manifest.getMainAttributes().getValue(SERVER_EXCLUDED_ATTR_NAME);
                        if ("true".equalsIgnoreCase(exclude)) {
                            return false;
                        }
                    }
                } catch (IOException e) {
                    LOG.log(Level.WARNING, KernelLoggerInfo.exceptionProcessingJAR,
                        new Object[] {file.getAbsolutePath(), e});
                }
                return true;
            }
            return false;
        }
    }
}
