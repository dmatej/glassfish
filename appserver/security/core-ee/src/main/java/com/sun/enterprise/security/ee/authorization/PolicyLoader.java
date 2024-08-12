/*
 * Copyright (c) 2022, 2022 Contributors to the Eclipse Foundation.
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

package com.sun.enterprise.security.ee.authorization;

import com.sun.enterprise.config.serverbeans.JaccProvider;
import com.sun.enterprise.config.serverbeans.SecurityService;
import com.sun.enterprise.security.SecurityLoggerInfo;
import com.sun.enterprise.util.i18n.StringManager;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.security.jacc.Policy;
import jakarta.security.jacc.PolicyFactory;

import java.util.List;
import java.util.logging.Logger;

import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.exousia.modules.def.DefaultPolicyFactory;
import org.glassfish.hk2.api.IterableProvider;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.types.Property;

import static com.sun.enterprise.security.SecurityLoggerInfo.policyConfigFactoryNotDefined;
import static com.sun.enterprise.security.SecurityLoggerInfo.policyFactoryOverride;
import static com.sun.enterprise.security.SecurityLoggerInfo.policyInstallError;
import static com.sun.enterprise.security.SecurityLoggerInfo.policyLoading;
import static com.sun.enterprise.security.SecurityLoggerInfo.policyNoSuchName;
import static com.sun.enterprise.security.SecurityLoggerInfo.policyNotLoadingWarning;
import static com.sun.enterprise.security.SecurityLoggerInfo.policyProviderConfigOverrideMsg;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

/**
 * Loads the Default Policy File into the system.
 *
 * @author Harpreet Singh
 * @author Jyri J. Virkki
 *
 */
@Service
@Singleton
public class PolicyLoader {

    @Inject
    @Named(ServerEnvironment.DEFAULT_INSTANCE_NAME)
    private SecurityService securityService;

    @Inject
    private IterableProvider<JaccProvider> authorizationModules;

    private static Logger LOGGER = SecurityLoggerInfo.getLogger();
    private static StringManager SM = StringManager.getManager(PolicyLoader.class);

    private static final String POLICY_PROVIDER = "jakarta.security.jacc.policy.provider";
    private static final String POLICY_CONF_FACTORY = "jakarta.security.jacc.PolicyConfigurationFactory.provider";
    private static final String POLICY_PROP_PREFIX = "com.sun.enterprise.jaccprovider.property.";
    private boolean isPolicyInstalled;

    /**
     * Attempts to install the policy-provider. The policy-provider element in domain.xml is consulted for the class to use. Note
     * that if the jakarta.security.jacc.policy.provider system property is set it will override the domain.xml configuration. This
     * will normally not be the case in S1AS.
     *
     */
    public void loadPolicy() {
        if (isPolicyInstalled) {
            LOGGER.log(FINE, "Policy already installed. Will not re-install.");
            return;
        }

        // Get configuration object from domain.xml
        JaccProvider authorizationModule = getConfiguredJakartaAuthorizationModule();

        // Set config properties (see method comments)
        setPolicyConfigurationFactory(authorizationModule);

        // Check if system property is set for the policy class name
        String javaPolicyClassName = System.getProperty(POLICY_PROVIDER);

        if (javaPolicyClassName != null) {
            // inform user domain.xml is being ignored
            LOGGER.log(INFO, policyProviderConfigOverrideMsg, new String[] { POLICY_PROVIDER, javaPolicyClassName });
        } else if (authorizationModule != null) {
            // Otherwise obtain authorization module policy-provider from domain.xml
            javaPolicyClassName = authorizationModule.getPolicyProvider();
        }

        // Set the role mapper
        // TODO: replace with standard version
        if (System.getProperty("simple.jacc.provider.JACCRoleMapper.class") == null) {
            System.setProperty("simple.jacc.provider.JACCRoleMapper.class",
                "com.sun.enterprise.security.ee.authorization.GlassfishRoleMapper");
        }

        // Now install the policy provider if one was identified
        if (javaPolicyClassName != null) {

            try {
                LOGGER.log(INFO, policyLoading, javaPolicyClassName);

                Policy policy = loadPolicy(javaPolicyClassName);
                PolicyFactory.setPolicyFactory(new DefaultPolicyFactory()); // TMP!!!
                PolicyFactory.getPolicyFactory().setPolicy(policy);
            } catch (Exception e) {
                LOGGER.log(SEVERE, policyInstallError, e.getLocalizedMessage());
                throw new RuntimeException(e);
            }

            // Success.
            LOGGER.fine("Policy set to: " + javaPolicyClassName);
            isPolicyInstalled = true;

        } else {
            // no value for policy provider found
            LOGGER.warning(policyNotLoadingWarning);
        }
    }

    private Policy loadPolicy(String javaPolicyClassName) throws ReflectiveOperationException, SecurityException {
        Object javaPolicyInstance =
                Thread.currentThread()
                      .getContextClassLoader()
                      .loadClass(javaPolicyClassName)
                      .getDeclaredConstructor()
                      .newInstance();

        if (!(javaPolicyInstance instanceof Policy)) {
            throw new RuntimeException(SM.getString("enterprise.security.plcyload.not14"));
        }

        return (Policy) javaPolicyInstance;
    }

    /**
     * Returns an authorization module object representing the jacc element from domain.xml which is configured in security-service.
     *
     * @return The config object or null on errors.
     *
     */
    private JaccProvider getConfiguredJakartaAuthorizationModule() {
        JaccProvider authorizationModule = null;
        try {
            String name = securityService.getJacc();
            authorizationModule = getAuthorizationModuleByName(name);
            if (authorizationModule == null) {
                LOGGER.log(WARNING, policyNoSuchName, name);
            }
        } catch (Exception e) {
            LOGGER.warning(SecurityLoggerInfo.policyReadingError);
            authorizationModule = null;
        }

        return authorizationModule;
    }

    private JaccProvider getAuthorizationModuleByName(String authorizationModuleName) {
        if (authorizationModules == null || authorizationModuleName == null) {
            return null;
        }

        for (JaccProvider authorizationModule : authorizationModules) {
            if (authorizationModule.getName().equals(authorizationModuleName)) {
                return authorizationModule;
            }
        }

        return null;
    }

    /**
     * Set internal properties based on domain.xml configuration.
     *
     * <P>
     * The POLICY_CONF_FACTORY property is consumed by the jacc-api as documented in the Jakarta Authorization specification. It's
     * value is set here to the value given in domain.xml <i>unless</i> it is already set in which case the value is not modified.
     *
     * <P>
     * Then and properties associated with this authorization module from domain.xml are set as internal properties prefixed with
     * POLICY_PROP_PREFIX. This is currently a workaround for bug 4846938. A cleaner interface should be adopted.
     *
     */
    private void setPolicyConfigurationFactory(JaccProvider authorizationModule) {
        if (authorizationModule == null) {
            return;
        }

        // Handle Jakarta Authorization-specified property for factory
        String factoryFromSystemProperty = System.getProperty(POLICY_CONF_FACTORY);
        if (factoryFromSystemProperty != null) {
            // warn user of override
            LOGGER.log(WARNING, policyFactoryOverride, new String[] { POLICY_CONF_FACTORY, factoryFromSystemProperty });

        } else {
            // use domain.xml value by setting the property to it
            String factoryFromDomain = authorizationModule.getPolicyConfigurationFactoryProvider();
            if (factoryFromDomain == null) {
                LOGGER.log(WARNING, policyConfigFactoryNotDefined);
            } else {
                System.setProperty(POLICY_CONF_FACTORY, factoryFromDomain);
            }
        }

        // Next, make properties of this authorization module available to module
        List<Property> authorizationModuleProperties = authorizationModule.getProperty();
        for (Property authorizationModuleProperty : authorizationModuleProperties) {
            String name = POLICY_PROP_PREFIX + authorizationModuleProperty.getName();
            String value = authorizationModuleProperty.getValue();
            LOGGER.finest("PolicyLoader set [" + name + "] to [" + value + "]");

            System.setProperty(name, value);
        }
    }
}
