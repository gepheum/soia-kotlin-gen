import { Field, convertCase } from "soiac";

export function toLowerCamelName(field: Field | string): string {
  const inputName = typeof field === "string" ? field : field.name.text;
  return KOTLIN_HARD_KEYWORDS.has(inputName) ||
    TOP_LEVEL_PACKAGE_NAMES.has(inputName)
    ? inputName + "_"
    : convertCase(inputName, "lower_underscore", "lowerCamel");
}

const KOTLIN_HARD_KEYWORDS: ReadonlySet<string> = new Set([
  "abstract",
  "annotation",
  "as",
  "break",
  "catch",
  "class",
  "const",
  "continue",
  "crossinline",
  "data",
  "else",
  "enum",
  "external",
  "final",
  "finally",
  "for",
  "fun",
  "if",
  "import",
  "in",
  "inline",
  "interface",
  "internal",
  "is",
  "lateinit",
  "noinline",
  "object",
  "open",
  "operator",
  "override",
  "package",
  "private",
  "protected",
  "public",
  "reified",
  "return",
  "sealed",
  "super",
  "suspend",
  "this",
  "throw",
  "try",
  "typealias",
  "val",
  "var",
  "when",
  "while",
]);

const TOP_LEVEL_PACKAGE_NAMES: ReadonlySet<string> = new Set<string>([
  "java",
  "kotlin",
  "okio",
  "soiagen",
]);

export function toEnumConstantName(field: Field): string {
  return field.name.text === "SERIALIZER" ? "SERIALIZER_" : field.name.text;
}
