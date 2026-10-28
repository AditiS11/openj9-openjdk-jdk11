/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang.invoke;

import jdk.internal.misc.Unsafe;
import sun.security.action.GetPropertyAction;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class consists exclusively of static names internal to the
 * method handle implementation.
 * Usage:  {@code import static java.lang.invoke.MethodHandleStatics.*}
 * @author John Rose, JSR 292 EG
 */
/*non-public*/ class MethodHandleStatics {

    private MethodHandleStatics() { }  // do not instantiate

    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    static final boolean DEBUG_METHOD_HANDLE_NAMES;
    static final boolean DUMP_CLASS_FILES;
    static final boolean TRACE_INTERPRETER;
    static final boolean TRACE_METHOD_LINKAGE;
    static final boolean TRACE_RESOLVE;
    static final int COMPILE_THRESHOLD;
    static final boolean LOG_LF_COMPILATION_FAILURE;
    static final int DONT_INLINE_THRESHOLD;
    static final int PROFILE_LEVEL;
    static final boolean PROFILE_GWT;
    static final int CUSTOMIZE_THRESHOLD;
    static final boolean VAR_HANDLE_GUARDS;
    static final int MAX_ARITY;

    /**
     * Trace every lookupPregenerated() call in
     * InvokerBytecodeGenerator, logging the LambdaForm kind, basic-type
     * signature, customization state, and the exact reason for each miss
     * (GATE1_CUSTOMIZED or GATE2_NOT_IN_HOLDER).  Also records the elapsed
     * nanoseconds for every generateCustomizedCodeBytes() call so the total
     * runtime bytecode-generation cost is visible.
     *
     */
    static final boolean TRACE_LF_PREGENERATION;

    /**
     * Suppress per-MethodHandle LambdaForm customization.
     * When set, maybeCustomize() becomes a no-op so mh.customize() is never
     * called, every MethodHandle retains the shared (uncustomized) LambdaForm,
     * and form.customized remains null throughout the run.
     *
     * This keeps lookupPregenerated() Gate 1 from firing, allowing Gate 2
     * (the Invokers.Holder pre-generated stub lookup) to run for every
     * compileToBytecode() call.  It also removes the CHECK_CUSTOM Name node
     * from invokeHandleForm() LambdaForms, eliminating the per-invocation
     * checkCustomized() overhead entirely.
     *
     * Safe under -Xint: the JIT never runs under -Xint, so the JIT-time
     * benefit of customization (constant-folded field reads, direct dispatch)
     * is never realized.  Shared LambdaForms execute identically.
     *
     */
    static final boolean SUPPRESS_CUSTOMIZATION;

    /**
     * Invocation counters. Only allocated and registered when
     * TRACE_LF_PREGENERATION is true, so there is zero overhead otherwise.
     * Each counter maps directly to one row in the perf regression table.
     */

    /** Number of times MethodHandleResolver.resolveInvokeDynamic() was entered. */
    static final AtomicInteger CNT_RESOLVE_INDY;

    /** Number of times MethodHandleNatives.linkCallSite() was entered. */
    static final AtomicInteger CNT_LINK_CALL_SITE;

    /** Number of times LambdaForm.compileToBytecode() did real work
     *  (i.e. was not skipped by the forceInterpretation or already-compiled guards). */
    static final AtomicInteger CNT_COMPILE_TO_BYTECODE;

    /** Number of times lookupPregenerated() returned a non-null result (cache hit). */
    static final AtomicInteger CNT_PREGEN_HIT;

    /** Number of times lookupPregenerated() returned null due to Gate 1
     *  (form.customized != null). */
    static final AtomicInteger CNT_PREGEN_MISS_GATE1;

    /** Number of times lookupPregenerated() returned null due to Gate 2
     *  (signature not present in the Holder class). */
    static final AtomicInteger CNT_PREGEN_MISS_GATE2;

    /** Number of times generateCustomizedCodeBytes() was actually invoked
     *  (i.e. IBG ran ASM to produce a new class file). */
    static final AtomicInteger CNT_IBG_CALLS;

    /** Total nanoseconds spent inside generateCustomizedCodeBytes() calls. */
    static final AtomicLong    CNT_IBG_TOTAL_NS;

    static {
        Properties props = GetPropertyAction.privilegedGetProperties();
        DEBUG_METHOD_HANDLE_NAMES = Boolean.parseBoolean(
                props.getProperty("java.lang.invoke.MethodHandle.DEBUG_NAMES"));
        DUMP_CLASS_FILES = Boolean.parseBoolean(
                props.getProperty("java.lang.invoke.MethodHandle.DUMP_CLASS_FILES"));
        TRACE_INTERPRETER = Boolean.parseBoolean(
                props.getProperty("java.lang.invoke.MethodHandle.TRACE_INTERPRETER"));
        TRACE_METHOD_LINKAGE = Boolean.parseBoolean(
                props.getProperty("java.lang.invoke.MethodHandle.TRACE_METHOD_LINKAGE"));
        TRACE_RESOLVE = Boolean.parseBoolean(
                props.getProperty("java.lang.invoke.MethodHandle.TRACE_RESOLVE"));
        COMPILE_THRESHOLD = Integer.parseInt(
                props.getProperty("java.lang.invoke.MethodHandle.COMPILE_THRESHOLD", "0"));
        LOG_LF_COMPILATION_FAILURE = Boolean.parseBoolean(
                props.getProperty("java.lang.invoke.MethodHandle.LOG_LF_COMPILATION_FAILURE", "false"));
        DONT_INLINE_THRESHOLD = Integer.parseInt(
                props.getProperty("java.lang.invoke.MethodHandle.DONT_INLINE_THRESHOLD", "30"));
        PROFILE_LEVEL = Integer.parseInt(
                props.getProperty("java.lang.invoke.MethodHandle.PROFILE_LEVEL", "0"));
        PROFILE_GWT = Boolean.parseBoolean(
                props.getProperty("java.lang.invoke.MethodHandle.PROFILE_GWT", "true"));
        CUSTOMIZE_THRESHOLD = Integer.parseInt(
                props.getProperty("java.lang.invoke.MethodHandle.CUSTOMIZE_THRESHOLD", "127"));
        VAR_HANDLE_GUARDS = Boolean.parseBoolean(
                props.getProperty("java.lang.invoke.VarHandle.VAR_HANDLE_GUARDS", "true"));

        // Do not adjust this except for special platforms:
        MAX_ARITY = Integer.parseInt(
                props.getProperty("java.lang.invoke.MethodHandleImpl.MAX_ARITY", "255"));

        if (CUSTOMIZE_THRESHOLD < -1 || CUSTOMIZE_THRESHOLD > 127) {
            throw newInternalError("CUSTOMIZE_THRESHOLD should be in [-1...127] range");
        }

        TRACE_LF_PREGENERATION = Boolean.parseBoolean(
                props.getProperty("java.lang.invoke.MethodHandle.TRACE_LF_PREGENERATION", "false"));
        SUPPRESS_CUSTOMIZATION = Boolean.parseBoolean(
                props.getProperty("java.lang.invoke.MethodHandle.SUPPRESS_CUSTOMIZATION", "false"));

        if (TRACE_LF_PREGENERATION) {
            CNT_RESOLVE_INDY         = new AtomicInteger();
            CNT_LINK_CALL_SITE       = new AtomicInteger();
            CNT_COMPILE_TO_BYTECODE  = new AtomicInteger();
            CNT_PREGEN_HIT           = new AtomicInteger();
            CNT_PREGEN_MISS_GATE1    = new AtomicInteger();
            CNT_PREGEN_MISS_GATE2    = new AtomicInteger();
            CNT_IBG_CALLS            = new AtomicInteger();
            CNT_IBG_TOTAL_NS         = new AtomicLong();

            // Print the summary table once, when the JVM exits.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.err.println("[LF_PREGEN] ========== MH resolution counter summary ==========");
                System.err.println("[LF_PREGEN] resolveInvokeDynamic calls  : " + CNT_RESOLVE_INDY.get());
                System.err.println("[LF_PREGEN] linkCallSite calls           : " + CNT_LINK_CALL_SITE.get());
                System.err.println("[LF_PREGEN] compileToBytecode calls      : " + CNT_COMPILE_TO_BYTECODE.get());
                System.err.println("[LF_PREGEN] lookupPregenerated hits      : " + CNT_PREGEN_HIT.get());
                System.err.println("[LF_PREGEN] lookupPregenerated Gate1miss : " + CNT_PREGEN_MISS_GATE1.get());
                System.err.println("[LF_PREGEN] lookupPregenerated Gate2miss : " + CNT_PREGEN_MISS_GATE2.get());
                System.err.println("[LF_PREGEN] IBG generateCodeBytes calls  : " + CNT_IBG_CALLS.get());
                System.err.println("[LF_PREGEN] IBG generateCodeBytes total  : " + CNT_IBG_TOTAL_NS.get() + " ns");
                System.err.println("[LF_PREGEN] ====================================================");
            }, "LF-pregen-stats"));
        } else {
            CNT_RESOLVE_INDY         = null;
            CNT_LINK_CALL_SITE       = null;
            CNT_COMPILE_TO_BYTECODE  = null;
            CNT_PREGEN_HIT           = null;
            CNT_PREGEN_MISS_GATE1    = null;
            CNT_PREGEN_MISS_GATE2    = null;
            CNT_IBG_CALLS            = null;
            CNT_IBG_TOTAL_NS         = null;
        }
    }

    /** Tell if any of the debugging switches are turned on.
     *  If this is the case, it is reasonable to perform extra checks or save extra information.
     */
    /*non-public*/ static boolean debugEnabled() {
        return (DEBUG_METHOD_HANDLE_NAMES |
                DUMP_CLASS_FILES |
                TRACE_INTERPRETER |
                TRACE_METHOD_LINKAGE |
                LOG_LF_COMPILATION_FAILURE);
    }

    // handy shared exception makers (they simplify the common case code)
    /*non-public*/ static InternalError newInternalError(String message) {
        return new InternalError(message);
    }
    /*non-public*/ static InternalError newInternalError(String message, Exception cause) {
        return new InternalError(message, cause);
    }
    /*non-public*/ static InternalError newInternalError(Exception cause) {
        return new InternalError(cause);
    }
    /*non-public*/ static RuntimeException newIllegalStateException(String message) {
        return new IllegalStateException(message);
    }
    /*non-public*/ static RuntimeException newIllegalStateException(String message, Object obj) {
        return new IllegalStateException(message(message, obj));
    }
    /*non-public*/ static RuntimeException newIllegalArgumentException(String message) {
        return new IllegalArgumentException(message);
    }
    /*non-public*/ static RuntimeException newIllegalArgumentException(String message, Object obj) {
        return new IllegalArgumentException(message(message, obj));
    }
    /*non-public*/ static RuntimeException newIllegalArgumentException(String message, Object obj, Object obj2) {
        return new IllegalArgumentException(message(message, obj, obj2));
    }
    /** Propagate unchecked exceptions and errors, but wrap anything checked and throw that instead. */
    /*non-public*/ static Error uncaughtException(Throwable ex) {
        if (ex instanceof Error)  throw (Error) ex;
        if (ex instanceof RuntimeException)  throw (RuntimeException) ex;
        throw new InternalError("uncaught exception", ex);
    }
    private static String message(String message, Object obj) {
        if (obj != null)  message = message + ": " + obj;
        return message;
    }
    private static String message(String message, Object obj, Object obj2) {
        if (obj != null || obj2 != null)  message = message + ": " + obj + ", " + obj2;
        return message;
    }
    /*non-public*/ static void rangeCheck2(int start, int end, int size) {
        if (0 > start || start > end || end > size)
            throw new IndexOutOfBoundsException(start+".."+end);
    }
    /*non-public*/ static int rangeCheck1(int index, int size) {
        if (0 > index || index >= size)
            throw new IndexOutOfBoundsException(index);
        return index;
    }
}
