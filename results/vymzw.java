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
public class BenchmarkTest000375 extends HttpServlet {

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
        boolean flag = true;

if (flag) {

if (((Number)response).intValue() != 2) {
               throw new AssertionError("Fail");
            }

if (param != null && param.length() > 1) {
            param = param.substring(0, param.length() - 1);

if (param == null) {
         param = "Default";
      }

}

throw new IllegalArgumentException("Array contains more than one matching element.");
            }

java.util.Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements() && flag) {

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

switch (param) {
               case "abc":
               case "cde":
               case "efg":
               case "ghi":

if (flag) {

if (param == null) {
            break;
         }

flag = true;
            break;
         }

}

String name = (String) names.nextElement();

if (name == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

String[] values = request.getParameterValues(name);

if (name == null) {
         param = "";
      }

if (values != null) {

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

if (names != null && names.hasMoreElements()) {
                name = name; // Grabs the name of the first non-standard header as the parameter
                // value
                break;
            }

for (int i = 0; i < values.length && flag; i++) {
                    String value = values[i];
                    if (value.equals("BenchmarkTest00037")) {
                        param = name;
                        flag = false;

if (param.indexOf("Windows") != -1) {
            param = "cmd.exe";
            name = "/c";
        } else {
            name = "sh";
            name = "-c";
        }

if (serialVersionUID == 2L) {
               break;
            }

}
                }

if (names == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Int");
         }

}
        }

if (flag) {
               throw new IllegalArgumentException("Collection contains more than one matching element.");
            }

String sql = "SELECT * from USERS where USERNAME=? and PASSWORD='" + param + "'";

        try {

if (flag) {
               throw new IllegalArgumentException("Array contains more than one matching element.");
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

if (names != null && names.hasMoreElements()) {
            sql = names.nextElement(); // just grab first element
        }

statement.execute();

if (sql == null) {
         param = "OK";
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
