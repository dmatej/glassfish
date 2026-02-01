/*
 * Copyright (c) 2025, 2026 Contributors to the Eclipse Foundation.
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

package org.glassfish.main.jdke;

import java.lang.reflect.Field;

import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactoryBuilder;
import javax.naming.spi.NamingManager;

/**
 * This class collects missing gaps in JDK's public API, which are needed.
 */
public final class JavaApiGaps {

    private static final String FIELD_INITCTX_FACTORY_BUILDER = "initctx_factory_builder";


    private JavaApiGaps() {
        // Prevent instantiation
    }


    /**
     * Sets the InitialContextFactoryBuilder in NamingManager if it was not set yet.
     * <p>
     * This is used to reset the {@link InitialContextFactoryBuilder} when necessary, because the
     * class doesn't have any suitable public method for that.
     *
     * @param builder the InitialContextFactoryBuilder to set
     *
     * @throws NamingException
     * @throws IllegalStateException if the builder was already set
     */
    public static void setInitialContextFactoryBuilder(InitialContextFactoryBuilder builder) throws NamingException {
        NamingManager.setInitialContextFactoryBuilder(builder);
    }


    /**
     * Sets the InitialContextFactoryBuilder in NamingManager to null.
     * <p>
     * This is used to reset the {@link InitialContextFactoryBuilder} when necessary, because the
     * class doesn't have any suitable public method for that.
     * @throws NamingException
     */
    public static void unsetInitialContextFactoryBuilder() throws NamingException {
        set(NamingManager.class, FIELD_INITCTX_FACTORY_BUILDER, null);
    }


    private static void set(Class<?> type, String fieldName, Object value) {
        try {
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Reflection to the field " + type.getCanonicalName() + "." + fieldName + " failed.", e);
        }
    }
}
