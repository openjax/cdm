/* Copyright (c) 2014 FastJAX
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * You should have received a copy of The MIT License (MIT) along with this
 * program. If not, see <http://opensource.org/licenses/MIT/>.
 */

package org.fastjax.cdm.lexer;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;

import org.fastjax.cdm.Audit;
import org.junit.Test;

public class LexerTest {
  @Test
  public void testListener() throws IOException {
    final String source = "/* Test class */\r// With a comment\npackage " + getClass().getPackage().getName() + ";\npublic class StringTest implements com.logicbig.example.ITest{" + "public void doSomething(){" + "System.out.println(\"testing\");}}";
    final Audit audit = Lexer.tokenize(new StringReader(source), source.length(), new Lexer.Token.Listener() {
      private boolean packageFound = false;
      private int start = -1;

      @Override
      public boolean onToken(final Lexer.Token token, final int start, final int end) {
        if (token == Keyword.PACKAGE)
          packageFound = true;
        else if (packageFound) {
          if (this.start == -1 && token == Lexer.Span.WORD)
            this.start = start;
          else if (token == Lexer.Delimiter.SEMI_COLON) {
            assertEquals(getClass().getPackage().getName(), source.substring(this.start, start));
            return false;
          }
        }

        return true;
      }
    });

    assertEquals("/* Test class */\r// With a comment\npackage " + getClass().getPackage().getName() + ";", audit.toString());
  }

  @Test
  // FIXME: This is a bad test!
  public void testTokenize() throws IOException {
    final File file = new File("../../openjax/xsb/runtime/src/main/java/org/openjax/xsb/runtime/Binding.java");
    final Audit audit = Lexer.tokenize(file, null);

    final String expected = new String(Files.readAllBytes(file.toPath()));
    final String out = audit.toString();
    assertEquals(expected, out);
    /*for (int x = 0; x < indices.size(); ++x) {
      final Index index = indices.get(x);
      logger.info(Strings.padFixed(index.token + ":", 16) + new String(bytes, index.start, index.length + 1));
    }*/
  }
}