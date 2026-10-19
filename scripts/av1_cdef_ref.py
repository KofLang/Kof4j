#!/usr/bin/env python3
"""CDEF reference for the AVIF front's chroma investigation.

Transcribed stage by stage from the FETCHED libaom sources (never from
memory): `av1/common/cdef.c` (the driver: packed strengths, damping) and
`av1/common/cdef_block.c` / `cdef.h` (`cdef_find_dir_c`, `constrain`,
`cdef_filter_block_internal`, `av1_cdef_filter_fb`). The CDEF_VERY_LARGE
border, the primary/secondary tap pairs, the direction table and the
`(8 + sum - (sum < 0)) >> 4` rounding are libaom's own.

STATUS (18/10, 130th pass): VALIDATED. Kof's PRE-CDEF decode of
`avif_real_cfl32x24.avif` (the planes below, from the probe's YROW/UROW/VROW
lines) filtered with the frame's own parameters reproduces ffmpeg's ENTIRE
post-CDEF V plane exactly. The parameters (captured by `AvifFrameHeader`):
cdef_damping_minus_3 = 2 (damping 5), one configuration, luma packed 0
(primary 0, secondary 0), chroma packed 56 (primary 14, secondary 0). The
luma is therefore COPIED (which is why it matched ffmpeg before), and the
chroma is filtered with the co-located luma direction (vertical, dir 6) and
damping 4 (`damping += coeff_shift - (plane != Y)`).

The V plane's +/-1 was the MISSING FILTER, not a decode error: libaom and
libdav1d agree byte for byte on this file, so the entropy, dequantization,
inverse transform, CFL prediction and add are all exonerated end to end.

Self-checks (all asserted below):
  1. the luma direction search returns vertical (dir 6) on every 8x8 block;
  2. the luma is copied unchanged (strength 0);
  3. the U plane (constant down its rows) is unchanged by the filter;
  4. the V plane matches ffmpeg's 12x16 samples exactly.

Usage: python3 scripts/av1_cdef_ref.py
"""

# Kof's reconstructed planes, before CDEF (probe YROW/UROW/VROW of the 130th
# pass). The luma is the exact libaom pre-CDEF reconstruction.
Y = [
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
    [3, 8, 15, 24, 33, 40, 47, 55, 64, 73, 82, 90, 97, 104, 111, 119, 128, 136, 144, 150, 158, 166, 174, 184, 192, 200, 207, 215, 223, 232, 240, 244],
]

U = [
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
]
V = [
    [129, 129, 129, 129, 129, 129, 129, 129, 129, 129, 129, 129, 129, 129, 129, 129],
    [130, 130, 130, 130, 130, 130, 130, 130, 130, 130, 130, 130, 130, 130, 130, 130],
    [132, 132, 132, 132, 132, 132, 132, 132, 132, 132, 132, 132, 132, 132, 132, 132],
    [135, 135, 135, 135, 135, 135, 135, 135, 135, 135, 135, 135, 135, 135, 135, 135],
    [139, 139, 139, 139, 139, 139, 139, 139, 139, 139, 139, 139, 139, 139, 139, 139],
    [143, 143, 143, 143, 143, 143, 143, 143, 143, 143, 143, 143, 143, 143, 143, 143],
    [147, 147, 147, 147, 147, 147, 147, 147, 147, 147, 147, 147, 147, 147, 147, 147],
    [151, 151, 151, 151, 151, 151, 151, 151, 151, 151, 151, 151, 151, 151, 151, 151],
    [154, 154, 154, 154, 154, 154, 154, 154, 154, 154, 154, 154, 154, 154, 154, 154],
    [157, 157, 157, 157, 157, 157, 157, 157, 157, 157, 157, 157, 157, 157, 157, 157],
    [159, 159, 159, 159, 159, 159, 159, 159, 159, 159, 159, 159, 159, 159, 159, 159],
    [160, 160, 160, 160, 160, 160, 160, 160, 160, 160, 160, 160, 160, 160, 160, 160],
]

# ffmpeg's decoded planes (libdav1d == libaom byte for byte), AFTER CDEF.
REFU = [
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
    [129, 131, 133, 135, 137, 139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159],
]
REFV = [
    [130, 130, 130, 130, 130, 130, 130, 130, 130, 130, 130, 130, 130, 130, 130, 130],
    [131, 131, 131, 131, 131, 131, 131, 131, 131, 131, 131, 131, 131, 131, 131, 131],
    [133, 133, 133, 133, 133, 133, 133, 133, 133, 133, 133, 133, 133, 133, 133, 133],
    [136, 136, 136, 136, 136, 136, 136, 136, 136, 136, 136, 136, 136, 136, 136, 136],
    [139, 139, 139, 139, 139, 139, 139, 139, 139, 139, 139, 139, 139, 139, 139, 139],
    [143, 143, 143, 143, 143, 143, 143, 143, 143, 143, 143, 143, 143, 143, 143, 143],
    [147, 147, 147, 147, 147, 147, 147, 147, 147, 147, 147, 147, 147, 147, 147, 147],
    [150, 150, 150, 150, 150, 150, 150, 150, 150, 150, 150, 150, 150, 150, 150, 150],
    [154, 154, 154, 154, 154, 154, 154, 154, 154, 154, 154, 154, 154, 154, 154, 154],
    [156, 156, 156, 156, 156, 156, 156, 156, 156, 156, 156, 156, 156, 156, 156, 156],
    [158, 158, 158, 158, 158, 158, 158, 158, 158, 158, 158, 158, 158, 158, 158, 158],
    [159, 159, 159, 159, 159, 159, 159, 159, 159, 159, 159, 159, 159, 159, 159, 159],
]

CDEF_VERY_LARGE = 0x4000
DIV_TABLE = [0, 840, 420, 280, 210, 168, 140, 120, 105]
# cdef_directions[dir][k] as (dy, dx); the +/-2 entries are the padded table.
DIR = [
    [(-1, 1), (-2, 2)], [(0, 1), (-1, 2)],
    [(0, 1), (0, 2)], [(0, 1), (1, 2)],
    [(1, 1), (2, 2)], [(1, 0), (2, 1)],
    [(1, 0), (2, 0)], [(1, 0), (2, -1)],
]


def get_msb(x):
    r = -1
    while x > 0:
        x >>= 1
        r += 1
    return r


def constrain(diff, threshold, damping):
    if not threshold:
        return 0
    shift = max(0, damping - get_msb(threshold))
    v = max(0, min(threshold - (abs(diff) >> shift), abs(diff)))
    return v if diff >= 0 else -v


def find_dir(img):
    """cdef_find_dir_c: the directionality score per line direction."""
    partial = [[0] * 15 for _ in range(8)]
    for i in range(8):
        for j in range(8):
            x = img[i][j] - 128  # coeff_shift = 0 for 8-bit
            partial[0][i + j] += x
            partial[1][i + j // 2] += x
            partial[2][i] += x
            partial[3][3 + i - j // 2] += x
            partial[4][7 + i - j] += x
            partial[5][3 - i // 2 + j] += x
            partial[6][j] += x
            partial[7][i // 2 + j] += x
    cost = [0] * 8
    for i in range(8):
        cost[2] += partial[2][i] ** 2
        cost[6] += partial[6][i] ** 2
    cost[2] *= DIV_TABLE[8]
    cost[6] *= DIV_TABLE[8]
    for i in range(7):
        cost[0] += (partial[0][i] ** 2 + partial[0][14 - i] ** 2) * DIV_TABLE[i + 1]
        cost[4] += (partial[4][i] ** 2 + partial[4][14 - i] ** 2) * DIV_TABLE[i + 1]
    cost[0] += partial[0][7] ** 2 * DIV_TABLE[8]
    cost[4] += partial[4][7] ** 2 * DIV_TABLE[8]
    for i in (1, 3, 5, 7):
        for j in range(5):
            cost[i] += partial[i][3 + j] ** 2
        cost[i] *= DIV_TABLE[8]
        for j in range(3):
            cost[i] += (partial[i][j] ** 2 + partial[i][10 - j] ** 2) * DIV_TABLE[2 * j + 2]
    best_cost, best_dir = 0, 0
    for i in range(8):
        if cost[i] > best_cost:
            best_cost, best_dir = cost[i], i
    return best_dir, (best_cost - cost[(best_dir + 4) & 7]) >> 10


def adjust_strength(strength, var):
    i = min(get_msb(var >> 6), 12) if var >> 6 else 0
    return (strength * (4 + i) + 8) >> 4 if var else 0


def filter_block(src, dst, bw, bh, bx, by, size, pri, sec, direction,
                 damping, pri_en, sec_en):
    """cdef_filter_block_internal: one 8x8 (luma) or 4x4 (4:2:0 chroma) block.

    `src` is the UNFILTERED plane for every block (libaom filters a copy into
    `dst`); reading the partially filtered plane shifts block edges.
    """
    pri_taps = (4, 2) if pri & 1 == 0 else (3, 3)
    sec_taps = (2, 1)
    clipping = pri_en and sec_en

    def get(x, y):
        if x < 0 or y < 0 or x >= bw or y >= bh:
            return CDEF_VERY_LARGE
        return src[y][x]

    for i in range(size):
        for j in range(size):
            x = get(bx + j, by + i)
            s, lo, hi = 0, x, x
            for k in range(2):
                if pri_en:
                    dy, dx = DIR[direction][k]
                    p0, p1 = get(bx + j + dx, by + i + dy), get(bx + j - dx, by + i - dy)
                    s += pri_taps[k] * (constrain(p0 - x, pri, damping) +
                                        constrain(p1 - x, pri, damping))
                    if clipping:
                        if p0 != CDEF_VERY_LARGE:
                            hi = max(hi, p0)
                        if p1 != CDEF_VERY_LARGE:
                            hi = max(hi, p1)
                        lo = min(lo, p0, p1)
                if sec_en:
                    for dd in (2, -2):
                        dy, dx = DIR[(direction + dd) & 7][k]
                        q0, q1 = get(bx + j + dx, by + i + dy), get(bx + j - dx, by + i - dy)
                        s += sec_taps[k] * (constrain(q0 - x, sec, damping) +
                                            constrain(q1 - x, sec, damping))
                        if clipping:
                            if q0 != CDEF_VERY_LARGE:
                                hi = max(hi, q0)
                            if q1 != CDEF_VERY_LARGE:
                                hi = max(hi, q1)
                            lo = min(lo, q0, q1)
            y = x + ((8 + s - (1 if s < 0 else 0)) >> 4)
            if clipping:
                y = max(lo, min(hi, y))
            dst[by + i][bx + j] = y


def cdef_plane(src, size, pri_strength, sec_strength, damping, directions=None):
    """av1_cdef_filter_fb for one plane (8-bit, coeff_shift 0).

    `directions` is None for the luma (the search runs here and is returned) or
    the luma's 8x8 directions for chroma.
    """
    bh, bw = len(src), len(src[0])
    dst = [row[:] for row in src]
    sec = sec_strength + (1 if sec_strength == 3 else 0)
    nby, nbx = (bh + size - 1) // size, (bw + size - 1) // size
    dirs, vars_ = {}, {}
    if directions is None:
        for by in range((bh + 7) // 8):
            for bx in range((bw + 7) // 8):
                img = [[src[min(by * 8 + i, bh - 1)][min(bx * 8 + j, bw - 1)]
                        for j in range(8)] for i in range(8)]
                dirs[(by, bx)] = find_dir(img)
    for by in range(nby):
        for bx in range(nbx):
            if directions is not None:
                direction = directions[(by, bx)][0]
                t = pri_strength
                damp = damping - 1  # damping += coeff_shift - (plane != Y)
            else:
                direction, var = dirs[(by, bx)]
                t = adjust_strength(pri_strength, var)
                damp = damping
            sec_en = sec != 0
            pri_en = t != 0
            if not pri_en and not sec_en:
                continue  # cdef_filter_*_3 copies the input
            filter_block(src, dst, bw, bh, bx * size, by * size, size, t, sec,
                         direction if pri_strength else 0, damp, pri_en, sec_en)
    return dst, dirs


def main():
    # 1. the direction search on the luma is VERTICAL (6) everywhere.
    Yf, dirs_y = cdef_plane(Y, 8, 0, 0, 5)
    found = {d for d, _ in dirs_y.values()}
    assert found == {6}, found

    # 2. the luma (strength 0) is COPIED unchanged.
    assert Yf == Y

    # 3. the chroma filter is primary-only (sec 0) with the luma direction;
    #    the U plane is constant down its rows, so the vertical taps are no-ops.
    Uf, _ = cdef_plane(U, 4, 14, 0, 5, dirs_y)
    assert Uf == U, "the U plane moved"

    # 4. the V plane matches ffmpeg's post-CDEF plane exactly.
    Vf, _ = cdef_plane(V, 4, 14, 0, 5, dirs_y)
    assert Vf == REFV, next((y, x, Vf[y][x], REFV[y][x])
                            for y in range(len(V)) for x in range(len(V[0]))
                            if Vf[y][x] != REFV[y][x])
    print("CDEF ok: luma copied, U unchanged, V == ffmpeg (%dx%d)"
          % (len(V), len(V[0])))


if __name__ == "__main__":
    main()
