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
 * @author Nick Sanidas
 * @created 2015
 */
package org.owasp.benchmark.testcode;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(value = "/xpathi-00/BenchmarkTest00442")
public class BenchmarkTest00442 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");

        String param = request.getParameter("BenchmarkTest00442");
        if (param == null) param = "";

        String bar;

        // Simple ? condition that assigns param to bar on false condition
        int num = 106;

        bar = (7 * 42) - num > 200 ? "This should never happen" : param;

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
            String result = xp.evaluate(expression, xmlDocument);

            response.getWriter().println("Your query results are: " + result + "<br/>");

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
    }
}
