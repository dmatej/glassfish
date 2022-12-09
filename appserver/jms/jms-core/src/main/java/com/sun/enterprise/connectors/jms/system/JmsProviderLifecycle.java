/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package com.sun.enterprise.connectors.jms.system;

import com.sun.appserv.connectors.internal.api.ConnectorRuntime;
import com.sun.appserv.connectors.internal.api.ConnectorRuntimeException;
import com.sun.appserv.connectors.internal.api.ConnectorsUtil;
import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.connectors.jms.config.JmsHost;
import com.sun.enterprise.connectors.jms.config.JmsService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;

import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.internal.api.PostStartupRunLevel;
import org.jvnet.hk2.annotations.Service;

import static com.sun.appserv.connectors.internal.api.ConnectorConstants.DEFAULT_JMS_ADAPTER;

@Service
@RunLevel(value = PostStartupRunLevel.VAL, mode = RunLevel.RUNLEVEL_MODE_NON_VALIDATING)
public class JmsProviderLifecycle {

    public static final String EMBEDDED = "EMBEDDED";
    public static final String LOCAL="LOCAL";
    public static final String REMOTE="REMOTE";
    public static final String JMS_SERVICE = "jms-service";

    private static final Logger LOG = System.getLogger(JmsProviderLifecycle.class.getName());
    private static final String JMS_INITIALIZE_ON_DEMAND = "org.glassfish.jms.InitializeOnDemand";


    @Inject
    @Named(ServerEnvironment.DEFAULT_INSTANCE_NAME)
    private Config config;

    @Inject
    private Provider<JMSConfigListener> jmsConfigListenerProvider;

    @Inject
    private Provider<ConnectorRuntime> connectorRuntimeProvider;

    @Inject
    private ActiveJmsResourceAdapter activeJmsResourceAdapter;

    @PostConstruct
    public void postConstruct() {
        final JmsService jmsService = config.getExtensionByType(JmsService.class);
        if (eagerStartupRequired()) {
            try {
                initializeBroker();
            } catch (ConnectorRuntimeException e) {
                LOG.log(Level.WARNING, "Eager JMS Resource Adapter initialization failed.", e);
            }
        }
        activeJmsResourceAdapter.initializeLazyListener(jmsService);
        // do a lookup of the config listener to get it started
        JMSConfigListener listener = jmsConfigListenerProvider.get();
        LOG.log(Level.TRACE, "Received config listener {0}, JMS Resource Adapter initialization succeeded", listener);
    }


    @PreDestroy
    public void preDestroy() throws Exception {
        LOG.log(Level.TRACE, "Stopping the default JMS Resource Adapter {0}", DEFAULT_JMS_ADAPTER);
        ConnectorRuntime connectorRuntime = connectorRuntimeProvider.get();
        connectorRuntime.destroyActiveResourceAdapter(DEFAULT_JMS_ADAPTER);
        LOG.log(Level.INFO, "The default JMS Resource Adapter {0} has been stopped.", DEFAULT_JMS_ADAPTER);
    }


    public void initializeBroker() throws ConnectorRuntimeException {
        LOG.log(Level.TRACE, "Initializing the default JMS Resource Adapter {0}", DEFAULT_JMS_ADAPTER);
        String directory = ConnectorsUtil.getSystemModuleLocation(DEFAULT_JMS_ADAPTER);
        ConnectorRuntime connectorRuntime = connectorRuntimeProvider.get();
        connectorRuntime.createActiveResourceAdapter(directory, DEFAULT_JMS_ADAPTER, null);
        LOG.log(Level.INFO, "The default JMS Resource Adapter {0} has been started.", DEFAULT_JMS_ADAPTER);
    }


    private boolean eagerStartupRequired(){
        // Initialize on demand is currently enabled based on a system property
        final String jmsInitializeOnDemand = System.getProperty(JMS_INITIALIZE_ON_DEMAND);
        // if the system property is true, don't do eager startup
        if ("true".equals(jmsInitializeOnDemand)) {
            return false;
        }

        final JmsService jmsService = config.getExtensionByType(JmsService.class);
        if (jmsService == null) {
            return false;
        }
        final String integrationMode = jmsService.getType();
        // we don't manage lifecycle of remote brokers
        if (REMOTE.equals(integrationMode)) {
            return false;
        }

        final List<JmsHost> jmsHostList = jmsService.getJmsHost();
        if (jmsHostList == null) {
            return false;
        }

        final JmsHost defaultJmsHost = getDefaultJmsHost(jmsService);
        final boolean lazyInit = defaultJmsHost == null ? false : Boolean.parseBoolean(defaultJmsHost.getLazyInit());

        if (EMBEDDED.equals(integrationMode) && !lazyInit) {
            return true;
        }

        // local broker has eager startup by default
        return LOCAL.equals(integrationMode);
    }


    private JmsHost getDefaultJmsHost(JmsService jmsService) {
        String defaultJmsHostName = jmsService.getDefaultJmsHost();
        List<JmsHost> jmsHostList = jmsService.getJmsHost();
        if (jmsHostList == null) {
            return null;
        }
        for (JmsHost host : jmsHostList) {
            if (defaultJmsHostName != null && defaultJmsHostName.equals(host.getName())) {
                return host;
            }
        }
        return jmsHostList.isEmpty() ? null : jmsHostList.get(0);
    }
}
