/*
 * Diagnostic counters for OpenJDK MethodHandle startup cost.
 * Enabled only with -Djava.lang.invoke.MethodHandle.TRACE_MH_STATS=true.
 * Nothing is printed while running; one summary is printed at JVM exit.
 */

package java.lang.invoke;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/*non-public*/ final class MHStats {
    private MHStats() { }

    // One slot per LambdaForm.Kind constant (the enum has more than 64 values).
    private static final int MAX_KINDS = LambdaForm.Kind.values().length;

    // ---- per LambdaForm.Kind counters (index = Kind.ordinal()) ----
    private static final long[] compiles   = new long[MAX_KINDS]; // generateCustomizedCode calls
    private static final long[] hits       = new long[MAX_KINDS]; // found in a Holder class
    private static final long[] customized = new long[MAX_KINDS]; // customized form, never pregenerated
    private static final long[] noCase     = new long[MAX_KINDS]; // lookupPregenerated has no case for this Kind
    private static final long[] kindNs     = new long[MAX_KINDS]; // lookup+ASM+define+resolve for misses

    // ---- per "kind signature" miss table: key -> {count, ns} ----
    private static final HashMap<String, long[]> missBySig = new HashMap<>();

    // ---- phase timers (non-overlapping, all inside generateCustomizedCode) ----
    private static long lookupNs, lookupCalls;
    private static long asmNs, asmBytes;
    private static long defineNs;
    private static long resolveNs;
    private static long compileWallNs;          // whole LambdaForm.compileToBytecode body

    // ---- who triggered compiles / interpreter use ----
    private static long compileFromPrepare;     // LambdaForm.prepare(), COMPILE_THRESHOLD == 0
    private static long compileFromThreshold;   // checkInvocationCounter() reached threshold
    private static long interpretCalls;         // interpretWithArguments entries

    // ---- indy (openj9 MethodHandleResolver.resolveInvokeDynamic) ----
    private static long indyCalls, indyNs, linkCallSiteNs;
    // exclusive sub-phases of resolveInvokeDynamic that run before linkCallSite
    private static long indyTypeNs, indyAccessNs, indyBsmNs, indyArgsNs;
    // per bootstrap method / per caller class: {count, totalNs, linkCallSiteNs}
    private static final HashMap<String, long[]> indyByBsm = new HashMap<>();
    private static final HashMap<String, long[]> indyByCaller = new HashMap<>();

    // ---- inside MethodHandleNatives.linkCallSiteImpl ----
    private static long linkCallSiteCalls, makeSiteNs, dynInvokerNs, linkToNs;

    // ---- InnerClassLambdaMetafactory (inclusive: part of makeSite) ----
    private static long lmfBuilds, lmfNonCapturing, lmfBuildNs, lmfSpinNs, lmfDefineNs, lmfBytes;

    // ---- StringConcatFactory.makeConcatWithConstants (inclusive: part of makeSite) ----
    private static long scfCalls, scfGenerateNs, scfAsTypeNs;

    // ---- CONSTANT_MethodHandle resolution (sendResolveMethodHandle), by reference kind 0..9 ----
    private static final long[] mhConstCount = new long[10];
    private static final long[] mhConstNs    = new long[10];

    // ---- invokehandle resolution (MethodHandleNatives.linkMethod): {count, totalNs, unused} ----
    private static final HashMap<String, long[]> linkMethodByName = new HashMap<>();

    static {
        try {
            Runtime.getRuntime().addShutdownHook(new Reporter());
        } catch (Throwable t) {
            // VM shutting down or hooks unavailable: no report
        }
    }

    // Named class, not a lambda: a lambda would use invokedynamic during boot.
    private static final class Reporter extends Thread {
        Reporter() { super("MHStats-reporter"); }
        @Override public void run() { print(); }
    }

    // ------------------------------------------------------------------ recorders

    static synchronized void lookup(LambdaForm.Kind k, boolean hit, long ns) {
        lookupCalls++;
        lookupNs += ns;
        compiles[k.ordinal()]++;
        if (hit) hits[k.ordinal()]++;
    }
    static synchronized void customized(LambdaForm.Kind k) { customized[k.ordinal()]++; }
    static synchronized void noCase(LambdaForm.Kind k)     { noCase[k.ordinal()]++; }

    static synchronized void asm(long ns, int bytes) { asmNs += ns; asmBytes += bytes; }
    static synchronized void define(long ns)         { defineNs += ns; }
    static synchronized void resolve(long ns)        { resolveNs += ns; }

    /** Called for a miss, once the class has been generated, defined and resolved. */
    static synchronized void miss(LambdaForm.Kind k, String key, long totalNs) {
        kindNs[k.ordinal()] += totalNs;
        long[] e = missBySig.get(key);
        if (e == null) { e = new long[2]; missBySig.put(key, e); }
        e[0]++;
        e[1] += totalNs;
    }

    static synchronized void compileWall(long ns)        { compileWallNs += ns; }
    static synchronized void compileFromPrepare()        { compileFromPrepare++; }
    static synchronized void compileFromThreshold()      { compileFromThreshold++; }
    // Not synchronized: can be called millions of times; a lost update only makes the count slightly low.
    static void interpretCall()                          { interpretCalls++; }
    static synchronized void indy(String bsm, String caller, long totalNs, long linkNs,
                                  long typeNs, long accessNs, long bsmNs, long argsNs) {
        indyCalls++; indyNs += totalNs; linkCallSiteNs += linkNs;
        indyTypeNs += typeNs; indyAccessNs += accessNs; indyBsmNs += bsmNs; indyArgsNs += argsNs;
        add(indyByBsm, bsm, totalNs, linkNs);
        add(indyByCaller, caller, totalNs, linkNs);
    }
    static synchronized void linkCallSiteParts(long makeSite, long dynInvoker, long linkTo) {
        linkCallSiteCalls++; makeSiteNs += makeSite; dynInvokerNs += dynInvoker; linkToNs += linkTo;
    }
    static synchronized void lambdaBuild(long ns, boolean nonCapturing) {
        lmfBuilds++; lmfBuildNs += ns;
        if (nonCapturing) lmfNonCapturing++;
    }
    static synchronized void lambdaSpin(long ns)             { lmfSpinNs += ns; }
    static synchronized void lambdaDefine(long ns, int bytes) { lmfDefineNs += ns; lmfBytes += bytes; }
    static synchronized void concat(long generateNs, long asTypeNs) {
        scfCalls++; scfGenerateNs += generateNs; scfAsTypeNs += asTypeNs;
    }
    static synchronized void mhConstant(int refKind, long ns) {
        int k = (refKind >= 0 && refKind < 10) ? refKind : 0;
        mhConstCount[k]++; mhConstNs[k] += ns;
    }
    static synchronized void linkMethod(String key, long ns) {
        add(linkMethodByName, key, ns, 0L);
    }
    private static void add(HashMap<String, long[]> map, String key, long ns, long extraNs) {
        long[] e = map.get(key);
        if (e == null) { e = new long[3]; map.put(key, e); }
        e[0]++; e[1] += ns; e[2] += extraNs;
    }

    // ------------------------------------------------------------------ report

    private static String ms(long ns) {
        long us = ns / 1000;
        return (us / 1000) + "." + String.valueOf(1000 + (us % 1000)).substring(1) + " ms";
    }

    static synchronized void print() {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("[MH_STATS] ========== MethodHandle startup statistics ==========\n");

        long totCompiles = 0, totHits = 0, totCust = 0, totNoCase = 0;
        LambdaForm.Kind[] kinds = LambdaForm.Kind.values();
        sb.append("[MH_STATS] --- lookupPregenerated outcome by LambdaForm.Kind ---\n");
        sb.append("[MH_STATS] kind                       compiles  hit  customized  noCase  notInHolder   missTime\n");
        for (int i = 0; i < kinds.length && i < MAX_KINDS; i++) {
            if (compiles[i] == 0) continue;
            long notInHolder = compiles[i] - hits[i] - customized[i] - noCase[i];
            totCompiles += compiles[i]; totHits += hits[i];
            totCust += customized[i];   totNoCase += noCase[i];
            sb.append("[MH_STATS] ").append(pad(kinds[i].name(), 26))
              .append(pad(compiles[i], 9)).append(pad(hits[i], 5))
              .append(pad(customized[i], 12)).append(pad(noCase[i], 8))
              .append(pad(notInHolder, 13)).append(ms(kindNs[i])).append('\n');
        }
        long totNotInHolder = totCompiles - totHits - totCust - totNoCase;
        sb.append("[MH_STATS] TOTAL compiles=").append(totCompiles)
          .append(" hit=").append(totHits)
          .append(" customized=").append(totCust)
          .append(" noCase=").append(totNoCase)
          .append(" notInHolder=").append(totNotInHolder).append('\n');

        sb.append("[MH_STATS] --- compile phase timers (exclusive, do not overlap) ---\n");
        long phases = lookupNs + asmNs + defineNs + resolveNs;
        sb.append("[MH_STATS] lookupPregenerated      : ").append(ms(lookupNs)).append(" (").append(lookupCalls).append(" calls)\n");
        sb.append("[MH_STATS] ASM bytecode generation : ").append(ms(asmNs)).append(" (").append(asmBytes).append(" bytes)\n");
        sb.append("[MH_STATS] defineAnonymousClass+init: ").append(ms(defineNs)).append('\n');
        sb.append("[MH_STATS] resolveInvokerMember    : ").append(ms(resolveNs)).append('\n');
        sb.append("[MH_STATS] sum of the four phases  : ").append(ms(phases)).append('\n');
        sb.append("[MH_STATS] compileToBytecode wall  : ").append(ms(compileWallNs)).append('\n');

        sb.append("[MH_STATS] --- who triggers compiles / interpreter ---\n");
        sb.append("[MH_STATS] compile from prepare()          : ").append(compileFromPrepare).append('\n');
        sb.append("[MH_STATS] compile from threshold reached  : ").append(compileFromThreshold).append('\n');
        sb.append("[MH_STATS] interpretWithArguments calls    : ").append(interpretCalls).append('\n');

        sb.append("[MH_STATS] --- invokedynamic resolution ---\n");
        sb.append("[MH_STATS] resolveInvokeDynamic calls      : ").append(indyCalls).append('\n');
        sb.append("[MH_STATS] resolveInvokeDynamic total      : ").append(ms(indyNs)).append('\n');
        sb.append("[MH_STATS]   linkCallSite (bootstrap+link) : ").append(ms(linkCallSiteNs)).append('\n');
        sb.append("[MH_STATS]   indy prep (total - link)      : ").append(ms(indyNs - linkCallSiteNs)).append('\n');
        sb.append("[MH_STATS]     prep: resolve MethodType    : ").append(ms(indyTypeNs)).append('\n');
        sb.append("[MH_STATS]     prep: Lookup.in + access chk: ").append(ms(indyAccessNs)).append('\n');
        sb.append("[MH_STATS]     prep: resolve BSM handle    : ").append(ms(indyBsmNs)).append('\n');
        sb.append("[MH_STATS]     prep: static BSM args       : ").append(ms(indyArgsNs)).append('\n');
        sb.append("[MH_STATS]     prep: unaccounted           : ")
          .append(ms(indyNs - linkCallSiteNs - indyTypeNs - indyAccessNs - indyBsmNs - indyArgsNs)).append('\n');

        sb.append("[MH_STATS] --- linkCallSiteImpl parts (").append(linkCallSiteCalls).append(" calls, exclusive) ---\n");
        sb.append("[MH_STATS] CallSite.makeSite (runs the BSM) : ").append(ms(makeSiteNs)).append('\n');
        sb.append("[MH_STATS] callSite.dynamicInvoker()        : ").append(ms(dynInvokerNs)).append('\n');
        sb.append("[MH_STATS] Invokers.linkTo*Method           : ").append(ms(linkToNs)).append('\n');
        sb.append("[MH_STATS]   inside makeSite (inclusive):\n");
        sb.append("[MH_STATS]   lambda metafactory builds      : ").append(lmfBuilds)
          .append(" (non-capturing ").append(lmfNonCapturing).append(") ").append(ms(lmfBuildNs)).append('\n');
        sb.append("[MH_STATS]     spinInnerClass (ASM+define)  : ").append(ms(lmfSpinNs)).append('\n');
        sb.append("[MH_STATS]     defineAnonymousClass         : ").append(ms(lmfDefineNs))
          .append(" (").append(lmfBytes).append(" bytes)\n");
        sb.append("[MH_STATS]   string concat BSM calls        : ").append(scfCalls).append('\n');
        sb.append("[MH_STATS]     generate strategy            : ").append(ms(scfGenerateNs)).append('\n');
        sb.append("[MH_STATS]     mh.asType(concatType)        : ").append(ms(scfAsTypeNs)).append('\n');

        sb.append("[MH_STATS] --- CONSTANT_MethodHandle resolution by refKind (inclusive) ---\n");
        for (int k = 0; k < mhConstCount.length; k++) {
            if (mhConstCount[k] == 0) continue;
            sb.append("[MH_STATS] refKind ").append(k).append(": ").append(pad(mhConstCount[k], 6))
              .append(ms(mhConstNs[k])).append('\n');
        }

        top(sb, "indy resolution by bootstrap method (count, total, linkCallSite)", indyByBsm, 10, true);
        top(sb, "indy resolution by caller class (count, total, linkCallSite)", indyByCaller, 20, true);
        top(sb, "invokehandle resolution (linkMethod) by target (count, total)", linkMethodByName, 10, false);

        sb.append("[MH_STATS] --- MemberName (OpenJ9 finalizable-object cost) ---\n");
        sb.append("[MH_STATS] constructed                      : ").append(MethodHandleStatics.MN_CREATED).append('\n');
        sb.append("[MH_STATS] cloned                           : ").append(MethodHandleStatics.MN_CLONED).append('\n');
        sb.append("[MH_STATS] native resolve calls / time      : ").append(MethodHandleStatics.MN_RESOLVES)
          .append(" / ").append(ms(MethodHandleStatics.MN_RESOLVE_NS)).append('\n');

        sb.append("[MH_STATS] --- top 40 missed shapes by time (feed these to the JLI trace file) ---\n");
        ArrayList<Map.Entry<String, long[]>> list = new ArrayList<>(missBySig.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, long[]>>() {
            @Override public int compare(Map.Entry<String, long[]> a, Map.Entry<String, long[]> b) {
                return Long.compare(b.getValue()[1], a.getValue()[1]);
            }
        });
        for (int i = 0; i < list.size() && i < 40; i++) {
            Map.Entry<String, long[]> e = list.get(i);
            sb.append("[MH_STATS] ").append(pad(e.getValue()[0], 4)).append("x ")
              .append(ms(e.getValue()[1])).append("  ").append(e.getKey()).append('\n');
        }
        sb.append("[MH_STATS] distinct missed shapes: ").append(list.size()).append('\n');
        sb.append("[MH_STATS] =====================================================\n");
        System.out.print(sb);
        System.out.flush();
    }

    private static void top(StringBuilder sb, String title, HashMap<String, long[]> map, int n, boolean withLink) {
        sb.append("[MH_STATS] --- top ").append(n).append(' ').append(title).append(" ---\n");
        ArrayList<Map.Entry<String, long[]>> l = new ArrayList<>(map.entrySet());
        Collections.sort(l, new Comparator<Map.Entry<String, long[]>>() {
            @Override public int compare(Map.Entry<String, long[]> a, Map.Entry<String, long[]> b) {
                return Long.compare(b.getValue()[1], a.getValue()[1]);
            }
        });
        for (int i = 0; i < l.size() && i < n; i++) {
            Map.Entry<String, long[]> e = l.get(i);
            sb.append("[MH_STATS] ").append(pad(e.getValue()[0], 5)).append("x ").append(ms(e.getValue()[1]));
            if (withLink) sb.append("  link ").append(ms(e.getValue()[2]));
            sb.append("  ").append(e.getKey()).append('\n');
        }
        sb.append("[MH_STATS] distinct: ").append(l.size()).append('\n');
    }

    private static String pad(String s, int w) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < w) b.append(' ');
        return b.toString();
    }
    private static String pad(long v, int w) { return pad(String.valueOf(v), w); }
}
