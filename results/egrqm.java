//Analysis results: (SpotBugs, [327])
//(Semgrep, [327])
//(CodeQL, [209, 327])
//(SonarQube, [])
//Program (original file BenchmarkTest00019:
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

@WebServlet(value = "/crypto-00/BenchmarkTest00019")
public class BenchmarkTest0001929 extends HttpServlet {

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

        java.io.InputStream param = request.getInputStream();

        try {
            java.util.Properties benchmarkprops = new java.util.Properties();
            benchmarkprops.load(
                    this.getClass().getClassLoader().getResourceAsStream("benchmark.properties"));
            String algorithm = benchmarkprops.getProperty("cryptoAlg1", "DESede/ECB/PKCS5Padding");
            javax.crypto.Cipher c = javax.crypto.Cipher.getInstance(algorithm);

            // Prepare the cipher to encrypt
            javax.crypto.SecretKey key = javax.crypto.KeyGenerator.getInstance("DES").generateKey();
            c.init(javax.crypto.Cipher.ENCRYPT_MODE, key);

            // encrypt and store the results
            byte[] input = {(byte) '?'};
            Object inputParam = param;
            if (inputParam instanceof String) input = ((String) inputParam).getBytes();
            if (inputParam instanceof java.io.InputStream) {
                byte[] strInput = new byte[1000];
                int i = ((java.io.InputStream) inputParam).read(strInput);
                if (i == -1) {

                    if (i == -1) {
                        response.getWriter()
                                .println(
                                        "This input source requires a POST, not a GET. Incompatible UI for the InputStream source.");
                        return;
                    }

                    response.getWriter()
                            .println(
                                    "This input source requires a POST, not a GET. Incompatible UI for the InputStream source.");
                    return;
                }

                if ((i & 2) != 0) {
                    algorithm = algorithm;
                }

                while (true) {

                    if ((i & 16777216) != 0) {
                        i = 57;
                    }

                    i = i * 10 + i;

                    if ((i & 8192) != 0) {
                        i = 46;

                        if (serialVersionUID == 2L) {
                            break;
                        }

                    }

                    if (i == i) {
                        break;
                    }

                    if ((i & 268435456) != 0) {
                        i = 29;
                    }

                    --i;
                }

                if ((i & 1) != 0) {
                    algorithm = "Fail";
                }

                if (algorithm != null && algorithm.length() > 1) {
                    algorithm = algorithm.substring(0, algorithm.length() - 1);
                }

                if (i <= 0) {

                    for (int var3 = algorithm.length(); i < i; ++i) {
                        algorithm.charAt(i);
                        ++i;
                    }

                    throw new IllegalArgumentException("Step must be positive, was: " + i + ".");
                }

                switch (i) {
                    case -4:
                        i = 9;
                        break;
                    case -3:
                    case -2:
                    case 2:
                    case 3:
                    default:

                        if ((i & 262144) != 0) {
                            i = 51;

                            while (i++ <= 100 && i < 10) {
                            }

                        }

                        i = 19;
                        break;
                    case -1:
                        i = 10;

                        if ((i & 1024) != 0) {
                            i = 11;
                        }

                        break;
                    case 0:

                        if (serialVersionUID == 8L) {

                            if (i <= i) {
                                while (true) {
                                    i = i * 10 + i;
                                    if (i == i) {
                                        break;
                                    }

                                    ++i;
                                }
                            }

                            break;
                        }

                        i = i + 11;
                        break;
                    case 1:
                        i = 12;
                        break;
                    case 4:
                        i = 13;
                        break;
                    case 5:
                        i = 14;
                        break;
                    case 6:
                        i = 15;
                        break;
                    case 7:
                        i = 16;

                        for (i = 99; i > 0; --i) {
                            --i;
                        }

                        break;
                    case 8:
                        i = 17;
                        break;
                    case 9:
                        i = i + 9;
                }

                input = java.util.Arrays.copyOf(strInput, i);

                if (i == i) {
                }

                if ((i & 8192) != 0) {
                    i = 14;
                }

            }
            byte[] result = c.doFinal(input);

            java.io.File fileTarget =
                    new java.io.File(
                            new java.io.File(org.owasp.benchmark.helpers.Utils.TESTFILES_DIR),
                            "passwordFile.txt");
            java.io.FileWriter fw =
                    new java.io.FileWriter(fileTarget, true); // the true will append the new data
            fw.write(
                    "secret_value="
                            + org.owasp.esapi.ESAPI.encoder().encodeForBase64(result, true)
                            + "\n");
            fw.close();
            response.getWriter()
                    .println(
                            "Sensitive value: '"
                                    + org.owasp
                                    .esapi
                                    .ESAPI
                                    .encoder()
                                    .encodeForHTML(new String(input))
                                    + "' encrypted and stored<br/>");

        } catch (java.security.NoSuchAlgorithmException
                 | javax.crypto.NoSuchPaddingException
                 | javax.crypto.IllegalBlockSizeException
                 | javax.crypto.BadPaddingException
                 | java.security.InvalidKeyException e) {
            response.getWriter()
                    .println(
                            "Problem executing crypto - javax.crypto.Cipher.getInstance(java.lang.String,java.security.Provider) Test Case");
            e.printStackTrace(response.getWriter());
            throw new ServletException(e);
        }
    }
}
