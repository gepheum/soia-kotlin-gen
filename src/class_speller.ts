import type { Module } from "soiac";
import { RecordLocation } from "soiac";

export interface ClassName {
  /** The name right after the 'class' keyword.. */
  name: string;
  /**
   * Fully qualified class name relative to a given module.
   * Examples: 'Foo', 'Foo.Bar', 'other.module.Foo.Bar'.
   */
  qualifiedName: string;
}

/** Returns the name of the frozen Python class for the given record. */
export function getClassName(record: RecordLocation): ClassName {
  const { recordAncestors } = record;
  const parts: string[] = [];
  for (let i = 0; i < recordAncestors.length; ++i) {
    const record = recordAncestors[i]!;
    const name = record.name.text;
    parts.push(name);
  }

  const name = parts.at(-1)!;

  const path = record.modulePath;
  const importPath = path.replace(/\.soia$/, "").replace("/", ".");
  const qualifiedName = `soiagen.${importPath}.${parts.join(".")}`;

  return { name, qualifiedName };
}
