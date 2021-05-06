/*
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

package com.sun.enterprise.v3.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Enumeration;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.api.deployment.archive.ReadableArchive;
import org.glassfish.hk2.classmodel.reflect.ArchiveAdapter;
import org.glassfish.hk2.classmodel.reflect.Parser;
import org.glassfish.hk2.classmodel.reflect.util.AbstractAdapter;
import org.glassfish.kernel.KernelLoggerInfo;

/**
 * ArchiveAdapter for DOL readable archive instances
 *
 * @author Jerome Dochez
 */
public class ReadableArchiveScannerAdapter extends AbstractAdapter {

    final ReadableArchive archive;
    final Parser parser;
    final URI uri;

    /**
     * Can be null or can be pointing to the archive adapter in which
     * we are embedded.
     */
    final ReadableArchiveScannerAdapter parent;

    /**
     * We need to maintain a count of the sub archives we have asked
     * the class-model to parse so we can close our archive once all
     * the sub-archives have been closed themselves.
     *
     * Obviously, we start with a count of 1 since we need to account
     * for our own closure.
     *
     */
    final AtomicInteger releaseCount = new AtomicInteger(1);

    /**
     * Default timeout value for parsing a single jar (plus all internal jars it may contains)
     */
    private final int DEFAULT_TIMEOUT = Integer.getInteger(Parser.DEFAULT_WAIT_SYSPROP, 600);

    private final static Level level = Level.FINE;
    final private static Logger alogger = KernelLoggerInfo.getLogger();


    public ReadableArchiveScannerAdapter(Parser parser, ReadableArchive archive) {
        this.archive = archive;
        this.parser = parser;
        this.uri = archive.getURI();
        this.parent = null;
    }

    private ReadableArchiveScannerAdapter(ReadableArchiveScannerAdapter parent, ReadableArchive archive, URI uri) {
        this.parent = parent;
        this.archive = archive;
        this.parser = parent.parser;
        this.uri = uri==null?archive.getURI():uri;
    }

    @Override
    public URI getURI() {
       return uri;
    }

    @Override
    public Manifest getManifest() throws IOException {
        return archive.getManifest();
    }

    @Override
    public void onSelectedEntries(ArchiveAdapter.Selector selector, EntryTask entryTask, final Logger logger ) throws IOException {

        Enumeration<String> entries = archive.entries();
        while (entries.hasMoreElements()) {
            final String name = entries.nextElement();
            Entry entry = new Entry(name, archive.getEntrySize(name), false);
            if (selector.isSelected(entry)) {
                handleEntry(name, entry, logger, entryTask);
            }
            // check for non exploded jars.
            if (name.endsWith(".jar")) {
                handleJar(name, logger);
            }
        }
        if (logger.isLoggable(level)) {
            logger.log(level, "Finished parsing " + this.uri);
        }
    }

    @Override
    public void close() throws IOException {
        releaseCount();
    }

    private void releaseCount() throws IOException {
        int release = releaseCount.decrementAndGet();
        if (release==0) {
            archive.close();
            if (parent!=null) {
                parent.releaseCount();
            }
        }
    }

    protected void handleEntry(String name, Entry entry, Logger logger /*ignored*/, EntryTask entryTask)
        throws IOException {

        InputStream is = null;
        try {
            try {
                is = archive.getEntry(name);
                if (is==null) {
                    alogger.log(Level.SEVERE, KernelLoggerInfo.invalidInputStream, name);
                    return;
                }
                 entryTask.on(entry, is);
            } catch (Exception e) {
                alogger.log(Level.SEVERE, KernelLoggerInfo.exceptionWhileParsing,
                        new Object[] { entry.name, archive.getURI(), entry.size, e});
            }
        } finally {
            if (is!=null)
                is.close();
        }
    }

    protected Future handleJar(final String name, final Logger logger)
        throws IOException {

       // we need to check that there is no exploded directory by this name.
        String explodedName = name.replaceAll("[/ ]", "__").replace(".jar", "_jar");
        if (!archive.exists(explodedName)) {

            final ReadableArchive subArchive = archive.getSubArchive(name);
            if (subArchive==null) {
                logger.log(Level.SEVERE, KernelLoggerInfo.cantOpenSubArchive,
                        new Object[] {name, archive.getURI()});
                return null;
            }

            if (logger.isLoggable(level)) {
                logger.log(level, "Spawning sub parsing " + subArchive.getURI());
            }
            final ReadableArchiveScannerAdapter adapter = new InternalJarAdapter(this, subArchive, subArchive.getURI());
            // we increment our release count, this tells us when we can safely close the parent
            // archive.
            releaseCount.incrementAndGet();
            return parser.parse(adapter, new Runnable() {
                @Override
                public void run() {
                    try {
                        if (logger.isLoggable(level))
                            logger.log(level, "Closing sub archive " + subArchive.getURI());
                        adapter.close();
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, KernelLoggerInfo.exceptionWhileClosing,
                                new Object[] { name, e });
                    }
                }
            });
        }
        return null;
    }

    /**
     * Adapter for internal jar files. we don't process further down any more internal
     * jar files. In other words, no jars inside jars inside jars can be deployed...
     */
    private static class InternalJarAdapter extends ReadableArchiveScannerAdapter {
        public InternalJarAdapter(ReadableArchiveScannerAdapter parent, ReadableArchive archive, URI uri) {
            super(parent, archive, uri);
        }

        @Override
        protected Future handleJar(String name, Logger logger) throws IOException {
            // we don't process second level internal jars
            return null;
        }
    }
}
