/*
 * Copyright (c) 2022, 2026 Contributors to the Eclipse Foundation
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

package org.glassfish.appclient.client.jws.boot;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.Vector;

import javax.swing.SwingUtilities;

import org.glassfish.main.jdke.cl.GlassfishUrlClassLoader;

import static org.glassfish.main.jdke.props.SystemProperties.setProperty;

/**
 * Alternate main class for ACC, used when launched by Java Web Start.
 * <p>
 * This class assigns security permissions needed by the app server code and
 * by the app client code, then starts the regular app client container.
 * <p>
 * Note that any logic this class executes that requires privileged access
 * must occur either:
 * - from a class in the signed jar containing this class, or
 * - after setPermissions has been invoked.
 * This is because Java Web Start grants elevated permissions only to the classes
 * in the appserv-jwsacc-signed.jar at the beginning. Only after setPermissions
 * has been invoked can other app server-provided code run with all permissions.
 *
 * @author tjquinn
 */
public class JWSACCMain implements Runnable {

    /** unpublished command-line argument conveying jwsacc information */
    private static final String JWSACC_ARGUMENT_PREFIX = "-jwsacc";

    private static final String JWSACC_EXIT_AFTER_RETURN = "ExitAfterReturn";

    private static final String JWSACC_FORCE_ERROR = "ForceError";

    private static final String JWSACC_KEEP_JWS_CLASS_LOADER = "KeepJWSClassLoader";

    private static final String JWSACC_RUN_ON_SWING_THREAD = "RunOnSwingThread";

    /**
     * request to exit the JVM upon return from the client - should be set (via
     * the -jwsacc command-line argument value) only for
     * command-line clients; otherwise it can prematurely end the JVM when
     * the GUI and other user work is continuing
     */
    private static boolean exitAfterReturn;

    /*
     *Normally the ACC is not run with the Java Web Start classloader as the
     *parent class loader because this causes problems loading dynamic stubs.
     *To profile performance, though, sometimes we need to keep the JWS
     *class loader as the parent rather than skipping it.
     */
    private static boolean keepJWSClassLoader;

    private static boolean runOnSwingThread;

    /** soapAuthenticationService for building the class loader and policy changes */
    private static ClassPathManager classPathManager;

    /** URLs for downloaded JAR files to be used in the class path */
    private static URL[] downloadedJarURLs;

    /** URLs for persistence-related JAR files for the class path and permissions */
    private static URL[] persistenceJarURLs;

    /** localizable strings */
    private static final ResourceBundle rb = ResourceBundle
        .getBundle(dotToSlash(JWSACCMain.class.getPackage().getName() + ".LocalStrings"));

    /** make the arguments passed to the constructor available to the main method */
    private final String[] args;

    /** Creates a new instance of JWSMain */
    public JWSACCMain(String[] args) {
        this.args = args;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            args = prepareJWSArgs(args);
            try {
                classPathManager = getClassPathManager();
                downloadedJarURLs = classPathManager.locateDownloadedJars();
                persistenceJarURLs = classPathManager.locatePersistenceJARs();

            } catch (Throwable thr) {
                throw new IllegalArgumentException(rb.getString("jwsacc.errorLocJARs"), thr);
            }

            /*
             *Make sure that the main ACC class is instantiated and run in the
             *same thread.  Java Web Start may not normally do so.
             */
            JWSACCMain jwsACCMain = new JWSACCMain(args);

            if (runOnSwingThread) {
                SwingUtilities.invokeAndWait(jwsACCMain);
            } else {
                jwsACCMain.run();
            }
            /*
             *Note that the app client is responsible for closing all GUI
             *components or the JVM will never exit.
             */
        } catch (Throwable thr) {
            thr.printStackTrace();
            System.exit(1);
        }
    }

    private static String dotToSlash(String orig) {
        return orig.replaceAll("\\.","/");
    }

    @Override
    public void run() {
        int exitValue = 0;
        try {
            File downloadedAppclientJarFile = findAppClientFileForJWSLaunch(getClass().getClassLoader());

            ClassLoader loader = prepareClassLoader(downloadedAppclientJarFile);

            /*
             *Set a property that the ACC will retrieve during a JWS launch
             *to locate the app client jar file.
             */
            setProperty("com.sun.aas.downloaded.appclient.jar", downloadedAppclientJarFile.getAbsolutePath(), true);

            Thread.currentThread().setContextClassLoader(loader);
            System.err.println("XXXX: JWSACCMain.run set CL to thread: " + loader);
            /*
             *Use the prepared class loader to load the ACC main method, prepare
             *the arguments to the constructor, and invoke the static main method.
             */
            Class<?> mainClass = Class.forName("com.sun.enterprise.appclient.MainWithModuleSupport",
                true /* initialize */, loader);
            Constructor<?> constr = mainClass.getDeclaredConstructor(String[].class, URL[].class);
            constr.newInstance(args, persistenceJarURLs);
        } catch(Throwable thr) {
            exitValue = 1;
            /*
             *Display the throwable and stack trace to System.err, then
             *display it to the user using the GUI dialog box.
             */
            System.err.println(rb.getString("jwsacc.errorLaunch"));
            System.err.println(thr.toString());
            thr.printStackTrace();
            ErrorDisplayDialog.showErrors(thr, rb);
        } finally {
            /*
             *If the user has requested, invoke System.exit as soon as the main
             *method returns.  Do so on the Swing event thread so the ACC
             *main can complete whatever it may be doing.
             */
            if (exitAfterReturn || (exitValue != 0)) {
                Runnable exit = new Runnable() {
                    private int statusValue;
                    @Override
                    public void run() {
                        System.out.printf("Exiting after return from client with status %1$d%n", statusValue);
                        System.exit(statusValue);
                    }

                    public Runnable init(int exitStatus) {
                        statusValue = exitStatus;
                        return this;
                    }
                }.init(exitValue);

                if (runOnSwingThread) {
                    SwingUtilities.invokeLater(exit);
                } else {
                    exit.run();
                }
            }
        }
    }

    /**
     *Process any command line arguments that are targeted for the
     *Java Web Start ACC main program (this class) as opposed to the
     *regular ACC or the client itself.
     *@param args the original command line arguments
     *@return command arguments with any handled by JWS ACC removed
     */
    private static String[] prepareJWSArgs(String[] args) {
        Vector<String> JWSACCArgs = new Vector<>();
        Vector<String> nonJWSACCArgs = new Vector<>();
        for (String arg : args) {
            if (arg.startsWith(JWSACC_ARGUMENT_PREFIX)) {
                JWSACCArgs.add(arg.substring(JWSACC_ARGUMENT_PREFIX.length()));
            } else {
                nonJWSACCArgs.add(arg);
            }
        }

        processJWSArgs(JWSACCArgs);
        return nonJWSACCArgs.toArray(new String[nonJWSACCArgs.size()]);
    }

    /**
     * Interpret the JWSACC arguments (if any) supplied on the command line.
     *
     * @param args the JWSACC arguments
     */
    private static void processJWSArgs(Vector<String> args) {
        for (String arg : args) {
            if (arg.equals(JWSACC_EXIT_AFTER_RETURN)) {
                exitAfterReturn = true;
            } else if (arg.equals(JWSACC_FORCE_ERROR)) {
                throw new RuntimeException("Forced error - testing only");
            } else if (arg.equals(JWSACC_KEEP_JWS_CLASS_LOADER)) {
                keepJWSClassLoader = true;
            } else if (arg.equals(JWSACC_RUN_ON_SWING_THREAD)) {
                runOnSwingThread = true;
            }
        }
    }

    /**
     * Locates the first free policy.url.x setting.
     *
     * @return the int value for the first unused policy setting
     */
    public static int firstFreePolicyIndex() {
        int i = 0;
        String propValue;
        do {
            propValue = java.security.Security.getProperty("policy.url." + String.valueOf(++i));
        } while ((propValue != null) && (!propValue.equals("")));

        return i;
    }


    /**
     * Create the class loader for loading code from the unsigned downloaded
     * app server jars.
     * <p>
     * During a Java Web Start launch the ACC will be run under this class loader.
     * Otherwise the JNLPClassLoader will load any stub classes that are
     * packaged at the top-level of the generated app client jar file. (It can
     * see them because it downloaded the gen'd app client jar, and therefore
     * includes the downloaded jar in its class path. This allows it to see the
     * classes at the top level of the jar but does not automatically let it see
     * classes in the jars nested within the gen'd app client jar. As a result,
     * the JNLPClassLoader would be the one to try to define the class for a
     * web services stub, for instance. But the loader will not be able to find
     * other classes and interfaces needed to completely define the class -
     * because these are in the jars nested inside the gen'd app client jar. So
     * the attempt to define the class would fail.
     *
     * @param downloadedAppclientJarFile the app client jar file
     * @return the class loader
     */
    private static ClassLoader prepareClassLoader(File downloadedAppclientJarFile) {
        return new GlassfishUrlClassLoader("JWS-ACC", downloadedJarURLs, classPathManager.getParentClassLoader());
    }


    /*
     * Returns the jar that contains the specified resource.
     * @param target entry name to look for
     * @param loader the class loader to use in finding the resource
     * @return File object for the jar or directory containing the entry
     */
    private static File findContainingJar(String target, ClassLoader loader) throws Exception {
        File result = null;
        /*
         * Use the specified class loader to find the resource.
         */
        URL resourceURL = loader.getResource(target);
        if (resourceURL != null) {
            result = classPathManager.findContainingJar(resourceURL);
        }
        return result;
    }


    /**
     * Locate the app client jar file during a Java Web Start launch.
     *
     * @param loader the class loader to use in searching for the descriptor entries
     * @return File object for the client jar file
     * @throws Exception if the loader finds neither descriptor
     */
    private File findAppClientFileForJWSLaunch(ClassLoader loader) throws Exception {
        /*
         * The downloaded jar should contain either META-INF/application.xml or
         * META-INF/application-client.xml. Look for either one and locate the
         * jar from the URL.
         */
        File containingJar = findContainingJar("META-INF/application.xml", loader);
        if (containingJar == null) {
            containingJar = findContainingJar("META-INF/application-client.xml", loader);
        }
        if (containingJar == null) {
            throw new IllegalArgumentException(
                "Could not locate META-INF/application.xml or META-INF/application-client.xml");
        }
        return containingJar;
    }


    /**
     * Return the class path manager appropriate to the current version.
     *
     * @return the correct type of ClassPathManager
     */
    public static ClassPathManager getClassPathManager() {
        return ClassPathManager.getClassPathManager(keepJWSClassLoader);
    }
}
