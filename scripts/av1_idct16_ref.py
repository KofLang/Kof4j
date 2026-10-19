#!/usr/bin/env python3
"""Reference 2D inverse transform for the AVIF front's chroma investigation.

Transcribed stage by stage from the FETCHED libaom source (never from memory):
`av1_idct16` (av1/common/av1_inv_txfm1d.c, seven stages), the 12-bit cospi
table (av1/common/av1_txfm.c) and the 2D driver's pass order and shifts
(av1/common/av1_inv_txfm2d.c: `inv_txfm2d_add_c`, whose "rows" pass gathers
`input[c * txfm_size_row + r]` — the COLUMN r of the raster — then shifts by
`-shift[0]`, and whose "cols" pass shifts by `-shift[1]`; for a 16x16 TX
`inv_shift_16x16 = {-2, -4}`).

STATUS (18/10, 123rd pass): VALIDATED, and it EXONERATES the decoder's inverse
transform. The 122nd pass's version disagreed with Kof (4/4/5/5/38) and was
recorded as unresolved; the validation it named — a DC-ONLY input must give a
CONSTANT output — failed on it (0 and 16), which no linear transform can produce,
and the comparison with the fetched source showed why: STAGE 6 had been
transcribed as a pass-through where libaom combines the halves (`bf1[0] =
bf0[0] + bf0[7]`, ...). With that fixed, a DC-only input gives a single flat
value, and the V transform block of `avif_real_cfl32x24.avif` (input: DC 2676 at
raster 0, -1525 at 16, -305 at 32) now reproduces Kof's measured residual
EXACTLY: (0,0)=1 (0,1)=1 (1,0)=2 (1,1)=2 (15,15)=34. Kof's 16x16 inverse
transform is therefore CORRECT, and the V plane's +/-1 lies in the prediction
(the CFL alpha/DC) or the add, not here.

Usage: python3 scripts/av1_idct16_ref.py
"""
C = None

def hb(w0, x, w1, y):
    return ((w0 * x + w1 * y) + (1 << 11)) >> 12


def idct16(inp, cospi):
    p = [0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 11, 7, 15]
    s = [inp[i] for i in p]
    o = [0] * 16
    for i in range(8):
        o[i] = s[i]
    o[8] = hb(cospi[60], s[8], -cospi[4], s[15])
    o[9] = hb(cospi[28], s[9], -cospi[36], s[14])
    o[10] = hb(cospi[44], s[10], -cospi[20], s[13])
    o[11] = hb(cospi[12], s[11], -cospi[52], s[12])
    o[12] = hb(cospi[52], s[11], cospi[12], s[12])
    o[13] = hb(cospi[20], s[10], cospi[44], s[13])
    o[14] = hb(cospi[36], s[9], cospi[28], s[14])
    o[15] = hb(cospi[4], s[8], cospi[60], s[15])
    s = o[:]; o = [0] * 16
    for i in range(4):
        o[i] = s[i]
    o[4] = hb(cospi[56], s[4], -cospi[8], s[7])
    o[5] = hb(cospi[24], s[5], -cospi[40], s[6])
    o[6] = hb(cospi[40], s[5], cospi[24], s[6])
    o[7] = hb(cospi[8], s[4], cospi[56], s[7])
    o[8] = s[8] + s[9]; o[9] = s[8] - s[9]
    o[10] = -s[10] + s[11]; o[11] = s[10] + s[11]
    o[12] = s[12] + s[13]; o[13] = s[12] - s[13]
    o[14] = -s[14] + s[15]; o[15] = s[14] + s[15]
    s = o[:]; o = [0] * 16
    o[0] = hb(cospi[32], s[0], cospi[32], s[1])
    o[1] = hb(cospi[32], s[0], -cospi[32], s[1])
    o[2] = hb(cospi[48], s[2], -cospi[16], s[3])
    o[3] = hb(cospi[16], s[2], cospi[48], s[3])
    o[4] = s[4] + s[5]; o[5] = s[4] - s[5]
    o[6] = -s[6] + s[7]; o[7] = s[6] + s[7]
    o[8] = s[8]; o[11] = s[11]; o[12] = s[12]; o[15] = s[15]
    o[9] = hb(-cospi[16], s[9], cospi[48], s[14])
    o[10] = hb(-cospi[48], s[10], -cospi[16], s[13])
    o[13] = hb(-cospi[16], s[10], cospi[48], s[13])
    o[14] = hb(cospi[48], s[9], cospi[16], s[14])
    s = o[:]; o = [0] * 16
    o[0] = s[0] + s[3]; o[1] = s[1] + s[2]; o[2] = s[1] - s[2]; o[3] = s[0] - s[3]
    o[4] = s[4]; o[7] = s[7]
    o[5] = hb(-cospi[32], s[5], cospi[32], s[6])
    o[6] = hb(cospi[32], s[5], cospi[32], s[6])
    o[8] = s[8] + s[11]; o[9] = s[9] + s[10]
    o[10] = s[9] - s[10]; o[11] = s[8] - s[11]
    o[12] = -s[12] + s[15]; o[13] = -s[13] + s[14]
    o[14] = s[13] + s[14]; o[15] = s[12] + s[15]
    s = o[:]; o = [0] * 16
    # stage 6 combines the halves (libaom: bf1[0] = bf0[0] + bf0[7], ...)
    o[0] = s[0] + s[7]; o[1] = s[1] + s[6]
    o[2] = s[2] + s[5]; o[3] = s[3] + s[4]
    o[4] = s[3] - s[4]; o[5] = s[2] - s[5]
    o[6] = s[1] - s[6]; o[7] = s[0] - s[7]
    o[8] = s[8]; o[9] = s[9]; o[14] = s[14]; o[15] = s[15]
    o[10] = hb(-cospi[32], s[10], cospi[32], s[13])
    o[11] = hb(-cospi[32], s[11], cospi[32], s[12])
    o[12] = hb(cospi[32], s[11], cospi[32], s[12])
    o[13] = hb(cospi[32], s[10], cospi[32], s[13])
    s = o[:]; o = [0] * 16
    for i in range(8):
        o[i] = s[i] + s[15 - i]
        o[15 - i] = s[i] - s[15 - i]
    return o


def rs(x, n):
    return (x + (1 << (n - 1))) >> n


def inv2d(inp, cospi):
    buf = [[0] * 16 for _ in range(16)]
    for r in range(16):
        vec = [inp[c][r] for c in range(16)]
        buf[r] = [rs(v, 2) for v in idct16(vec, cospi)]
    res = [[0] * 16 for _ in range(16)]
    for c in range(16):
        vec = [buf[r][c] for r in range(16)]
        res[c] = [rs(v, 4) for v in idct16(vec, cospi)]
    return res


if __name__ == "__main__":
    # the 12-bit cospi row of av1/common/av1_txfm.c
    cospi = [4096, 4095, 4091, 4085, 4076, 4065, 4052, 4036, 4017, 3996, 3973,
             3948, 3920, 3889, 3857, 3822, 3784, 3745, 3703, 3659, 3612, 3564,
             3513, 3461, 3406, 3349, 3290, 3229, 3166, 3102, 3035, 2967, 2896,
             2824, 2751, 2675, 2598, 2520, 2440, 2359, 2276, 2191, 2106, 2019,
             1931, 1842, 1751, 1660, 1567, 1474, 1380, 1285, 1189, 1092, 995,
             897, 799, 700, 601, 501, 401, 301, 201, 101]
    inp = [[0] * 16 for _ in range(16)]
    inp[0][0] = 2676
    inp[1][0] = -1525
    inp[2][0] = -305
    # SELF-CHECK 1: a DC-only input must give a CONSTANT output (linearity).
    dc = [[0] * 16 for _ in range(16)]
    dc[0][0] = 2007          # the U's DC-only residual: level 9 x dc_q 223
    flat = sorted(set(v for row in inv2d(dc, cospi) for v in row))
    print("DC-only 2007 -> flat values:", flat)
    assert len(flat) == 1, "DC-only input is not constant: the transcription is wrong"
    # SELF-CHECK 2: the V transform block against the decoder's measured residual.
    res = inv2d(inp, cospi)
    got = (res[0][0], res[0][1], res[1][0], res[1][1], res[15][15])
    print("reference residual: (0,0)=%d (0,1)=%d (1,0)=%d (1,1)=%d (15,15)=%d" % got)
    print("Kof VRES:           (0,0)=1 (0,1)=1 (1,0)=2 (1,1)=2 (15,15)=34")
    assert got == (1, 1, 2, 2, 34), "the reference no longer matches the decoder"
