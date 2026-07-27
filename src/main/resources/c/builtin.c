#include "builtin.h"
#include <float.h>

// ===== exception handling =====
_Thread_local Feng$ExFrame* Feng$ex_top = NULL;

void Feng$throw(void* ex) {
    if (!Feng$ex_top) {
        fprintf(stderr, "unhandled exception\n");
        abort();
    }
    Feng$ex_top->exception = ex;
    Feng$ex_top->state = 2;  // unhandled (catch sets to 1 if matched)
    longjmp(Feng$ex_top->buf, 1);
}

// ===== runtime exception types =====
const Feng$Meta Feng$meta_NullPointerError = {sizeof(Feng$NullPointerError), &Feng$meta_$Object, 0, NULL, NULL};
const Feng$Meta Feng$meta_IndexOutOfBoundsError = {sizeof(Feng$IndexOutOfBoundsError), &Feng$meta_$Object, 0, NULL, NULL};

void Feng$throwNullPointer() {
    Feng$NullPointerError* e = Feng$alloc(sizeof(Feng$NullPointerError));
    e->$meta = &Feng$meta_NullPointerError;
    Feng$throw(e);
}

void Feng$throwIndexOutOfBounds() {
    Feng$IndexOutOfBoundsError* e = Feng$alloc(sizeof(Feng$IndexOutOfBoundsError));
    e->$meta = &Feng$meta_IndexOutOfBoundsError;
    Feng$throw(e);
}

// ===== built-in type metadata =====
const Feng$Meta Feng$meta_$Object = {sizeof(struct $Object), NULL, 0, NULL, NULL};

const Feng$Meta_$Writer Feng$meta_$Writer = {{0}};
const Feng$Meta_$Writable Feng$meta_$Writable = {{0}};
const Feng$Meta_$Reader Feng$meta_$Reader = {{0}};

// ===== built-in function implementations =====

Int $intToStr(Int n, Feng$ArrayPRef_Byte buf) {
    if (buf.$length <= 0) return 0;

    // 0 needs special handling
    if (n == 0) {
        buf.$values[0] = (Byte)'0';
        return 1;
    }

    Byte tmp[21];  // max: "-9223372036854775808" = 20 chars + safety
    Int len = 0;
    Int64 abs = n;
    if (n < 0) {
        tmp[len++] = (Byte)'-';
        // INT64_MIN = -9223372036854775808, cannot negate directly
        if (n == INT64_MIN) {
            // Hardcode INT64_MIN as string — the only safe way
            static const Byte minStr[] = {
                '-','9','2','2','3','3','7','2','0','3','6',
                '8','5','4','7','7','5','8','0','8'
            };
            Int total = 20;
            Int64 to_copy = total < buf.$length ? total : buf.$length;
            for (Int64 i = 0; i < to_copy; i++) buf.$values[i] = minStr[i];
            return total;
        }
        abs = -n;
    }

    // extract digits in reverse order
    Byte digits[20];
    int nd = 0;
    do {
        digits[nd++] = (Byte)('0' + (abs % 10));
        abs /= 10;
    } while (abs > 0);

    for (int i = nd - 1; i >= 0; i--) tmp[len++] = digits[i];

    Int64 to_copy = len < buf.$length ? len : buf.$length;
    for (Int64 i = 0; i < to_copy; i++) buf.$values[i] = tmp[i];
    return len;
}

Int $floatToStr(Float64 n, Feng$ArrayPRef_Byte buf) {
    if (buf.$length <= 0) return 0;

    // NaN
    if (isnan(n)) {
        static const Byte nanStr[] = {'n','a','n'};
        Int64 to_copy = 3 < buf.$length ? 3 : buf.$length;
        for (Int64 i = 0; i < to_copy; i++) buf.$values[i] = nanStr[i];
        return 3;
    }

    // Infinity
    if (isinf(n)) {
        if (n < 0) {
            static const Byte negInf[] = {'-','i','n','f'};
            Int64 to_copy = 4 < buf.$length ? 4 : buf.$length;
            for (Int64 i = 0; i < to_copy; i++) buf.$values[i] = negInf[i];
            return 4;
        }
        static const Byte infStr[] = {'i','n','f'};
        Int64 to_copy = 3 < buf.$length ? 3 : buf.$length;
        for (Int64 i = 0; i < to_copy; i++) buf.$values[i] = infStr[i];
        return 3;
    }

    // Sign
    int negative = (n < 0.0);
    if (negative) n = -n;

    // Zero
    if (n == 0.0) {
        Int pos = 0;
        if (negative) { if (pos < buf.$length) buf.$values[pos] = (Byte)'-'; pos++; }
        if (pos < buf.$length) buf.$values[pos] = (Byte)'0';
        return pos + 1;
    }

    // --- convert via stack buffer, then copy without \0 ---
    #define FBUF_MAX 64
    Byte fbuf[FBUF_MAX];
    Int flen = 0;

    if (negative) fbuf[flen++] = (Byte)'-';

    // Compute decimal exponent k such that num = sig * 10^k, 1 <= sig < 10
    int k = (int)floor(log10(n));
    double sig = n / pow(10.0, (double)k);

    // Floating-point error correction
    if (sig < 1.0)  { sig *= 10.0; k--; }
    if (sig >= 10.0) { sig /= 10.0; k++; }

    // Extract up to 16 significant digits (15 + 1 for rounding)
    #define SIG_DIGITS 16
    int digits[SIG_DIGITS];
    int nd = 0;
    double rem = sig;
    for (int i = 0; i < SIG_DIGITS; i++) {
        int d = (int)rem;
        digits[i] = d;
        nd++;
        rem = (rem - d) * 10.0;
    }

    // Round: last extracted digit (index 15) is rounding digit
    int round_digit = digits[SIG_DIGITS - 1];
    nd = SIG_DIGITS - 1;  // 15 significant digits before rounding
    if (round_digit >= 5) {
        int i = nd - 1;
        while (i >= 0) {
            digits[i]++;
            if (digits[i] < 10) break;
            digits[i] = 0;
            i--;
        }
        if (i < 0) {
            // carry over: sig becomes 10.0 → 1.0 * 10^(k+1)
            digits[0] = 1;
            k++;
        }
    }

    // Trim trailing zeros
    while (nd > 1 && digits[nd - 1] == 0) nd--;

    // Decide format: scientific if k >= 15 or k < -4 (like %g)
    if (k >= 15 || k < -4) {
        // Scientific notation: d.dddde[+-]xx
        fbuf[flen++] = (Byte)('0' + digits[0]);
        if (nd > 1) {
            fbuf[flen++] = (Byte)'.';
            for (int i = 1; i < nd && flen < FBUF_MAX - 10; i++)
                fbuf[flen++] = (Byte)('0' + digits[i]);
        }
        fbuf[flen++] = (Byte)'e';
        int exp_val = k;
        if (exp_val >= 0) {
            fbuf[flen++] = (Byte)'+';
        } else {
            fbuf[flen++] = (Byte)'-';
            exp_val = -exp_val;
        }
        // Convert exponent (at most 3 digits for double, 308 max)
        Byte expDigits[4];
        int ed = 0;
        do {
            expDigits[ed++] = (Byte)('0' + (exp_val % 10));
            exp_val /= 10;
        } while (exp_val > 0);
        for (int i = ed - 1; i >= 0; i--) fbuf[flen++] = expDigits[i];
    } else {
        // Fixed notation
        int intDigits = (k >= 0) ? k + 1 : 1;
        for (int i = 0; i < intDigits && i < nd; i++)
            fbuf[flen++] = (Byte)('0' + digits[i]);
        // Pad with '0' if k >= nd
        for (int i = nd; i <= k; i++)
            fbuf[flen++] = (Byte)'0';
        // Fractional part
        if (k < 0) {
            fbuf[flen++] = (Byte)'0';
            fbuf[flen++] = (Byte)'.';
            for (int i = 0; i < -k - 1; i++)
                fbuf[flen++] = (Byte)'0';
            for (int i = 0; i < nd && flen < FBUF_MAX - 1; i++)
                fbuf[flen++] = (Byte)('0' + digits[i]);
        } else if (k + 1 < nd) {
            fbuf[flen++] = (Byte)'.';
            for (int i = k + 1; i < nd && flen < FBUF_MAX - 1; i++)
                fbuf[flen++] = (Byte)('0' + digits[i]);
        }
    }

    #undef SIG_DIGITS
    #undef FBUF_MAX

    Int64 to_copy = flen < buf.$length ? flen : buf.$length;
    for (Int64 i = 0; i < to_copy; i++) buf.$values[i] = fbuf[i];
    return flen;
}
