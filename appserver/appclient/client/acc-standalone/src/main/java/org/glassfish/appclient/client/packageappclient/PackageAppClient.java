/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 * Copyright (c) 1997, 2021 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.appclient.client.packageappclient;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipException;

import static java.util.jar.Attributes.Name.CLASS_PATH;

/**
 * Creates a JAR file containing the runtime bits required to run app clients remotely, on a system without a full
 * GlassFish installation.
 *
 * <p>
 * The resulting file will contain:
 * <ul>
 *   <li>the stand-alone ACC JAR file (the one containing this class)
 *   <li>all JARs listed in the Class-Path of this JAR's manifest
 *   <li>the contents of the ${installDir}/lib/dtds and schemas directories (for local resolution of schemas and DTDs on
 *       the remote client system)
 *   <li>the specified sun-acc.xml file (the one from domains/domain1/config if unspecified)
 *   <li>the handful of other config files to which sun-acc.xml refers
 * </ul>
 *
 * <p>
 * Optional command-line options:
 * <ul>
 *   <li><code>-output path-to-output-file</code> (default: ${installDir}/lib/appclient/appclient.jar)
 *   <li><code>-xml path-to-sun-acc.xml-config-file</code> (default: ${installDir}/domains/domain1/config/sun-acc.xml)
 *   <li><code>-verbose</code> reports progress and errors that are non-fatal
 * </ul>
 *
 * @author tjquinn
 */
public class PackageAppClient {

    private final static String OUTPUT_PREFIX = "appclient/";
    private final static String GLASSFISH_LIB = "glassfish/lib";
    private final static String GLASSFISH_BIN = "glassfish/bin";
    private final static String GLASSFISH_CONFIG = "glassfish/config";
    private final static String MQ_LIB = "mq/lib";
    private final static String DOMAIN_1_CONFIG = "glassfish/domains/domain1/config";
    private final static String INDENT = "  ";
    private final static String ACC_CONFIG_FILE_DEFAULT = "/glassfish-acc.xml";
    private final static String ACC_CONFIG_FILE_DEFAULT_OLD = "/sun-acc.xml";

    /* DIRS_TO_COPY entries are all relative to the installation directory */
    private final static String[] DIRS_TO_COPY = new String[] {
        GLASSFISH_LIB + "/dtds",
        GLASSFISH_LIB + "/schemas",
        GLASSFISH_LIB + "/appclient",
        GLASSFISH_LIB + "/bootstrap",
        };

    /* default sun-acc.xml is relative to the installation directory */
    private final static String DEFAULT_ACC_XML = DOMAIN_1_CONFIG + ACC_CONFIG_FILE_DEFAULT;
    private final static String OLD_ACC_XML = DOMAIN_1_CONFIG + ACC_CONFIG_FILE_DEFAULT_OLD;

    private final static String[] DEFAULT_ACC_CONFIG_FILES = { DEFAULT_ACC_XML, OLD_ACC_XML };

    private final static String IMQJMSRA_APP = GLASSFISH_LIB + "/install/applications/jmsra/imqjmsra.jar";

    private final static String IMQ_JAR = MQ_LIB + "/imq.jar";
    private final static String IMQADMIN_JAR = MQ_LIB + "/imqadmin.jar";
    private final static String IMQUTIL_JAR = MQ_LIB + "/imqutil.jar";
    private final static String FSCONTEXT_JAR = MQ_LIB + "/fscontext.jar";

    private final static String WIN_SCRIPT = GLASSFISH_BIN + "/appclient.bat";
    private final static String WIN_JS = GLASSFISH_BIN + "/appclient.js";
    private final static String NONWIN_SCRIPT = GLASSFISH_BIN + "/appclient";

    private final static String ASENV_CONF = GLASSFISH_CONFIG + "/asenv.conf";
    private final static String ASENV_BAT = GLASSFISH_CONFIG + "/asenv.bat";

    private final static String[] SINGLE_FILES_TO_COPY = {
        IMQJMSRA_APP,
        IMQ_JAR,
        IMQADMIN_JAR,
        IMQUTIL_JAR,
        FSCONTEXT_JAR,
        WIN_SCRIPT,
        WIN_JS,
        NONWIN_SCRIPT,
        ASENV_CONF,
        ASENV_BAT };

    /* default output file */
    private final static String DEFAULT_OUTPUT_PATH = GLASSFISH_LIB + "/appclient.jar";

    private boolean isVerbose;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            new PackageAppClient().run(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void run(String[] args) throws URISyntaxException, IOException {
        isVerbose = isVerboseOutputLevel(args);
        File thisJarFile = findCurrentJarFile();
        File installDir = findInstallDir(thisJarFile);
        File modulesDir = new File(installDir.toURI().resolve("glassfish/modules/"));

        // Write the new JAR to a temp file in the install directory. Then we can simply rename
        // the file to the correct name.
        // (Rename does not work on Windows systems across volumes.)
        File tempFile = File.createTempFile("appc", ".tmp", installDir);
        File outputFile = chooseOutputFile(installDir, args);

        File[] configFiles = chooseConfigFiles(installDir, args);
        String[] classPathElements = getJarClassPath(thisJarFile).split(" ");

        try (JarOutputStream os = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)))) {
            // Add this JAR file to the output.
            addFile(os, installDir.toURI(), thisJarFile.toURI(), tempFile, "");

            // JARs listed in the Class-Path are all relative to the modules directory so resolve
            // each Class-Path entry against the modules directory.
            for (String classPathElement : classPathElements) {
                File classPathJAR = new File(modulesDir, classPathElement);
                addFile(os, installDir.toURI(), modulesDir.toURI().resolve(classPathJAR.toURI()), tempFile, "");
            }

            // The directories to copy are all relative to the installation directory, so resolve
            // them against the installDir file.
            for (String dirToCopy : DIRS_TO_COPY) {
                addDir(os, installDir.toURI(), installDir.toURI().resolve(dirToCopy), tempFile, "");
            }

            for (String singleFileToCopy : SINGLE_FILES_TO_COPY) {
                addFile(os, installDir.toURI(), installDir.toURI().resolve(singleFileToCopy), tempFile, "");
            }

            // The glassfish-acc.xml file and sun-acc.xml files.
            for (File configFile : configFiles) {
                addFile(os, installDir.toURI(), configFile.toURI(), tempFile, "");
            }
        }
        placeFile(tempFile, outputFile);
    }

    private void placeFile(final File tempFile, final File outputFile) {
        if (outputFile.exists()) {
            if (!outputFile.delete()) {
                throw new RuntimeException("Stopping; could not delete the existing output file " + outputFile.getAbsolutePath());
            }
            System.out.println("Replacing " + outputFile.getAbsolutePath());
        } else {
            System.out.println("Creating" + outputFile.getAbsolutePath());
        }

        if (isVerbose) {
            System.out
                .println("Moving temp file " + tempFile.getAbsolutePath() + " to " + outputFile.getAbsolutePath());
        }

        if (!tempFile.renameTo(outputFile)) {
            throw new RuntimeException(
                "Error renaming temp file " + tempFile.getAbsolutePath() + " to " + outputFile.getAbsolutePath());
        }
    }

    /**
     * Adds all endorsed JAR files in the app server's endorsed directory to the output JAR file.
     *
     * @param os
     * @param installDirURI
     * @param endorsedDirURI
     * @param outputFile
     * @throws java.io.IOException
     */
    private void addEndorsedFiles(JarOutputStream os, URI installDirURI, URI endorsedDirURI, File outputFile) throws IOException {
        addDir(os, installDirURI, endorsedDirURI, pathname -> pathname.getName().endsWith(".jar") || pathname.isDirectory(), outputFile, "");
    }

    /**
     * Adds a single file to the output JAR, avoiding the output file itself (if by some chance the user is creating the
     * output JAR in one of the places from which we gather input).
     *
     * @param os
     * @param installDirURI
     * @param absoluteURIToAdd
     * @param outputFile
     * @throws java.io.IOException
     */
    private void addFile(JarOutputStream os, URI installDirURI, URI absoluteURIToAdd, File outputFile, String indent) throws IOException {
        try {
            if (isVerbose) {
                System.err.println(indent + "Adding directory " + absoluteURIToAdd);
            }

            File fileToCopy = new File(absoluteURIToAdd);
            if (fileToCopy.equals(outputFile)) {
                return;
            }

            JarEntry entry = new JarEntry(OUTPUT_PREFIX + installDirURI.relativize(absoluteURIToAdd).toString());
            try {
                /*
                 * Some modules in the GlassFish build are marked as optional dependencies, and the resulting JARs are not included in
                 * the GlassFish distribution. But the JAR file names do appear in the maven-generated Class-Path for the stand-alone
                 * client JAR library which this tool uses to find out what JARs to include in the generated appclient.jar file. So, if
                 * the Class-Path specifies a file we cannot find we keep going -- silently.
                 */
                if (!new File(absoluteURIToAdd).exists()) {
                    if (isVerbose) {
                        System.err.println(indent + "Error locating file "
                            + new File(absoluteURIToAdd).getAbsolutePath() + "; continuing");
                    }
                    return;
                }
                os.putNextEntry(entry);
                if (new File(absoluteURIToAdd).isFile()) {
                    copyFileToStream(os, absoluteURIToAdd);
                }
                os.closeEntry();

            } catch (ZipException e) {
                /*
                 * Probably duplicate entry. Keep going after logging the error.
                 */
                if (isVerbose) {
                    System.err.println(
                        indent + "Continuing after ZipException when adding a file: " + e.getLocalizedMessage());
                }
            } catch (FileNotFoundException ignore) {
            }
        } catch (Exception ex) {
            throw new IOException(absoluteURIToAdd.toASCIIString(), ex);
        }
    }

    /**
     * Add all the files from the specified directory, recursing to lower-level subdirectories.
     *
     * @param os
     * @param installDirURI
     * @param absoluteDirURIToAdd
     * @param outputFile
     * @throws java.io.IOException
     */
    private void addDir(JarOutputStream os, URI installDirURI, URI absoluteDirURIToAdd, File outputFile, String indent) throws IOException {
        addDir(
            os, installDirURI, absoluteDirURIToAdd,
            null /* with null filter File.listFiles accepts all files and directories */,
            outputFile, indent);
    }

    /**
     * Add all files from a directory that meet the criteria of the specified file filter.
     *
     * @param os
     * @param installDirURI
     * @param absoluteDirURIToAdd
     * @param fileFilter
     * @param outputFile
     * @throws java.io.IOException
     */
    private void addDir(JarOutputStream os, URI installDirURI, URI absoluteDirURIToAdd, FileFilter fileFilter, File outputFile, String indent) throws IOException {
        File dirFile = new File(absoluteDirURIToAdd);
        File[] matchingFiles = dirFile.listFiles(fileFilter);
        if (matchingFiles == null) {
            /*
             * The file does not exist or is not a directory. This could happen for example if the user has not created the endorsed
             * directory.
             */
            return;
        }

        if (isVerbose) {
            System.err.println(indent + "Adding dir " + dirFile.getAbsolutePath());
        }

        for (File fileToAdd : matchingFiles) {
            if (fileToAdd.isFile()) {
                addFile(os, installDirURI, fileToAdd.toURI(), outputFile, indent + INDENT);
            } else if (fileToAdd.isDirectory()) {
                addDir(os, installDirURI, fileToAdd.toURI(), outputFile, indent + INDENT);
            }
        }
    }

    /**
     * Copies the contents of a given file to the output stream.
     *
     * @param os
     * @param uriToCopy
     * @throws java.io.FileNotFoundException
     * @throws java.io.IOException
     */
    private void copyFileToStream(OutputStream os, URI uriToCopy) throws FileNotFoundException, IOException {
        File fileToCopy = new File(uriToCopy);
        try (InputStream is = new BufferedInputStream(new FileInputStream(fileToCopy))) {
            int bytesRead;
            byte[] buffer = new byte[4096];
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * Returns the File to create. The user can optionally specify the output file as <code>-output outFile</code> on the
     * command line. The default is <code>${installDir}/lib/appclient.jar</code>
     *
     * @param args command-line arguments
     * @return File for the file to create
     */
    private File chooseOutputFile(final File installDir, final String[] args) {
        return chooseFile("-output", DEFAULT_OUTPUT_PATH, installDir, args);
    }

    /**
     * Returns a file, either a default value or one explicitly set via a user-provided option on the command line.
     *
     * @param option
     * @param defaultRelativeURI
     * @param installDir
     * @param args
     * @return
     */
    private File chooseFile(final String option, final String defaultRelativeURI, final File installDir, final String[] args) {
        /*
         * Look for the option in the arguments.
         */
        String optionValue = argValue(option, args);
        if (optionValue != null) {
            return new File(optionValue);
        }

        return new File(installDir.toURI().resolve(defaultRelativeURI));
    }

    /**
     * Returns the user-specified or default *-acc.xml config files.
     *
     * @param installDir
     * @param args
     * @return
     */
    private File[] chooseConfigFiles(final File installDir, final String[] args) {
        String xmlArg = argValue("-xml", args);
        File[] files;
        if (xmlArg == null) {
            files = new File[DEFAULT_ACC_CONFIG_FILES.length];
            int slot = 0;
            for (String s : DEFAULT_ACC_CONFIG_FILES) {
                files[slot++] = new File(installDir.toURI().resolve(s));
            }
        } else {
            File userSpecifiedFile = new File(xmlArg);
            files = new File[] { userSpecifiedFile };
            if (!userSpecifiedFile.exists()) {
                System.err.println("The XML configuration file " + userSpecifiedFile.getAbsolutePath()
                    + " does not exist; continuing but output is incomplete");
            }

        }
        return files;

    }

    private File findInstallDir(File currentJarFile) {
        return currentJarFile.getParentFile().getParentFile().getParentFile();
    }

    private boolean isVerboseOutputLevel(String[] args) {
        for (String arg : args) {
            if (arg.equals("-verbose")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the value for the specified option.
     *
     * @param option option name to search the args for
     * @param args   args to search
     * @return token after the specified optionon the command line, if any;
     * @throws IllegalArgumentException if there is no value on the command line for the specified option
     */
    private String argValue(String option, String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(option)) {
                if (i + 1 < args.length) {
                    return args[i + 1];
                }

                throw new IllegalArgumentException(option);
            }
        }

        return null;
    }

    /**
     * Returns a File object for the JAR file that contains this class.
     *
     * @return
     * @throws java.net.URISyntaxException
     */
    private File findCurrentJarFile() throws URISyntaxException {
        URI thisJarURI = getClass().getProtectionDomain()
                                   .getCodeSource()
                                   .getLocation()
                                   .toURI();

        URI thisJarFileBasedURI = (thisJarURI.getScheme().startsWith("jar")) ?
            URI.create("file:" + thisJarURI.getRawSchemeSpecificPart()) :
            thisJarURI;

        /*
         * One getParent gives the modules directory; the second gives the installation directory.
         */
        return new File(thisJarFileBasedURI);
    }

    /**
     * Returns the Class-Path setting for the specified File, presumed to be a JAR.
     *
     * @param currentJarFile
     * @return
     * @throws java.io.IOException
     */
    private String getJarClassPath(File currentJarFile) throws IOException {
        try (JarFile jarFile = new JarFile(currentJarFile)) {
            return jarFile.getManifest()
                          .getMainAttributes()
                          .getValue(CLASS_PATH);
        }
    }
}
