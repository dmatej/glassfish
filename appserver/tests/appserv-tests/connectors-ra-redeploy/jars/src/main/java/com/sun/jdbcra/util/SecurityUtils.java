/*
 * Copyright (c) 2022, 2026 Contributors to the Eclipse Foundation
 * Copyright (c) 2003, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jdbcra.util;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ManagedConnectionFactory;
import jakarta.resource.spi.security.PasswordCredential;

import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import javax.security.auth.Subject;

/**
 * SecurityUtils for Generic JDBC Connector.
 *
 * @version        1.0, 02/07/22
 * @author        Evani Sai Surya Kiran
 */
public class SecurityUtils {

    /**
     * This method returns the <code>PasswordCredential</code> object, given
     * the <code>ManagedConnectionFactory</code>, subject and the
     * <code>ConnectionRequestInfo</code>. It first checks if the
     * <code>ConnectionRequestInfo</code> is null or not. If it is not null,
     * it constructs a <code>PasswordCredential</code> object with
     * the user and password fields from the <code>ConnectionRequestInfo</code> and returns this
     * <code>PasswordCredential</code> object. If the <code>ConnectionRequestInfo</code>
     * is null, it retrieves the <code>PasswordCredential</code> objects from
     * the <code>Subject</code> parameter and returns the first
     * <code>PasswordCredential</code> object which contains a
     * <code>ManagedConnectionFactory</code>, instance equivalent
     * to the <code>ManagedConnectionFactory</code>, parameter.
     *
     * @param        mcf        <code>ManagedConnectionFactory</code>
     * @param        subject        <code>Subject</code>
     * @param        info        <code>ConnectionRequestInfo</code>
     * @return        <code>PasswordCredential</code>
     * @throws ResourceException generic exception if operation fails
     */
    public static PasswordCredential getPasswordCredential(final ManagedConnectionFactory mcf,
         final Subject subject, jakarta.resource.spi.ConnectionRequestInfo info) throws ResourceException {

        if (info == null) {
            if (subject == null) {
                return null;
            }
            PasswordCredential pc = getPrivatePasswordCredential(subject, mcf);
            if (pc == null) {
                throw new jakarta.resource.spi.SecurityException("No PasswordCredential found");
            }
            return pc;
        }
        com.sun.jdbcra.spi.ConnectionRequestInfo cxReqInfo = (com.sun.jdbcra.spi.ConnectionRequestInfo) info;
        PasswordCredential pc = new PasswordCredential(cxReqInfo.getUser(), cxReqInfo.getPassword().toCharArray());
        pc.setManagedConnectionFactory(mcf);
        return pc;
    }

    private static PasswordCredential getPrivatePasswordCredential(Subject subject, Object mcf) {
        Set<PasswordCredential> passwdCredentialSet = subject.getPrivateCredentials(PasswordCredential.class);
        Iterator<PasswordCredential> iter = passwdCredentialSet.iterator();
        while (iter.hasNext()) {
            PasswordCredential credential = iter.next();
            if (credential.getManagedConnectionFactory().equals(mcf)) {
                return credential;
            }
        }
        return null;
    }

    /**
     * Returns true if two <code>PasswordCredential</code> objects are equal; false otherwise
     *
     * @param        pC1        <code>PasswordCredential</code>
     * @param        pC2        <code>PasswordCredential</code>
     * @return        true        if the two PasswordCredentials are equal
     *                false        otherwise
     */
    static public boolean isPasswordCredentialEqual(PasswordCredential pC1, PasswordCredential pC2) {
        if (pC1 == pC2) {
            return true;
        }
        if(pC1 == null || pC2 == null) {
            return (pC1 == pC2);
        }
        if (!Objects.equals(pC1.getUserName(), pC2.getUserName())) {
            return false;
        }
        String p1 = null;
        String p2 = null;
        if (pC1.getPassword() != null) {
            p1 = new String(pC1.getPassword());
        }
        if (pC2.getPassword() != null) {
            p2 = new String(pC2.getPassword());
        }
        return Objects.equals(p1, p2);
    }
}
