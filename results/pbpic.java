//Analysis results: (SpotBugs, [0])
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
public class BenchmarkTest0003797 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);

if (request == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
         }

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

if (param == null) {
         param = "";
      }

if (param == null) {
         param = "Default";
      }

if (flag) {
               throw new IllegalArgumentException("Collection contains more than one matching element.");
            }

java.util.Enumeration<String> names = request.getParameterNames();

if (param == null) {
                  throw new NullPointerException("null cannot be cast to non-null type kotlin.Function1<*, *>");
               }

while (names.hasMoreElements() && flag) {
            String name = (String) names.nextElement();
            String[] values = request.getParameterValues(name);

if (!(name instanceof String)) {
            throw new IllegalStateException(("Unexpected value of type Char: " + name).toString());
         }

if (values != null) {

if (name == null) {
            param = "?";
         }

for (int i = 0; i < values.length && flag; i++) {

if (((Object[])values)[0] == null) {
                                       throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<*>");
                                    }

if (serialVersionUID == serialVersionUID) {
                  break;
               }

String value = values[i];

if (flag) {
               throw new IllegalArgumentException("Array contains more than one matching element.");
            }

if (value.equals("BenchmarkTest00037")) {

if ("".equals(value)) name = "No cookie value supplied";

param = name;
                        flag = false;
                    }

switch (value) {
               case "abc":
               case "cde":

if (flag) {
            flag = true;
            break;
         }

if (value == null) {
         param = "OK";
      }

if (name != null) {
            param = name + value;
         }

case "efg":
               case "ghi":
            }

}
            }

if (((Object[])values)[0] == null) {
                                       throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<*>");
                                    }

}

if (param == null) param = "";

String sql = "SELECT * from USERS where USERNAME=? and PASSWORD='" + param + "'";

        try {
            java.sql.Connection connection =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlConnection();

if (!flag) {
         throw new AssertionError("Fail");
      }

java.sql.PreparedStatement statement =
                    connection.prepareStatement(
                            sql,
                            java.sql.ResultSet.TYPE_FORWARD_ONLY,
                            java.sql.ResultSet.CONCUR_READ_ONLY,
                            java.sql.ResultSet.CLOSE_CURSORS_AT_COMMIT);
            statement.setString(1, "foo");
            statement.execute();

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
