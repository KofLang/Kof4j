/*
 * kof_text_shim.c — a ponte minima Kof <-> FreeType para o texto do Énois.
 *
 * Por que existe (known-bugs §1191): o `FT_New_Face` direto pelo `extern` do
 * Kof segfalta quando o out-pointer opaco atravessa como `Buffer(U8)`. Este
 * shim recebe os handles por VALOR (`uint64_t`) e devolve inteiros simples —
 * a forma que o FFI do Kof comprovadamente entrega.
 *
 * Compilacao (o host nao tem `freetype2-devel`; os headers oficiais vem do
 * fonte e sao DADOS, nao instalacao de sistema):
 *
 *   curl -sL https://download.savannah.gnu.org/releases/freetype/freetype-2.13.3.tar.xz | tar xJ
 *   gcc -shared -fPIC -O2 -I freetype-2.13.3/include \
 *       -o libkof_text.so kof_text_shim.c -L/usr/lib64 -l:libfreetype.so.6
 *
 * API (toda em valores, nunca estruturas do FreeType):
 *   kt_open(path, px)          -> handle (0 = falha)
 *   kt_glyph(face, codepoint)  -> 0 ok; o bitmap fica em kt_bitmap()
 *   kt_bitmap()                -> o bitmap 8-bit (alfa 0..255)
 *   kt_width()/kt_height()     -> dimensoes do glifo em px
 *   kt_left()/kt_top()         -> o deslocamento do glifo a partir da origem
 *   kt_advance()/kt_ascent()/kt_descent() -> metricas verticais em px
 *   kt_rgba(out, cap)          -> expande o bitmap para RGBA branco
 *   kt_close(face)
 */
#include <stdint.h>
#include <stdlib.h>
#include <ft2build.h>
#include FT_FREETYPE_H

static FT_Library g_lib;
static int g_ready;
static int g_w, g_h, g_left, g_top, g_advance, g_ascent, g_descent;
static unsigned char *g_alpha;
static int g_cap;

int kt_init(void) {
    if (!g_ready) {
        if (FT_Init_FreeType(&g_lib) != 0) return 0;
        g_ready = 1;
    }
    return 1;
}

unsigned long kt_open(const char *path, int px) {
    if (!kt_init()) return 0;
    FT_Face face = 0;
    if (FT_New_Face(g_lib, path, 0, &face) != 0) return 0;
    if (FT_Set_Pixel_Sizes(face, 0, (unsigned) px) != 0) {
        FT_Done_Face(face);
        return 0;
    }
    return (unsigned long) (uintptr_t) face;
}

void kt_close(unsigned long h) { if (h) FT_Done_Face((FT_Face) (uintptr_t) h); }

int kt_glyph(unsigned long h, unsigned long codepoint) {
    FT_Face face = (FT_Face) (uintptr_t) h;
    if (!face) return -1;
    if (FT_Load_Char(face, codepoint, FT_LOAD_RENDER) != 0) return -2;
    FT_GlyphSlot slot = face->glyph;
    g_w = (int) slot->bitmap.width;
    g_h = (int) slot->bitmap.rows;
    g_left = slot->bitmap_left;
    g_top = slot->bitmap_top;
    g_advance = (int) (slot->advance.x >> 6);
    g_ascent = (int) (face->size->metrics.ascender >> 6);
    g_descent = (int) (-(face->size->metrics.descender) >> 6);
    int need = g_w * g_h;
    if (need > g_cap) {
        unsigned char *nb = (unsigned char *) realloc(g_alpha, (size_t) need);
        if (!nb) return -3;
        g_alpha = nb;
        g_cap = need;
    }
    for (int y = 0; y < g_h; y++)
        for (int x = 0; x < g_w; x++)
            g_alpha[y * g_w + x] = slot->bitmap.buffer[y * slot->bitmap.pitch + x];
    return 0;
}

int kt_width(void) { return g_w; }
int kt_height(void) { return g_h; }
int kt_left(void) { return g_left; }
int kt_top(void) { return g_top; }
int kt_advance(void) { return g_advance; }
int kt_ascent(void) { return g_ascent; }
int kt_descent(void) { return g_descent; }
unsigned char *kt_bitmap(void) { return g_alpha; }

/* A cor do texto e' um PARAMETRO: o glifo carrega so' o ALFA (a cobertura);
 * pintar sempre branco fazia o texto desaparecer sobre fundo claro (medido
 * 22/11: `tinta=0` no card branco com o texto branco). */
int kt_rgba(unsigned char *out, int cap, int r, int g, int bl) {
    int need = g_w * g_h * 4;
    if (!out || cap < need) return -1;
    for (int i = 0; i < g_w * g_h; i++) {
        out[i * 4 + 0] = (unsigned char) r;
        out[i * 4 + 1] = (unsigned char) g;
        out[i * 4 + 2] = (unsigned char) bl;
        out[i * 4 + 3] = g_alpha ? g_alpha[i] : 0;
    }
    return need;
}
