/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 * Copyright (c) 1997, 2020 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.security.webservices;

import com.sun.enterprise.deployment.ServiceReferenceDescriptor;
import com.sun.enterprise.deployment.WebServiceEndpoint;
import com.sun.enterprise.deployment.runtime.common.MessageSecurityBindingDescriptor;
import com.sun.enterprise.security.SecurityContext;
import com.sun.enterprise.security.authorize.PolicyContextHandlerImpl;
import com.sun.enterprise.security.ee.audit.AppServerAuditManager;
import com.sun.enterprise.security.jmac.provider.ServerAuthConfig;
import com.sun.enterprise.security.web.integration.WebPrincipal;
import com.sun.enterprise.web.WebModule;
import com.sun.web.security.RealmAdapter;
import com.sun.xml.ws.assembler.metro.dev.ClientPipelineHook;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.security.jacc.PolicyContext;
import jakarta.servlet.http.HttpServletRequest;

import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.catalina.Globals;
import org.glassfish.security.common.UserNameAndPassword;
import org.glassfish.webservices.EjbRuntimeEndpointInfo;
import org.glassfish.webservices.SecurityService;
import org.glassfish.webservices.WebServiceContextImpl;
import org.glassfish.webservices.monitoring.AuthenticationListener;
import org.glassfish.webservices.monitoring.Endpoint;
import org.glassfish.webservices.monitoring.WebServiceEngineImpl;
import org.jvnet.hk2.annotations.Service;

/**
 * @author Kumar
 */
@Service
@Singleton
public class SecurityServiceImpl implements SecurityService {

    @Inject
    private AppServerAuditManager auditManager;

    protected static final Logger _logger = LogUtils.getLogger();

    private static final String AUTHORIZATION_HEADER = "authorization";

    @Override
    public Object mergeSOAPMessageSecurityPolicies(MessageSecurityBindingDescriptor desc) {
        try {
            // merge message security policy from domain.xml and sun-specific
            // deployment descriptor
            ServerAuthConfig serverAuthConfig = com.sun.enterprise.security.jmac.provider.ServerAuthConfig
                .getConfig(com.sun.enterprise.security.jauth.AuthConfig.SOAP, desc, null);
            return serverAuthConfig;
        } catch (Exception ae) {
            _logger.log(Level.SEVERE, LogUtils.EJB_SEC_CONFIG_FAILURE, ae);
        }
        return null;
    }

    @Override
    public boolean doSecurity(HttpServletRequest hreq, EjbRuntimeEndpointInfo epInfo, String realmName, WebServiceContextImpl context) {
        //BUG2263 - Clear the value of UserPrincipal from previous request
        //If authentication succeeds, the proper value will be set later in
        //this method.
        boolean authenticated = false;
        try {
            //calling this for a GET request WSDL query etc can cause problems
            String method = hreq.getMethod();
//            if (method.equals("POST") /*&& hreq.getUserPrincipal() == null*/) {
//                resetSecurityContext();
//            }

            if (context != null) {
                context.setUserPrincipal(null);
            }

            WebServiceEndpoint endpoint = epInfo.getEndpoint();

            String rawAuthInfo = hreq.getHeader(AUTHORIZATION_HEADER);
            if (method.equals("GET") || !endpoint.hasAuthMethod()) {
            //if (method.equals("GET") || rawAuthInfo == null) {
                authenticated = true;
                return true;
            }

            WebPrincipal webPrincipal = null;
            String endpointName = endpoint.getEndpointName();
            if (endpoint.hasBasicAuth() || rawAuthInfo != null) {
                //String rawAuthInfo = hreq.getHeader(AUTHORIZATION_HEADER);
                if (rawAuthInfo == null) {
                    sendAuthenticationEvents(false, hreq.getRequestURI(), null);
                    authenticated = false;
                    return false;
                }

                UserNameAndPassword usernamePassword = parseUsernameAndPassword(rawAuthInfo);
                if (usernamePassword == null) {
                    _logger.log(Level.WARNING, LogUtils.BASIC_AUTH_ERROR, endpointName);
                } else {
                    webPrincipal = new WebPrincipal(usernamePassword, SecurityContext.init());
                }
            } else {
                //org.apache.coyote.request.X509Certificate
                X509Certificate certs[] = (X509Certificate[]) hreq.getAttribute(Globals.CERTIFICATES_ATTR);
                if (certs == null || certs.length < 1) {
                    certs = (X509Certificate[]) hreq.getAttribute(Globals.SSL_CERTIFICATE_ATTR);
                }

                if (certs == null) {
                    _logger.log(Level.WARNING, LogUtils.CLIENT_CERT_ERROR, endpointName);
                } else {
                    webPrincipal = new WebPrincipal(certs, SecurityContext.init());
                }
            }

            if (webPrincipal == null) {
                sendAuthenticationEvents(false, hreq.getRequestURI(), null);
                return authenticated;
            }

            RealmAdapter ra = new RealmAdapter(realmName, endpoint.getBundleDescriptor().getModuleID());
            authenticated = ra.authenticate(webPrincipal);
            if (authenticated) {
                sendAuthenticationEvents(true, hreq.getRequestURI(), webPrincipal);
            } else {
                sendAuthenticationEvents(false, hreq.getRequestURI(), webPrincipal);
                _logger.log(Level.FINE, "authentication failed for {0}", endpointName);
            }

            //Setting if userPrincipal in WSCtxt applies for JAXWS endpoints only
            epInfo.prepareInvocation(false);
            WebServiceContextImpl ctxt = (WebServiceContextImpl) epInfo.getWebServiceContext();
            ctxt.setUserPrincipal(webPrincipal);

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (auditManager != null && auditManager.isAuditOn()) {
                auditManager.ejbAsWebServiceInvocation(epInfo.getEndpoint().getEndpointName(), authenticated);
            }
        }
        return authenticated;
    }


    private UserNameAndPassword parseUsernameAndPassword(String rawAuthInfo) {
        if (rawAuthInfo != null && rawAuthInfo.startsWith("Basic ")) {
            String authString = rawAuthInfo.substring(6).trim();
            // Decode and parse the authorization credentials
            String unencoded = new String(Base64.getDecoder().decode(authString.getBytes()));
            int colon = unencoded.indexOf(':');
            if (colon > 0) {
                String user = unencoded.substring(0, colon).trim();
                String password = unencoded.substring(colon + 1).trim();
                return new UserNameAndPassword(user, password);
            }
        }
        return null;
    }


    private void sendAuthenticationEvents(boolean success, String url, Principal principal) {
        Endpoint endpoint = WebServiceEngineImpl.getInstance().getEndpoint(url);
        if (endpoint == null) {
            return;
        }
        for (AuthenticationListener listener : WebServiceEngineImpl.getInstance().getAuthListeners()) {
            if (success) {
                listener.authSucess(endpoint.getDescriptor().getBundleDescriptor(), endpoint, principal);
            } else {
                listener.authFailure(endpoint.getDescriptor().getBundleDescriptor(), endpoint, principal);
            }
        }
    }


    @Override
    public void resetSecurityContext() {
        SecurityContext.setUnauthenticatedContext();
    }


    @Override
    public void resetPolicyContext() {
        PolicyContextHandlerImpl.getInstance().reset();
        PolicyContext.setContextID(null);
    }


    @Override
    public ClientPipelineHook getClientPipelineHook(ServiceReferenceDescriptor ref) {
        return new ClientPipeCreator(ref);
    }


    @Override
    public Principal getUserPrincipal(boolean isWeb) {
        // This is a servlet endpoint
        SecurityContext ctx = SecurityContext.getCurrent();
        if (ctx == null) {
            return null;
        }
        if (ctx.didServerGenerateCredentials()) {
            if (isWeb) {
                return null;
            }
        }
        return ctx.getCallerPrincipal();
    }


    @Override
    public boolean isUserInRole(WebModule webModule, Principal principal, String servletName, String role) {
        if (webModule.getRealm() instanceof RealmAdapter) {
            RealmAdapter realmAdapter = (RealmAdapter) webModule.getRealm();
            return realmAdapter.hasRole(servletName, principal, role);
        }
        return false;
    }
}
