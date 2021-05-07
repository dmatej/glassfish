/*
 * Copyright (c) 2009, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.deployment.deploy.shared;

import com.sun.enterprise.deployment.deploy.shared.InputJarArchive.CollectionWrappedEnumeration;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.glassfish.api.deployment.archive.ReadableArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Tim
 */
public class InputJarArchiveTest {

    private static final String NESTED_JAR_ENTRY_NAME = "nested/archive.jar";

    public InputJarArchiveTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of getArchiveSize method, of class InputJarArchive.
     */
    @Test
    public void testCollectionWrappedEnumerationSimple() {
        System.out.println("collection wrapped enumeration - simple iterator test");
        final Enumeration<String> e = testEnum();

        CollectionWrappedEnumeration<String> cwe = new CollectionWrappedEnumeration<String>(
                new CollectionWrappedEnumeration.EnumerationFactory() {

            @Override
            public Enumeration enumeration() {
                return e;
            }

        });

        ArrayList<String> answer = new ArrayList<String>(cwe);
        assertEquals("resulting array list != original", testStringsAsArrayList(), answer);
    }

    @Test
    public void testCollectionWrappedEnumerationInitialSize() {
        System.out.println("collection wrapped enumeration - initial size() call");
        final Enumeration<String> e = testEnum();

        CollectionWrappedEnumeration<String> cwe = new CollectionWrappedEnumeration<String>(
                new CollectionWrappedEnumeration.EnumerationFactory() {

            @Override
            public Enumeration enumeration() {
                return e;
            }

        });

        int size = cwe.size();
        ArrayList<String> answer = new ArrayList<String>(cwe);
        assertEquals("array list of size " + size + " after initial size != original", testStringsAsArrayList(), answer);
    }

    @Test
    public void testCollectionWrappedEnumerationMiddleSize() {
        System.out.println("collection wrapped enumeration - middle size() call");

        CollectionWrappedEnumeration<String> cwe = new CollectionWrappedEnumeration<String>(
                new CollectionWrappedEnumeration.EnumerationFactory() {

            @Override
            public Enumeration enumeration() {
                return testEnum();
            }

        });

        ArrayList<String> answer = new ArrayList<String>();
        Iterator<String> it = cwe.iterator();

        answer.add(it.next());
        answer.add(it.next());
        answer.add(it.next());
        int size = cwe.size();
        answer.add(it.next());
        answer.add(it.next());

        assertEquals("array list of size " + size + " after middle size call != original", testStringsAsArrayList(), answer);
    }

    @Test
    public void testCollectionWrappedEnumerationEndSize() {
        System.out.println("collection wrapped enumeration - end size() call");

        CollectionWrappedEnumeration<String> cwe = new CollectionWrappedEnumeration<String>(
                new CollectionWrappedEnumeration.EnumerationFactory() {

            @Override
            public Enumeration enumeration() {
                return testEnum();
            }

        });

        List<String> answer = new ArrayList<String>();
        Iterator<String> it = cwe.iterator();

        answer.add(it.next());
        answer.add(it.next());
        answer.add(it.next());
        answer.add(it.next());
        answer.add(it.next());
        int size = cwe.size();

        assertEquals("array list of size " + size + " after middle size call != original", testStringsAsArrayList(), answer);
    }

    private ReadableArchive getArchiveForTest() throws IOException {
        File tempJAR = createTestJAR();
        final InputJarArchive arch = new InputJarArchive();
        arch.open(tempJAR.toURI());
        return arch;
    }

    private void retireArchive(final ReadableArchive arch) throws IOException {
        arch.close();
        final File tempJAR = new File(arch.getURI().getSchemeSpecificPart());
        tempJAR.delete();
    }

    @Test
    public void testTopLevelDirEntryNamesForInputJarArchive() {
        try {
            System.out.println("top-level directory entry names in InputJarArchive");
            final ReadableArchive arch = getArchiveForTest();
            final Set<String> returnedNames = new HashSet<String>(arch.getDirectories());
            assertEquals("Returned top-level directories do not match expected", testJarTopLevelDirEntryNames(), returnedNames);
            retireArchive(arch);
        } catch (IOException ex) {
            ex.printStackTrace(System.out);
            fail("Error during test");
        }
    }

    @Test
    public void testNonDirEnryNames() {
        try {
            System.out.println("non-directory entry names in InputJarArchive");
            final ReadableArchive arch = getArchiveForTest();
            final Set<String> returnedNames = new HashSet<String>(setFromEnumeration(arch.entries()));
            assertEquals("Returned non-directory entry names do not match expected", testStandAloneArchiveJarNonDirEntryNames(), returnedNames);
            retireArchive(arch);
        } catch (IOException ex) {
            ex.printStackTrace(System.out);
            fail("Error during test");
        }
    }

    @Test
    public void testNestedTopLevelDirEntryNames() {
        try {
            System.out.println("nested top-level directory entry names in InputJarArchive");
            final ReadableArchive arch = getArchiveForTest();
            ReadableArchive subArchive = arch.getSubArchive(NESTED_JAR_ENTRY_NAME);

            final Set<String> returnedNames = new HashSet<String>(subArchive.getDirectories());
            assertEquals("Returned nested top-level directories do not match expected",
                    testJarTopLevelDirEntryNames(), returnedNames);
            retireArchive(arch);
        } catch (IOException ex) {
            ex.printStackTrace(System.out);
            fail("Error during test");
        }
    }

    @Test
    public void testNestedNonDirEntryNames() {
        try {
            System.out.println("nested non-directory entry names in InputJarArchive");
            final ReadableArchive arch = getArchiveForTest();
            ReadableArchive subArchive = arch.getSubArchive(NESTED_JAR_ENTRY_NAME);

            final Set<String> returnedNames = new HashSet<String>(
                    setFromEnumeration(subArchive.entries()));
            assertEquals("Returned nested non-directories do not match expected",
                    testSubArchiveNonDirEntryNames(), returnedNames);
            retireArchive(arch);
        } catch (IOException ex) {
            ex.printStackTrace(System.out);
            fail("Error during test");
        }
    }

    private File createTestJAR() throws IOException {
        final File tempJAR = File.createTempFile("InputJarArchive", ".jar");
        tempJAR.deleteOnExit();
        final Manifest mf = new Manifest();
        Attributes mainAttrs = mf.getMainAttributes();
        mainAttrs.put(Name.MANIFEST_VERSION, "1.0");

        final JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(tempJAR)),
                mf);


        for (String entryName : testJarEntryNames()) {
            final JarEntry entry = new JarEntry(entryName);
            jos.putNextEntry(entry);
            jos.closeEntry();
            // Note that these entries in the test JAR are empty - we just need to test the names
        }

        /*
         * Now create a nested JAR within the main test JAR.  For simplicity
         * use the same entry names as the outer JAR.
         */
        final JarEntry nestedJarEntry = new JarEntry(NESTED_JAR_ENTRY_NAME);
        jos.putNextEntry(nestedJarEntry);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final JarOutputStream nestedJOS = new JarOutputStream(baos);

        for (String entryName : testJarEntryNames()) {
            final JarEntry nestedEntry = new JarEntry(entryName);
            nestedJOS.putNextEntry(nestedEntry);
            nestedJOS.closeEntry();
        }

        nestedJOS.close();
        jos.write(baos.toByteArray());
        jos.closeEntry();


        jos.close();
        return tempJAR;
    }


    private static Enumeration<String> testEnum() {
        Enumeration<String> e = Collections.enumeration(testStringsAsArrayList());

        return e;
    }

    private static List<String> testStringsAsArrayList() {
        return Arrays.asList("one","two","three","four","five");
    }

    private static List<String> testJarEntryNames() {
        return Arrays.asList(
                "topLevelNonDir",
                "topLevelDir/",
                "topLevelDir/secondLevelNonDir",
                "topLevelDir/secondLevelDir/",
                "topLevelDir/secondLevelDir/thirdLevelNonDir");
    }

    private static Set<String> testJarTopLevelDirEntryNames() {
        return new HashSet(Arrays.asList(
                "topLevelDir/"));
    }

    private static Set<String> testSubArchiveNonDirEntryNames() {
        return new HashSet(Arrays.asList(
                "topLevelNonDir", "topLevelDir/secondLevelNonDir",
                "topLevelDir/secondLevelDir/thirdLevelNonDir"));
    }

    private static Set<String> testStandAloneArchiveJarNonDirEntryNames() {
        final Set<String> result = testSubArchiveNonDirEntryNames();
        result.add(NESTED_JAR_ENTRY_NAME);
        return result;
    }

    private static <T> Set<T> setFromEnumeration(final Enumeration<T> e) {
        final Set<T> result = new HashSet<T>();
        while (e.hasMoreElements()) {
            result.add(e.nextElement());
        }
        return result;
    }
}
