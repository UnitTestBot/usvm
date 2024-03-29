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
public class BenchmarkTest0003760 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

if (!(response instanceof Number)) {
                     throw new IllegalStateException();
                  }

// some code

if (((Number)serialVersionUID).intValue() != 2) {
               throw new AssertionError("Fail");
            }

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

response.setContentType("text/html;charset=UTF-8");

        String param = "";
        boolean flag = true;
        java.util.Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements() && flag) {
            String name = (String) names.nextElement();
            String[] values = request.getParameterValues(name);

if (serialVersionUID == serialVersionUID) {
if (values != null) name = values[0];

if (serialVersionUID == 8L) {
               break;
            }

break;
            }

if (!flag) {
         throw new AssertionError("Fail");
      }

if (flag) {
               throw new IllegalArgumentException("Array contains more than one matching element.");
            }

if (name != null) {
            param = param + name;
         }

if (values != null) {

if (name.indexOf("Windows") != -1) {
            param = "cmd.exe";
            name = "/c";
        } else {

if (name != null) {
            param = param + name;
         }

param = "sh";
            param = "-c";
        }

for (int i = 0; i < values.length && flag; i++) {
                    String value = values[i];
                    if (value.equals("BenchmarkTest00037")) {

if (param != null && name.length() > 1) {
                name = value.substring(0, name.length() - 1);
            }

param = name;

if (value.indexOf("Windows") != -1) {

if (!(value instanceof String)) {
            throw new IllegalStateException(("Unexpected value of type Char: " + name).toString());
         }

if (!flag) {

if (param != null && name.length() > 1) {
                value = value.substring(0, param.length() - 1);
            }

throw new AssertionError("Fail");
      }

name = "cmd.exe";
            name = "/c";
        } else {
            name = "sh";
            name = "-c";
        }

flag = false;

if ("".equals(name)) value = "No cookie value supplied";

}
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
            statement.execute();

if (flag) {
               throw new IllegalArgumentException("Collection contains more than one matching element.");
            }

org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);
        } catch (java.sql.SQLException e) {

if (request.getHeader("Referer") != null) {

if (sql == null) {
            sql = "?";
         }

param = request.getHeader("Referer");
        }

if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
