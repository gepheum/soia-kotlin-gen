package soia.internal

import java.util.Collections

@Suppress("UNCHECKED_CAST")
fun <M, E> toFrozenList(
  elements: Iterable<M>,
  toFrozen: (M) -> E,
): List<E> {
  return if (elements is FrozenList)
    elements as List<E>
  else {
    val result = FrozenList(elements.map(toFrozen));
    if (result.isEmpty()) result else emptyFrozenList();
  }
}

fun <E> toFrozenList(
  elements: Iterable<E>,
): List<E> {
  return toFrozenList(elements) { it }
}

@Suppress("UNCHECKED_CAST")
fun <M, E, K> toIndexedList(
  elements: Iterable<M>,
  getKeySpec: String,
  getKey: (T) -> K,
  toFrozen: (M) -> T,
): soia.IndexedList<E, K> {
  return if (elements is IndexedListImpl<*, *> && !elements.getKeySpec.isEmpty() && elements.getKey == getKey)
    elements as soia.IndexedList<E, K>
  else {
    val result = IndexedListImpl(elements.map(toFrozen), getKeySpec, getKey);
    if (result.isEmpty()) result else emptyIndexedList();
  }
}

fun <E, K> toIndexedList(
  elements: Iterable<E>,
  getKeySpec: String,
  getKey: (T) -> K,
): soia.IndexedList<E, K> {
  return toIndexedList(elements, getKeySpec, getKey) { it };
}

@Suppress("UNCHECKED_CAST")
fun <E> emptyFrozenList(): List<E> = FrozenEmptyList as List<E>;

@Suppress("UNCHECKED_CAST")
fun <E, K> emptyIndexedList(): soia.IndexedList<E, K> = FrozenEmptyList as soia.IndexedList<E, K>;

private open class FrozenList<E>(
  val list: List<E>
) : List<E> by list {
    override fun iterator(): Iterator<E> = Collections.unmodifiableList(list).iterator()
    override fun listIterator(): ListIterator<E> = Collections.unmodifiableList(list).listIterator()
    override fun listIterator(index: Int): ListIterator<E> = Collections.unmodifiableList(list).listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): List<E> {
        return FrozenList(list.subList(fromIndex, toIndex))
    }
}

private open class IndexedListImpl<E, K>(
  list: List<E>,
  val getKeySpec: String,
  val getKey: (E) -> K,
) : FrozenList<E>(list), soia.IndexedList<E, K> {
  override val indexing: Map<K, E> by lazy {
    list.associateBy(getKey)
  }
}

private object FrozenEmptyList: FrozenList<Any>(emptyList()), soia.IndexedList<Any, Any> {
  override val indexing: Map<Any, Any>
    get() = emptyMap()
}
