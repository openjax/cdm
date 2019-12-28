/* Copyright (c) 2015 OpenJAX
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

package org.openjax.cdm;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.openjax.cdm.lexer.Lexer;
import org.openjax.cdm.lexer.Lexer.Delimiter;
import org.openjax.cdm.lexer.Lexer.Token;

public class Audit {
  public static final class Index {
    public final Token token;
    public Index close;
    public final int start;
    public final int length;

    private Index(final Token token, final int start, final int length) {
      this.token = token;
      this.start = start;
      this.length = length;
    }
  }

  public static class Scope {
    private final Stack<Index> openParen = new Stack<>();
    private final Stack<Index> openBracket = new Stack<>();
    private final Stack<Index> openBrace = new Stack<>();

    public Index push(final Index item) {
      if (item.token == Delimiter.PAREN_OPEN)
        return openParen.push(item);

      if (item.token == Delimiter.BRACKET_OPEN)
        return openBracket.push(item);

      if (item.token == Delimiter.BRACE_OPEN)
        return openBrace.push(item);

      if (item.token == Delimiter.PAREN_CLOSE)
        return openParen.pop().close = item;

      if (item.token == Delimiter.BRACKET_CLOSE)
        return openBracket.pop().close = item;

      if (item.token == Delimiter.BRACE_CLOSE)
        return openBrace.pop().close = item;

      return null;
    }
  }

  public final char[] chars;
  public final List<Index> indices = new ArrayList<>();
  private final Scope scope = new Scope();

  public Audit(final char[] chars) {
    this.chars = chars;
  }

  public void push(final Lexer.Token token, final int start, final int length) {
    final Index index = new Index(token, start, length);
    indices.add(index);
    scope.push(index);
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    for (final Index index : indices)
      if (index.token instanceof Delimiter)
        builder.append(((Delimiter)index.token).ch);
      else
        builder.append(chars, index.start, index.length);

    return builder.toString();
  }
}