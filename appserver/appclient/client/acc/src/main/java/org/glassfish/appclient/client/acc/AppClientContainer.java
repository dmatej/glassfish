/*
 * Copyright (c) 2022, 2025 Contributors to the Eclipse Foundation.
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

package org.glassfish.appclient.client.acc;

import com.sun.appserv.connectors.internal.api.ConnectorRuntime;
import com.sun.enterprise.container.common.spi.ManagedBeanManager;
import com.sun.enterprise.container.common.spi.util.ComponentEnvManager;
import com.sun.enterprise.container.common.spi.util.InjectionException;
import com.sun.enterprise.container.common.spi.util.InjectionManager;
import com.sun.enterprise.deployment.ApplicationClientDescriptor;
import com.sun.enterprise.deployment.PersistenceUnitDescriptor;
import com.sun.enterprise.deployment.ServiceReferenceDescriptor;
import com.sun.enterprise.security.webservices.client.ClientPipeCloser;
import com.sun.logging.LogDomains;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.security.auth.callback.CallbackHandler;
import javax.swing.SwingUtilities;

import org.apache.naming.resources.DirContextURLStreamHandlerFactory;
import org.glassfish.api.invocation.ComponentInvocation;
import org.glassfish.api.invocation.InvocationManager;
import org.glassfish.appclient.client.acc.config.AuthRealm;
import org.glassfish.appclient.client.acc.config.ClientCredential;
import org.glassfish.appclient.client.acc.config.MessageSecurityConfig;
import org.glassfish.appclient.client.acc.config.Property;
import org.glassfish.appclient.client.acc.config.Security;
import org.glassfish.appclient.client.acc.config.TargetServer;
import org.glassfish.embeddable.client.ApplicationClientContainer;
import org.glassfish.embeddable.client.UserError;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.persistence.jpa.PersistenceUnitLoader;
import org.jvnet.hk2.annotations.Service;
import org.omg.CORBA.NO_PERMISSION;
import org.xml.sax.SAXException;

import static org.glassfish.appclient.client.acc.AppClientContainer.ClientMainClassSetting.getClientMainClass;
import static org.glassfish.main.jdke.props.SystemProperties.setProperty;

/**
 * Embeddable Glassfish app client container (ACC).
 *
 * <p>
 * Allows Java programs to:
 * <ul>
 * <li>create a new builder for an ACC (see {@link #newBuilder} and {@link AppClientContainerBuilder}),
 * <li>optionally modify the configuration by invoking various builder methods,
 * <li>create an embedded instance of the ACC from the builder using {@link AppClientContainerBuilder#newContainer() },
 * <li>startClient the client using {@link #launch(String[])}, and
 * <li>stop the container using {@link #stop()}.
 * </ul>
 *
 * <p>
 * Each instance of the {@link TargetServer} class passed to the <code>newBuilder</code> method represents one server,
 * conveying its host and port number, which the ACC can use to "bootstrap" into the server-side ORB(s). The calling
 * program can request to use secured communication to a server by also passing an instance of the {@link Security}
 * configuration class when it creates the <code>TargetServer</code> object. Note that the caller prepares the
 * <code>TargetServer</code> array completely before passing it to one of the <code>newConfig</code> factory methods.
 * The <code>Builder</code> implementation does not override or augment the list of target servers using system property
 * values, property settings in the container configuration, etc. If such work is necessary to find additional target
 * servers the calling program should do it and prepare the array of <code>TargetServer</code> objects accordingly.
 *
 * <p>
 * The calling program also passes either a File or URI for the app client archive to be run or a Class object for the
 * main class to be run as an app client.
 *
 * <p>
 * After the calling program has created a new <code>AppClientContainer.Builder</code> instance it can set optional
 * information to control the ACC's behavior, such as
 * <ul>
 * <li>setting the authentication realm
 * <li>setting client credentials (and optionally setting an authentication realm in which the username and password are
 * valid)
 * <li>setting the callback handler class
 * <li>adding one or more {@link MessageSecurityConfig} objects
 * </ul>
 *
 * <p>
 * Once the calling program has used the builder to configure the ACC to its liking it invokes the builder's
 * <code>newContainer()</code> method. The return type is an <code>AppClientContainer</code>, and by the time
 * <code>newContainer</code> returns the <code>AppClientContainer</code> has invoked the app client's main method and
 * that method has returned to the ACC. Any new thread the client creates or any GUI work it triggers on the AWT
 * dispatcher thread continues independently from the thread that called <code>newContainer</code>.
 *
 * <p>
 * If needed, the calling program can invoke the <code>stop</code> method on the <code>AppClientContainer</code> to shut
 * down the ACC-provided services. Invoking <code>stop</code> does not stop any threads the client might have started.
 * If the calling program needs to control such threads it should do so itself, outside the
 * <code>AppClientContainer</code> API. If the calling program does not invoke <code>stop</code> the ACC will clean up
 * automatically as the JVM exits.
 *
 * <p>
 * A simple case in which the calling program provides an app client JAR file and a single TargetServer might look like
 * this:
 * <p>
 * <code>
 *
 * import org.glassfish.appclient.client.acc.AppClientContainer;<br>
 * import org.glassfish.appclient.client.acc.config.TargetServer;<br>
 * <br>
 * AppClientContainerBuilder builder = AppClientContainer.newBuilder(<br>
 * &nbsp;&nbsp;    new TargetServer("localhost", 3700));<br>
 * <br>
 * AppClientContainer acc = builder.newContainer(new File("myAC.jar").toURI());<br>
 * <br>
 * </code>(or, alternatively)<code><br>
 * <br>
 * AppClientContainer acc = builder.newContainer(MyClient.class);<br>
 * <br>
 * <br</code>Then, <code><br>
 * <br>
 * acc.startClient(clientArgs);<br>
 * // The newContainer method returns as soon as the client's main method returns,<br>
 * // even if the client has started another thread or is using the AWT event<br>
 * // dispatcher thread
 * <br>
 * // At some later point, the program can synchronize with the app client in<br>
 * // a user-specified way at which point it could invoke<br>
 * <br>
 * acc.stop();<br>
 * <br>
 * </code>
 * <p>
 * Public methods on the Builder interfaces which set configuration information return the Builder object itself. This
 * allows the calling program to chain together several method invocations, such as
 * <p>
 * <code>
 * AppClientContainerBuilder builder = AppClientContainer.newBuilder(...);<br>
 * builder.clientCredentials(myUser, myPass).logger(myLogger);<br>
 * </code>
 *
 * @author tjquinn
 */
@Service
@PerLookup
public class AppClientContainer implements ApplicationClientContainer {

    // XXX move this
    /** Prop name for keeping temporary files */
    public static final String APPCLIENT_RETAIN_TEMP_FILES_PROPERTYNAME = "com.sun.aas.jws.retainTempFiles";

    private static final Logger logger = LogDomains.getLogger(AppClientContainer.class, LogDomains.ACC_LOGGER);

    private static final Logger _logger = Logger.getLogger(AppClientContainer.class.getName());

    @Inject
    private AppClientContainerSecurityHelper appClientContainerSecurityHelper;

    @Inject
    private InjectionManager injectionManager;

    @Inject
    private InvocationManager invocationManager;

    @Inject
    private ComponentEnvManager componentEnvManager;

    @Inject
    private ConnectorRuntime connectorRuntime;

    @Inject
    private ServiceLocator habitat;

    private Cleanup cleanup;

    private volatile State state;

    private TransformingClassLoader classLoader;

    private Launchable client;

    /** returned from binding the app client to naming; used in preparing component invocation */
    private String componentId;

    /**
     * Creates a new ACC builder object, preset with the specified target servers.
     *
     * @param targetServers server(s) to contact during ORB bootstrapping
     * @return <code>AppClientContainer.Builder</code> object
     */
    public static AppClientContainer.Builder newBuilder(final TargetServer[] targetServers) {
        return new AppClientContainerBuilder(targetServers);
    }


    private AppClientContainer() {
        this.classLoader = (TransformingClassLoader) Thread.currentThread().getContextClassLoader();
        this.state = State.INSTANTIATED;
    }

    void prepareSecurity(final TargetServer[] targetServers, final List<MessageSecurityConfig> msgSecConfigs,
            final Properties containerProperties, final ClientCredential clientCredential,
            final CallbackHandler callerSuppliedCallbackHandler, final boolean isTextAuth)
            throws ReflectiveOperationException, InjectionException, IOException, SAXException {
        appClientContainerSecurityHelper.init(targetServers, msgSecConfigs, containerProperties, clientCredential,
            callerSuppliedCallbackHandler, classLoader, client.getDescriptor(classLoader), isTextAuth);
    }

    public void prepare(final Instrumentation inst) throws Exception, UserError {
        completePreparation(inst);
    }

    void setClient(final Launchable client) throws ClassNotFoundException {
        this.client = client;
        ClientMainClassSetting.setMainClass(client.getMainClass());
    }

    void processPermissions() throws IOException {
        // need to process the permissions files
        classLoader.processDeclaredPermissions();
    }

    protected Class<?> loadClass(final String className) throws ClassNotFoundException {
        return Class.forName(className, true, classLoader);
    }

    protected ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Gets the ACC ready so the main class can run. This can be followed, immediately or after some time, by either an
     * invocation of {@link #launch(java.lang.String[])}
     * @throws Exception  or by the JVM invoking the client's main method (as would happen
     * during a <code>java -jar theClient.jar</code> launch.
     * @throws UserError
     */
    private void completePreparation(final Instrumentation inst) throws Exception, UserError {
        if (state != State.INSTANTIATED) {
            throw new IllegalStateException(
                "Expected state was " + State.INSTANTIATED + ", but current state was " + state);
        }

        // Attach any names defined in the app client. Validate the descriptor first, then use it to
        // bind names in the app client. This order is important - for example, to set up message
        // destination refs correctly.
        client.validateDescriptor();
        final ApplicationClientDescriptor desc = client.getDescriptor(classLoader);
        componentId = componentEnvManager.bindToComponentNamespace(desc);

        // Arrange for cleanup now instead of during launch() because in some use cases the JVM will
        // invoke the client's main method itself and launch will be skipped.
        cleanup = Cleanup.arrangeForShutdownCleanup(logger, habitat, desc);

        // Allow pre-destroy handling to work on the main class during clean-up.
        cleanup.setInjectionManager(injectionManager, ClientMainClassSetting.clientMainClass);

        // If this app client contains persistence unit refs, then initialize the PU handling.
        Collection<? extends PersistenceUnitDescriptor> referencedPUs = desc.findReferencedPUs();
        if (referencedPUs != null && !referencedPUs.isEmpty()) {

            ProviderContainerContractInfoImpl pcci = new ProviderContainerContractInfoImpl(
                (TransformingClassLoader) getClassLoader(), inst, client.getAnchorDir(), connectorRuntime);
            for (PersistenceUnitDescriptor puDesc : referencedPUs) {
                PersistenceUnitLoader pul = new PersistenceUnitLoader(puDesc, pcci);
                desc.addEntityManagerFactory(puDesc.getName(), pul.getEMF());
            }

            cleanup.setEMFs(pcci.emfs());
        }

        cleanup.setConnectorRuntime(connectorRuntime);

        prepareURLStreamHandling();

        // This is required for us to enable interrupt jaxws service creation calls
        setProperty("jakarta.xml.ws.spi.Provider", "com.sun.xml.ws.spi.ProviderImpl", true);
        // InjectionManager's injectClass will be called from getMainMethod

        // Load any managed beans
        ManagedBeanManager managedBeanManager = habitat.getService(ManagedBeanManager.class);
        managedBeanManager.loadManagedBeans(desc.getApplication());
        cleanup.setManagedBeanManager(managedBeanManager);

        // We don't really need the main method here but we do need the side-effects.
        resolveMainMethod();

        state = State.PREPARED;
    }

    @Override
    public void launch(String[] args) throws UserError {

        if (state != State.PREPARED) {
            throw new IllegalStateException("Unexpected state. Expected " + State.PREPARED + ", actual is " + state);
        }
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            Method mainMethod = resolveMainMethod();
            // build args to the main and call it
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                    "Current thread's classloader: " + Thread.currentThread().getContextClassLoader());
            }
            mainMethod.invoke(null, new Object[] {args});
            state = State.STARTED;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof UserError) {
                throw (UserError) e.getCause();
            }
            throw new IllegalStateException("Launch failed.", e.getCause());
        } catch (Exception e) {
            throw new IllegalStateException("Launch failed.", e);
        } finally {
            // We need to clean up when the EDT ends or, if there is no EDT, right away.
            // In particular, JMS/MQ-related non-daemon threads might still be running due to open
            // queueing connections.
            cleanupWhenSafe();
        }
    }

    private boolean isEDTRunning() {
        Map<Thread, StackTraceElement[]> threads = AccessController
            .doPrivileged((PrivilegedAction<Map<Thread, StackTraceElement[]>>) Thread::getAllStackTraces);

        logger.fine("Checking for EDT thread...");
        for (Map.Entry<Thread, StackTraceElement[]> entry : threads.entrySet()) {
            logger.log(Level.FINE, "  {0}", entry.getKey().toString());
            StackTraceElement[] frames = entry.getValue();
            if (frames.length > 0) {
                StackTraceElement last = frames[frames.length - 1];
                if (last.getClassName().equals("java.awt.EventDispatchThread") && last.getMethodName().equals("run")) {
                    logger.log(Level.FINE, "Thread {0} seems to be the EDT", entry.getKey().toString());
                    return true;
                }
            }
            logger.fine("Did not recognize any thread as the EDT");
        }
        return false;
    }

    private void cleanupWhenSafe() {
        if (isEDTRunning()) {
            final AtomicReference<Thread> edt = new AtomicReference<>();
            try {
                SwingUtilities.invokeAndWait(() -> edt.set(Thread.currentThread()));
                edt.get().join();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Waiting for Swing thread failed.", e);
            }
        }
        stop();
    }


    private Method resolveMainMethod()
        throws UserError, ReflectiveOperationException, IOException, SAXException, InjectionException {
        // determine the main method using reflection
        // verify that it is public static void and takes
        // String[] as the only argument
        Class<?> mainClass = getClientMainClass(classLoader, injectionManager, invocationManager, componentId, this,
            client.getDescriptor(classLoader));
        Method mainMethod = mainClass.getMethod("main", new Class[] {String[].class});

        // check modifiers: public static
        int modifiers = mainMethod.getModifiers();
        if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
            final String err = MessageFormat
                .format(logger.getResourceBundle().getString("appclient.notPublicOrNotStatic"), (Object[]) null);
            throw new NoSuchMethodException(err);
        }

        // check return type and exceptions
        if (!mainMethod.getReturnType().equals(Void.TYPE)) {
            final String err = MessageFormat.format(logger.getResourceBundle().getString("appclient.notVoid"), (Object[]) null);
            throw new NoSuchMethodException(err);
        }
        return mainMethod;
    }


    /**
     * Stops the app client container.
     * <p>
     * Note that the calling program should not stop the ACC if there might be other threads
     * running, such as the Swing event dispatcher thread. Stopping the ACC can shut down various
     * services that those continuing threads might try to use.
     * <p>
     * Also note that stopping the ACC will have no effect on any thread that the app client itself
     * might have created. If the calling program needs to control such threads it and the client
     * code running in the threads should agree on how they will communicate with each other.
     * The ACC cannot help with this.
     */
    public void stop() {
        // Because stop can be invoked automatically at the end of launch, allow the developer's
        // driver program to invoke stop again without penalty.
        if (state == State.STOPPED) {
            return;
        }
        cleanup.start();
        state = State.STOPPED;
    }

    /**
     * Records how the main class has been set - by name or by class - and encapsulates the retrieval of the main class.
     */
    static class ClientMainClassSetting {

        private static String clientMainClassName;
        private static Class<?> clientMainClass;
        private static boolean isInjected;

        static void setMainClassName(final String name) {
            clientMainClassName = name;
            clientMainClass = null;
        }

        static void setMainClass(final Class<?> cl) {
            clientMainClass = cl;
            clientMainClassName = null;
        }

        static Class<?> getClientMainClass(final ClassLoader loader, InjectionManager injectionManager, InvocationManager invocationManager,
                String componentId, AppClientContainer container, ApplicationClientDescriptor acDesc)
                throws ClassNotFoundException, UserError, InjectionException {

            if (isInjected) {
                return clientMainClass;
            }

            if (clientMainClass == null) {
                if (clientMainClassName == null) {
                    throw new IllegalStateException("neither client main class nor its class name has been set");
                }
                clientMainClass = Class.forName(clientMainClassName, true, loader);
                logger.log(Level.FINE, "Loaded client main class {0}", clientMainClassName);
            }

            final ComponentInvocation ci = new ComponentInvocation(componentId,
                ComponentInvocation.ComponentInvocationType.APP_CLIENT_INVOCATION, container,
                acDesc.getApplication().getAppName(), acDesc.getModuleName());

            invocationManager.preInvoke(ci);
            clientMainClass = injectMainClass(injectionManager, acDesc, container);
            isInjected = true;
            return clientMainClass;
        }


        private static Class<?> injectMainClass(InjectionManager injectionManager, ApplicationClientDescriptor acDesc,
            AppClientContainer container) throws UserError, InjectionException {
            int retriesLeft = Integer.getInteger("org.glassfish.appclient.acc.maxLoginRetries", 3);
            while (true) {
                try {
                    injectionManager.injectClass(clientMainClass, acDesc);
                    return clientMainClass;
                } catch (final InjectionException e) {
                    if (container.appClientContainerSecurityHelper.isLoginCancelled()) {
                        throw new UserError(logger.getResourceBundle().getString("appclient.userCanceledAuth"));
                    }
                    if (isCausedByCorbaNoPermission(e)) {
                        container.appClientContainerSecurityHelper.clearClientSecurityContext();
                        if (retriesLeft == 0) {
                            throw new UserError(logger.getResourceBundle().getString("appclient.noPermission"));
                        }
                        retriesLeft--;
                    } else if (e.getCause() instanceof NamingException) {
                        final String message = toMessage((NamingException) e.getCause());
                        throw new UserError(message, e);
                    } else {
                        throw e;
                    }
                }
            }
        }

        private static String toMessage(final NamingException e) {
            // Despite retries, the credentials were not accepted.
            // Throw a user error which the ACC will display nicely.
            final String expl = e.getExplanation();
            return MessageFormat.format(logger.getResourceBundle().getString("appclient.RemoteAuthError"), expl);
        }

        private static boolean isCausedByCorbaNoPermission(Throwable t) {
            while (t != null) {
                if (t instanceof NO_PERMISSION) {
                    return true;
                }
                t = t.getCause();
            }
            return false;
        }
    }

    /**
     * Records the current state of the ACC.
     */
    enum State {
        /**
         * HK2 has created the ACC instance
         */
        INSTANTIATED,

        /**
         * ACC is ready for the client to run
         */
        PREPARED,

        /**
         * the ACC has started the client.
         * <p>
         * Note that if the user launches the client JAR directly (using java -jar theClient.jar) the ACC will not be aware of
         * this and so the state remains PREPARED.
         */
        STARTED,

        /**
         * the ACC has stopped in response to a request from the calling program
         */
        STOPPED;
    }

    /**
     * Sets the name of the main class to be executed.
     * <p>
     * Normally the ACC reads the app client JAR's manifest to get the Main-Class attribute. The calling program can
     * override that value by invoking this method. The main class name is also useful if the calling program provides an
     * EAR that contains multiple app clients as submodules within it; the ACC needs the calling program to specify which of
     * the possibly several app client modules is the one to execute.
     *
     * @param clientMainClassName
     */
    public void setClientMainClassName(final String clientMainClassName) {
        ClientMainClassSetting.setMainClassName(clientMainClassName);
    }

    public void setClientMainClass(final Class<?> clientMainClass) {
        ClientMainClassSetting.setMainClass(clientMainClass);
    }

    /**
     * Assigns the URL stream handler factory.
     * <p>
     * Needed for web services support.
     */
    private static void prepareURLStreamHandling() {
        // Set the HTTPS URL stream handler.
        PrivilegedAction<Void> action = () -> {
            URL.setURLStreamHandlerFactory(new DirContextURLStreamHandlerFactory());
            return null;
        };
        AccessController.doPrivileged(action);
    }


    /**
     * Prescribes the exposed behavior of ACC configuration that can be set up further, and can be
     * used to newContainer an ACC.
     */
    public interface Builder {

        AppClientContainer newContainer(URI archiveURI) throws Exception, UserError;

        AppClientContainer newContainer(URI archiveURI, CallbackHandler callbackHandler, String mainClassName, String appName)
                throws Exception, UserError;

        AppClientContainer newContainer(URI archiveURI, CallbackHandler callbackHandler, String mainClassName, String appName,
                boolean isTextAuth) throws Exception, UserError;

        AppClientContainer newContainer(Class mainClass) throws Exception, UserError;

        TargetServer[] getTargetServers();

        /**
         * Adds an optional {@link MessageSecurityConfig} setting.
         *
         * @param msConfig the new MessageSecurityConfig
         * @return the <code>Builder</code> instance
         */
        Builder addMessageSecurityConfig(final MessageSecurityConfig msConfig);

        List<MessageSecurityConfig> getMessageSecurityConfig();

        /**
         * Sets the optional authentication realm for the ACC.
         * <p>
         * Each specific realm will determine which properties should be set in the Properties argument.
         *
         * @param className name of the class which implements the realm
         * @return the <code>Builder</code> instance
         */
        Builder authRealm(final String className);

        AuthRealm getAuthRealm();

        /**
         * Sets the optional client credentials to be used during authentication to the back-end.
         * <p>
         * If the client does not invoke <code>clientCredentials</code> then the ACC will use a {@link CallbackHandler} when it
         * discovers that authentication is required. See {@link #callerSuppliedCallbackHandler}.
         *
         * @param username username valid in the default realm on the server
         * @param password password valid in the default realm on the server for the username
         * @return the <code>Builder</code> instance
         */
        Builder clientCredentials(final String user, final char[] password);

        ClientCredential getClientCredential();

        /**
         * Sets the optional client credentials and server-side realm to be used during authentication to the back-end.
         * <p>
         * If the client does not invoke <code>clientCredentials</code> then the ACC will use a {@link CallbackHandler} when it
         * discovers that authentication is required. See {@link #callerSuppliedCallbackHandler}.
         *
         * @param username username valid in the specified realm on the server
         * @param password password valid in the specified realm on the server for the username
         * @param realmName name of the realm on the server within which the credentials are valid
         * @return the <code>Builder</code> instance
         */
        Builder clientCredentials(final String user, final char[] password, final String realm);

        /**
         * Sets the container-level Properties.
         *
         * @param containerProperties
         * @return
         */
        Builder containerProperties(final Properties containerProperties);

        /**
         * Sets the container-level properties.
         * <p>
         * Typically used when setting the properties from the parsed XML config file.
         *
         * @param containerProperties Property objects to use in setting the properties
         * @return
         */
        Builder containerProperties(final List<Property> containerProperties);

        /**
         * Returns the container-level Properties.
         *
         * @return container-level properties
         */
        Properties getContainerProperties();

        /**
         * Sets the logger which the ACC should use as it runs.
         *
         * @param logger
         * @return
         */
        Builder logger(final Logger logger);

        Logger getLogger();

        /**
         * Sets whether the ACC should send the password to the server during authentication.
         *
         * @param sendPassword
         * @return
         */
        Builder sendPassword(final boolean sendPassword);

        boolean getSendPassword();
    }

    /**
     * Encapsulates all clean-up activity.
     * <p>
     * The calling program can invoke clean-up by invoking the <code>stop</code> method or by letting the JVM exit, in which
     * case clean-up will occur as part of VM shutdown.
     */
    private static class Cleanup implements Runnable {
        private AppClientInfo appClientInfo = null;
        private boolean cleanedUp = false;
        private InjectionManager injectionMgr = null;
        private ApplicationClientDescriptor appClient = null;
        private Class cls = null;
        private final Logger logger;
        private Thread cleanupThread = null;
        private Collection<EntityManagerFactory> emfs = null;
        private final ServiceLocator habitat;
        private ConnectorRuntime connectorRuntime;
        private ManagedBeanManager managedBeanMgr;

        static Cleanup arrangeForShutdownCleanup(final Logger logger, final ServiceLocator habitat,
                final ApplicationClientDescriptor appDesc) {
            final Cleanup cu = new Cleanup(logger, habitat, appDesc);
            cu.enable();
            return cu;
        }

        private Cleanup(final Logger logger, final ServiceLocator habitat, final ApplicationClientDescriptor appDesc) {
            this.logger = logger;
            this.habitat = habitat;
            this.appClient = appDesc;
        }

        void setAppClientInfo(AppClientInfo info) {
            appClientInfo = info;
        }

        void setInjectionManager(InjectionManager injMgr, Class cls) {
            injectionMgr = injMgr;
            this.cls = cls;
        }

        void setManagedBeanManager(ManagedBeanManager mgr) {
            managedBeanMgr = mgr;
        }

        void setEMFs(Collection<EntityManagerFactory> emfs) {
            this.emfs = emfs;
        }

        void setConnectorRuntime(ConnectorRuntime connectorRuntime) {
            this.connectorRuntime = connectorRuntime;
        }

        void enable() {
            Runtime.getRuntime().addShutdownHook(cleanupThread = new Thread(this, "Cleanup"));
        }

        void disable() {
            java.security.AccessController.doPrivileged(new java.security.PrivilegedAction() {

                @Override
                public Object run() {
                    Runtime.getRuntime().removeShutdownHook(cleanupThread);
                    return null;
                }
            });
        }

        /**
         * Requests cleanup without relying on the VM's shutdown handling.
         */
        void start() {
            disable();
            run();
        }

        /**
         * Performs clean-up of the ACC.
         * <p>
         * This method should be invoked directly only by the VM's shutdown handling (or by the CleanUp newContainer method). To
         * trigger clean-up without relying on the VM's shutdown handling invoke Cleanup.newContainer() not Cleanup.run().
         */
        @Override
        public void run() {
            logger.fine("Clean-up starting");
            _logger.fine("Clean-up starting");
            /*
             * Do not invoke disable from here. The run method might execute while the VM shutdown is in progress, and attempting to
             * remove the shutdown hook at that time would trigger an exception.
             */
            cleanUp();
            logger.fine("Clean-up complete");
            _logger.fine("Clean-up complete");
        }

        void cleanUp() {
            if (!cleanedUp) {

                // Do managed bean cleanup early since it can result in
                // application code (@PreDestroy) invocations
                cleanupManagedBeans();
                cleanupEMFs();
                cleanupInfo();
                cleanupInjection();
                cleanupServiceReferences();
                cleanupTransactions();
                cleanupConnectorRuntime();

                cleanedUp = true;
            } // End if -- cleanup required
        }

        private void cleanupEMFs() {
            try {
                if (emfs != null) {
                    for (EntityManagerFactory emf : emfs) {
                        emf.close();
                    }
                    emfs.clear();
                    emfs = null;
                }
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "cleanupEMFs", t);
            }
        }

        private void cleanupInfo() {
            try {
                if (appClientInfo != null) {
                    appClientInfo.close();
                }
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "cleanupInfo", t);
            }
        }

        private void cleanupInjection() {
            try {
                if (injectionMgr != null) {
                    // inject the pre-destroy methods before shutting down
                    injectionMgr.invokeClassPreDestroy(cls, appClient);
                    injectionMgr = null;
                }
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "cleanupInjection", t);
            }

        }

        private void cleanupManagedBeans() {
            try {
                if (managedBeanMgr != null) {
                    managedBeanMgr.unloadManagedBeans(appClient.getApplication());
                }
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "cleanupManagedBeans", t);
            }

        }

        private void cleanupServiceReferences() {
            try {
                if (appClient != null && appClient.getServiceReferenceDescriptors() != null) {
                    // Cleanup client pipe line, if there were service references
                    for (Object desc : appClient.getServiceReferenceDescriptors()) {
                        ClientPipeCloser.getInstance().cleanupClientPipe((ServiceReferenceDescriptor) desc);
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "cleanupServiceReferences", t);
            }
        }

        private void cleanupTransactions() {
            try {
                ServiceHandle<TransactionManager> inhabitant = habitat.getServiceHandle(TransactionManager.class);
                if (inhabitant != null && inhabitant.isActive()) {
                    TransactionManager txmgr = inhabitant.getService();
                    if (txmgr.getStatus() == Status.STATUS_ACTIVE || txmgr.getStatus() == Status.STATUS_MARKED_ROLLBACK) {
                        txmgr.rollback();
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "cleanupTransactions", t);
            }

        }

        private void cleanupConnectorRuntime() {
            try {
                if (connectorRuntime != null) {
                    connectorRuntime.cleanUpResourcesAndShutdownAllActiveRAs();
                    connectorRuntime = null;
                }
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "cleanupConnectorRuntime", t);
            }
        }
    }
}
