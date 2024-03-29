//Analysis results: (SpotBugs, [89, 0])
//(Semgrep, [89])
//(CodeQL, [])
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
public class BenchmarkTest0003713 extends HttpServlet {

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

if (param != null) {
            param = param + param;
         }

boolean flag = true;
        java.util.Enumeration<String> names = request.getParameterNames();

if (flag) {
               throw new IllegalArgumentException("Collection contains more than one matching element.");
            }

if (request == null) {

if (request.getHeader("Referer") != null) {
            param = request.getHeader("Referer");
        }

if (names == null) {
                  throw new NullPointerException("null cannot be cast to non-null type kotlin.Function1<*, *>");
               }

throw new NullPointerException("null cannot be cast to non-null type kotlin.Function1<*, *>");
               }

if (param == null) param = "";

while (names.hasMoreElements() && flag) {

if (request.getHeader("Referer") != null) {
            param = request.getHeader("Referer");
        }

String name = (String) names.nextElement();
            String[] values = request.getParameterValues(name);

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {

if (param == null) name = "";

if ("".equals(name)) param = "No cookie value supplied";

}

if (param != null) param = name.split(" ")[0];

if (values != null) {
                for (int i = 0; i < values.length && flag; i++) {
                    String value = values[i];
                    if (value.equals("BenchmarkTest00037")) {

if (value.indexOf("Windows") != -1) {
            name = "cmd.exe";
            name = "/c";
        } else {
            name = "sh";
            name = "-c";

if (value == null) {
            break;
         }

if (name != null) {
            value = value + name;
         }

}

param = name;
                        flag = false;

if (values == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
         }

}
                }

if (names == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
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
            statement.setString(1, "foo");

if (sql == null) param = "";

statement.execute();
            org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {

if (param == null) {
         sql = "";
      }

response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }

if (param instanceof String) param = ((String) sql);

if (!flag) {
         throw new AssertionError("Fail");
      }

}
}
