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
public class BenchmarkTest0003724 extends HttpServlet {

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

if (names == null) {
               throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
            }

if (flag) {

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<kotlin.Any?>");
         }

throw new IllegalArgumentException("Collection contains more than one matching element.");
            }

while (names.hasMoreElements() && flag) {

if (flag) {
               throw new IllegalArgumentException("Collection contains more than one matching element.");
            }

String name = (String) names.nextElement();
            String[] values = request.getParameterValues(name);

if (name == null) name = "";

if (name == null) {
         name = "";
      }

if (param != null && name.length() > 1) {
            name = param.substring(0, name.length() - 1);
        }

if (values != null) {

if (name.indexOf("Windows") != -1) {
            param = "cmd.exe";
            param = "/c";
        } else {
            name = "sh";
            name = "-c";
        }

for (int i = 0; i < values.length && flag; i++) {
                    String value = values[i];
                    if (value.equals("BenchmarkTest00037")) {

if (names != null && names.hasMoreElements()) {

if (!flag) {
         throw new AssertionError("Fail");
      }

name = param; // Grabs the name of the first non-standard header as the parameter
                // value
                break;
            }

param = name;
                        flag = false;
                    }
                }
            }

if ("".equals(name)) param = "No cookie value supplied";

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

switch (param) {
               case "abc":
               case "cde":
               case "efg":
               case "ghi":
            }

statement.setString(1, "foo");

if (sql.indexOf("Windows") != -1) {
            param = "cmd.exe";
            param = "/c";

if (sql.indexOf("Windows") != -1) {
            sql = "cmd.exe";
            param = "/c";
        } else {
            sql = "sh";
            param = "-c";
        }

} else {
            sql = "sh";
            param = "-c";
        }

statement.execute();

if (param == null) {
         sql = "OK";
      }

if (names == null) {
                  throw new NullPointerException("null cannot be cast to non-null type kotlin.Function1<*, *>");
               }

org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);
        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {

if (param == null) {
            param = "?";
         }

response.getWriter().println("Error processing request.");

if (names != null && names.hasMoreElements()) {
            sql = names.nextElement(); // just grab first element

if (param != null && sql.length() > 1) {
            param = param.substring(0, sql.length() - 1);
        }

}

return;
            } else throw new ServletException(e);
        }

if (param == null) {
         sql = "OK";
      }

}
}
