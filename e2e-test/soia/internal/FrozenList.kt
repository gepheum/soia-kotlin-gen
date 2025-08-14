package soia.internal

import java.util.Collections

@Suppress("UNCHECKED_CAST")
fun <M, T> toFrozenList(
  elements: Iterable<M>,
  toFrozen: (M) -> T,
): List<T> {
  return if (elements is FrozenList)
    elements as List<T>
  else
    FrozenList(elements.map(toFrozen)).orEmptySingleton
}

fun <T> toFrozenList(
  elements: Iterable<T>,
): List<T> {
  return if (elements is FrozenList) {
    elements
  } else {
    FrozenList(elements.toList()).orEmptySingleton;
  }
}

@Suppress("UNCHECKED_CAST")
fun <T> frozenListOf(): List<T> = FrozenList.EMPTY as List<T>;

private class FrozenList<T>(
  val list: List<T>
) : List<T> by list {
    companion object {
        val EMPTY = FrozenList<Any>(emptyList());
    }
    override fun iterator(): Iterator<T> = Collections.unmodifiableList(list).iterator()
    override fun listIterator(): ListIterator<T> = Collections.unmodifiableList(list).listIterator()
    override fun listIterator(index: Int): ListIterator<T> = Collections.unmodifiableList(list).listIterator(index)
    override fun subList(fromIndex: Int, toIndex: Int): List<T> {
        return FrozenList(list.subList(fromIndex, toIndex))
    }
    @Suppress("UNCHECKED_CAST")
    val orEmptySingleton: FrozenList<T>
      get() = if (this.isEmpty()) EMPTY as FrozenList<T> else this
}
