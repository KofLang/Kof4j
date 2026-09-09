package dev.kof.compiler;

/**
 * Array multidimensional: {@code new T[d1][d2]...[dn]} — pilha recebe os n
 * tamanhos (int), empurra o array n-dimensional. {@code baseType} é o tipo
 * do ELEMENTO final (ex.: Int em {@code new Int[a][b]}); {@code dims} é o
 * número de dimensões criadas (≥2). Backends que só cobrem 1 dim NÃO devem
 * cair em KofNewArray (R6: sem fallback silencioso).
 */
public record KofNewMultiArray(Type baseType, int dims) implements KofOperation {
}
