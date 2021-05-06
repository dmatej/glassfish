/*
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

package test;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.WebFilter;

@WebFilter("/*")
public class FilterTest2 implements Filter{

    public void destroy() {
        System.out.println("[Filter.destroy]");
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws java.io.IOException, jakarta.servlet.ServletException {
        System.out.println("[Filter.doFilter]");

        ((HttpServletRequest)request).getSession(true).setAttribute("FILTER2", "PASS");
        filterChain.doFilter(request, response);

    }

    public void init(jakarta.servlet.FilterConfig filterConfig) throws jakarta.servlet.ServletException {
        System.out.println("[Filter.init]");
    }
}
