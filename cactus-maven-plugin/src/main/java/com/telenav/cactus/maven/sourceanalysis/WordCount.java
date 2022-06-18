////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// © 2011-2022 Telenav, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
package com.telenav.cactus.maven.sourceanalysis;

import java.nio.file.Path;

/**
 * A simple word count SourceScorer.
 *
 * @author Tim Boudreau
 */
public class WordCount implements SourceScorer.StringSourceScorer
{
    @Override
    public int score(Path path, String lines)
    {
        int result = 0;
        for (String line : lines.split("\n"))
        {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("/*"))
            {
                continue;
            }
            result += scoreLine(line);
        }
        return result;
    }

    private int scoreLine(String line)
    {
        Kind lastState = Kind.WHITESPACE;
        int result = 0;
        for (int i = 0; i < line.length(); i++)
        {
            char c = line.charAt(i);
            Kind newState = Kind.get(c);
            if (lastState != newState)
            {
                if (newState.isWord() && !lastState.isWord())
                {
                    result++;
                }
            }
            lastState = newState;
        }
        return result;
    }

    enum Kind
    {
        WHITESPACE,
        WORD_CHAR,
        OTHER_CHAR;

        boolean isWord()
        {
            return this == WORD_CHAR;
        }

        boolean is(char c)
        {
            switch (this)
            {
                case WHITESPACE:
                    return Character.isWhitespace(c);
                case WORD_CHAR:
                    return Character.isAlphabetic(c) || Character.isDigit(c) || c == '.' || c == '$';
                case OTHER_CHAR:
                    return !Character.isAlphabetic(c) && !Character.isDigit(c) && !Character
                            .isWhitespace(c);
                default:
                    throw new AssertionError(this);
            }
        }

        static Kind get(char ch)
        {
            if (WHITESPACE.is(ch))
            {
                return WHITESPACE;
            }
            else
                if (WORD_CHAR.is(ch))
                {
                    return WORD_CHAR;
                }
                else
                {
                    return OTHER_CHAR;
                }
        }
    }

}
