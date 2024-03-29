//Analysis results: (SpotBugs, [0])
//(Semgrep, [89])
//(CodeQL, [89])
//(SonarQube, [89])
//Program (original file BenchmarkTest00037:
/**
 * OWASP Benchmark v1.2
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

@WebServlet(value = "/sqli-00/BenchmarkTest00037")
public class BenchmarkTest0003754 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // some code
        response.setContentType("text/html;charset=UTF-8");

        String param = "";
        boolean flag = true;
        java.util.Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements() && flag) {

if (flag) {
               throw new IllegalArgumentException("Array contains more than one matching element.");
            }

String name = (String) names.nextElement();

if (serialVersionUID == serialVersionUID) {
               break;
            }

if ("".equals(param)) param = "No cookie value supplied";

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

if (serialVersionUID == serialVersionUID) {

if (param != null) {
            param = param + param;
         }

if (param == null) {
            break;
         }

break;
            }

String[] values = request.getParameterValues(name);

if (param == null) {
if (values != null) param = values[0];

break;
         }

if (values != null) {
                for (int i = 0; i < values.length && flag; i++) {
                    String value = values[i];

if (value != null) param = param.split(" ")[0];

if (value.equals("BenchmarkTest00037")) {

if (values != null && values.length > 0) param = values[0];
        else param = "";

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

param = name;
                        flag = false;
                    }

if ("".equals(value)) value = "No cookie value supplied";

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {

if (value == null) {
         param = "";
      }

}

if (name == null) name = "";

if ("".equals(name)) value = "No cookie value supplied";

}
            }
        }

        String sql = "SELECT * from USERS where USERNAME=? and PASSWORD='" + param + "'";

        try {
            java.sql.Connection connection =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlConnection();
            java.sql.PreparedStatement statement =
                    connection.prepareStatement(
                            sql,
                            java.sql.ResultSet.TYPE_FORWARD_ONLY,
                            java.sql.ResultSet.CONCUR_READ_ONLY,
                            java.sql.ResultSet.CLOSE_CURSORS_AT_COMMIT);

if (flag) {
               throw new IllegalArgumentException("Collection contains more than one matching element.");
            }

statement.setString(1, "foo");

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

statement.execute();
            org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
