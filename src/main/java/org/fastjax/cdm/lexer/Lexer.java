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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.fastjax.cdm.Audit;
import org.fastjax.util.StreamSearcher;

public class Lexer {
  private static final StreamSearcher.Char eol = new StreamSearcher.Char(new char[] {'\r'}, new char[] {'\n'});
  private static final StreamSearcher.Char closeComment = new StreamSearcher.Char(new char[] {'*', '/'});
  private static final StreamSearcher.Char singleQuote = new StreamSearcher.Char(new char[] {'\''});
  private static final StreamSearcher.Char doubleQuote = new StreamSearcher.Char(new char[] {'"'});

  public interface Token {
    public interface Listener {
      public boolean onToken(final Token token, final int start, final int end);
    }

    public String name();
    public int ordinal();
  }

  public static enum Delimiter implements Token {
    // NOTE: The declaration list of Delimiter(s) must be in sorted alphabetical order!
    EXCLAMATION("!"), PERCENT("%"), AMPERSAND("&"), AND("&&"), PAREN_OPEN("("), PAREN_CLOSE(")"), ASTERISK("*"), PLUS("+"), PLUS_PLUS("++"), PLUS_EQ("+="), COMMA(","), MINUS("-"), MINUS_MINUS("--"), MINUS_EQ("-="), DOT("."), SLASH("/"), COLON(":"), SEMI_COLON(";"), LT("<"), LTLT("<<"), LTLTLT("<<<"), LTE("<="), EQ("="), EQEQ("=="), GT(">"), GTE(">="), GTGT(">>"), GTGTGT(">>>"), QUESTION("?"), AT("@"), BRACKET_OPEN("["), ARRAY("[]"), BRACKET_CLOSE("]"), CARAT("^"), BRACE_OPEN("{"), PIPE("|"), OR("||"), BRACE_CLOSE("}"), TILDE("~");

    public final String ch;
    public final int[][] children;

    Delimiter(final String ch) {
      this.ch = ch;
      this.children = new int[ch.length() + 1][];
    }

    @Override
    public String toString() {
      return ch;
    }
  }

  public static enum Span implements Token {
    WHITESPACE("\t", "\n", "\r", " "), LINE_COMMENT("//"), BLOCK_COMMENT("/*"), NUMBER, CHARACTER, STRING, WORD;

    public final String[] ch;

    Span(final String ... ch) {
      this.ch = ch;
    }
  }

  /*
   * So far, best performance is with FileInputStream, reading chars.
   */
  public static Audit tokenize(final File file, final Token.Listener listener) throws IOException {
    try (final InputStreamReader in = new FileReader(file)) {
      // FIXME: What if the length of the file is greater than Integer.MAX_VALUE?!
      return tokenize(in, (int)file.length(), listener);
    }
  }

  public static Audit tokenize(final Reader in, final int length, final Token.Listener listener) throws IOException {
    final char[] chars = new char[length];
    final Audit audit = new Audit(chars);
    int i = 0;
    int b = -1;
    char ch;
    Token token = null;
    int len = 0;

    while (i < chars.length) {
      if ((b = in.read()) == -1)
        throw new IOException("Unexpected end of stream");

      ch = chars[i++] = (char)b;
      if ('0' <= ch && ch <= '9' && (token == null || token != Span.WORD)) {
        if (token != Span.NUMBER) {
          audit.push(token, i - len - 1, len);
          if (listener != null && !listener.onToken(token, i - len - 1, len))
            return audit;

          len = 1;
          token = Span.NUMBER;
        }
        else {
          ++len;
        }
      }
      else if (('a' <= ch && ch <= 'z') || ('A' <= ch && ch <= 'Z') || (len != 0 && '0' <= ch && ch <= '9') || ch == '$' || ch == '_') {
        // TODO: Handle 0x0000, 0b0000, 1.234e2, and 999_99__9999L
        if (token == Span.NUMBER && (ch == 'd' || ch == 'D' || ch == 'f' || ch == 'F' || ch == 'l' || ch == 'L')) {
          ++len;
        }
        else if (token == Span.WORD) {
          ++len;
        }
        else {
          if (token == null || token == Span.WHITESPACE || !(token instanceof Keyword)) {
            audit.push(token, i - len - 1, len);
            if (listener != null && !listener.onToken(token, i - len - 1, len))
              return audit;

            len = 1;

            token = Keyword.findNext(null, 0, ch);
            if (token == null)
              token = Span.WORD;
          }
          else {
            token = Keyword.findNext(((Keyword)token), len, ch);
            if (token == null)
              token = Span.WORD;

            ++len;
          }
        }
      }
      else if (ch == '\n' || ch == '\r' || ch == ' ' || ch == '\t') {
        if (token == Span.WHITESPACE) {
          ++len;
        }
        else {
          audit.push(token, i - len - 1, len);
          if (listener != null && !listener.onToken(token, i - len - 1, len))
            return audit;

          len = 1;
          token = Span.WHITESPACE;
        }
      }
      else if (ch == '.') {
        if (token == null || token == Span.WHITESPACE || token == Delimiter.BRACKET_OPEN || token == Delimiter.BRACE_OPEN) {
          len = 1;
          token = Span.NUMBER;
        }
        else if (token == Span.NUMBER) {
          ++len;
        }
        else {
          audit.push(token, i - len - 1, len);
          if (listener != null && !listener.onToken(token, i - len - 1, len))
            return audit;

          len = 1;
          token = Delimiter.DOT;
        }
      }
      else if (ch == '&') {
        if (token == Delimiter.AMPERSAND) { // &&
          len = 2;
          token = Delimiter.AND;
        }
        else {
          audit.push(token, i - len - 1, len);
          if (listener != null && !listener.onToken(token, i - len - 1, len))
            return audit;

          len = 1;
          token = Delimiter.AMPERSAND;
        }
      }
      else if (ch == '|') {
        if (token == Delimiter.PIPE) { // ||
          len = 2;
          token = Delimiter.OR;
        }
        else {
          audit.push(token, i - len - 1, len);
          if (listener != null && !listener.onToken(token, i - len - 1, len))
            return audit;

          len = 1;
          token = Delimiter.PIPE;
        }
      }
      else if (ch == '=') {
        if (token == Delimiter.LT) { // <=
          len = 2;
          token = Delimiter.LTE;
        }
        else if (token == Delimiter.GT) { // >=
          len = 2;
          token = Delimiter.GTE;
        }
        else if (token == Delimiter.EQ) { // ==
          len = 2;
          token = Delimiter.EQEQ;
        }
        else if (token == Delimiter.MINUS) { // -=
          len = 2;
          token = Delimiter.MINUS_EQ;
        }
        else if (token == Delimiter.PLUS) { // +=
          len = 2;
          token = Delimiter.PLUS_EQ;
        }
        else {
          audit.push(token, i - len - 1, len);
          if (listener != null && !listener.onToken(token, i - len - 1, len))
            return audit;

          len = 1;
          token = Delimiter.EQ;
        }
      }
      else if (ch == '<') {
        if (token == Delimiter.LT) { // <<
          len = 2;
          token = Delimiter.LTLT;
        }
        else if (token == Delimiter.LTLT) { // <<<
          len = 3;
          token = Delimiter.LTLTLT;
        }
        else {
          audit.push(token, i - len - 1, len);
          if (listener != null && !listener.onToken(token, i - len - 1, len))
            return audit;

          len = 1;
          token = Delimiter.LT;
        }
      }
      else if (ch == '>') {
        if (token == Delimiter.GT) { // >>
          len = 2;
          token = Delimiter.GTGT;
        }
        else if (token == Delimiter.GTGT) { // >>>
          len = 3;
          token = Delimiter.GTGTGT;
        }
        else {
          audit.push(token, i - len - 1, len);
          if (listener != null && !listener.onToken(token, i - len - 1, len))
            return audit;

          len = 1;
          token = Delimiter.GT;
        }
      }
      else if (ch == '-') {
        if (token == Delimiter.MINUS) { // --
          len = 2;
          token = Delimiter.MINUS_MINUS;
        }
        else {
          audit.push(token, i - len - 1, len);
          if (listener != null && !listener.onToken(token, i - len - 1, len))
            return audit;

          len = 1;
          token = Delimiter.MINUS;
        }
      }
      else if (ch == '+') {
        if (token == Delimiter.PLUS) { // ++
          len = 2;
          token = Delimiter.PLUS_PLUS;
        }
        else {
          audit.push(token, i - len - 1, len);
          if (listener != null && !listener.onToken(token, i - len - 1, len))
            return audit;

          len = 1;
          token = Delimiter.PLUS;
        }
      }
      else if (ch == '~') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        token = Delimiter.TILDE;
      }
      else if (ch == '!') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        token = Delimiter.EXCLAMATION;
      }
      else if (ch == '@') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        token = Delimiter.AT;
      }
      else if (ch == '^') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        token = Delimiter.CARAT;
      }
      else if (ch == '%') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        token = Delimiter.PERCENT;
      }
      else if (ch == ',') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        token = Delimiter.COMMA;
      }
      else if (ch == ';') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        token = Delimiter.SEMI_COLON;
      }
      else if (ch == ':') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        token = Delimiter.COLON;
      }
      else if (ch == '?') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        token = Delimiter.QUESTION;
      }
      else if (ch == '(') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        token = Delimiter.PAREN_OPEN;
      }
      else if (ch == ')') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        token = Delimiter.PAREN_CLOSE;
      }
      else if (ch == '{') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        token = Delimiter.BRACE_OPEN;
      }
      else if (ch == '}') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        token = Delimiter.BRACE_CLOSE;
      }
      else if (ch == '[') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        token = Delimiter.BRACKET_OPEN;
      }
      else if (ch == ']') {
        if (token == Delimiter.BRACKET_OPEN) { // []
          len = 2;
          token = Delimiter.ARRAY;
        }
        else {
          audit.push(token, i - len - 1, len);
          if (listener != null && !listener.onToken(token, i - len - 1, len))
            return audit;

          len = 1;
          token = Delimiter.BRACKET_CLOSE;
        }
      }
      else if (ch == '/') {
        if (token == Delimiter.SLASH) { // Start of line comment
          // find end of line
          // index from // to end of comment, not including newline
          // this is the only situation where the token is added at time of detection of end of block, cause the eol char is not supposed to be a part of the
          // token
          len = eol.search(in, chars, i);
          audit.push(Span.LINE_COMMENT, i - 2, len + 2);
          if (listener != null && !listener.onToken(token, i - len - 1, len))
            return audit;

          i += len;
          len = 0;
          token = null;
        }
        else {
          audit.push(token, i - len - 1, len);
          if (listener != null && !listener.onToken(token, i - len - 1, len))
            return audit;

          len = 1;
          token = Delimiter.SLASH;
        }
      }
      else if (ch == '*') {
        if (token == Delimiter.SLASH) { // Start of block comment
          // find end of block comment
          // index from /* to */ including any & all characters between
          i += len = closeComment.search(in, chars, i);
          len += 2;
          token = Span.BLOCK_COMMENT;
        }
        else {
          audit.push(token, i - len - 1, len);
          if (listener != null && !listener.onToken(token, i - len - 1, len))
            return audit;

          len = 1;
          token = Delimiter.ASTERISK;
        }
      }
      else if (ch == '\'') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        int t;
        i += t = singleQuote.search(in, chars, i);
        len += t;
        // take care of '\'' situation
        // TODO: Handle '\u0000' and '\0'
        if (chars[i - 2] == '\\' && len == 3) {
          i += t = singleQuote.search(in, chars, i);
          len += t;
        }

        token = Span.CHARACTER;
      }
      else if (ch == '"') {
        audit.push(token, i - len - 1, len);
        if (listener != null && !listener.onToken(token, i - len - 1, len))
          return audit;

        len = 1;
        int l;
        i += l = doubleQuote.search(in, chars, i);
        len += l;
        // take care of \" situation
        if (chars[i - 2] == '\\') {
          i += l = doubleQuote.search(in, chars, i);
          len += l;
        }

        token = Span.STRING;
      }
      else {
        System.err.print(ch);
      }
    }

    // add the last token, because its final delimiter can be the EOF
    audit.push(token, i - len, len);

    return audit;
  }
}