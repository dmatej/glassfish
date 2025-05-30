/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 * Copyright (c) 2002, 2020 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.s1asdev.jmsdd;

import com.sun.ejte.ccl.reporter.SimpleReporterAdapter;
import com.sun.enterprise.deployment.*;
import com.sun.enterprise.deployment.archivist.AppClientArchivist;
import com.sun.enterprise.deployment.archivist.ApplicationArchivist;
import com.sun.enterprise.loader.ASURLClassLoader;
import junit.framework.TestCase;
import org.glassfish.deployment.common.JavaEEResourceType;
import org.glassfish.ejb.deployment.archivist.EjbArchivist;
import org.glassfish.ejb.deployment.descriptor.EjbBundleDescriptorImpl;
import org.glassfish.ejb.deployment.descriptor.EjbDescriptor;
import org.glassfish.web.deployment.archivist.WebArchivist;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ArchiveTest extends TestCase {

    String archiveDir = null;
    private static SimpleReporterAdapter stat = new SimpleReporterAdapter("appserv-tests");

    protected void setUp() throws Exception {
        super.setUp();
        TestUtil.setupHK2();
        archiveDir = System.getProperty("ArchiveDir");
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testApplicationArchive() throws Exception {
        String tcName = "jms-destination-definition-application-archive-test";

        try {
            doTestApplicationArchive();
            stat.addStatus(tcName, stat.PASS);
        } catch(Exception e) {
            stat.addStatus(tcName, stat.FAIL);
            throw e;
        }
    }

    private void doTestApplicationArchive() throws IOException, SAXParseException {
        String appArchiveName = "jms-destination-definition-application";
        File archive = new File(archiveDir, appArchiveName);
        assertTrue("Do not find the archive: " + archive.getAbsolutePath(), archive.exists());

        ApplicationArchivist reader = (ApplicationArchivist) TestUtil.getByType(ApplicationArchivist.class);
        reader.setAnnotationProcessingRequested(true);
        ASURLClassLoader classLoader = new ASURLClassLoader(appArchiveName, this.getClass().getClassLoader());
        classLoader.addURL(archive.toURL());
        reader.setClassLoader(classLoader);
        Application applicationDesc = reader.open(archive);

        Map<String, JMSDestinationDefinitionDescriptor> expectedJMSDDDs =
                new HashMap<String, JMSDestinationDefinitionDescriptor>();
        JMSDestinationDefinitionDescriptor desc;

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("global-scope jms destination defined in UT Application DD");
        desc.setName("java:global/env/UT_Application_DD_JMSDestination");
        desc.setClassName("jakarta.jms.Queue");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalQueue");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("application-scope jms destination defined in UT Application DD");
        desc.setName("java:app/env/UT_Application_DD_JMSDestination");
        desc.setClassName("jakarta.jms.Topic");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalTopic");
        expectedJMSDDDs.put(desc.getName(), desc);

        TestUtil.compareJMSDDD(expectedJMSDDDs, applicationDesc.getResourceDescriptors(JavaEEResourceType.JMSDD));
    }

    public void testApplicationClientArchive() throws Exception {
        String tcName = "jms-destination-definition-appclient-archive-test";

        try {
            doTestApplicationClientArchive();
            stat.addStatus(tcName, stat.PASS);
        } catch(Exception e) {
            stat.addStatus(tcName, stat.FAIL);
            throw e;
        }
    }

    private void doTestApplicationClientArchive() throws IOException, SAXParseException {
        String appArchiveName = "jms-destination-definition-appclient";
        File archive = new File(archiveDir, appArchiveName);
        assertTrue("Do not find the archive: " + archive.getAbsolutePath(), archive.exists());

        ASURLClassLoader classLoader = new ASURLClassLoader(appArchiveName, this.getClass().getClassLoader());
        classLoader.addURL(archive.toURL());

        AppClientArchivist reader = (AppClientArchivist)TestUtil.getByType(AppClientArchivist.class);
        reader.setAnnotationProcessingRequested(true);
        reader.setClassLoader(classLoader);
        assertTrue("Archivist should handle annotations.", reader.isAnnotationProcessingRequested());

        ApplicationClientDescriptor appclientDesc = reader.open(archive);

        Map<String, JMSDestinationDefinitionDescriptor> expectedJMSDDDs =
                new HashMap<String, JMSDestinationDefinitionDescriptor>();
        JMSDestinationDefinitionDescriptor desc;

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("global-scope resource to be modified by Appclient DD");
        desc.setName("java:global/env/Appclient_ModByDD_JMSDestination");
        desc.setClassName("jakarta.jms.Topic");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalTopic");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("global-scope resource defined by @JMSDestinationDefinition");
        desc.setName("java:global/env/Appclient_Annotation_JMSDestination");
        desc.setClassName("jakarta.jms.Queue");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalQueue");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("application-scope resource defined by @JMSDestinationDefinition");
        desc.setName("java:app/env/Appclient_Annotation_JMSDestination");
        desc.setClassName("jakarta.jms.Topic");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalTopic");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("module-scope resource defined by @JMSDestinationDefinition");
        desc.setName("java:module/env/Appclient_Annotation_JMSDestination");
        desc.setClassName("jakarta.jms.Topic");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalTopic");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("component-scope resource defined by @JMSDestinationDefinition");
        desc.setName("java:comp/env/Appclient_Annotation_JMSDestination");
        desc.setClassName("jakarta.jms.Queue");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalQueue");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("global-scope resource defined in Appclient DD");
        desc.setName("java:global/env/Appclient_DD_JMSDestination");
        desc.setClassName("jakarta.jms.Queue");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalQueue");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("application-scope resource defined in Appclient DD");
        desc.setName("java:app/env/Appclient_DD_JMSDestination");
        desc.setClassName("jakarta.jms.Topic");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalTopic");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("module-scope resource defined in Appclient DD");
        desc.setName("java:module/env/Appclient_DD_JMSDestination");
        desc.setClassName("jakarta.jms.Topic");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalTopic");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("component-scope resource defined in Appclient DD");
        desc.setName("java:comp/env/Appclient_DD_JMSDestination");
        desc.setClassName("jakarta.jms.Queue");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalQueue");
        expectedJMSDDDs.put(desc.getName(), desc);

        TestUtil.compareJMSDDD(expectedJMSDDDs, appclientDesc.getResourceDescriptors(JavaEEResourceType.JMSDD));
    }

    public void testWebArchive() throws Exception {
        String tcName = "jms-destination-definition-web-archive-test";

        try {
            doTestWebArchive();
            stat.addStatus(tcName, stat.PASS);
        } catch(Exception e) {
            stat.addStatus(tcName, stat.FAIL);
            throw e;
        }
    }

    private void doTestWebArchive() throws IOException, SAXParseException {
        String appArchiveName = "jms-destination-definition-web";
        File archive = new File(archiveDir, appArchiveName);
        assertTrue("Do not find the archive: " + archive.getAbsolutePath(), archive.exists());

        ASURLClassLoader classLoader = new ASURLClassLoader(appArchiveName, this.getClass().getClassLoader());
        classLoader.addURL(archive.toURL());

        WebArchivist reader = (WebArchivist) TestUtil.getByType(WebArchivist.class);
        reader.setAnnotationProcessingRequested(true);
        reader.setClassLoader(classLoader);
        assertTrue("Archivist should handle annotations.", reader.isAnnotationProcessingRequested());

        WebBundleDescriptor webDesc = reader.open(archive);

        Map<String, JMSDestinationDefinitionDescriptor> expectedJMSDDDs =
                new HashMap<String, JMSDestinationDefinitionDescriptor>();
        JMSDestinationDefinitionDescriptor desc;

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("global-scope resource to be modified by Web DD");
        desc.setName("java:global/env/Servlet_ModByDD_JMSDestination");
        desc.setClassName("jakarta.jms.Topic");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalTopic");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("global-scope resource defined by @JMSDestinationDefinition");
        desc.setName("java:global/env/Servlet_JMSDestination");
        desc.setClassName("jakarta.jms.Queue");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalQueue");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("application-scope resource defined by @JMSDestinationDefinition");
        desc.setName("java:app/env/Servlet_JMSDestination");
        desc.setClassName("jakarta.jms.Topic");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalTopic");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("module-scope resource defined by @JMSDestinationDefinition");
        desc.setName("java:module/env/Servlet_JMSDestination");
        desc.setClassName("jakarta.jms.Topic");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalTopic");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("component-scope resource defined by @JMSDestinationDefinition");
        desc.setName("java:comp/env/Servlet_JMSDestination");
        desc.setClassName("jakarta.jms.Queue");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalQueue");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("global-scope resource defined in Web DD");
        desc.setName("java:global/env/Web_DD_JMSDestination");
        desc.setClassName("jakarta.jms.Queue");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalQueue");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("application-scope resource defined in Web DD");
        desc.setName("java:app/env/Web_DD_JMSDestination");
        desc.setClassName("jakarta.jms.Topic");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalTopic");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("module-scope resource defined in Web DD");
        desc.setName("java:module/env/Web_DD_JMSDestination");
        desc.setClassName("jakarta.jms.Topic");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalTopic");
        expectedJMSDDDs.put(desc.getName(), desc);

        desc = new JMSDestinationDefinitionDescriptor();
        desc.setDescription("component-scope resource defined in Web DD");
        desc.setName("java:comp/env/Web_DD_JMSDestination");
        desc.setClassName("jakarta.jms.Queue");
        desc.setResourceAdapter("jmsra");
        desc.setDestinationName("myPhysicalQueue");
        expectedJMSDDDs.put(desc.getName(), desc);

        TestUtil.compareJMSDDD(expectedJMSDDDs, webDesc.getResourceDescriptors(JavaEEResourceType.JMSDD));
    }

    public void testEJBArchive() throws Exception {
        String tcName = "jms-destination-definition-ejb-archive-test";

        try {
            doTestEJBArchive();
            stat.addStatus(tcName, stat.PASS);
        } catch(Exception e) {
            stat.addStatus(tcName, stat.FAIL);
            throw e;
        }
    }

    private void doTestEJBArchive() throws IOException, SAXParseException {
        String appArchiveName = "jms-destination-definition-ejb";
        File archive = new File(archiveDir, appArchiveName);
        assertTrue("Do not find the archive: " + archive.getAbsolutePath(), archive.exists());

        ASURLClassLoader classLoader = new ASURLClassLoader(appArchiveName, this.getClass().getClassLoader());
        classLoader.addURL(archive.toURL());

        EjbArchivist reader = (EjbArchivist) TestUtil.getByType(EjbArchivist.class);
        reader.setClassLoader(classLoader);
        reader.setAnnotationProcessingRequested(true);
        assertTrue("Archivist should handle annotations.", reader.isAnnotationProcessingRequested());

        EjbBundleDescriptorImpl ejbBundleDesc = reader.open(archive);
        Set<ResourceDescriptor> acturalCRDDs = new HashSet<ResourceDescriptor>();
        for (EjbDescriptor ejbDesc : ejbBundleDesc.getEjbs()) {
            acturalCRDDs.addAll(ejbDesc.getResourceDescriptors(JavaEEResourceType.JMSDD));
        }

        Map<String, JMSDestinationDefinitionDescriptor> expectedJMSDDDs =
                new HashMap<String, JMSDestinationDefinitionDescriptor>();
        JMSDestinationDefinitionDescriptor desc;

        // jms-destination in DD for stateful EJB
        {
            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("global-scope resource to be modified by EJB DD");
            desc.setName("java:global/env/HelloStatefulEJB_ModByDD_JMSDestination");
            desc.setClassName("jakarta.jms.Topic");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalTopic");
            expectedJMSDDDs.put(desc.getName(), desc);

            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("global-scope resource defined in EJB DD");
            desc.setName("java:global/env/HelloStatefulEJB_DD_JMSDestination");
            desc.setClassName("jakarta.jms.Queue");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalQueue");
            expectedJMSDDDs.put(desc.getName(), desc);

            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("application-scope resource defined in EJB DD");
            desc.setName("java:app/env/HelloStatefulEJB_DD_JMSDestination");
            desc.setClassName("jakarta.jms.Topic");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalTopic");
            expectedJMSDDDs.put(desc.getName(), desc);

            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("module-scope resource defined in EJB DD");
            desc.setName("java:module/env/HelloStatefulEJB_DD_JMSDestination");
            desc.setClassName("jakarta.jms.Topic");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalTopic");
            expectedJMSDDDs.put(desc.getName(), desc);

            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("component-scope resource defined in EJB DD");
            desc.setName("java:comp/env/HelloStatefulEJB_DD_JMSDestination");
            desc.setClassName("jakarta.jms.Queue");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalQueue");
            expectedJMSDDDs.put(desc.getName(), desc);
        }

        // jms-destination in DD for stateless EJB
        {
            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("global-scope resource to be modified by EJB DD");
            desc.setName("java:global/env/HelloEJB_ModByDD_JMSDestination");
            desc.setClassName("jakarta.jms.Topic");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalTopic");
            expectedJMSDDDs.put(desc.getName(), desc);

            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("global-scope resource defined in EJB DD");
            desc.setName("java:global/env/HelloEJB_DD_JMSDestination");
            desc.setClassName("jakarta.jms.Queue");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalQueue");
            expectedJMSDDDs.put(desc.getName(), desc);

            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("application-scope resource defined in EJB DD");
            desc.setName("java:app/env/HelloEJB_DD_JMSDestination");
            desc.setClassName("jakarta.jms.Topic");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalTopic");
            expectedJMSDDDs.put(desc.getName(), desc);

            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("module-scope resource defined in EJB DD");
            desc.setName("java:module/env/HelloEJB_DD_JMSDestination");
            desc.setClassName("jakarta.jms.Topic");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalTopic");
            expectedJMSDDDs.put(desc.getName(), desc);

            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("component-scope resource defined in EJB DD");
            desc.setName("java:comp/env/HelloEJB_DD_JMSDestination");
            desc.setClassName("jakarta.jms.Queue");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalQueue");
            expectedJMSDDDs.put(desc.getName(), desc);
        }

        // jms-destination in annotation for stateful EJB
        {
            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("global-scope resource defined by @JMSDestinationDefinition");
            desc.setName("java:global/env/HelloStatefulEJB_Annotation_JMSDestination");
            desc.setClassName("jakarta.jms.Queue");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalQueue");
            expectedJMSDDDs.put(desc.getName(), desc);

            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("application-scope resource defined by @JMSDestinationDefinition");
            desc.setName("java:app/env/HelloStatefulEJB_Annotation_JMSDestination");
            desc.setClassName("jakarta.jms.Topic");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalTopic");
            expectedJMSDDDs.put(desc.getName(), desc);

            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("module-scope resource defined by @JMSDestinationDefinition");
            desc.setName("java:module/env/HelloStatefulEJB_Annotation_JMSDestination");
            desc.setClassName("jakarta.jms.Topic");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalTopic");
            expectedJMSDDDs.put(desc.getName(), desc);

            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("component-scope resource defined by @JMSDestinationDefinition");
            desc.setName("java:comp/env/HelloStatefulEJB_Annotation_JMSDestination");
            desc.setClassName("jakarta.jms.Queue");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalQueue");
            expectedJMSDDDs.put(desc.getName(), desc);
        }

        // jms-destination in annotation for stateless EJB
        {
            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("global-scope resource defined by @JMSDestinationDefinition");
            desc.setName("java:global/env/HelloEJB_Annotation_JMSDestination");
            desc.setClassName("jakarta.jms.Queue");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalQueue");
            expectedJMSDDDs.put(desc.getName(), desc);

            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("application-scope resource defined by @JMSDestinationDefinition");
            desc.setName("java:app/env/HelloEJB_Annotation_JMSDestination");
            desc.setClassName("jakarta.jms.Topic");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalTopic");
            expectedJMSDDDs.put(desc.getName(), desc);

            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("module-scope resource defined by @JMSDestinationDefinition");
            desc.setName("java:module/env/HelloEJB_Annotation_JMSDestination");
            desc.setClassName("jakarta.jms.Topic");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalTopic");
            expectedJMSDDDs.put(desc.getName(), desc);

            desc = new JMSDestinationDefinitionDescriptor();
            desc.setDescription("component-scope resource defined by @JMSDestinationDefinition");
            desc.setName("java:comp/env/HelloEJB_Annotation_JMSDestination");
            desc.setClassName("jakarta.jms.Queue");
            desc.setResourceAdapter("jmsra");
            desc.setDestinationName("myPhysicalQueue");
            expectedJMSDDDs.put(desc.getName(), desc);
        }

        TestUtil.compareJMSDDD(expectedJMSDDDs, acturalCRDDs);
    }
}
