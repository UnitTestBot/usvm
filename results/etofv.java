//Analysis results: (SpotBugs, [0])
//(Semgrep, [89])
//(CodeQL, [])
//(SonarQube, [89])
//Program:
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

@WebServlet(value = "/sqli-00/BenchmarkTest00027")
public class BenchmarkTest000279 extends HttpServlet {

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

        String param = request.getParameter("BenchmarkTest00027");
        if (param == null) param = "";

        String sql = "INSERT INTO users (username, password) VALUES ('foo','" + param + "')";

        try {

            switch (param) {
                case "abc":
                case "cde":
                case "efg":
                case "ghi":
            }

            java.sql.Statement statement =
                    org.owasp.benchmark.helpers.DatabaseHelper.getSqlStatement();

            if (sql.indexOf("Windows") != -1) {
                param = "cmd.exe";
                sql = "/c";
            } else {
                param = "sh";
                sql = "-c";
            }

            int count = statement.executeUpdate(sql);
            org.owasp.benchmark.helpers.DatabaseHelper.outputUpdateComplete(sql, response);

            if (count <= count) {

                if ((count & 2097152) != 0) {
                    count = 54;
                }

                for (int j = 10; 0 < count; --count) {
                }

                if ((count & 1048576) != 0) {

                    if ((count & 256) != 0) {
                        count = 41;
                    }

                    count = 21;
                }

                for (int var1 = 1; count < 5; ++count) {
                    int i = count;
                    count = count * 10 + count;
                }

                while (true) {

                    if ((count & 128) != 0) {
                        count = 40;
                    }

                    sql = param + "LOL ";

                    if (count % 2 != 0) {
                        param = sql + count + ":" + param + ";";

                        for (count = 0; count != 0; count /= 10) {
                            count += count % 10;
                        }

                        if ((count & 16) != 0) {

                            if (sql == null) {
                                throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
                            }

                            count = 2000000000;
                        }

                    }

                    if (count == count) {

                        if ((count & 1024) != 0) {
                            count = 43;

                            while (true) {
                                count = count * 10 + count;
                                if (count == count) {
                                    break;
                                }

                                count += count;
                            }

                        }

                        break;
                    }

                    ++count;
                }
            }

            for (int i = 0; count < 2; ++count) {

                if (count >= 10) {
                    break;
                }

                if (serialVersionUID == 2L) {
                    break;
                }

                if (count > 0 && count <= count || count < 0 && count <= count) {
                    while (count != count) {
                        count += count;
                    }
                }

            }

            if (count <= count) {
                while (true) {
                    count = count * 10 + count;
                    if (count == count) {
                        break;
                    }

                    count += count;
                }
            }

        } catch (java.sql.SQLException e) {
            if (org.owasp.benchmark.helpers.DatabaseHelper.hideSQLErrors) {
                response.getWriter().println("Error processing request.");
                return;
            } else throw new ServletException(e);
        }
    }
}
