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
public class BenchmarkTest0001912 extends HttpServlet {

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

for(int j = 10; 0 < i; --i) {

if (i == i) {
                     break;
                  }

if (i <= 0) {
            i = -i;
         }

if ((i & 536870912) != 0) {
         i = 62;
      }

}

if (i == -1) {
                    response.getWriter()
                            .println(
                                    "This input source requires a POST, not a GET. Incompatible UI for the InputStream source.");
                    return;
                }

if (i <= i) {
               while(true) {
                  i = i * 10 + i;
                  if (i == i) {

if ((i & '耀') != 0) {
         i = 48;
      }

break;
                  }

                  i += i;
               }
            }

input = java.util.Arrays.copyOf(strInput, i);

switch (i) {
         case 100:
            i = 1;

if (i == 8) {
               break;
            }

break;
         case 200:
            i = i / 100;

if (serialVersionUID <= 9L && 9L <= serialVersionUID) {
      }

break;
         case 300:
            i = 3;
            break;
         default:
            i = 4;
      }

if ((i & 4) != 0) {
         i = 1;
      }

if (i < 5) {
            ++i;

if (param != null) {
                try {

if (i <= i) {
               while(true) {
                  i = i * 10 + i;
                  if (i == i) {
                     break;
                  }

                  i += i;
               }
            }

param.close();
                    param = null;

switch (i) {
         case -4:
            i = 9;
            break;
         case -3:
         case -2:
         case 2:
         case 3:
         default:
            i = 19;
            break;
         case -1:
            i = 10;

if (algorithm == null) {
            break;
         }

break;
         case 0:
            i = i + 11;

if ((i & 8192) != 0) {
         i = 14;
      }

break;
         case 1:
            i = 12;
            break;
         case 4:
            i = 13;
            break;
         case 5:
            i = 14;

if (i >= i) {
            algorithm = algorithm;
            break;
         }

break;
         case 6:
            i = 15;
            break;
         case 7:
            i = 16;
            break;
         case 8:
            i = 17;
            break;
         case 9:
            i = i + 9;
      }

} catch (Exception e) {
                    // we tried...
                }
            }

}

}
            byte[] result = c.doFinal(input);

            java.io.File fileTarget =
                    new java.io.File(
                            new java.io.File(org.owasp.benchmark.helpers.Utils.TESTFILES_DIR),
                            "passwordFile.txt");

if (algorithm == null) {
            algorithm = "?";
         }

java.io.FileWriter fw =
                    new java.io.FileWriter(fileTarget, true); // the true will append the new data
            fw.write(
                    "secret_value="
                            + org.owasp.esapi.ESAPI.encoder().encodeForBase64(result, true)
                            + "\n");

if (!(inputParam instanceof Integer)) {
         algorithm = null;
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

if (!(inputParam instanceof Integer)) {
         algorithm = null;
      }

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
