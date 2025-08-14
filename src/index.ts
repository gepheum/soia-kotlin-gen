// TODO: field names
// TODO: equals, hashCode, toString
// TODO: name conflicts:
//   - careful about 'soia' or 'kotlin' or 'soiagen' as param name...
// TODO: mutableGetter
// TODO: serializers...
import { getClassName } from "./class_speller.js";
import { TypeSpeller } from "./type_speller.js";
import {
  type CodeGenerator,
  type Constant,
  type Method,
  type Module,
  type RecordKey,
  type RecordLocation,
  type ResolvedType,
  convertCase,
} from "soiac";
import { z } from "zod";

const Config = z.object({});

type Config = z.infer<typeof Config>;

class KotlinCodeGenerator implements CodeGenerator<Config> {
  readonly id = "kotlin";
  readonly configType = Config;
  readonly version = "1.0.0";

  generateCode(input: CodeGenerator.Input<Config>): CodeGenerator.Output {
    const { recordMap, config } = input;
    const outputFiles: CodeGenerator.OutputFile[] = [];
    for (const module of input.modules) {
      outputFiles.push({
        path: module.path.replace(/\.soia$/, ".kt"),
        code: new KotlinSourceFileGenerator(
          module,
          recordMap,
          config,
        ).generate(),
      });
    }
    return { files: outputFiles };
  }
}

// Generates the code for one Kotlin file.
class KotlinSourceFileGenerator {
  constructor(
    private readonly inModule: Module,
    recordMap: ReadonlyMap<RecordKey, RecordLocation>,
    private readonly config: Config,
  ) {
    this.typeSpeller = new TypeSpeller(recordMap);
  }

  generate(): string {
    // http://patorjk.com/software/taag/#f=Doom&t=Do%20not%20edit
    this.push(`
      //  ______                        _               _  _  _
      //  |  _  \\                      | |             | |(_)| |
      //  | | | |  ___    _ __    ___  | |_    ___   __| | _ | |_
      //  | | | | / _ \\  | '_ \\  / _ \\ | __|  / _ \\ / _\` || || __|
      //  | |/ / | (_) | | | | || (_) || |_  |  __/| (_| || || |_ 
      //  |___/   \\___/  |_| |_| \\___/  \\__|  \\___| \\__,_||_| \\__|
      //

      `);

    this.push("package soiagen.");
    this.push(this.inModule.path.replace(/\.soia$/, "").replace("/", "."));
    this.push(";\n\n");
    this.push(
      "import soia.internal.MustNameArguments as _MustNameArguments;\n\n",
    );

    this.writeClassesForRecords(
      this.inModule.records.filter(
        // Only retain top-level records.
        // Nested records will be processed from within their ancestors.
        (r: RecordLocation) => r.recordAncestors.length === 1,
      ),
    );

    for (const method of this.inModule.methods) {
      this.writeMethod(method);
    }

    for (const constant of this.inModule.constants) {
      this.writeConstant(constant);
    }

    return this.joinLinesAndFixFormatting();
  }

  private writeClassesForRecords(
    recordLocations: readonly RecordLocation[],
  ): void {
    const { recordMap } = this.typeSpeller;
    for (const record of recordLocations) {
      const { recordType } = record.record;
      this.pushEol();
      if (recordType === "struct") {
        this.writeClassesForStruct(record);
      } else {
        this.writeClassForEnum(record);
      }
    }
  }

  private writeClassesForStruct(struct: RecordLocation): void {
    const { typeSpeller } = this;
    const { recordMap } = typeSpeller;
    const { fields } = struct.record;
    const className = getClassName(struct);
    const { qualifiedName } = className;
    this.push(`sealed interface ${className.name}_OrMutable {\n`);
    this.push(`  fun toFrozen(): ${qualifiedName};\n`);
    this.push("}\n\n");
    this.push('@kotlin.Suppress("UNUSED_PARAMETER")\n');
    this.push(`class ${className.name}_Mutable(\n`);
    this.push(
      "_mustNameArguments: _MustNameArguments =\n_MustNameArguments,\n",
    );
    for (const field of fields) {
      const allRecordsFrozen = !!field.isRecursive;
      const type = typeSpeller.getKotlinType(
        field.type!,
        "maybe-mutable",
        allRecordsFrozen,
      );
      const defaultExpr = this.getDefaultExpression(field.type!);
      this.push(`  var ${field.name.text}: ${type} =\n${defaultExpr},\n`);
    }
    this.push(`): ${qualifiedName}_OrMutable {\n`);
    this.push(`override fun toFrozen() = ${className.name}(\n`);
    for (const field of fields) {
      this.push(`${field.name.text} = this.${field.name.text},\n`);
    }
    this.push(`);\n\n`);
    this.push(`fun toMutable() = ${qualifiedName}_Mutable(\n`);
    for (const field of fields) {
      this.push(`    ${field.name.text} = this.${field.name.text},\n`);
    }
    this.push(`);\n`);
    this.push("}\n\n");
    this.push('@kotlin.Suppress("UNUSED_PARAMETER")\n');
    this.push(`class ${className.name} private constructor(\n`);
    for (const field of fields) {
      const type = typeSpeller.getKotlinType(field.type!, "frozen");
      if (field.isRecursive === "hard") {
        this.push(`private val _${field.name.text}: ${type}?,\n`);
      } else {
        this.push(`val ${field.name.text}: ${type},\n`);
      }
    }
    this.push(`): ${qualifiedName}_OrMutable {\n`);
    for (const field of fields) {
      if (field.isRecursive === "hard") {
        const name = field.name.text;
        const defaultExpr = this.getDefaultExpression(field.type!);
        this.push(`val ${name} get() = _${name} ?: ${defaultExpr},\n`);
      }
    }
    this.pushEol();
    this.push("constructor(\n");
    this.push(
      "_mustNameArguments: _MustNameArguments =\n_MustNameArguments,\n",
    );
    for (const field of fields) {
      const type = typeSpeller.getKotlinType(field.type!, "initializer");
      this.push(`${field.name.text}: ${type},\n`);
    }
    this.push("): this(\n");
    for (const field of fields) {
      this.push(this.toFrozenExpression(field.name.text, field.type!));
      this.push(",\n");
    }
    this.push(") {}\n\n");

    this.push('@kotlin.Deprecated("Already frozen")\n');
    this.push("override fun toFrozen() = this;\n\n");

    if (fields.length) {
      this.push("fun copy(\n");
      this.push(
        "_mustNameArguments: _MustNameArguments = _MustNameArguments,\n",
      );
      for (const field of fields) {
        const type = typeSpeller.getKotlinType(field.type!, "initializer");
        this.push(
          `  ${field.name.text}: ${type} =\nthis.${field.name.text},\n`,
        );
      }
      this.push(`) = ${qualifiedName}(\n`);
      for (const field of fields) {
        this.push(this.toFrozenExpression(field.name.text, field.type!));
        this.push(",\n");
      }
      this.push(");\n\n");

      this.push(
        '@kotlin.Deprecated("No point in creating an exact copy of an immutable object")\n',
      );
      this.push("fun copy() = copy(_MustNameArguments)\n\n");
    }

    this.push("companion object {\n");
    this.push("val DEFAULT =\n");
    this.push(`${qualifiedName}(\n`);
    for (const field of fields) {
      this.push(
        field.isRecursive === "hard"
          ? "null"
          : this.getDefaultExpression(field.type!),
      );
      this.push(",\n");
    }
    this.push(");\n\n");
    this.push("fun partial(\n");
    this.push(
      "_mustNameArguments: _MustNameArguments =\n_MustNameArguments,\n",
    );
    for (const field of fields) {
      const type = typeSpeller.getKotlinType(field.type!, "initializer");
      const defaultExpr = this.getDefaultExpression(field.type!);
      this.push(`${field.name.text}: ${type} =\n${defaultExpr},\n`);
    }
    this.push(`) = ${qualifiedName}(\n`);
    for (const field of fields) {
      this.push(`${field.name.text} = ${field.name.text},\n`);
    }
    this.push(");\n\n");

    this.push("fun mutable(\n");
    this.push(
      "_mustNameArguments: _MustNameArguments =\n_MustNameArguments,\n",
    );
    for (const field of fields) {
      const allRecordsFrozen = !!field.isRecursive;
      const type = typeSpeller.getKotlinType(
        field.type!,
        "maybe-mutable",
        allRecordsFrozen,
      );
      const defaultExpr = this.getDefaultExpression(field.type!);
      this.push(`${field.name.text}: ${type} =\n${defaultExpr},\n`);
    }
    this.push(`) = ${qualifiedName}_Mutable(\n`);
    for (const field of fields) {
      this.push(`${field.name.text} = ${field.name.text},\n`);
    }
    this.push(");\n\n");
    this.push("}\n");

    // Write the classes for the records nested in `record`.
    const nestedRecords = struct.record.nestedRecords.map(
      (r) => recordMap.get(r.key)!,
    );
    this.writeClassesForRecords(nestedRecords);

    this.push("}\n\n");
  }

  private writeClassForEnum(record: RecordLocation): void {
    const { typeSpeller } = this;
    const { recordMap } = typeSpeller;
    const { fields } = record.record;
    const constantFields = fields.filter((f) => !f.type);
    const valueFields = fields.filter((f) => f.type);
    const className = getClassName(record);
    // const { qualifiedName } = className;
    this.push(`sealed class ${className.name} {\n`);
    this.push(`object UNKNOWN : ${className.name}() {}\n`);
    for (const constField of constantFields) {
      this.push(`object ${constField.name.text} : ${className.name}() {}\n`);
    }
    for (const valueField of valueFields) {
      const wrapClassName =
        "wrap" +
        convertCase(valueField.name.text, "lower_underscore", "UpperCamel");
      const initializerType = typeSpeller
        .getKotlinType(valueField.type!, "initializer")
        .toString();
      const frozenType = typeSpeller
        .getKotlinType(valueField.type!, "initializer")
        .toString();
      this.pushEol();
      if (initializerType === frozenType) {
        this.push(`class ${wrapClassName}(\n`);
        this.push(`val value: ${initializerType}\n`);
        this.push(`) : ${className.name}() {}\n`);
      } else {
        this.push(`class ${wrapClassName} private constructor (`);
        this.push(`val value: ${initializerType}\n`);
        this.push(`) : ${className.name}() {\n`);
        this.push(`constructor(value: ${initializerType}): this(value) {}\n`);
        this.push("}\n");
      }
    }

    // Write the classes for the records nested in `record`.
    const nestedRecords = record.record.nestedRecords.map(
      (r) => recordMap.get(r.key)!,
    );
    this.writeClassesForRecords(nestedRecords);
    this.push("}\n\n");
    // this.pushLine("@typing.final");
    // this.pushLine(`class ${className.name}:`);
    // this.pushLine(`UNKNOWN: typing.Final["${qualifiedName}"] = _`);
    // for (const constantField of constantFields) {
    //   const attribute = enumValueFieldToAttr(constantField.name.text);
    //   this.pushLine(`${attribute}: typing.Final["${qualifiedName}"] = _`);
    // }
    // for (const valueField of valueFields) {
    //   const name = valueField.name.text;
    //   const type = valueField.type!;
    //   const kotlinType = typeSpeller.getKotlinType(type, "initializer");
    //   this.pushLine();
    //   this.pushLine("@staticmethod");
    //   this.pushLine(
    //     `def wrap_${name}(value: ${pyType}) -> "${qualifiedName}": ...`,
    //   );
    //   if (type.kind === "record") {
    //     const { record } = typeSpeller.recordMap.get(type.key)!;
    //     if (record.recordType === "struct") {
    //       this.pushLine();
    //       this.pushLine("@staticmethod");
    //       this.pushLine(`def create_${name}(`);
    //       this.writeStructFieldsAsParams(record, "initializer", "no-default");
    //       this.pushLine(`) -> "${qualifiedName}": ...`);
    //     }
    //   }
    // }
    // this.pushLine();
    // this.pushLine("def __init__(self, _: typing.NoReturn): ...");
    // if (fields.length === 0) {
    //   return;
    // }
    // this.pushLine();
    // {
    //   const kindTypeArgs = ['"?"']
    //     .concat(fields.map((f) => `"${f.name.text}"`))
    //     .join(", ");
    //   this.pushLine(`Kind: typing.TypeAlias = typing.Literal[${kindTypeArgs}]`);
    // }
    // {
    //   const kindType = PyType.quote(`${qualifiedName}.Kind`);
    //   this.pushLine();
    //   this.pushLine("@property");
    //   this.pushLine(`def kind(self) -> ${kindType}: ...`);
    // }
    // {
    //   const typesInUnion: PyType[] = valueFields.map((f) =>
    //     typeSpeller.getKotlinType(f.type!, "frozen"),
    //   );
    //   typesInUnion.push(PyType.NONE);
    //   const valueType = PyType.union(typesInUnion);
    //   this.pushLine();
    //   this.pushLine("@property");
    //   this.pushLine(`def value(self) -> ${valueType}: ...`);
    // }
    // {
    //   const getVariantType = (name: string) =>
    //     PyType.quote(`${qualifiedName}._${name}`);
    //   const typesInUnion = [getVariantType("Unknown")].concat(
    //     fields.map((f) => getVariantType(f.name.text)),
    //   );
    //   this.pushLine();
    //   this.pushLine("@property");
    //   this.pushLine(`def union(self) -> ${PyType.union(typesInUnion)}: ...`);
    // }
    // this.writeVariantClass("Unknown", PyType.NONE, "?");
    // for (const field of fields) {
    //   const fieldName = field.name.text;
    //   const valueType = field.type
    //     ? typeSpeller.getKotlinType(field.type, "frozen")
    //     : PyType.NONE;
    //   this.writeVariantClass(fieldName, valueType);
    // }
    // this.pushLine();
    // this.pushLine(
    //   `SERIALIZER: typing.Final[soia.Serializer["${qualifiedName}"]] = _`,
    // );
  }

  private writeMethod(method: Method): void {
    // const { typeSpeller } = this;
    // const methodName = method.name.text;
    // const varName = PY_UPPER_CAMEL_KEYWORDS.has(methodName)
    //   ? `${methodName}_`
    //   : methodName;
    // const requestType = typeSpeller.getKotlinType(method.requestType!, "frozen");
    // const responseType = typeSpeller.getKotlinType(method.responseType!, "frozen");
    // const methodType = `soia.Method[${requestType}, ${responseType}]`;
    // this.pushLine();
    // this.pushLine(`${varName}: typing.Final[${methodType}] = _`);
  }

  private writeConstant(constant: Constant): void {
    // const { typeSpeller } = this;
    // const name = constant.name.text;
    // const type = typeSpeller.getKotlinType(constant.type!, "frozen");
    // this.pushLine();
    // this.pushLine(`${name}: typing.Final[${type}] = _`);
  }

  // private typeToSpec(type: ResolvedType): string {
  // switch (type.kind) {
  //   case "array": {
  //     const itemSpec = this.typeToSpec(type.item);
  //     let keyArg = "";
  //     if (type.key) {
  //       const attributes = type.key.path
  //         .map((n) => `"${structFieldToAttr(n.name.text)}", `)
  //         .join("")
  //         .trimEnd();
  //       keyArg = `, (${attributes})`;
  //     }
  //     return `_spec.ArrayType(${itemSpec}${keyArg})`;
  //   }
  //   case "optional": {
  //     const otherSpec = this.typeToSpec(type.other);
  //     return `_spec.OptionalType(${otherSpec})`;
  //   }
  //   case "primitive":
  //     return `_spec.PrimitiveType.${type.primitive.toUpperCase()}`;
  //   case "record": {
  //     const record = this.typeSpeller.recordMap.get(type.key)!;
  //     const { recordAncestors, modulePath } = record;
  //     const recordQualname = recordAncestors
  //       .map((r) => r.name.text)
  //       .join(".");
  //     return `"${modulePath}:${recordQualname}"`;
  //   }
  // }
  // }

  private getDefaultExpression(type: ResolvedType): string {
    switch (type.kind) {
      case "primitive": {
        switch (type.primitive) {
          case "bool":
            return "false";
          case "int32":
          case "int64":
          case "uint64":
            return "0";
          case "float32":
            return "0.0f";
          case "float64":
            return "0.0";
          case "timestamp":
            return "java.time.Instant.EPOCH";
          case "string":
            return '""';
          case "bytes":
            return "kotlin.byteArrayOf()";
        }
        break;
      }
      case "array": {
        return "soia.internal.frozenListOf()";
      }
      case "optional": {
        return "null";
      }
      case "record": {
        const record = this.typeSpeller.recordMap.get(type.key)!;
        const kotlinType = this.typeSpeller.getKotlinType(type, "frozen");
        switch (record.record.recordType) {
          case "struct": {
            return `${kotlinType}.DEFAULT`;
          }
          case "enum": {
            return `${kotlinType}.UNKNOWN`;
          }
        }
        break;
      }
    }
  }

  private toFrozenExpression(inputExpr: string, type: ResolvedType): string {
    switch (type.kind) {
      case "primitive": {
        return inputExpr;
      }
      case "array": {
        const itemToFrozenExpr = this.toFrozenExpression("it", type.item);
        if (itemToFrozenExpr === "it") {
          return `soia.internal.toFrozenList(${inputExpr})`;
        } else {
          return `soia.internal.toFrozenList(${inputExpr}) { ${itemToFrozenExpr} }`;
        }
      }
      case "optional": {
        const otherExpr = this.toFrozenExpression(inputExpr, type.other);
        if (otherExpr === inputExpr) {
          return otherExpr;
        } else {
          return `if (${inputExpr} != null) ${otherExpr} else null`;
        }
      }
      case "record": {
        const record = this.typeSpeller.recordMap.get(type.key)!;
        if (record.record.recordType === "struct") {
          return `${inputExpr}.toFrozen()`;
        } else {
          return inputExpr;
        }
      }
    }
  }

  private push(code: string): void {
    this.code += code.trimStart();
  }

  private pushEol(): void {
    this.code += "\n";
  }

  private joinLinesAndFixFormatting(): string {
    const indentUnit = "  ";
    let result = "";
    // The indent at every line is obtained by repeating indentUnit N times,
    // where N is the length of this array.
    const contextStack: Array<"{" | "(" | "[" | "<" | ":" | "."> = [];
    // Returns the last element in `contextStack`.
    const peakTop = () => contextStack.at(-1)!;
    const getMatchingLeftBracket = (r: "}" | ")" | "]" | ">") => {
      switch (r) {
        case "}":
          return "{";
        case ")":
          return "(";
        case "]":
          return "[";
        case ">":
          return "<";
      }
    };
    for (let line of this.code.split("\n")) {
      line = line.trim();
      if (line.length <= 0) {
        // Don't indent empty lines.
        result += "\n";
        continue;
      }

      const firstChar = line[0];
      switch (firstChar) {
        case "}":
        case ")":
        case "]":
        case ">": {
          const left = getMatchingLeftBracket(firstChar);
          while (contextStack.pop() !== left) {
            if (contextStack.length <= 0) {
              throw Error();
            }
          }
          break;
        }
        case ".": {
          if (peakTop() !== ".") {
            contextStack.push(".");
          }
          break;
        }
      }
      const indent = indentUnit.repeat(contextStack.length);
      result += `${indent}${line.trimEnd()}\n`;
      if (line.startsWith("//")) {
        continue;
      }
      const lastChar = line.slice(-1);
      switch (lastChar) {
        case "{":
        case "(":
        case "[":
        case "<": {
          // The next line will be indented
          contextStack.push(lastChar);
          break;
        }
        case ":":
        case "=": {
          if (peakTop() !== ":") {
            contextStack.push(":");
          }
          break;
        }
        case ";":
        case ",": {
          if (peakTop() === "." || peakTop() === ":") {
            contextStack.pop();
          }
        }
      }
    }

    return (
      result
        // Remove spaces enclosed within curly brackets if that's all there is.
        .replace(/\{\s+\}/g, "{}")
        // Remove spaces enclosed within round brackets if that's all there is.
        .replace(/\(\s+\)/g, "()")
        // Remove spaces enclosed within square brackets if that's all there is.
        .replace(/\[\s+\]/g, "[]")
        // Remove empty line following an open curly bracket.
        .replace(/(\{\n *)\n/g, "$1")
        // Remove empty line preceding a closed curly bracket.
        .replace(/\n(\n *\})/g, "$1")
        // Coalesce consecutive empty lines.
        .replace(/\n\n\n+/g, "\n\n")
        .replace(/\n\n$/g, "\n")
    );
  }

  private readonly typeSpeller: TypeSpeller;
  private code = "";
}

export const GENERATOR = new KotlinCodeGenerator();
