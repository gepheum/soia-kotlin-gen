import { ClassName, getClassName } from "./class_speller.js";
import type { Module, RecordKey, RecordLocation, ResolvedType } from "soiac";

export type TypeFlavor =
  | "initializer"
  | "frozen"
  | "maybe-mutable"
  | "mutable"
  | "kind";

/**
 * Transforms a type found in a `.soia` file into a Kotlin type.
 *
 * The flavors are:
 *   · initializer
 *       The value can be passed by parameter to the `create` method of a frozen
 *       class or the constructor of a mutable class.
 *   · frozen:
 *       The type is deeply immutable. All the fields of a frozen class are also
 *       frozen.
 *   · maybe-mutable:
 *       Type union of the frozen type and the mutable type. All the fields of a
 *       mutable class are maybe-mutable.
 *   · mutable:
 *       A mutable value. Not all types found in `.soia` files support this, e.g.
 *       strings and numbers are always immutable.
 */
export class TypeSpeller {
  constructor(readonly recordMap: ReadonlyMap<RecordKey, RecordLocation>) {}

  getKotlinType(
    type: ResolvedType,
    flavor: "initializer" | "frozen" | "mutable",
    allRecordsFrozen?: undefined,
  ): string;

  getKotlinType(
    type: ResolvedType,
    flavor: TypeFlavor,
    // Only matters if mode is "maybe-mutable"
    allRecordsFrozen: boolean | undefined,
  ): string;

  getKotlinType(
    type: ResolvedType,
    flavor: TypeFlavor,
    // Only matters if mode is "maybe-mutable"
    allRecordsFrozen: boolean | undefined,
  ): string {
    switch (type.kind) {
      case "record": {
        const recordLocation = this.recordMap.get(type.key)!;
        const record = recordLocation.record;
        const className = getClassName(recordLocation).qualifiedName;
        if (record.recordType === "struct") {
          if (flavor === "frozen" || allRecordsFrozen) {
            return className;
          } else if (flavor === "maybe-mutable" || flavor === "initializer") {
            return allRecordsFrozen ? className : `${className}_OrMutable`;
          } else if (flavor === "mutable") {
            return `${className}_Mutable`;
          } else {
            const _: "kind" = flavor;
            throw TypeError();
          }
        }
        // An enum.
        const _: "enum" = record.recordType;
        if (
          flavor === "initializer" ||
          flavor === "frozen" ||
          flavor === "maybe-mutable" ||
          flavor === "mutable"
        ) {
          return className;
        } else if (flavor === "kind") {
          return `${className}_Kind`;
        } else {
          const _: never = flavor;
          throw TypeError();
        }
      }
      case "array": {
        if (flavor === "initializer") {
          const itemType = this.getKotlinType(
            type.item,
            "maybe-mutable",
            allRecordsFrozen,
          );
          return `kotlin.collections.Iterable<${itemType}>`;
        } else if (flavor === "frozen") {
          const itemType = this.getKotlinType(
            type.item,
            "frozen",
            allRecordsFrozen,
          );
          if (type.key) {
            const { keyType } = type.key;
            let kotlinKeyType = this.getKotlinType(keyType, "frozen");
            if (keyType.kind === "record") {
              kotlinKeyType += "_Kind";
            }
            return `soia.IndexedList<${itemType}, ${kotlinKeyType}>`;
          } else {
            return `kotlin.collections.List<${itemType}>`;
          }
        } else if (flavor === "maybe-mutable") {
          const itemType = this.getKotlinType(
            type.item,
            "maybe-mutable",
            allRecordsFrozen,
          );
          return `kotlin.collections.List<${itemType}>`;
        } else if (flavor === "mutable") {
          const itemType = this.getKotlinType(
            type.item,
            "maybe-mutable",
            allRecordsFrozen,
          );
          return `kotlin.collections.MutableList<${itemType}>`;
        } else {
          const _: "kind" = flavor;
          throw TypeError();
        }
      }
      case "optional": {
        const otherType = this.getKotlinType(
          type.other,
          flavor,
          allRecordsFrozen,
        );
        return `${otherType}?`;
      }
      case "primitive": {
        const { primitive } = type;
        switch (primitive) {
          case "bool":
            return "Boolean";
          case "int32":
            return "Int";
          case "int64":
            return "Long";
          case "uint64":
            return "ULong";
          case "float32":
            return "Float";
          case "float64":
            return "Double";
          case "timestamp":
            return "java.time.Instant";
          case "string":
            return "String";
          case "bytes":
            return "kotlin.ByteArray";
        }
      }
    }
  }

  getClassName(recordKey: RecordKey): ClassName {
    const record = this.recordMap.get(recordKey)!;
    return getClassName(record);
  }
}
