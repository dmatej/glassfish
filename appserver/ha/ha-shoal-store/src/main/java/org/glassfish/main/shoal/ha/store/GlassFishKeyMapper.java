/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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

package org.glassfish.main.shoal.ha.store;

import org.glassfish.ha.common.GlassFishHAReplicaPredictor;
import org.glassfish.ha.common.HACookieInfo;
import org.glassfish.ha.common.HACookieManager;
import org.glassfish.shoal.ha.cache.mapper.DefaultKeyMapper;

/**
 * @author Mahesh Kannan
 *
 */
public class GlassFishKeyMapper
    extends DefaultKeyMapper
    implements GlassFishHAReplicaPredictor {

    public GlassFishKeyMapper(String instanceName) {
        super(instanceName);
    }


    @Override
    public HACookieInfo makeCookie(String groupName, Object key, String oldReplicaCookie) {
        final String cookieStr;
        if (key == null) {
            cookieStr = null;
        } else {
            cookieStr = super.getMappedInstance(groupName, key);
        }
        HACookieInfo ha = new HACookieInfo(cookieStr, oldReplicaCookie);
        return ha;
    }

    @Override
    public String getMappedInstance(String groupName, Object key1) {
        HACookieInfo cookieInfo = HACookieManager.getCurrent();
        if (cookieInfo.getNewReplicaCookie() == null) {
            return super.getMappedInstance(groupName, key1);
        }
        return cookieInfo.getNewReplicaCookie();
    }
}
