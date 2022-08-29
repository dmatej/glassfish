/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 * Copyright (c) 2012, 2018 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.deployment;

import com.sun.enterprise.deployment.core.ResourceDescriptor;

import java.util.Set;

import org.glassfish.deployment.common.JavaEEResourceType;

/**
 * @author naman
 */
public abstract class CommonResourceBundleDescriptor extends BundleDescriptor {

    private static final long serialVersionUID = 3030097808191647490L;

    private final ResourceDescriptorRegistry resourceDescriptorRegistry = new ResourceDescriptorRegistry();

    public CommonResourceBundleDescriptor() {
        super();
    }

    public CommonResourceBundleDescriptor(String name, String description) {
        super(name, description);
    }

    public void addResourceDescriptor(ResourceDescriptor descriptor) {
        resourceDescriptorRegistry.addResourceDescriptor(descriptor);
    }

    public void removeResourceDescriptor(ResourceDescriptor descriptor) {
        resourceDescriptorRegistry.removeResourceDescriptor(descriptor.getResourceType(),descriptor);
    }

    public Set<ResourceDescriptor> getResourceDescriptors(JavaEEResourceType type) {
        return resourceDescriptorRegistry.getResourceDescriptors(type);
    }

    protected ResourceDescriptor getResourceDescriptor(JavaEEResourceType type, String name) {
        return resourceDescriptorRegistry.getResourceDescriptor(type,name);
    }

    public Set<ResourceDescriptor> getAllResourcesDescriptors() {
        return resourceDescriptorRegistry.getAllResourcesDescriptors();
    }

    public Set<ResourceDescriptor> getAllResourcesDescriptors(Class givenClazz) {
        return resourceDescriptorRegistry.getAllResourcesDescriptors(givenClazz);
    }
}

