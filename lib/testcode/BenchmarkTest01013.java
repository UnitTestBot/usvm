/**
 * OWASP Benchmark Project v1.2
 *
 * <p>This file is part of the Open Web Application Security Project (OWASP) Benchmark Project. For
 * details, please see <a
 * href="https://owasp.org/www-project-benchmark/">https://owasp.org/www-project-benchmark/</a>.
 *
 * <p>The OWASP Benchmark is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, version 2.
 *
 * <p>The OWASP Benchmark is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * @author Dave Wichers
 * @created 2015
 */
package org.owasp.benchmark.testcode;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(value = "/xpathi-00/BenchmarkTest01013")
public class BenchmarkTest01013 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        javax.servlet.http.Cookie userCookie =
                new javax.servlet.http.Cookie("BenchmarkTest01013", "2222");
        userCookie.setMaxAge(60 * 3); // Store cookie for 3 minutes
        userCookie.setSecure(true);
        userCookie.setPath(request.getRequestURI());
        userCookie.setDomain(new java.net.URL(request.getRequestURL().toString()).getHost());
        response.addCookie(userCookie);
        javax.servlet.RequestDispatcher rd =
                request.getRequestDispatcher("/xpathi-00/BenchmarkTest01013.html");
        rd.include(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");

        javax.servlet.http.Cookie[] theCookies = request.getCookies();

        String param = "noCookieValueSupplied";
        if (theCookies != null) {
            for (javax.servlet.http.Cookie theCookie : theCookies) {
                if (theCookie.getName().equals("BenchmarkTest01013")) {
                    param = java.net.URLDecoder.decode(theCookie.getValue(), "UTF-8");
                    break;
                }
            }
        }

        String bar = new Test().doSomething(request, param);

        try {
            java.io.FileInputStream file =
                    new java.io.FileInputStream(
                            org.owasp.benchmark.helpers.Utils.getFileFromClasspath(
                                    "employees.xml", this.getClass().getClassLoader()));
            javax.xml.parsers.DocumentBuilderFactory builderFactory =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            // Prevent XXE
            builderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            javax.xml.parsers.DocumentBuilder builder = builderFactory.newDocumentBuilder();
            org.w3c.dom.Document xmlDocument = builder.parse(file);
            javax.xml.xpath.XPathFactory xpf = javax.xml.xpath.XPathFactory.newInstance();
            javax.xml.xpath.XPath xp = xpf.newXPath();

            String expression = "/Employees/Employee[@emplid='" + bar + "']";
            org.w3c.dom.NodeList nodeList =
                    (org.w3c.dom.NodeList)
                            xp.compile(expression)
                                    .evaluate(xmlDocument, javax.xml.xpath.XPathConstants.NODESET);

            response.getWriter().println("Your query results are: <br/>");

            for (int i = 0; i < nodeList.getLength(); i++) {
                org.w3c.dom.Element value = (org.w3c.dom.Element) nodeList.item(i);
                response.getWriter().println(value.getTextContent() + "<br/>");
            }
        } catch (javax.xml.xpath.XPathExpressionException
                | javax.xml.parsers.ParserConfigurationException
                | org.xml.sax.SAXException e) {
            response.getWriter()
                    .println(
                            "Error parsing XPath input: '"
                                    + org.owasp.esapi.ESAPI.encoder().encodeForHTML(bar)
                                    + "'");
            throw new ServletException(e);
        }
    } // end doPost

    private class Test {

        public String doSomething(HttpServletRequest request, String param)
                throws ServletException, IOException {

            String bar;

            // Simple ? condition that assigns constant to bar on true condition
            int num = 106;

            bar = (7 * 18) + num > 200 ? "This_should_always_happen" : param;

            return bar;
        }
    } // end innerclass Test
} // end DataflowThruInnerClass
