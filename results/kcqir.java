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
public class BenchmarkTest0003740 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

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

if (param != null) {
            param = param + param;
         }

if (param != null && param.length() > 1) {
            param = param.substring(0, param.length() - 1);
        }

while (names.hasMoreElements() && flag) {

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

String name = (String) names.nextElement();

if ("".equals(param)) param = "No cookie value supplied";

String[] values = request.getParameterValues(name);

if (serialVersionUID == serialVersionUID) {
                     break;
                  }

if (serialVersionUID == 8L) {
               break;
            }

if (values != null) {

if (serialVersionUID == serialVersionUID) {
                  break;
               }

if (!(names instanceof Number)) {
                     throw new IllegalStateException();
                  }

for (int i = 0; i < values.length && flag; i++) {
                    String value = values[i];

if (request.getHeader("Referer") != null) {

if (param == null) {
         param = "OK";
      }

param = request.getHeader("Referer");
        }

if (value.equals("BenchmarkTest00037")) {
                        param = name;
                        flag = false;
                    }

if (value == null) {
         param = "";
      }

if (param == null) {

if (values != null && values.length > 0) name = values[0];

if (flag) {
               throw new IllegalArgumentException("Collection contains more than one matching element.");
            }

else name = "";

value = "";
      }

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

}

if (name == null) {
         param = "Default";

if (values == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

}

if (serialVersionUID == 2L) {
               break;
            }

}
        }

        String sql = "SELECT * from USERS where USERNAME=? and PASSWORD='" + param + "'";

        try {

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

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
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
