/*------------------------------------------------------------------
 * Copyright (c) 2014 Cisco Systems
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *------------------------------------------------------------------*/
/**
 * @author Roberto Attias
 * @since  Aug 2014
 */
package com.cisco.mscviewer.util;

import java.util.ArrayList;
import java.util.List;

import com.cisco.mscviewer.io.JSonException;
import com.cisco.mscviewer.model.JSonArrayValue;
import com.cisco.mscviewer.model.JSonBooleanValue;
import com.cisco.mscviewer.model.JSonNumberValue;
import com.cisco.mscviewer.model.JSonObject;
import com.cisco.mscviewer.model.JSonStringValue;
import com.cisco.mscviewer.model.JSonValue;

class MutableInteger {

    int v;

    MutableInteger(int a) {
        v = a;
    }
}

/**
 *
 * @author rattias
 */
public class JSonParser {
    private final static void skipSpaces(String str, MutableInteger pos) {
        while (Character.isSpaceChar(str.charAt(pos.v))) {
            pos.v++;
        }        
    }
    
    private static void expect(String str, String file, int lineNum,
            MutableInteger pos, char ch) throws JSonException {
        try {
            while (Character.isSpaceChar(str.charAt(pos.v))) {
                pos.v++;
            }
            if (str.charAt(pos.v) != ch) {
                throw new JSonException(null, 0, pos.v, str, "Expecting '" + ch
                        + "', found '" + str.charAt(pos.v) + "'");
            }
            pos.v++;
        } catch (final StringIndexOutOfBoundsException ex) {
            throw new JSonException(null, lineNum, pos.v, str, "Expecting '"
                    + ch + "', found end-of-line");
        }
    }

    private static char expectOneOf(String str, String file, int lineNum,
            MutableInteger pos, String exp) throws JSonException {
        try {
            while (Character.isSpaceChar(str.charAt(pos.v))) {
                pos.v++;
            }
            final char c = str.charAt(pos.v);
            if (exp.indexOf(c) == -1) {
                throw new JSonException(null, lineNum, pos.v, str,
                        "Expecting one of '" + exp + "', found '" + c + "'\n");
            }
            pos.v++;
            return c;
        } catch (final StringIndexOutOfBoundsException ex) {
            throw new JSonException(null, lineNum, pos.v, str,
                    "Expecting one of '" + exp + "', found end-of-line");
        }
    }

    @SuppressWarnings("unused")
    private void expect(String str, String file, int lineNum,
            MutableInteger pos, String exp) throws JSonException {
        while (Character.isSpaceChar(str.charAt(pos.v))) {
            pos.v++;
        }
        if (!str.startsWith(exp, pos.v)) {
            throw new JSonException(file, lineNum, pos.v, str, "Expecting '"
                    + exp + "', found '" + str.charAt(pos.v) + "'");
        }
    }

    private static JSonStringValue parseString(String str, String file,
            int lineNum, MutableInteger pos) throws JSonException {
        final StringBuilder sb = new StringBuilder();
        expect(str, file, lineNum, pos, '"');
        while (true) {
            final char c = str.charAt(pos.v);
            pos.v++;
            if (c == '\\') {
                switch (str.charAt(pos.v)) {
                case '"':
                    sb.append('"');
                    pos.v++;
                    break;
                case '\\':
                    sb.append('\\');
                    pos.v++;
                    break;
                case '/':
                    sb.append('/');
                    pos.v++;
                    break;
                case 'b':
                    sb.append('\b');
                    pos.v++;
                    break;
                case 'n':
                    sb.append('\n');
                    pos.v++;
                    break;
                case 'r':
                    sb.append('\r');
                    pos.v++;
                    break;
                case 't':
                    sb.append('\t');
                    pos.v++;
                    break;
                case 'u':
                    sb.append((char) Integer.parseInt(
                            str.substring(pos.v + 2, pos.v + 6), 16));
                    pos.v += 6;
                    break;
                default:
                    throw new JSonException("Invalid escape sequence");
                }
            } else if (c == '"') {
                return new JSonStringValue(sb.toString());
            } else {
                sb.append(c);
            }
        }
    }

    private static JSonArrayValue parseArray(String str, String file,
            int lineNum, MutableInteger pos) throws JSonException {
        final JSonArrayValue jal = new JSonArrayValue();
        final List<JSonValue> al = jal.value();
        expect(str, file, lineNum, pos, '[');
        // could use expect() here, but would cause a lot of exceptions to be instantiated and thrown during parsing. 
        skipSpaces(str, pos);
        if (str.charAt(pos.v) == ']') {
            pos.v++;
            return jal;
        }
        // array is not empty
        while (true) {
            try {
                final JSonValue value = parseValue(str, file, lineNum, pos);
                al.add(value);
                final char res = expectOneOf(str, file, lineNum, pos, ",]");
                if (res == ']') {
                    return jal;
                }
            } catch (final JSonException ex) {
                // here either we got a ] without any value, or
                // something went wrong. In the first case, it's ok, it
                // was an empty array. we check for that
                if (al.isEmpty() && str.charAt(pos.v) == ']')
                    return jal;
                throw ex;
            }
        }
    }

    private static JSonNumberValue parseNumber(String str, String file,
            int lineNum, MutableInteger pos) throws JSonException {
        int intPart = 0;
        boolean negative = false;
        // parse int
        if (str.charAt(pos.v) == '-') {
            pos.v++;
            negative = true;
        }
        if (str.charAt(pos.v) != '0') {
            while (Character.isDigit(str.charAt(pos.v))) {
                intPart = intPart * 10 + (str.charAt(pos.v) - '0');
                pos.v++;
            }
        } else
            pos.v++;
        if (negative) {
            intPart = -intPart;
        }
        final char res = expectOneOf(str, file, lineNum, pos, ".eE, \t\n}]");
        switch (res) {
        case '.':
            double d = intPart;
            for (int div = 10; Character.isDigit(str.charAt(pos.v)); div *= 10) {
                d = d + (str.charAt(pos.v) - '0') / div;
                pos.v++;
            }
            return new JSonNumberValue(d);
        case 'e':
        case 'E':
            negative = false;
            if (str.charAt(pos.v) == '-') {
                negative = true;
                pos.v++;
            } else if (str.charAt(pos.v) == '+') {
                pos.v++;
            }
            int exp = 0;
            while (Character.isDigit(str.charAt(pos.v))) {
                exp = exp * 10 + (str.charAt(pos.v) - '0');
                pos.v++;
            }
            if (negative)
                exp = -exp;
            return new JSonNumberValue(intPart * Math.pow(10, exp));
        default:
            pos.v--;
            return new JSonNumberValue(intPart);
        }
    }

    private static JSonValue parseValue(String str, String file, int lineNum,
            MutableInteger pos) throws JSonException {
        while (Character.isSpaceChar(str.charAt(pos.v))) {
            pos.v++;
        }
        final char c = str.charAt(pos.v);
        switch (c) {
        case '"':
            return parseString(str, file, lineNum, pos);
        case '{':
            return parseObject(str, file, lineNum, pos, new JSonObject());
        case '[':
            return parseArray(str, file, lineNum, pos);
        default:
            if (Character.isDigit(c) || c == '-') {
                return parseNumber(str, file, lineNum, pos);
            } else if (str.startsWith("true", pos.v)) {
                pos.v += 4;
                return JSonBooleanValue.TRUE;
            } else if (str.startsWith("false", pos.v)) {
                pos.v += 5;
                return JSonBooleanValue.FALSE;
            } else if (str.startsWith("null", pos.v)) {
                pos.v += 4;
                return null;
            } else {
                throw new JSonException("Unexpected character '" + c
                        + "' while parsing value");
            }
        }
    }

    public static JSonObject parseObject(String str, String file, int lineNum)
            throws JSonException {
        final MutableInteger pos = new MutableInteger(0);
        return parseObject(str, file, lineNum, pos, new JSonObject());
    }

    public static JSonObject parseObject(String str, JSonObject o)
            throws JSonException {
        return parseObject(str, null, -1, new MutableInteger(0), o);
    }

    private static JSonObject parseObject(String str, String file, int lineNum,
            MutableInteger pos, JSonObject o) throws JSonException {
        if (o == null)
            o = new JSonObject();

        expect(str, file, lineNum, pos, '{');
        // could use expect() here, but would cause a lot of exceptions to be instantiated and thrown during parsing. 
        skipSpaces(str, pos);
        if (str.charAt(pos.v) == '}') {
            pos.v++;
            return o;
        }
        while (true) {
            final String key = parseString(str, file, lineNum, pos).toString();
            expect(str, file, lineNum, pos, ':');
            final JSonValue value = parseValue(str, file, lineNum, pos);
            o.set(key, value);
            final char res = expectOneOf(str, file, lineNum, pos, ",}");
            if (res == '}') {
                return o;
            }
        }
    }
}
