/*
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

/**
 * This generated bean class Loadbalancer matches the schema element loadbalancer
 *
 * Generated on Wed Jul 13 18:07:29 IST 2005
 *
 * This class matches the root element of the DTD,
 * and is the root of the following bean graph:
 *
 *    loadbalancer : Loadbalancer
 *        cluster : Cluster[0,n]
 *            [attr: name CDATA #REQUIRED ]
 *            [attr: policy CDATA round-robin]
 *            [attr: policy-module CDATA ]
 *            instance : Boolean[0,n]
 *                [attr: name CDATA #REQUIRED ]
 *                [attr: enabled CDATA true]
 *                [attr: disable-timeout-in-minutes CDATA 31]
 *                [attr: listeners CDATA #REQUIRED ]
 *                [attr: weight CDATA 100]
 *                EMPTY : String
 *            web-module : WebModule[0,n]
 *                [attr: context-root CDATA #REQUIRED ]
 *                [attr: enabled CDATA true]
 *                [attr: disable-timeout-in-minutes CDATA 31]
 *                [attr: error-url CDATA ]
 *                idempotent-url-pattern : Boolean[0,n]
 *                    [attr: url-pattern CDATA #REQUIRED ]
 *                    [attr: no-of-retries CDATA -1]
 *                    EMPTY : String
 *            health-checker : Boolean?
 *                [attr: url CDATA /]
 *                [attr: interval-in-seconds CDATA 30]
 *                [attr: timeout-in-seconds CDATA 10]
 *                EMPTY : String
 *        property2 : Property[0,n]
 *            [attr: name CDATA #REQUIRED ]
 *            [attr: value CDATA #REQUIRED ]
 *            description : String?
 *
 */
package org.glassfish.loadbalancer.admin.cli.beans;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Vector;

import org.netbeans.modules.schema2beans.AttrProp;
import org.netbeans.modules.schema2beans.Common;
import org.netbeans.modules.schema2beans.GraphManager;
import org.netbeans.modules.schema2beans.Schema2BeansException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

// BEGIN_NOI18N
public class Loadbalancer extends org.netbeans.modules.schema2beans.BaseBean {

    static Vector comparators = new Vector();
    static public final String CLUSTER = "Cluster";    // NOI18N
    static public final String PROPERTY2 = "Property2";    // NOI18N

    public Loadbalancer() {
        this(null, Common.USE_DEFAULT_VALUES);
    }

    public Loadbalancer(org.w3c.dom.Node doc, int options) {
        this(Common.NO_DEFAULT_VALUES);
        try {
            initFromNode(doc, options);
        } catch (Schema2BeansException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }

    protected void initFromNode(org.w3c.dom.Node doc, int options) throws Schema2BeansException {
        if (doc == null) {
            doc = GraphManager.createRootElementNode("loadbalancer");    // NOI18N
            if (doc == null) {
                throw new Schema2BeansException(Common.getMessage(
                        "CantCreateDOMRoot_msg", "loadbalancer"));
            }
        }
        Node n = GraphManager.getElementNode("loadbalancer", doc);    // NOI18N
        if (n == null) {
            throw new Schema2BeansException(Common.getMessage(
                    "DocRootNotInDOMGraph_msg", "loadbalancer", doc.getFirstChild().getNodeName()));
        }

        this.graphManager.setXmlDocument(doc);

        // Entry point of the createBeans() recursive calls
        this.createBean(n, this.graphManager());
        this.initialize(options);
    }

    public Loadbalancer(int options) {
        super(comparators, new org.netbeans.modules.schema2beans.Version(1, 2, 0));
        initOptions(options);
    }

    protected void initOptions(int options) {
        // The graph manager is allocated in the bean root
        this.graphManager = new GraphManager(this);
        this.createRoot("loadbalancer", "Loadbalancer", // NOI18N
                Common.TYPE_1 | Common.TYPE_BEAN, Loadbalancer.class);

        // Properties (see root bean comments for the bean graph)
        this.createProperty("cluster", // NOI18N
                CLUSTER,
                Common.TYPE_0_N | Common.TYPE_BEAN | Common.TYPE_KEY,
                Cluster.class);
        this.createAttribute(CLUSTER, "name", "Name",
                AttrProp.CDATA | AttrProp.REQUIRED,
                null, null);
        this.createAttribute(CLUSTER, "policy", "Policy",
                AttrProp.CDATA,
                null, "round-robin");
        this.createAttribute(CLUSTER, "policy-module", "PolicyModule",
                AttrProp.CDATA,
                null, "");
        this.createProperty("property", // NOI18N
                PROPERTY2,
                Common.TYPE_0_N | Common.TYPE_BEAN | Common.TYPE_KEY,
                Property.class);
        this.createAttribute(PROPERTY2, "name", "Name",
                AttrProp.CDATA | AttrProp.REQUIRED,
                null, null);
        this.createAttribute(PROPERTY2, "value", "Value",
                AttrProp.CDATA | AttrProp.REQUIRED,
                null, null);
        this.initialize(options);
    }

    // Setting the default values of the properties
    void initialize(int options) {
    }

    // This attribute is an array, possibly empty
    public void setCluster(int index, Cluster value) {
        this.setValue(CLUSTER, index, value);
    }

    //
    public Cluster getCluster(int index) {
        return (Cluster) this.getValue(CLUSTER, index);
    }

    // This attribute is an array, possibly empty
    public void setCluster(Cluster[] value) {
        this.setValue(CLUSTER, value);
    }

    //
    public Cluster[] getCluster() {
        return (Cluster[]) this.getValues(CLUSTER);
    }

    // Return the number of properties
    public int sizeCluster() {
        return this.size(CLUSTER);
    }

    // Add a new element returning its index in the list
    public int addCluster(org.glassfish.loadbalancer.admin.cli.beans.Cluster value) {
        return this.addValue(CLUSTER, value);
    }

    //
    // Remove an element using its reference
    // Returns the index the element had in the list
    //
    public int removeCluster(org.glassfish.loadbalancer.admin.cli.beans.Cluster value) {
        return this.removeValue(CLUSTER, value);
    }

    // This attribute is an array, possibly empty
    public void setProperty2(int index, Property value) {
        this.setValue(PROPERTY2, index, value);
    }

    //
    public Property getProperty2(int index) {
        return (Property) this.getValue(PROPERTY2, index);
    }

    // This attribute is an array, possibly empty
    public void setProperty2(Property[] value) {
        this.setValue(PROPERTY2, value);
    }

    //
    public Property[] getProperty2() {
        return (Property[]) this.getValues(PROPERTY2);
    }

    // Return the number of properties
    public int sizeProperty2() {
        return this.size(PROPERTY2);
    }

    // Add a new element returning its index in the list
    public int addProperty2(org.glassfish.loadbalancer.admin.cli.beans.Property value) {
        return this.addValue(PROPERTY2, value);
    }

    //
    // Remove an element using its reference
    // Returns the index the element had in the list
    //
    public int removeProperty2(org.glassfish.loadbalancer.admin.cli.beans.Property value) {
        return this.removeValue(PROPERTY2, value);
    }

    //
    public static void addComparator(org.netbeans.modules.schema2beans.BeanComparator c) {
        comparators.add(c);
    }

    //
    public static void removeComparator(org.netbeans.modules.schema2beans.BeanComparator c) {
        comparators.remove(c);
    }
    //
    // This method returns the root of the bean graph
    // Each call creates a new bean graph from the specified DOM graph
    //

    public static Loadbalancer createGraph(org.w3c.dom.Node doc) {
        return new Loadbalancer(doc, Common.NO_DEFAULT_VALUES);
    }

    public static Loadbalancer createGraph(java.io.InputStream in) {
        return createGraph(in, false);
    }

    public static Loadbalancer createGraph(java.io.InputStream in, boolean validate) {
        try {
            Document doc = GraphManager.createXmlDocument(in, validate);
            return createGraph(doc);
        } catch (Exception t) {
            t.printStackTrace();
            throw new RuntimeException(Common.getMessage(
                    "DOMGraphCreateFailed_msg",
                    t.getMessage()));
        }
    }

    //
    // This method returns the root for a new empty bean graph
    //
    public static Loadbalancer createGraph() {
        return new Loadbalancer();
    }

    public void validate() throws org.netbeans.modules.schema2beans.ValidateException {
    }

    // Special serializer: output XML as serialization
    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        write(baos);
        String str = baos.toString();
        out.writeUTF(str);
    }
    // Special deserializer: read XML as deserialization

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        try {
            init(comparators, new org.netbeans.modules.schema2beans.Version(1, 2, 0));
            String strDocument = in.readUTF();
            ByteArrayInputStream bais = new ByteArrayInputStream(strDocument.getBytes());
            Document doc = GraphManager.createXmlDocument(bais, false);
            initOptions(Common.NO_DEFAULT_VALUES);
            initFromNode(doc, Common.NO_DEFAULT_VALUES);
        } catch (Schema2BeansException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }

    // Dump the content of this bean returning it as a String
    @Override
    public void dump(StringBuffer str, String indent) {
        String s;
        Object o;
        org.netbeans.modules.schema2beans.BaseBean n;
        str.append(indent);
        str.append("Cluster[" + this.sizeCluster() + "]");    // NOI18N
        for (int i = 0; i < this.sizeCluster(); i++) {
            str.append(indent + "\t");
            str.append("#" + i + ":");
            n = (org.netbeans.modules.schema2beans.BaseBean) this.getCluster(i);
            if (n != null) {
                n.dump(str, indent + "\t");    // NOI18N
            } else {
                str.append(indent + "\tnull");    // NOI18N
            }
            this.dumpAttributes(CLUSTER, i, str, indent);
        }

        str.append(indent);
        str.append("Property2[" + this.sizeProperty2() + "]");    // NOI18N
        for (int i = 0; i < this.sizeProperty2(); i++) {
            str.append(indent + "\t");
            str.append("#" + i + ":");
            n = (org.netbeans.modules.schema2beans.BaseBean) this.getProperty2(i);
            if (n != null) {
                n.dump(str, indent + "\t");    // NOI18N
            } else {
                str.append(indent + "\tnull");    // NOI18N
            }
            this.dumpAttributes(PROPERTY2, i, str, indent);
        }

    }

    @Override
    public String dumpBeanNode() {
        StringBuffer str = new StringBuffer();
        str.append("Loadbalancer\n");    // NOI18N
        this.dump(str, "\n  ");    // NOI18N
        return str.toString();
    }
}
// END_NOI18N

