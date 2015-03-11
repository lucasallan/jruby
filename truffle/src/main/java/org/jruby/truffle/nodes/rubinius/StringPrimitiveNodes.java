/*
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 * 
 * Some of the code in this class is transliterated from C++ code in Rubinius.
 * 
 * Copyright (c) 2007-2014, Evan Phoenix and contributors
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * * Neither the name of Rubinius nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *  
 */
package org.jruby.truffle.nodes.rubinius;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.ConditionProfile;
import org.jcodings.Encoding;
import org.jcodings.exception.EncodingException;
import org.jcodings.specific.ASCIIEncoding;
import org.jruby.truffle.nodes.RubyNode;
import org.jruby.truffle.nodes.cast.TaintResultNode;
import org.jruby.truffle.nodes.core.StringNodes;
import org.jruby.truffle.nodes.core.StringNodesFactory;
import org.jruby.truffle.nodes.dispatch.CallDispatchHeadNode;
import org.jruby.truffle.nodes.dispatch.DispatchHeadNodeFactory;
import org.jruby.truffle.runtime.RubyContext;
import org.jruby.truffle.runtime.UndefinedPlaceholder;
import org.jruby.truffle.runtime.control.RaiseException;
import org.jruby.truffle.runtime.core.*;
import org.jruby.util.ByteList;
import org.jruby.util.ConvertBytes;
import org.jruby.util.StringSupport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Rubinius primitives associated with the Ruby {@code String} class.
 */
public abstract class StringPrimitiveNodes {

    @RubiniusPrimitive(name = "string_awk_split")
    public static abstract class StringAwkSplitPrimitiveNode extends RubiniusPrimitiveNode {

        @Child private TaintResultNode taintResultNode;

        public StringAwkSplitPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
            taintResultNode = new TaintResultNode(context, sourceSection, true, new int[]{});
        }

        public StringAwkSplitPrimitiveNode(StringAwkSplitPrimitiveNode prev) {
            super(prev);
            taintResultNode = prev.taintResultNode;
        }

        @Specialization
        public RubyArray stringAwkSplit(RubyString string, int lim) {
            notDesignedForCompilation();

            final List<RubyString> ret = new ArrayList<>();
            final ByteList value = string.getBytes();
            final boolean limit = lim > 0;
            int i = lim > 0 ? 1 : 0;

            byte[]bytes = value.getUnsafeBytes();
            int p = value.getBegin();
            int ptr = p;
            int len = value.getRealSize();
            int end = p + len;
            Encoding enc = value.getEncoding();
            boolean skip = true;

            int e = 0, b = 0;
            final boolean singlebyte = StringSupport.isSingleByteOptimizable(string, enc);
            while (p < end) {
                final int c;
                if (singlebyte) {
                    c = bytes[p++] & 0xff;
                } else {
                    try {
                        c = StringSupport.codePoint(getContext().getRuntime(), enc, bytes, p, end);
                    } catch (org.jruby.exceptions.RaiseException ex) {
                        throw new RaiseException(getContext().toTruffle(ex.getException(), this));
                    }

                    p += StringSupport.length(enc, bytes, p, end);
                }

                if (skip) {
                    if (enc.isSpace(c)) {
                        b = p - ptr;
                    } else {
                        e = p - ptr;
                        skip = false;
                        if (limit && lim <= i) break;
                    }
                } else {
                    if (enc.isSpace(c)) {
                        ret.add(makeString(string, b, e - b));
                        skip = true;
                        b = p - ptr;
                        if (limit) i++;
                    } else {
                        e = p - ptr;
                    }
                }
            }

            if (len > 0 && (limit || len > b || lim < 0)) ret.add(makeString(string, b, len - b));

            return RubyArray.fromObjects(getContext().getCoreLibrary().getArrayClass(), ret.toArray());
        }

        private RubyString makeString(RubyString source, int index, int length) {
            final ByteList bytes = new ByteList(source.getBytes(), index, length);
            bytes.setEncoding(source.getBytes().getEncoding());

            final RubyString ret = getContext().makeString(source.getLogicalClass(), bytes);
            taintResultNode.maybeTaint(source, ret);

            return ret;
        }
    }

    @RubiniusPrimitive(name = "string_byte_substring")
    public static abstract class StringByteSubstringPrimitiveNode extends RubiniusPrimitiveNode {

        @Child private TaintResultNode taintResultNode;

        public StringByteSubstringPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
            taintResultNode = new TaintResultNode(context, sourceSection, true, new int[]{});
        }

        public StringByteSubstringPrimitiveNode(StringByteSubstringPrimitiveNode prev) {
            super(prev);
            taintResultNode = prev.taintResultNode;
        }

        @Specialization
        public Object stringByteSubstring(RubyString string, int index, UndefinedPlaceholder length) {
            final Object subString = stringByteSubstring(string, index, 1);

            if (subString == getContext().getCoreLibrary().getNilObject()) {
                return subString;
            }

            if (((RubyString) subString).getByteList().length() == 0) {
                return getContext().getCoreLibrary().getNilObject();
            }

            return subString;
        }

        @Specialization
        public Object stringByteSubstring(RubyString string, int index, int length) {
            final ByteList bytes = string.getBytes();

            if (length < 0) {
                return getContext().getCoreLibrary().getNilObject();
            }

            final int normalizedIndex = string.normalizeIndex(index);

            if (normalizedIndex > bytes.length()) {
                return getContext().getCoreLibrary().getNilObject();
            }

            int rangeEnd = normalizedIndex + length;
            if (rangeEnd > bytes.length()) {
                rangeEnd = bytes.length();
            }

            if (normalizedIndex < bytes.getBegin()) {
                return getContext().getCoreLibrary().getNilObject();
            }

            final byte[] copiedBytes = Arrays.copyOfRange(bytes.getUnsafeBytes(), normalizedIndex, rangeEnd);
            final RubyString result = getContext().makeString(string.getLogicalClass(), new ByteList(copiedBytes, string.getBytes().getEncoding()));

            return taintResultNode.maybeTaint(string, result);
        }

        @Specialization
        public Object stringByteSubstring(RubyString string, int index, double length) {
            return stringByteSubstring(string, index, (int) length);
        }

        @Specialization
        public Object stringByteSubstring(RubyString string, double index, UndefinedPlaceholder length) {
            return stringByteSubstring(string, (int) index, 1);
        }

        @Specialization
        public Object stringByteSubstring(RubyString string, double index, double length) {
            return stringByteSubstring(string, (int) index, (int) length);
        }

        @Specialization
        public Object stringByteSubstring(RubyString string, double index, int length) {
            return stringByteSubstring(string, (int) index, length);
        }

        @Specialization
        public Object stringByteSubstring(RubyString string, RubyRange range, UndefinedPlaceholder unused) {
            return null;
        }

        @Specialization(guards = "!isRubyRange(arguments[1])")
        public Object stringByteSubstring(RubyString string, Object indexOrRange, Object length) {
            return null;
        }

    }

    @RubiniusPrimitive(name = "string_check_null_safe", needsSelf = false)
    public static abstract class StringCheckNullSafePrimitiveNode extends RubiniusPrimitiveNode {

        private final ConditionProfile nullByteProfile = ConditionProfile.createBinaryProfile();

        public StringCheckNullSafePrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StringCheckNullSafePrimitiveNode(StringCheckNullSafePrimitiveNode prev) {
            super(prev);
        }

        @Specialization
        public boolean stringCheckNullSafe(RubyString string) {
            for (byte b : string.getBytes().unsafeBytes()) {
                if (nullByteProfile.profile(b == 0)) {
                    return false;
                }
            }

            return true;
        }

    }

    @RubiniusPrimitive(name = "string_equal", needsSelf = true)
    public static abstract class StringEqualPrimitiveNode extends RubiniusPrimitiveNode {

        private final ConditionProfile incompatibleEncodingProfile = ConditionProfile.createBinaryProfile();

        public StringEqualPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StringEqualPrimitiveNode(StringEqualPrimitiveNode prev) {
            super(prev);
        }

        @Specialization
        public boolean stringEqual(RubyString string, RubyString other) {
            final ByteList a = string.getBytes();
            final ByteList b = other.getBytes();

            if (incompatibleEncodingProfile.profile((a.getEncoding() != b.getEncoding()) &&
                    (org.jruby.RubyEncoding.areCompatible(string, other) == null))) {
                return false;
            }

            return a.equal(b);
        }

    }

    @RubiniusPrimitive(name = "string_find_character")
    public static abstract class StringFindCharacterPrimitiveNode extends RubiniusPrimitiveNode {

        @Child private StringNodes.GetIndexNode getIndexNode;

        public StringFindCharacterPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
            getIndexNode = StringNodesFactory.GetIndexNodeFactory.create(context, sourceSection, new RubyNode[]{});
        }

        public StringFindCharacterPrimitiveNode(StringFindCharacterPrimitiveNode prev) {
            super(prev);
            getIndexNode = prev.getIndexNode;
        }

        @Specialization
        public Object stringFindCharacter(RubyString string, int index) {
            return getIndexNode.getIndex(string, index, UndefinedPlaceholder.INSTANCE);
        }

    }

    @RubiniusPrimitive(name = "string_from_codepoint", needsSelf = false)
    public static abstract class StringFromCodepointPrimitiveNode extends RubiniusPrimitiveNode {

        public StringFromCodepointPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StringFromCodepointPrimitiveNode(StringFromCodepointPrimitiveNode prev) {
            super(prev);
        }

        @Specialization(guards = "isSimple")
        public RubyString stringFromCodepointSimple(int code, RubyEncoding encoding) {
            return new RubyString(
                    getContext().getCoreLibrary().getStringClass(),
                    new ByteList(new byte[]{(byte) code}, encoding.getEncoding()));
        }

        @Specialization(guards = "!isSimple")
        public RubyString stringFromCodepoint(int code, RubyEncoding encoding) {
            notDesignedForCompilation();

            final int length;

            try {
                length = encoding.getEncoding().codeToMbcLength(code);
            } catch (EncodingException e) {
                CompilerDirectives.transferToInterpreter();
                throw new RaiseException(getContext().getCoreLibrary().rangeError(code, encoding, this));
            }

            if (length <= 0) {
                CompilerDirectives.transferToInterpreter();
                throw new RaiseException(getContext().getCoreLibrary().rangeError(code, encoding, this));
            }

            final byte[] bytes = new byte[length];

            try {
                encoding.getEncoding().codeToMbc(code, bytes, 0);
            } catch (EncodingException e) {
                CompilerDirectives.transferToInterpreter();
                throw new RaiseException(getContext().getCoreLibrary().rangeError(code, encoding, this));
            }

            return new RubyString(
                    getContext().getCoreLibrary().getStringClass(),
                    new ByteList(bytes, encoding.getEncoding()));
        }

        @Specialization
        public RubyString stringFromCodepointSimple(long code, RubyEncoding encoding) {
            notDesignedForCompilation();

            if (code < Integer.MIN_VALUE || code > Integer.MAX_VALUE) {
                CompilerDirectives.transferToInterpreter();
                throw new UnsupportedOperationException();
            }

            return stringFromCodepointSimple((int) code, encoding);
        }

        protected boolean isSimple(int code, RubyEncoding encoding) {
            return encoding.getEncoding() == ASCIIEncoding.INSTANCE && code >= 0x00 && code <= 0xFF;
        }

    }

    @RubiniusPrimitive(name = "string_to_f", needsSelf = false)
    public static abstract class StringToFPrimitiveNode extends RubiniusPrimitiveNode {

        public StringToFPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StringToFPrimitiveNode(StringToFPrimitiveNode prev) {
            super(prev);
        }

        @Specialization
        public Object stringToF(RubyString string) {
            notDesignedForCompilation();

            try {
                return Double.parseDouble(string.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }

    }

    @RubiniusPrimitive(name = "string_index")
    public static abstract class StringIndexPrimitiveNode extends RubiniusPrimitiveNode {

        public StringIndexPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StringIndexPrimitiveNode(StringIndexPrimitiveNode prev) {
            super(prev);
        }

        @Specialization
        public Object stringIndex(RubyString string, RubyString pattern, int start) {
            final int index = StringSupport.index(string,
                    pattern,
                    start, string.getBytes().getEncoding());

            if (index == -1) {
                return getContext().getCoreLibrary().getNilObject();
            }

            return index;
        }

    }

    @RubiniusPrimitive(name = "string_character_byte_index", needsSelf = false)
    public static abstract class StringCharacterByteIndexPrimitiveNode extends RubiniusPrimitiveNode {

        public StringCharacterByteIndexPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StringCharacterByteIndexPrimitiveNode(StringCharacterByteIndexPrimitiveNode prev) {
            super(prev);
        }

        @Specialization(guards = "isSingleByteOptimizable")
        public int stringCharacterByteIndex(RubyString string, int index, int start) {
            return start + index;
        }

        @Specialization(guards = "!isSingleByteOptimizable")
        public int stringCharacterByteIndexMultiByteEncoding(RubyString string, int index, int start) {
            final ByteList bytes = string.getBytes();

            return StringSupport.nth(bytes.getEncoding(), bytes.getUnsafeBytes(), bytes.getBegin(),
                    bytes.getBegin() + bytes.getRealSize(), start + index);
        }

        public static boolean isSingleByteOptimizable(RubyString string) {
            return StringSupport.isSingleByteOptimizable(string, string.getBytes().getEncoding());
        }
    }

    @RubiniusPrimitive(name = "string_byte_character_index", needsSelf = false)
    public static abstract class StringByteCharacterIndexPrimitiveNode extends RubiniusPrimitiveNode {

        public StringByteCharacterIndexPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StringByteCharacterIndexPrimitiveNode(StringByteCharacterIndexPrimitiveNode prev) {
            super(prev);
        }

        @Specialization
        public Object stringByteCharacterIndex(RubyString string, Object index, Object start) {
            throw new UnsupportedOperationException("string_byte_character_index");
        }

    }

    @RubiniusPrimitive(name = "string_character_index", needsSelf = false)
    public static abstract class StringCharacterIndexPrimitiveNode extends RubiniusPrimitiveNode {

        public StringCharacterIndexPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StringCharacterIndexPrimitiveNode(StringCharacterIndexPrimitiveNode prev) {
            super(prev);
        }

        @Specialization
        public Object stringCharacterIndex(RubyString string, Object indexedString, Object start) {
            throw new UnsupportedOperationException("string_character_index");
        }

    }

    @RubiniusPrimitive(name = "string_byte_index", needsSelf = false)
    public static abstract class StringByteIndexPrimitiveNode extends RubiniusPrimitiveNode {

        public StringByteIndexPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StringByteIndexPrimitiveNode(StringByteIndexPrimitiveNode prev) {
            super(prev);
        }

        @Specialization
        public Object stringByteIndex(RubyString string, int characters, int start) {
            if (string.getByteList().getEncoding().isSingleByte()) {
                return characters - start;
            } else {
                final Encoding encoding = string.getByteList().getEncoding();
                final int length = string.getByteList().length();

                int count = 0;

                int i;

                for(i = 0; i < characters && count < length; i++) {
                    if(!encoding.isMbcHead(string.getByteList().getUnsafeBytes(), count, length)) {
                        count++;
                    } else {
                        count += encoding.codeToMbcLength(string.getByteList().getUnsafeBytes()[count]);
                    }
                }

                if(i < characters) {
                    return getContext().getCoreLibrary().getNilObject();
                } else {
                    return count;
                }
            }
        }

    }

    @RubiniusPrimitive(name = "string_previous_byte_index", needsSelf = false)
    public static abstract class StringPreviousByteIndexPrimitiveNode extends RubiniusPrimitiveNode {

        public StringPreviousByteIndexPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StringPreviousByteIndexPrimitiveNode(StringPreviousByteIndexPrimitiveNode prev) {
            super(prev);
        }

        @Specialization
        public Object stringPreviousByteIndex(RubyString string, Object indexedString, Object start) {
            throw new UnsupportedOperationException("string_previous_byte_index");
        }

    }

    @RubiniusPrimitive(name = "string_copy_from", needsSelf = false)
    public static abstract class StringCopyFromPrimitiveNode extends RubiniusPrimitiveNode {

        public StringCopyFromPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StringCopyFromPrimitiveNode(StringCopyFromPrimitiveNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString stringCopyFrom(RubyString string, RubyString other, int start, int size, int dest) {
            int src = start;
            int dst = dest;
            int cnt = size;

            int osz = other.getByteList().length();
            if(src >= osz) return string;
            if(cnt < 0) return string;
            if(src < 0) src = 0;
            if(cnt > osz - src) cnt = osz - src;

            int sz = string.getByteList().length();
            if(dst >= sz) return string;
            if(dst < 0) dst = 0;
            if(cnt > sz - dst) cnt = sz - dst;

            System.arraycopy(other.getByteList().unsafeBytes(), src, string.getByteList().getUnsafeBytes(), dest, cnt);

            return string;
        }

    }

    @RubiniusPrimitive(name = "string_resize_capacity", needsSelf = false)
    public static abstract class StringResizeCapacityPrimitiveNode extends RubiniusPrimitiveNode {

        public StringResizeCapacityPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StringResizeCapacityPrimitiveNode(StringResizeCapacityPrimitiveNode prev) {
            super(prev);
        }

        @Specialization
        public Object stringResizeCapacity(RubyString string, Object capacity) {
            throw new UnsupportedOperationException("string_resize_capacity");
        }

    }

    @RubiniusPrimitive(name = "string_pattern")
    public static abstract class StringPatternPrimitiveNode extends RubiniusPrimitiveNode {

        public StringPatternPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StringPatternPrimitiveNode(StringPatternPrimitiveNode prev) {
            super(prev);
        }

        @Specialization
        public RubyString stringPattern(RubyClass stringClass, int size, RubyString string) {
            final byte[] bytes = new byte[size];
            final byte[] stringBytes = string.getByteList().unsafeBytes();

            if (string.getByteList().length() > 0) {
                for (int n = 0; n < size; n += string.getByteList().length()) {
                    System.arraycopy(stringBytes, 0, bytes, n, Math.min(string.getByteList().length(), size - n));
                }
            }
            
            return new RubyString(stringClass, new ByteList(bytes));
        }

    }

    @RubiniusPrimitive(name = "string_to_inum")
    public static abstract class StringToInumPrimitiveNode extends RubiniusPrimitiveNode {

        public StringToInumPrimitiveNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public StringToInumPrimitiveNode(StringToInumPrimitiveNode prev) {
            super(prev);
        }

        @Specialization
        public Object stringToInum(RubyString string, int fixBase, boolean strict) {
            notDesignedForCompilation();

            try {
                final org.jruby.RubyInteger result = ConvertBytes.byteListToInum19(getContext().getRuntime(),
                        string.getBytes(),
                        fixBase,
                        strict);

                return getContext().toTruffle(result);

            } catch (org.jruby.exceptions.RaiseException e) {
                throw new RaiseException(getContext().toTruffle(e.getException(), this));
            }
        }

    }

}
