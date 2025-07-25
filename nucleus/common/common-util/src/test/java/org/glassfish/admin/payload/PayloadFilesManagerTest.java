/*
 * Copyright (c) 2021, 2025 Contributors to the Eclipse Foundation.
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.admin.payload;

import com.sun.enterprise.util.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.Set;

import org.glassfish.api.admin.Payload;
import org.glassfish.api.admin.Payload.Inbound;
import org.glassfish.api.admin.Payload.Outbound;
import org.glassfish.api.admin.Payload.Part;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author tjquinn
 */
public class PayloadFilesManagerTest {

    /**
     * Test of getOutputFileURI method, of class PayloadFilesManager.
     */
    @Test
    public void testGetOutputFileURI() throws Exception {
        PayloadFilesManager.Temp instance = new PayloadFilesManager.Temp();
        try {
            String originalPath = "way/over/there/myApp.ear";
            Part testPart = PayloadImpl.Part.newInstance("text/plain", originalPath, null, "random content");
            URI result = instance.getOutputFileURI(testPart, testPart.getName());
            assertTrue(result.toASCIIString().endsWith("/myApp.ear"));
        } finally {
            instance.cleanup();
        }

    }

    @Test
    public void testBraces() throws Exception {
        final File tmpDir = File.createTempFile("gfpayl{braces}", "tmp");
        tmpDir.delete();
        tmpDir.mkdir();

        try {
            final PayloadFilesManager instance = new PayloadFilesManager.Perm(tmpDir, null);
            final String originalPath = "some/path";
            final Part testPart = PayloadImpl.Part.newInstance("text/plain", originalPath, null, "random content");
            final URI result = instance.getOutputFileURI(testPart, testPart.getName());
            assertFalse(result.toASCIIString().contains("{"));
        } finally {
            PayloadFilesManagerTest.cleanup(tmpDir);
        }
    }

    @Test
    public void testDiffFilesFromSamePath() throws Exception {
        new CommonTempTest() {

            @Override
            protected void addParts(Outbound ob,
                    PayloadFilesManager instance) throws Exception {
                final Properties props = fileXferProps();
                ob.addPart("text/plain", "dir/x.txt", props, "sample data");
                ob.addPart("text/plain", "dir/y.txt", props, "y content in same temp dir as dir/x.txt");
            }

            @Override
            protected void checkResults(Inbound ib, PayloadFilesManager instance) throws Exception {
                List<File> files = instance.processParts(ib);
                File parent = null;
                boolean success = true;
                // Make sure all files have the same parent - since in this test
                // they all came from the same path originally.
                for (File f : files) {
                    ////System.out.println("  " + f.toURI().toASCIIString());
                    if (parent == null) {
                        parent = f.getParentFile();
                    } else {
                        success &= (parent.equals(f.getParentFile()));
                    }
                }
                assertTrue(success, "Failed because the temp files should have had the same parent");
            }

        }.run("diffFilesFromSamePath");
    }

    @Test
    public void testSameFilesInDiffPaths() throws Exception {
        new CommonTempTest() {

            @Override
            protected void addParts(Outbound ob, PayloadFilesManager instance) throws Exception {
                final Properties props = fileXferProps();
                ob.addPart("text/plain", "here/x.txt", props, "data from here");
                ob.addPart("text/plain", "elsewhere/x.txt", props, "data from elsewhere");
            }

            @Override
            protected void checkResults(Inbound ib, PayloadFilesManager instance) throws Exception {
                List<File> files = instance.processParts(ib);
                boolean success = true;
                String fileName = null;
                List<File> parents = new ArrayList<>();
                for (File f : files) {
                    if (fileName == null) {
                        fileName= f.getName();
                    } else {
                        success &= (f.getName().equals(fileName)) && ( ! parents.contains(f.getParentFile()));
                    }
                }
                assertTrue(success, "Failed because temp file names did not match or at least two had a parent in common");
            }

        }.run("sameFilesInDiffPaths");
    }

    @Test
    public void testLeadingSlashes() throws Exception {


        new CommonTempTest() {
            private static final String originalPath = "/here/x.txt";
            private final File originalFile = new File(originalPath);

            @Override
            protected void addParts(Outbound ob, PayloadFilesManager instance) throws Exception {
                final Properties props = fileXferProps();
                ob.addPart("application/octet-stream", originalPath, props, "data from here");
            }

            @Override
            protected void checkResults(Inbound ib, PayloadFilesManager instance) throws Exception {
                List<File> files = instance.processParts(ib);

                for (File f : files) {
                    assertNotEquals(originalFile, f,
                        "Temp file was created at original top-level path; should have been in a temp dir");
                }
            }
        }.run("testLeadingSlashes");
    }

    @Test
    public void testPathlessFile() throws Exception {
        new CommonTempTest() {

            @Override
            protected void addParts(Outbound ob, PayloadFilesManager instance) throws Exception {
                final Properties props = fileXferProps();
                ob.addPart("application/octet-stream", "flat.txt", props, "flat data");
                ob.addPart("text/plain", "x/other.txt", props, "one level down");
            }

            @Override
            protected void checkResults(Inbound ib, PayloadFilesManager instance) throws Exception {
                List<File> files = instance.processParts(ib);
                boolean success = true;
                for (File f : files) {
                    if (f.getName().equals("flat.txt")) {
                        success &= (f.getParentFile().equals(instance.getTargetDir()));
                    }
                }
                assertTrue(success, "Flat file was not deposited in top-level temp directory");
            }
        }.run("testPathlessFile");
    }

    @Test
    public void simplePermanentTransferTest() throws Exception {
        final String FILE_A_PREFIX = "";
        final String FILE_A_NAME = "fileA.txt";
        final String FILE_B_PREFIX = "x/y/z";
        final String FILE_B_NAME = "fileB.txt";

        final Set<File> desiredResults = new HashSet<>();

         // Create a directory into which we'll transfer some small files.
        final File origDir = File.createTempFile("pfm", "");
        origDir.delete();

        File targetDir = null;

        try {
            // Choose the directory into which we want the PayloadFilesManager to deliver the files.
            targetDir = File.createTempFile("tgt", "");
            targetDir.delete();
            targetDir.mkdir();

            origDir.mkdir();

            final File fileA = new File(origDir, FILE_A_NAME);
            writeFile(fileA, "This is File A", "and another line");
            desiredResults.add(new File(targetDir.toURI().resolve(FILE_A_PREFIX + FILE_A_NAME)));

            final File fileB = new File(origDir, FILE_B_NAME);
            desiredResults.add(new File(targetDir.toURI().resolve(FILE_B_PREFIX + FILE_B_NAME)));
            writeFile(fileB, "Here is File B", "which has an", "additional line");


            new CommonPermTest() {

                @Override
                protected CommonPermTest init(File targetDir) {
                    super.init(targetDir);

                    return this;
                }

                @Override
                protected void addParts(Outbound ob, PayloadFilesManager instance) throws Exception {
                    ob.attachFile(
                            "application/octet-stream",
                            URI.create(FILE_A_PREFIX + fileA.getName()),
                            "test-xfer",
                            fileA);
                    ob.attachFile(
                            "text/plain",
                            URI.create(FILE_B_PREFIX + fileB.getName()),
                            "test-xfer",
                            fileB);
                }

                @Override
                protected void checkResults(Inbound ib, PayloadFilesManager instance) throws Exception {
                    // Extract files to where we want them.
                    instance.processParts(ib);

                    final Set<File> missing = new HashSet<>();
                    for (File f : desiredResults) {
                        if ( ! f.exists()) {
                            missing.add(f);
                        }
                    }
                    assertEquals(Collections.EMPTY_SET, missing, "Unexpected missing files after extraction");
                }

                @Override
                protected void cleanup() {
                    for (File f : desiredResults) {
                        f.delete();
                    }
                    PayloadFilesManagerTest.cleanup(origDir);
                }

            }.init(targetDir).run("simplePermanentTransferTest");
        } finally {
            if (targetDir != null) {
                FileUtils.whack(targetDir);
            }
        }
    }

    @Test
    public void simplePermanentTransferAndRemovalTest() throws Exception {
        final String FILE_A_PREFIX = "";
        final String FILE_A_NAME = "fileA.txt";
        final String FILE_B_PREFIX = "x/y/z/";
        final String FILE_B_NAME = "fileB.txt";
        final String FILE_C_PREFIX = FILE_B_PREFIX;
        final String FILE_C_NAME = "fileC.txt";

        final Set<File> desiredPresent = new HashSet<>();
        final Set<File> desiredAbsent = new HashSet<>();

        // Create a directory into which we'll transfer some small files.
        final File origDir = File.createTempFile("pfm", "");
        origDir.delete();

        File targetDir = null;
        try {
            // Choose the directory into which we want the PayloadFilesManager to deliver the files.
            targetDir = File.createTempFile("tgt", "");
            targetDir.delete();
            targetDir.mkdir();

            origDir.mkdir();

            final File fileA = new File(origDir, FILE_A_NAME);
            writeFile(fileA, "This is File A", "and another line");
            desiredPresent.add(new File(targetDir.toURI().resolve(FILE_A_PREFIX + FILE_A_NAME)));

            // In this test result, we want file B to be absent after we transfer
            // it (with files A and C) and then use a second PayloadFilesManager
            // to request B's removal.
            final File fileB = new File(origDir, FILE_B_NAME);
            desiredAbsent.add(new File(targetDir.toURI().resolve(FILE_B_PREFIX + FILE_B_NAME)));
            writeFile(fileB, "Here is File B", "which has an", "additional line");

            final File fileC = new File(origDir, FILE_C_NAME);
            desiredPresent.add(new File(targetDir.toURI().resolve(FILE_C_PREFIX + FILE_C_NAME)));
            writeFile(fileC, "Here is File C", "which has an", "additional line", "even beyond what fileB has");


            new CommonPermTest() {

                @Override
                protected CommonPermTest init(File targetDir) {
                    super.init(targetDir);

                    return this;
                }

                @Override
                protected void addParts(Outbound ob, PayloadFilesManager instance) throws Exception {
                    ob.attachFile(
                            "application/octet-stream",
                            URI.create(FILE_A_PREFIX + fileA.getName()),
                            "test-xfer",
                            fileA);
                    ob.attachFile(
                            "text/plain",
                            URI.create(FILE_B_PREFIX + fileB.getName()),
                            "test-xfer",
                            fileB);
                    ob.attachFile(
                            "text/plain",
                            URI.create(FILE_C_PREFIX + fileC.getName()),
                            "test-xfer",
                            fileC);
                }

                @Override
                protected void checkResults(Inbound ib, PayloadFilesManager instance) throws Exception {
                    // Extract files to where we want them.
                    instance.processParts(ib);

                    // Now ask another PayloadFilesManager to remove one of the just-extracted files.
                    Payload.Outbound ob = PayloadImpl.Outbound.newInstance();
                    ob.requestFileRemoval(
                            URI.create(FILE_B_PREFIX + FILE_B_NAME),
                            "removeTest" /* dataRequestName */,
                            null /* props */);

                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ob.writeTo(baos);
                    baos.close();

                    final PayloadFilesManager remover = new PayloadFilesManager.Perm(instance.getTargetDir(), null);

                    final ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                    Payload.Inbound removerIB = PayloadImpl.Inbound.newInstance("application/zip", bais);

                    remover.processParts(removerIB);

                    final Set<File> missing = new HashSet<>();
                    for (File f : desiredPresent) {
                        if ( ! f.exists()) {
                            missing.add(f);
                        }
                    }
                    assertEquals(Collections.EMPTY_SET, missing, "Unexpected missing files after extraction");

                    final Set<File> unexpectedlyPresent = new HashSet<>();
                    for (File f : desiredAbsent) {
                        if (f.exists()) {
                            unexpectedlyPresent.add(f);
                        }
                    }
                    assertEquals(Collections.EMPTY_SET, unexpectedlyPresent,
                        "Unexpected files remain after removal request");
                }

                @Override
                protected void cleanup() {
                    for (File f : desiredPresent) {
                        f.delete();
                    }
                    PayloadFilesManagerTest.cleanup(origDir);
                }

            }.init(targetDir).run("simplePermanentTransferAndRemovalTest");
        } finally {
            if (targetDir != null) {
                FileUtils.whack(targetDir);
            }
        }
    }

    @Test
    public void simplePermanentDirWithNoSlashRemovalTest() throws Exception {
        final String DIR = "x/";
        final String FILE_A_PREFIX = DIR;
        final String FILE_A_NAME = "fileA.txt";
        final String DIR_WITH_NO_SLASH = "x";

        final Set<File> desiredAbsent = new HashSet<>();
        // Create a directory into which we'll copy some small files.
        final File origDir = File.createTempFile("pfm", "");
        origDir.delete();
        origDir.mkdir();

        final File dir = new File(origDir, DIR);
        dir.mkdir();

        final File fileA = new File(dir, FILE_A_NAME);

        writeFile(fileA, "This is FileA", "with two lines of content");

        File targetDir = null;

        try {
            // Choose the directory into which we want the PayloadFilesManager to deliver the files.
            targetDir = File.createTempFile("tgt", "");
            targetDir.delete();
            targetDir.mkdir();

            desiredAbsent.add(new File(targetDir.toURI().resolve(FILE_A_PREFIX + FILE_A_NAME)));

            new CommonPermTest() {

                @Override
                protected CommonPermTest init(File targetDir) {
                    super.init(targetDir);

                    return this;
                }

                @Override
                protected void addParts(Outbound ob, PayloadFilesManager instance) throws Exception {
                    ob.attachFile(
                            "application/octet-stream",
                            URI.create(DIR),
                            "test-xfer",
                            dir,
                            true /* isRecursive */);

                }

                @Override
                protected void checkResults(Inbound ib, PayloadFilesManager instance) throws Exception {
                    // Extract files to where we want them.
                    instance.processParts(ib);

                    // Now ask another PayloadFilesManager to remove a directory recursively.
                    Payload.Outbound ob = PayloadImpl.Outbound.newInstance();
                    ob.requestFileRemoval(
                            URI.create(DIR_WITH_NO_SLASH),
                            "removeTest" /* dataRequestName */,
                            null /* props */,
                            true /* isRecursive */);

                    ob.requestFileRemoval(
                            URI.create("notThere"),
                            "removeTest",
                            null,
                            true);
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ob.writeTo(baos);
                    baos.close();

                    final PayloadFilesManager remover = new PayloadFilesManager.Perm(instance.getTargetDir(), null);

                    final ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                    Payload.Inbound removerIB = PayloadImpl.Inbound.newInstance("application/zip", bais);

                    remover.processParts(removerIB);

                    final Set<File> unexpectedlyPresent = new HashSet<>();
                    for (File f : desiredAbsent) {
                        if (f.exists()) {
                            unexpectedlyPresent.add(f);
                        }
                    }
                    assertEquals(Collections.EMPTY_SET, unexpectedlyPresent,
                        "Unexpected files remain after removal request");
                }

                @Override
                protected void cleanup() {
                    for (File f : desiredAbsent) {
                        if (f.exists()) {
                            f.delete();
                        }
                    }
                    PayloadFilesManagerTest.cleanup(origDir);
                }

            }.init(targetDir).run("simplePermanentDirWithNoSlashRemovalTest");
        } finally {
            if (targetDir != null) {
                FileUtils.whack(targetDir);
            }
        }
    }

    @Test
    public void recursiveReplacementTest() throws Exception {

        // Populate the target directory with a subdirectory containing a file,
        // then replace the subdirectory via a replacement request in a Payload.
        final String DIR = "x/";

        final String FILE_A_PREFIX = DIR;
        final String FILE_A_NAME = "fileA.txt";

        final String FILE_B_PREFIX = DIR;
        final String FILE_B_NAME = "fileB.txt";

        final Set<File> desiredAbsent = new HashSet<>();
        final Set<File> desiredPresent = new HashSet<>();

        final File targetDir = File.createTempFile("tgt", "");
        targetDir.delete();
        targetDir.mkdir();

        final File dir = new File(targetDir, DIR);
        dir.mkdir();

        final File fileA = new File(dir, FILE_A_NAME);
        writeFile(fileA, "This is FileA", "with two lines of content");

        final File origDir = File.createTempFile("pfm", "");
        origDir.delete();
        origDir.mkdir();

        final File origSubDir = new File(origDir, DIR);
        origSubDir.mkdirs();
        final File fileB = new File(origSubDir, FILE_B_NAME);
        writeFile(fileB, "This is FileB", "with yet another", "line of content");


        try {
            desiredPresent.add(new File(targetDir.toURI().resolve(FILE_B_PREFIX + FILE_B_NAME)));
            desiredAbsent.add(new File(targetDir.toURI().resolve(FILE_A_PREFIX + FILE_A_NAME)));

            new CommonPermTest() {

                @Override
                protected CommonPermTest init(File targetDir) {
                    super.init(targetDir);

                    return this;
                }

                @Override
                protected void addParts(Outbound ob, PayloadFilesManager instance) throws Exception {
                    ob.requestFileReplacement(
                            "application/octet-stream",
                            URI.create(DIR),
                            "test-xfer",
                            null, /* props */
                            origSubDir,
                            true /* isRecursive */);

                }

                @Override
                protected void checkResults(Inbound ib, PayloadFilesManager instance) throws Exception {

                    listDir("After creation, before deletion", myTargetDir);
                    instance.processParts(ib);
                    listDir("After deletion" , myTargetDir);

                    final Set<File> unexpectedlyPresent = new HashSet<>();
                    for (File f : desiredAbsent) {
                        if (f.exists()) {
                            unexpectedlyPresent.add(f);
                        }
                    }
                    assertEquals(Collections.EMPTY_SET, unexpectedlyPresent,
                        "Unexpected files remain after replacement request");

                    final Set<File> unexpectedlyAbsent = new HashSet<>();
                    for (File f : desiredPresent) {
                        if ( ! f.exists()) {
                            unexpectedlyAbsent.add(f);
                        }
                    }
                    assertEquals(Collections.EMPTY_SET, unexpectedlyAbsent,
                        "Unexpected files absent after replacement request");
                }

                @Override
                protected void cleanup() {
                    for (File f : desiredAbsent) {
                        if (f.exists()) {
                            f.delete();
                        }
                    }
                    PayloadFilesManagerTest.cleanup(origDir);
                }

            }.init(targetDir).run("replacementTest");
        } finally {
            FileUtils.whack(targetDir);
        }
    }

    private static void listDir(final String title, final File dir) {
        listDir(dir);
    }

    private static void listDir(final File dir) {
        if (!dir.exists()) {
            return;
        }
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                listDir(f);
            }
        }
    }

    @Test
    public void simplePermanentTransferDirTest() throws Exception {
        final String DIR = "x/y/z/";
        final String FILE_PREFIX = "x/y/z/";
        final String FILE_NAME = "fileB.txt";

        final Set<File> desiredResults = new HashSet<>();

        // Create a directory into which we'll transfer some small files.
        final File origDir = File.createTempFile("pfm", "");
        origDir.delete();

        File targetDir = null;

        try {
            // Choose the directory into which we want the PayloadFilesManager to deliver the files.
            targetDir = File.createTempFile("tgt", "");
            targetDir.delete();
            targetDir.mkdir();

            origDir.mkdir();

            // Add the directory first, then add a file in the directory.
            // That will let us check to make sure the PayloadFileManager
            // set the lastModified time on the directory correctly.
            final URI dirURI = URI.create(DIR);
            final File dir = new File(origDir, DIR);
            dir.mkdirs();
            desiredResults.add(dir);

            final File file = new File(dir, FILE_NAME);
            desiredResults.add(new File(targetDir.toURI().resolve(FILE_PREFIX + FILE_NAME)));
            writeFile(file, "Here is the File", "which has an", "additional line");
            final long dirCreationTime = dir.lastModified();


            new CommonPermTest() {

                @Override
                protected CommonPermTest init(File targetDir) {
                    super.init(targetDir);

                    return this;
                }

                @Override
                protected void addParts(Outbound ob, PayloadFilesManager instance) throws Exception {
                    ob.attachFile(
                            "application/octet-stream",
                            URI.create(DIR),
                            "test-xfer",
                            dir);
                    ob.attachFile(
                            "text/plain",
                            URI.create(FILE_PREFIX + file.getName()),
                            "test-xfer",
                            file);
                }

                @Override
                protected void checkResults(Inbound ib, PayloadFilesManager instance) throws Exception {
                    // Extract files to where we want them.
                    instance.processParts(ib);

                    final URI extractedDirURI = myTargetDir.toURI().resolve(dirURI);
                    final File extractedDir = new File(extractedDirURI);
                    final long extractedLastModified = extractedDir.lastModified();

                    assertEquals(dirCreationTime, extractedLastModified,
                        "Directory lastModified mismatch after extraction");
                }

                @Override
                protected void cleanup() {
                    for (File f : desiredResults) {
                        f.delete();
                    }
                    PayloadFilesManagerTest.cleanup(origDir);
                }

            }.init(targetDir).run("simplePermanentTransferDirTest");
        } finally {
            if (targetDir != null) {
                FileUtils.whack(targetDir);
            }
        }
    }

    @Test
    public void simplePermanentRecursiveTransferTest() throws Exception {
        final String DIR = "x/";
        final String Y_SUBDIR = "y/";
        final String Z_SUBDIR = "z/";
        final String FILE_A_PREFIX = DIR + Y_SUBDIR;
        final String FILE_A_NAME = "fileA.txt";

        final String FILE_B_PREFIX = DIR + Y_SUBDIR + Z_SUBDIR;
        final String FILE_B_NAME = "fileB.txt";

        final Set<File> desiredResults = new HashSet<>();

        // Create a directory into which we'll copy some small files.
        final File origDir = File.createTempFile("pfm", "");
        origDir.delete();
        origDir.mkdir();

        final File dir = new File(origDir, DIR);
        dir.mkdir();
        final File ySubdir = new File(dir, Y_SUBDIR);
        ySubdir.mkdir();
        final File zSubdir = new File(dir, Y_SUBDIR + Z_SUBDIR);
        zSubdir.mkdir();

        final File fileA = new File(ySubdir, FILE_A_NAME);
        final File fileB = new File(zSubdir, FILE_B_NAME);

        writeFile(fileA, "This is FileA", "with two lines of content");
        writeFile(fileB, "This is FileB", "with a" , "third line");

        File targetDir = null;

        try {
            // Choose the directory into which we want the PayloadFilesManager to deliver the files.
            targetDir = File.createTempFile("tgt", "");
            targetDir.delete();
            targetDir.mkdir();

            desiredResults.add(new File(targetDir.toURI().resolve(FILE_A_PREFIX + FILE_A_NAME)));
            desiredResults.add(new File(targetDir.toURI().resolve(FILE_B_PREFIX + FILE_B_NAME)));

            // Add the original directory recursively.
            new CommonPermTest() {

                @Override
                protected CommonPermTest init(File targetDir) {
                    super.init(targetDir);
                    return this;
                }

                @Override
                protected void addParts(Outbound ob, PayloadFilesManager instance) throws Exception {
                    ob.attachFile(
                            "application/octet-stream",
                            URI.create("x/"),
                            "test-xfer",
                            dir,
                            true /* isRecursive */);
                }

                @Override
                protected void checkResults(Inbound ib, PayloadFilesManager instance) throws Exception {
                    // Extract files to where we want them.
                    final List<File> files = instance.processParts(ib);

                    final Set<File> missing = new HashSet<>();
                    for (File f : desiredResults) {
                        if ( ! f.exists()) {
                            missing.add(f);
                        }
                    }
                    assertEquals(Collections.EMPTY_SET, missing, "Unexpected missing files after extraction");
                }

                @Override
                protected void cleanup() {
                    for (File f : desiredResults) {
                        f.delete();
                    }
                    PayloadFilesManagerTest.cleanup(origDir);
                }

            }.init(targetDir).run("simplePermanentRecursiveTransferTest");
        } finally {
            if (targetDir != null) {
                FileUtils.whack(targetDir);
            }
        }
    }

    @Test
    public void simplePermanentRecursiveTransferDirOnlyTest() throws Exception {
        final String DIR = "x/";
        final String Y_SUBDIR = "y/";
        final String Z_SUBDIR = "z/";

        final Set<File> desiredResults = new HashSet<>();

        /*
         * Create a directory into which we'll copy some small files.
         */
        final File origDir = File.createTempFile("pfm", "");
        origDir.delete();
        origDir.mkdir();

        final File dir = new File(origDir, DIR);
        dir.mkdir();
        final File ySubdir = new File(dir, Y_SUBDIR);
        ySubdir.mkdir();
        final File zSubdir = new File(dir, Y_SUBDIR + Z_SUBDIR);
        zSubdir.mkdir();

        File targetDir = null;

        try {
            // Choose the directory into which we want the PayloadFilesManager to deliver the files.
            targetDir = File.createTempFile("tgt", "");
            targetDir.delete();
            targetDir.mkdir();

            desiredResults.add(new File(targetDir.toURI().resolve(DIR)));
            desiredResults.add(new File(targetDir.toURI().resolve(DIR + Y_SUBDIR)));
            desiredResults.add(new File(targetDir.toURI().resolve(DIR + Y_SUBDIR + Z_SUBDIR)));

            // Add the original directory recursively.
            new CommonPermTest() {

                @Override
                protected CommonPermTest init(File targetDir) {
                    super.init(targetDir);

                    return this;
                }

                @Override
                protected void addParts(Outbound ob, PayloadFilesManager instance) throws Exception {
                    ob.attachFile(
                            "application/octet-stream",
                            URI.create(DIR),
                            "test-xfer",
                            dir,
                            true /* isRecursive */);

                }

                @Override
                protected void checkResults(Inbound ib, PayloadFilesManager instance) throws Exception {
                    // Extract files to where we want them.
                    instance.processParts(ib);

                    final Set<File> missing = new HashSet<>();
                    for (File f : desiredResults) {
                        if ( ! f.exists()) {
                            missing.add(f);
                        }
                    }
                    assertEquals(Collections.EMPTY_SET, missing, "Unexpected missing files after extraction");
                }

                @Override
                protected void cleanup() {
                    for (File f : desiredResults) {
                        f.delete();
                    }
                    PayloadFilesManagerTest.cleanup(origDir);
                }

            }.init(targetDir).run("simplePermanentRecursiveTransferDirOnlyTest");
        } finally {
            if (targetDir != null) {
                FileUtils.whack(targetDir);
            }
        }
    }

    @Test
    public void simpleTempRecursiveTransferDirOnlyTest() throws Exception {
        final String DIR = "x/";
        final String Y_SUBDIR = "y/";
        final String Z_SUBDIR = "z/";

        final List<String> desiredResultsNamePrefixes = new ArrayList<>();

        /*
         * Create a directory into which we'll copy some small files.
         */
        final File origDir = File.createTempFile("pfm", "");
        origDir.delete();
        origDir.mkdir();

        final File dir = new File(origDir, DIR);
        dir.mkdir();
        final File ySubdir = new File(dir, Y_SUBDIR);
        ySubdir.mkdir();
        final File zSubdir = new File(ySubdir, Z_SUBDIR);
        zSubdir.mkdir();

        File targetDir = null;

        try {
            // Choose the directory into which we want the PayloadFilesManager to deliver the files.
            targetDir = File.createTempFile("tgt", "");
            targetDir.delete();
            targetDir.mkdir();

            desiredResultsNamePrefixes.add("/x");
            desiredResultsNamePrefixes.add("/x/y");
            desiredResultsNamePrefixes.add("/x/y/z");

            // Add the original directory recursively.
            new CommonTempTest() {

                @Override
                protected void addParts(Outbound ob, PayloadFilesManager instance) throws Exception {
                    ob.attachFile(
                            "application/octet-stream",
                            URI.create(DIR),
                            "test-xfer",
                            dir,
                            true /* isRecursive */);
                }

                @Override
                protected void checkResults(Inbound ib, PayloadFilesManager instance) throws Exception {
                    // Extract files to where we want them.
                    final List<File> files = instance.processParts(ib);

                  checkNextFile:
                    for (File f : files) {

                        for (ListIterator<String> it = desiredResultsNamePrefixes.listIterator(); it.hasNext();) {
                            final String desiredPrefix = it.next().replace("/", File.separator);
                            if (f.getPath().contains(desiredPrefix)) {
                                it.remove();
                                continue checkNextFile;
                            }
                        }
                    }

                    assertEquals(Collections.EMPTY_LIST, desiredResultsNamePrefixes,
                        "Unexpected missing files after extraction");
                }

                @Override
                protected void doCleanup() {
                    PayloadFilesManagerTest.cleanup(origDir);
                }

            }.run("simpleTempRecursiveTransferDirOnlyTest");
        } finally {
            if (targetDir != null) {
                FileUtils.whack(targetDir);
            }
        }
    }

    private static void cleanup(final File... files) {
        boolean ok = true;
        for (File f : files) {
            /*
             * If this is a directory we've been asked to clean up then
             * clean it recursively.
             */
            if (f.isDirectory()) {
                if ( ! FileUtils.whack(f)) {
                    System.err.println("** Could not whack " + f.getAbsolutePath());
                    ok = false;
                }
            } else {
                if ( f.exists() && ! f.delete()) {
                    System.err.println("** Could not clean up " + f.getAbsolutePath());
                    ok = false;
                    f.deleteOnExit();
                }
            }
        }
        if ( ! ok) {
            new Exception().printStackTrace();
        }
    }

    private void writeFile(final File file, final String... content) throws IOException {
        try (PrintStream ps = new PrintStream(file, StandardCharsets.UTF_8)) {
            for (String s : content) {
                ps.println(s);
            }
        }
    }

    private Properties fileXferProps() {
        final Properties props = new Properties();
        props.setProperty("data-request-type", "file-xfer");
        return props;
    }

    private abstract class CommonTest {
        protected final static String payloadType = "application/zip";

        protected abstract void addParts(Payload.Outbound ob, PayloadFilesManager instance) throws Exception;

        protected abstract void checkResults(Payload.Inbound ib, PayloadFilesManager instance) throws Exception;

        protected abstract PayloadFilesManager instance() throws IOException;

        protected abstract void cleanup();

        public void run(String testName) throws Exception {
            File tempZipFile = null;

            //System.out.println(testName);


            try {
                tempZipFile = File.createTempFile("testzip", ".zip");
                Payload.Outbound ob = PayloadImpl.Outbound.newInstance();

                addParts(ob, instance());

                OutputStream os;
                ob.writeTo(os = new BufferedOutputStream(new FileOutputStream(tempZipFile)));
                os.close();

                Payload.Inbound ib = PayloadImpl.Inbound.newInstance(payloadType, new BufferedInputStream(new FileInputStream(tempZipFile)));

                checkResults(ib, instance());

                cleanup();
            } finally {
                if (tempZipFile != null) {
                    tempZipFile.delete();
                }
            }

        }
    }

    private abstract class CommonTempTest extends CommonTest {

        private final List<PayloadFilesManager.Temp> tempInstances = new ArrayList<>();

        @Override
        protected PayloadFilesManager instance() throws IOException {
            final PayloadFilesManager.Temp tempInstance = new PayloadFilesManager.Temp();
            tempInstances.add(tempInstance);
            return tempInstance;
        }

        @Override
        protected void cleanup() {
            doCleanup();
            for (PayloadFilesManager.Temp tempInstance : tempInstances) {
                tempInstance.cleanup();
            }
        }

        protected void doCleanup() {}
    }

    private abstract class CommonPermTest extends CommonTest {
        private PayloadFilesManager.Perm permInstance;
        protected File myTargetDir;

        protected CommonPermTest init(final File targetDir) {
            permInstance = new PayloadFilesManager.Perm(targetDir, null);
            myTargetDir = targetDir;
            return this;
        }

        @Override
        protected PayloadFilesManager instance() throws IOException {
            return permInstance;
        }
    }
}
