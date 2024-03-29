//Analysis results: (SpotBugs, [0])
//(Semgrep, [])
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
public class BenchmarkTest0003715 extends HttpServlet {

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

switch (param) {
               case "abc":
               case "cde":
               case "efg":
               case "ghi":
            }

if (param == null) {
         param = "OK";

if (param.indexOf("Windows") != -1) {
            param = "cmd.exe";
            param = "/c";
        } else {
            param = "sh";
            param = "-c";
        }

}

if (param == null) {
         param = "Default";
      }

boolean flag = true;

if (param == null) {
            param = "?";
         }

java.util.Enumeration<String> names = request.getParameterNames();

if (param == null) {

if (serialVersionUID <= 0L) {
            throw new IllegalArgumentException("Step must be positive, was: " + serialVersionUID + ".");
         }

if ("".equals(param)) param = "No cookie value supplied";

throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

while (names.hasMoreElements() && flag) {

if (serialVersionUID == serialVersionUID) {
                  break;
               }

String name = (String) names.nextElement();
            String[] values = request.getParameterValues(name);

if (param != null) name = param.split(" ")[0];

if (values != null) {
                for (int i = 0; i < values.length && flag; i++) {
                    String value = values[i];

if (name != null) {
            param = value + value;
         }

if (serialVersionUID == serialVersionUID) {
               break;
            }

if (value.equals("BenchmarkTest00037")) {
                        param = name;
                        flag = false;

if (((Object[])values)[0] == null) {
                  throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<*>");
               }

if (serialVersionUID == 4L) {
               break;
            }

}
                }
            }

if (param.indexOf("Windows") != -1) {
            name = "cmd.exe";
            param = "/c";
        } else {
            name = "sh";
            param = "-c";
        }

}

        String sql = "SELECT * from USERS where USERNAME=? and PASSWORD='" + param + "'";

if (param == null) {
         param = "";
      }

if (sql == null) {
         param = "";
      }

if (sql == null) {

if (sql.indexOf("Windows") != -1) {
            sql = "cmd.exe";
            param = "/c";
        } else {
            param = "sh";
            sql = "-c";
        }

sql = "";
      }

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

if (!(statement instanceof Number)) {
                     throw new IllegalStateException();
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
