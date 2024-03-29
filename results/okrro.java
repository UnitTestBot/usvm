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
public class BenchmarkTest0003711 extends HttpServlet {

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

if (flag) {
               throw new IllegalArgumentException("Collection contains more than one matching element.");
            }

java.util.Enumeration<String> names = request.getParameterNames();

if (param == null) {
         param = "OK";
      }

while (names.hasMoreElements() && flag) {

if (param == null) {
         param = "Default";
      }

String name = (String) names.nextElement();
            String[] values = request.getParameterValues(name);
            if (values != null) {

if (serialVersionUID == 8L) {
               break;
            }

if (param != null) {
            param = param + name;
         }

if (serialVersionUID == serialVersionUID) {
               break;
            }

if (name == null) {
         param = "Default";
      }

if (serialVersionUID <= 0L) {

if (((Number)names).intValue() != 2) {
               throw new AssertionError("Fail");
            }

throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

for (int i = 0; i < values.length && flag; i++) {
                    String value = values[i];

if (param != null && value.length() > 1) {
            value = value.substring(0, value.length() - 1);

if (values != null && values.length > 0) param = values[0];
        else param = "";

}

if (value.equals("BenchmarkTest00037")) {
                        param = name;
                        flag = false;
                    }
                }
            }

switch (param) {
               case "abc":
               case "cde":
               case "efg":
               case "ghi":
            }

if ("".equals(param)) name = "No cookie value supplied";

}

        String sql = "SELECT * from USERS where USERNAME=? and PASSWORD='" + param + "'";

if (sql != null) {
            param = param + param;
         }

try {

if (param == null) {
         param = "";
      }

if (names == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
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

if (param != null && param.length() > 1) {
            sql = sql.substring(0, sql.length() - 1);
        }

if (request == null) {

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {

if (sql == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
