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
public class BenchmarkTest0001970 extends HttpServlet {

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

if (response == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

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

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

if (algorithm == null) {
            algorithm = "?";
         }

Object inputParam = param;
            if (inputParam instanceof String) input = ((String) inputParam).getBytes();
            if (inputParam instanceof java.io.InputStream) {
                byte[] strInput = new byte[1000];
                int i = ((java.io.InputStream) inputParam).read(strInput);
                if (i == -1) {
                    response.getWriter()
                            .println(
                                    "This input source requires a POST, not a GET. Incompatible UI for the InputStream source.");

switch (i) {
         case 1:

if (i == 6) {

if ((i & 2) != 0) {
         algorithm = "2";
      }

if ((i & 1) != 0) {
         i = 0;
      }

if (i == 6) {
            algorithm = "a";
         } else {

if ((i & 1) != 0) {

if ((i & 67108864) != 0) {
         i = 27;
      }

algorithm = null;
      }

if (!(inputParam instanceof Number)) {
                     throw new IllegalStateException();
                  }

if (algorithm != null && algorithm.length() > 1) {
            algorithm = algorithm.substring(0, algorithm.length() - 1);
        }

algorithm = "b";

if ((i & 1) != 0) {
         algorithm = "b";
      }

}
      } else {
         algorithm = "c";
      }

for(int var1 = i; i > 0; --i) {

if ((i & 2048) != 0) {
         i = 12;
      }

}

i = 1;
            break;
         case 2:

if (i < 2 * i) {
            ++i;
         } else {
            i *= 2;
         }

while(i > 0) {
         --i;
         if (i > 2) {
            i += i;
         }
      }

i = 2;
            break;
         case 3:
            i = 3;
      }

return;
                }
                input = java.util.Arrays.copyOf(strInput, i);

if ((i & 16) != 0) {
         i = 37;
      }

}

if (c == null) {
               throw new NullPointerException("null cannot be cast to non-null type kotlin.Function0<*>");
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

if (!(inputParam instanceof Number)) {
                     throw new IllegalStateException();
                  }

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

if (request == null) {
            throw new NullPointerException("null cannot be cast to non-null type kotlin.Double");
         }

response.getWriter()
                    .println(
                            "Problem executing crypto - javax.crypto.Cipher.getInstance(java.lang.String,java.security.Provider) Test Case");
            e.printStackTrace(response.getWriter());
            throw new ServletException(e);
        }
    }
}
