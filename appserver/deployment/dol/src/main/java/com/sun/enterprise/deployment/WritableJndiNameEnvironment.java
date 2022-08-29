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

package com.sun.enterprise.deployment;

import com.sun.enterprise.deployment.core.ResourceDescriptor;

/**
 * Objects implementing this interface allow their
 * environment properties, ejb references and resource
 * references to be written.
 *
 * @author Danny Coward
 */
public interface WritableJndiNameEnvironment extends JndiNameEnvironment {

    /**
     * Adds the specified environment property to the receiver.
     *
     * @param environmentProperty the EnvironmentProperty to add.
     */
    void addEnvironmentProperty(EnvironmentProperty environmentProperty);

    /**
     * Removes the specified environment property from receiver.
     *
     * @param environmentProperty the EnvironmentProperty to remove.
     */
    void removeEnvironmentProperty(EnvironmentProperty environmentProperty);

    /**
     * Adds the specified ejb reference to the receiver.
     *
     * @param ejbReference the EjbReferenceDescriptor to add.
     */
    void addEjbReferenceDescriptor(EjbReferenceDescriptor ejbReference);

    /**
     * Removes the specificed ejb reference from the receiver.
     *
     * @param ejbReference the EjbReferenceDescriptor to remove.
     */
    void removeEjbReferenceDescriptor(EjbReferenceDescriptor ejbReference);

    /**
     * Adds the specified resource reference to the receiver.
     *
     * @param resourceReference the ResourceReferenceDescriptor to add.
     */
    void addResourceReferenceDescriptor(ResourceReferenceDescriptor resourceReference);

    /**
     * Removes the specified resource reference from the receiver.
     *
     * @param resourceReference the ResourceReferenceDescriptor to remove.
     */
    void removeResourceReferenceDescriptor(ResourceReferenceDescriptor resourceReference);

    /**
     * Adds the specified resource environment reference to the receiver.
     *
     * @param resourceEnvReference the ResourceEnvReferenceDescriptor to add.
     */
    void addResourceEnvReferenceDescriptor(ResourceEnvReferenceDescriptor resourceEnvReference);

    /**
     * Removes the specified resource environment reference from the receiver.
     *
     * @param resourceEnvReference the ResourceEnvReferenceDescriptor to remove.
     */
    void removeResourceEnvReferenceDescriptor(ResourceEnvReferenceDescriptor resourceEnvReference);

    /**
     * Adds the specified message destination reference to the receiver.
     *
     * @param msgDestRef the MessageDestinationReferenceDescriptor to add.
     */
    void addMessageDestinationReferenceDescriptor(MessageDestinationReferenceDescriptor msgDestRef);

    /**
     * Removes the specified message destination reference from the receiver.
     *
     * @param msgDestRef ref MessageDestinationReferenceDescriptor to remove.
     */
    void removeMessageDestinationReferenceDescriptor(MessageDestinationReferenceDescriptor msgDestRef);

    /**
     * Adds the specified post-construct descriptor to the receiver.
     *
     * @param postConstructDesc the post-construct LifecycleCallbackDescriptor to add.
     */
    void addPostConstructDescriptor(LifecycleCallbackDescriptor postConstructDesc);

    /**
     * Adds the specified pre-destroy descriptor to the receiver.
     *
     * @param preDestroyDesc the pre-destroy LifecycleCallbackDescriptor to add.
     */
    void addPreDestroyDescriptor(LifecycleCallbackDescriptor preDestroyDesc);

    /**
     * Adds the specified service reference to the receiver.
     *
     * @param serviceReference the ServiceReferenceDescriptor to add.
     */
    void addServiceReferenceDescriptor(ServiceReferenceDescriptor serviceReference);

    /**
     * Removes the specified service reference from the receiver.
     *
     * @param serviceReference the ServiceReferenceDescriptor to remove.
     */
    void removeServiceReferenceDescriptor(ServiceReferenceDescriptor serviceReference);

    void addEntityManagerFactoryReferenceDescriptor(EntityManagerFactoryReferenceDescriptor reference);

    void addEntityManagerReferenceDescriptor(EntityManagerReferenceDescriptor reference);

    /**
     * Adds the specified descriptor to the receiver.
     *
     * @param reference Descriptor to add.
     */
    void addResourceDescriptor(ResourceDescriptor reference);

    /**
     * Removes the specified descriptor from the receiver.
     *
     * @param reference Descriptor to remove.
     */
    void removeResourceDescriptor(ResourceDescriptor reference);
}

