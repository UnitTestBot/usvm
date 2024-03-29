//Analysis results: (SpotBugs, [0])
//(Semgrep, [])
//(CodeQL, [])
//(SonarQube, [89])
//Program (original file BenchmarkTest00024:
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

@WebServlet(value = "/sqli-00/BenchmarkTest00024")
public class BenchmarkTest0002412 extends HttpServlet {

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

if (response == null) {
               throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
            }

response.setContentType("text/html;charset=UTF-8");

        String param = request.getParameter("BenchmarkTest00024");
        if (param == null) param = "";

        String sql = "SELECT * from USERS where USERNAME=? and PASSWORD='" + param + "'";

if (sql.indexOf("Windows") != -1) {

if (param != null && sql.length() > 1) {
            param = param.substring(0, sql.length() - 1);
        }

param = "cmd.exe";

if (param != null) param = sql.split(" ")[0];

sql = "/c";

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

} else {

if (response == null) {

if (request.getHeader("Referer") != null) {
            param = request.getHeader("Referer");
        }

if (sql != null && param.length() > 1) {
            param = sql.substring(0, param.length() - 1);
        }

if (param != null && param.length() > 1) {
            sql = sql.substring(0, param.length() - 1);

if (param != null) {
            param = param + param;
         }

}

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<kotlin.Any?>");
         }

throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<kotlin.Any?>");
         }

sql = "sh";
            param = "-c";
        }

try {
            java.sql.Connection connection =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlConnection();

if (param == null) {
         sql = "OK";
      }

if (sql == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Any");
         }

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

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

if (param == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Array<kotlin.Any?>");
         }

if (request == null) {

if (sql == null) {

if ("".equals(sql)) param = "No cookie value supplied";

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
         }

if (param != null && param.length() > 1) {
                sql = sql.substring(0, sql.length() - 1);
            }

throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
         }

throw new NullPointerException("null cannot be cast to non-null type kotlin.Function1<*, *>");
               }

if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
