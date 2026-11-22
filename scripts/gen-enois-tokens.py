import re, sys

def oklch_to_rgb(l, c, hdeg):
    import math
    h = hdeg * math.pi / 180.0
    a = c * math.cos(h); b = c * math.sin(h)
    l_ = l + 0.3963377774*a + 0.2158037573*b
    m_ = l - 0.1055613458*a - 0.0638541728*b
    s_ = l - 0.0894841775*a - 1.2914855480*b
    l3, m3, s3 = l_**3, m_**3, s_**3
    rl =  4.0767416621*l3 - 3.3077115913*m3 + 0.2309699292*s3
    gl = -1.2684380046*l3 + 2.6097574011*m3 - 0.3413193965*s3
    bl = -0.0041960863*l3 - 0.7034186147*m3 + 1.7076147010*s3
    def enc(v):
        v = max(0.0, min(1.0, v))
        e = 12.92*v if v <= 0.0031308 else 1.055*(v**(1/2.4)) - 0.055
        return round(e*255)
    return enc(rl), enc(gl), enc(bl)

src = open(sys.argv[1]).read()
block = re.search(r':root\s*\{(.*?)\n\}', src, re.S).group(1)
vars = {}
for line in block.splitlines():
    m = re.match(r'\s*(--[a-z0-9-]+):\s*([^;]+);', line)
    if m: vars[m.group(1)] = re.sub(r'\s+', ' ', m.group(2).strip())

def resolve(name, depth=0):
    v = vars.get(name)
    if v is None or depth > 4: return None
    m = re.match(r'var\((--[a-z0-9-]+)\)', v)
    return resolve(m.group(1), depth+1) if m else v

def parse(v):
    if v is None: return None
    if v.startswith('#'):
        h = v[1:]
        if len(h) == 3: h = ''.join(ch*2 for ch in h)
        return (int(h[0:2],16), int(h[2:4],16), int(h[4:6],16))
    m = re.match(r'oklch\(\s*([0-9.]+%?)\s+([0-9.]+)\s+([0-9.]+)', v)
    if m:
        L = m.group(1)
        L = float(L[:-1])/100 if L.endswith('%') else float(L)
        return oklch_to_rgb(L, float(m.group(2)), float(m.group(3)))
    return None

colors, radii = {}, {}
for name in vars:
    v = resolve(name)
    rgb = parse(v or '')
    if rgb: colors[name] = rgb
for name in vars:
    if name.startswith('--radius'):
        radii[name] = vars[name]

print(f"// gerado de enois-design-system/src/styles.css ({len(colors)} cores, {len(radii)} raios)")
print("package enois")
print()
print("// Énois design tokens — GERADOS de `enois-design-system/src/styles.css`")
print("// (`:root`, o tema claro do site em enois.ia.br). Os OKLCH são convertidos")
print("// pelo algoritmo do CSS Color 4 em tempo de GERACAO (Java `Math.cos`),")
print("// entao o Kof guarda o RGB final — sem trig no runtime nativo.")
print()
print("record ThemeColor(Int r, Int g, Int b)")
print()
print("class Tokens {")
for name, (r,g,b) in sorted(colors.items()):
    ident = name[2:].replace('-', '_')
    caps = ''.join(p[:1].upper()+p[1:] for p in ident.split('_'))
    print(f"    static ThemeColor {caps}() {{ return ThemeColor({r}, {g}, {b}) }}   // {name}: {vars[name]}")
for name, v in sorted(radii.items()):
    ident = name[2:].replace('-', '_')
    caps = ''.join(p[:1].upper()+p[1:] for p in ident.split('_'))
    m = re.match(r'([0-9.]+)rem', v)
    if m:
        px = round(float(m.group(1))*16)
        print(f"    static Int {caps}Px() {{ return {px} }}   // {name}: {v}")
print("}")
