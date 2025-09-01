package soia

interface KeyedList<T, K> : List<T> {
    val indexing: Map<K, T>
}

fun <T, K> toKeyedList(
    elements: Iterable<T>,
    getKey: (T) -> K,
): KeyedList<T, K> {
    return soia.internal.toKeyedList(elements, "", getKey) { it }
}
