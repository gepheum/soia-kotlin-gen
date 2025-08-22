package soia

interface IndexedList<T, K>: List<T> {
  val indexing: Map<K, T>;
}

fun <T, K> toIndexedList(
  elements: Iterable<T>,
  getKey: (T) -> K
): IndexedList<T, K> {
  return soia.internal.toIndexedList(elements, "", getKey) { it }
}
