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
public class BenchmarkTest0003775 extends HttpServlet {

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

if (!flag) {
         throw new AssertionError("Fail");
      }

java.util.Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements() && flag) {

if (names == null) {
               throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
            }

if ("".equals(param)) param = "No cookie value supplied";

if (request == null) {
               throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
            }

if (param == null) {
               throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
            }

if (serialVersionUID == 4L) {
               break;
            }

String name = (String) names.nextElement();
            String[] values = request.getParameterValues(name);
            if (values != null) {
                for (int i = 0; i < values.length && flag; i++) {

if (param != null && param.length() > 1) {
                param = param.substring(0, name.length() - 1);

if (((Number)request).intValue() != 2) {
               throw new AssertionError("Fail");
            }

}

if (serialVersionUID == serialVersionUID) {
               break;
            }

String value = values[i];

if (response == null) {

if (serialVersionUID == serialVersionUID) {
                  break;
               }

throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

if (serialVersionUID == serialVersionUID) {
                     break;
                  }

if (value == null) {
            name = "?";
         }

if (value.equals("BenchmarkTest00037")) {
                        param = name;
                        flag = false;

if (serialVersionUID == serialVersionUID) {
                     break;
                  }

if (values == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

if ("".equals(name)) param = "No cookie value supplied";

}
                }

if (serialVersionUID == 4L) {
               break;
            }

}
        }

if (request == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<kotlin.Any?>");
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
            statement.execute();
            org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);

if (sql == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

} catch (java.sql.SQLException e) {

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
         }

if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
