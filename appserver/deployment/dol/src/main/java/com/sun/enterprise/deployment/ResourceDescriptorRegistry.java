/*
 * Copyright (c) 2022, 2022 Contributors to the Eclipse Foundation.
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
import com.sun.enterprise.util.LocalStringManagerImpl;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.glassfish.deployment.common.JavaEEResourceType;

import static com.sun.enterprise.util.Utility.isEmpty;
import static java.util.Arrays.asList;
import static org.glassfish.deployment.common.JavaEEResourceType.AODD;
import static org.glassfish.deployment.common.JavaEEResourceType.CFD;
import static org.glassfish.deployment.common.JavaEEResourceType.CSDD;
import static org.glassfish.deployment.common.JavaEEResourceType.DSD;
import static org.glassfish.deployment.common.JavaEEResourceType.JMSCFDD;
import static org.glassfish.deployment.common.JavaEEResourceType.JMSDD;
import static org.glassfish.deployment.common.JavaEEResourceType.MEDD;
import static org.glassfish.deployment.common.JavaEEResourceType.MSD;
import static org.glassfish.deployment.common.JavaEEResourceType.MSEDD;
import static org.glassfish.deployment.common.JavaEEResourceType.MTFDD;

/**
 * This class maintains registry for all resources and used by all Descriptor and BundleDescriptor classes.
 *
 * @author naman
 */
public class ResourceDescriptorRegistry implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final LocalStringManagerImpl I18N = new LocalStringManagerImpl(ResourceDescriptorRegistry.class);

    /**
     * This map contains the list of descriptors for where a particular annotation is not applicable. In future update this
     * list for non applicable descriptor.
     *
     * e.g. ConnectionFactoryDescriptor and AdminObjectDescriptor is not allowed to define at Application Client Descriptor.
     */
    private static Map<JavaEEResourceType, Set<Class<?>>> invalidResourceTypeScopes = new HashMap<>();
    static {
        invalidResourceTypeScopes.put(MSD, new HashSet<>());
        invalidResourceTypeScopes.put(DSD, new HashSet<>());
        invalidResourceTypeScopes.put(JMSCFDD, new HashSet<>());
        invalidResourceTypeScopes.put(JMSDD, new HashSet<>());
        invalidResourceTypeScopes.put(CFD, new HashSet<>(asList(ApplicationClientDescriptor.class)));
        invalidResourceTypeScopes.put(AODD, new HashSet<>(asList(ApplicationClientDescriptor.class)));
        invalidResourceTypeScopes.put(CSDD, new HashSet<>());
        invalidResourceTypeScopes.put(MEDD, new HashSet<>());
        invalidResourceTypeScopes.put(MSEDD, new HashSet<>());
        invalidResourceTypeScopes.put(MTFDD, new HashSet<>());
    }


    private final Map<JavaEEResourceType, Set<ResourceDescriptor>> resourceDescriptors = new HashMap<>();

    /**
     * @return all descriptors associated with the app.
     */
    public Set<ResourceDescriptor> getAllResourcesDescriptors() {
        Set<ResourceDescriptor> allResourceDescriptors = new HashSet<>();
        allResourceDescriptors.addAll(getResourceDescriptors(DSD));
        allResourceDescriptors.addAll(getResourceDescriptors(MSD));
        allResourceDescriptors.addAll(getResourceDescriptors(CFD));
        allResourceDescriptors.addAll(getResourceDescriptors(AODD));
        allResourceDescriptors.addAll(getResourceDescriptors(JMSCFDD));
        allResourceDescriptors.addAll(getResourceDescriptors(JMSDD));
        allResourceDescriptors.addAll(getResourceDescriptors(CSDD));
        allResourceDescriptors.addAll(getResourceDescriptors(MEDD));
        allResourceDescriptors.addAll(getResourceDescriptors(MSEDD));
        allResourceDescriptors.addAll(getResourceDescriptors(MTFDD));
        return allResourceDescriptors;
    }


    /**
     * @param givenClazz - Class which is either AppClientDescriptor, Application etc.
     * @return all valid descriptor for given class. USes 'invalidResourceTypeScopes' to validate
     *         the scope for given class.
     */
    public Set<ResourceDescriptor> getAllResourcesDescriptors(Class<?> givenClazz) {
        Set<ResourceDescriptor> allResourceDescriptors = new HashSet<>();

        for (JavaEEResourceType javaEEResourceType : JavaEEResourceType.values()) {
            Set<Class<?>> invalidClassSet = invalidResourceTypeScopes.get(javaEEResourceType);
            if (!isEmpty(invalidClassSet)) {
                for (Class<?> invalidClass : invalidClassSet) {
                    if (!invalidClass.isAssignableFrom(givenClazz)) {
                        allResourceDescriptors.addAll(getResourceDescriptors(javaEEResourceType));
                    }
                }
            } else if (invalidClassSet != null) {
                allResourceDescriptors.addAll(getResourceDescriptors(javaEEResourceType));
            }
        }

        return allResourceDescriptors;
    }


    /**
     * @return descriptor by name.
     */
    protected ResourceDescriptor getResourcesDescriptor(String name) {
        for (ResourceDescriptor resourceDescriptor : getAllResourcesDescriptors()) {
            if (resourceDescriptor.getName().equals(name)) {
                return resourceDescriptor;
            }
        }

        return null;
    }


    /**
     * Validate descriptor is already defined or not.
     *
     * @param reference
     * @return
     */
    private boolean isDescriptorRegistered(ResourceDescriptor reference) {
        return getResourcesDescriptor(reference.getName()) != null;
    }


    /**
     * @return descriptors based on the Resource Type.
     */
    public Set<ResourceDescriptor> getResourceDescriptors(JavaEEResourceType javaEEResourceType) {
        Set<ResourceDescriptor> resourceDescriptorSet = resourceDescriptors.get(javaEEResourceType);
        if (resourceDescriptorSet == null) {
            resourceDescriptors.put(javaEEResourceType, new HashSet<ResourceDescriptor>());
        }

        return resourceDescriptors.get(javaEEResourceType);
    }


    /**
     * @return descriptors based on resource type and given name.
     */
    protected ResourceDescriptor getResourceDescriptor(JavaEEResourceType javaEEResourceType, String name) {
        for (ResourceDescriptor resourceDescriptor : getResourceDescriptors(javaEEResourceType)) {
            if (resourceDescriptor.getName().equals(name)) {
                return resourceDescriptor;
            }
        }

        return null;
    }


    /**
     * Adding resource descriptor for gvien reference
     *
     * @param reference
     */
    public void addResourceDescriptor(ResourceDescriptor reference) {
        if (isDescriptorRegistered(reference)) {
            throw new IllegalStateException(I18N.getLocalString("exceptionwebduplicatedescriptor",
                "This app cannot have descriptor definitions of same name : [{0}]", reference.getName()));
        }

        Set<ResourceDescriptor> resourceDescriptorSet = getResourceDescriptors(reference.getResourceType());
        resourceDescriptorSet.add(reference);
        resourceDescriptors.put(reference.getResourceType(), resourceDescriptorSet);
    }


    /**
     * Remove resource descriptor based on resource type and given reference
     *
     * @param javaEEResourceType
     * @param reference
     */
    public void removeResourceDescriptor(JavaEEResourceType javaEEResourceType, ResourceDescriptor reference) {
        Set<ResourceDescriptor> resourceDescriptorSet = getResourceDescriptors(reference.getResourceType());
        resourceDescriptorSet.remove(reference);
        resourceDescriptors.put(javaEEResourceType, resourceDescriptorSet);
    }
}