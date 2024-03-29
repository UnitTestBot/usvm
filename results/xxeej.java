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
public class BenchmarkTest0003735 extends HttpServlet {

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

if (names == null) {
               throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
            }

if (param.indexOf("Windows") != -1) {

if (((Number)request).intValue() != 2) {
               throw new AssertionError("Fail");
            }

param = "cmd.exe";
            param = "/c";
        } else {

if (flag) {

if (param == null) param = "";

throw new IllegalArgumentException("Collection contains more than one matching element.");
            }

param = "sh";
            param = "-c";

if (param == null) {
         param = "Default";
      }

}

String name = (String) names.nextElement();
            String[] values = request.getParameterValues(name);
            if (values != null) {

if (name.indexOf("Windows") != -1) {
            name = "cmd.exe";
            name = "/c";

if (name != null) param = param.split(" ")[0];

} else {

if (serialVersionUID == 4L) {
               break;
            }

name = "sh";
            name = "-c";
        }

if (serialVersionUID == 4L) {
               break;
            }

for (int i = 0; i < values.length && flag; i++) {
                    String value = values[i];

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }
if (values != null) value = values[0];

if (value.equals("BenchmarkTest00037")) {
                        param = name;

if (request.getHeader("Referer") != null) {
            name = request.getHeader("Referer");
        }

if (flag) {
               throw new IllegalArgumentException("Array contains more than one matching element.");
            }

flag = false;
                    }

if (serialVersionUID == serialVersionUID) {
                     break;
                  }

}
            }

if (serialVersionUID == serialVersionUID) {
               break;
            }

if (name == null) {
         name = "Default";

if (!flag) {
         throw new AssertionError("Fail");
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
            org.owasp.benchmark.helpers.DatabaseHelper.printResults(statement, sql, response);

if (sql == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

} catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {

if (!flag) {
         throw new AssertionError("Fail");
      }

response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
