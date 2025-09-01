// TODO: field names
//   - wrappers around the fields
// TODO: equals, hashCode, toString
// TODO: name conflicts:
//   - careful about 'soia' or 'kotlin' or 'soiagen' as param name...
//   - class name conflict if a nested record in an enum is named Unknown or Wrap...
// TODO: serializers...
// TODO: possibility to specify package prefix after soiagen
// TODO: make this.push expect possibly a vararg of strings
// TODO: use emoji prefix instead of underscore for internal symbols?
import { getClassName } from "./class_speller.js";
import { TypeSpeller } from "./type_speller.js";
import {
  type CodeGenerator,
  type Constant,
  Field,
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
      "import soia.internal.MustNameArguments as _MustNameArguments;\n",
    );
    this.push(
      "import soia.internal.UnrecognizedFields as _UnrecognizedFields;\n\n",
    );
    this.push(
      "import soia.internal.UnrecognizedEnum as _UnrecognizedEnum;\n\n",
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
    for (const record of recordLocations) {
      const { recordType } = record.record;
      this.pushEol();
      if (recordType === "struct") {
        this.writeClassesForStruct(record);
      } else {
        this.writeClassesForEnum(record);
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
    this.push(`fun toFrozen(): ${qualifiedName};\n`);
    this.push("}\n\n");
    this.push('@kotlin.Suppress("UNUSED_PARAMETER")\n');
    this.push(`class ${className.name}_Mutable internal constructor(\n`);
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
      this.push(`var ${field.name.text}: ${type} =\n${defaultExpr},\n`);
    }
    this.push(
      `internal var _unrecognizedFields: _UnrecognizedFields<${qualifiedName}>? =\n`,
    );
    this.push("null,\n");
    this.push(`): ${qualifiedName}_OrMutable {\n`);
    this.push(`override fun toFrozen() = ${className.name}(\n`);
    for (const field of fields) {
      this.push(`${field.name.text} = this.${field.name.text},\n`);
    }
    this.push("_unrecognizedFields = this._unrecognizedFields,\n");
    this.push(`);\n\n`);
    this.writeMutableGetters(fields);
    this.push("}\n\n");
    this.push('@kotlin.Suppress("UNUSED_PARAMETER")\n');
    this.push(`class ${className.name} private constructor(\n`);
    for (const field of fields) {
      const type = typeSpeller.getKotlinType(field.type!, "frozen");
      if (field.isRecursive === "hard") {
        this.push(`private val __${field.name.text}: ${type}?,\n`);
      } else {
        this.push(`val ${field.name.text}: ${type},\n`);
      }
    }
    this.push(
      `private val _unrecognizedFields: _UnrecognizedFields<${qualifiedName}>? =\n`,
    );
    this.push("null,\n");
    this.push(`): ${qualifiedName}_OrMutable {\n`);
    for (const field of fields) {
      if (field.isRecursive === "hard") {
        const name = field.name.text;
        const defaultExpr = this.getDefaultExpression(field.type!);
        this.push(`val ${name} get() = __${name} ?: ${defaultExpr};\n`);
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
    this.push(
      `_unrecognizedFields: _UnrecognizedFields<${qualifiedName}>? =\n`,
    );
    this.push("null,\n");
    this.push("): this(\n");
    for (const field of fields) {
      this.push(this.toFrozenExpression(field.name.text, field.type!));
      this.push(",\n");
    }
    this.push("_unrecognizedFields,\n");
    this.push(") {}\n\n");

    this.push('@kotlin.Deprecated("Already frozen")\n');
    this.push("override fun toFrozen() = this;\n\n");

    this.push(`fun toMutable() = ${qualifiedName}_Mutable(\n`);
    for (const field of fields) {
      this.push(`${field.name.text} = this.${field.name.text},\n`);
    }
    this.push(`);\n\n`);

    if (fields.length) {
      this.push("fun copy(\n");
      this.push(
        "_mustNameArguments: _MustNameArguments = _MustNameArguments,\n",
      );
      for (const field of fields) {
        const type = typeSpeller.getKotlinType(field.type!, "initializer");
        this.push(`${field.name.text}: ${type} =\nthis.${field.name.text},\n`);
      }
      this.push(`) = ${qualifiedName}(\n`);
      for (const field of fields) {
        this.push(this.toFrozenExpression(field.name.text, field.type!));
        this.push(",\n");
      }
      this.push("this._unrecognizedFields,\n");
      this.push(");\n\n");

      this.push(
        '@kotlin.Deprecated("No point in creating an exact copy of an immutable object")\n',
      );
      this.push("fun copy() = this;\n\n");
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
    this.push("_unrecognizedFields = null,\n");
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
    this.push("private val serializerImpl = soia.internal.StructSerializer(\n");
    this.push("defaultInstance = DEFAULT,\n");
    this.push("newMutable = { mutable() },\n");
    this.push("toFrozen = { it.toFrozen() },\n");
    this.push("getUnrecognizedFields = { it._unrecognizedFields },\n");
    this.push(
      "setUnrecognizedFields = { m, u -> m._unrecognizedFields = u },\n",
    );
    this.push(");\n\n");
    this.push("val SERIALIZER = soia.Serializer(serializerImpl);\n\n");
    this.push("init {\n");
    for (const field of fields) {
      this.push("serializerImpl.addField(\n");
      this.push(`"${field.name.text}",\n`);
      this.push(`${field.number},\n`);
      this.push(`${typeSpeller.getSerializerExpression(field.type!)},\n`);
      this.push(`{ it.${field.name.text} },\n`);
      this.push(`{ mut, v -> mut.${field.name.text} = v },\n`);
      this.push(");\n");
    }
    for (const removedNumber of struct.record.removedNumbers) {
      this.push(`serializerImpl.addRemovedNumber(${removedNumber});\n`);
    }
    this.push("serializerImpl.finalizeStruct();\n");
    this.push("}\n");
    this.push("}\n");

    // Write the classes for the records nested in `record`.
    const nestedRecords = struct.record.nestedRecords.map(
      (r) => recordMap.get(r.key)!,
    );
    this.writeClassesForRecords(nestedRecords);

    this.push("}\n\n");
  }

  private writeMutableGetters(fields: readonly Field[]): void {
    const { typeSpeller } = this;
    for (const field of fields) {
      if (field.isRecursive) {
        continue;
      }
      const type = field.type!;
      const fieldName = field.name.text;
      const mutableGetterName =
        "mutable" + convertCase(fieldName, "lower_underscore", "UpperCamel");
      const mutableType = typeSpeller.getKotlinType(field.type!, "mutable");
      const accessor = `this.${fieldName}`;
      let bodyLines: string[] = [];
      if (type.kind === "array") {
        bodyLines = [
          "return when (value) {\n",
          "is soia.internal.MutableList -> value;\n",
          "else -> {\n",
          "value = soia.internal.MutableList(value);\n",
          `${accessor} = value;\n`,
          "value;\n",
          "}\n",
          "}\n",
        ];
      } else if (type.kind === "record") {
        const record = this.typeSpeller.recordMap.get(type.key)!;
        if (record.record.recordType === "struct") {
          const structQualifiedName = getClassName(record).qualifiedName;
          bodyLines = [
            "return when (value) {\n",
            `is ${structQualifiedName} -> {\n`,
            "value = value.toMutable();\n",
            `${accessor} = value;\n`,
            "return value;\n",
            "}\n",
            `is ${structQualifiedName}_Mutable -> value;\n`,
            "}\n",
          ];
        }
      }
      if (bodyLines.length) {
        this.push(`val ${mutableGetterName}: ${mutableType} get() {\n`);
        this.push(`var value = ${accessor};\n`);
        for (const line of bodyLines) {
          this.push(line);
        }
        this.push("}\n\n");
      }
    }
  }

  private writeClassesForEnum(record: RecordLocation): void {
    const { typeSpeller } = this;
    const { recordMap } = typeSpeller;
    const { fields } = record.record;
    const constantFields = fields.filter((f) => !f.type);
    const valueFields = fields.filter((f) => f.type);
    const className = getClassName(record);
    const qualifiedName = className.qualifiedName;
    this.push(`enum class ${className.name}_Kind {\n`);
    this.push(`CONST_UNKNOWN,\n`);
    for (const field of constantFields) {
      this.push(`CONST_${field.name.text},\n`);
    }
    for (const field of valueFields) {
      this.push(
        `VAL_${convertCase(field.name.text, "lower_underscore", "UPPER_UNDERSCORE")},\n`,
      );
    }
    this.push("}\n\n");
    this.push(`sealed class ${className.name} {\n`);
    this.push("class Unknown private constructor(\n");
    this.push(
      `internal val _unrecognized: _UnrecognizedEnum<${qualifiedName}>?,\n`,
    );
    this.push(`) : ${qualifiedName}() {\n`);
    this.push(
      `override val kind get() = ${className.name}_Kind.CONST_UNKNOWN;\n\n`,
    );
    this.push("companion object {\n");
    this.push("private val UNKNOWN = Unknown(null);\n\n");
    this.push("internal fun _create(\n");
    this.push(`u: _UnrecognizedEnum<${qualifiedName}>?,\n`);
    this.push(") = if (u != null) Unknown(u) else UNKNOWN;\n");
    this.push("}\n"); // companion object
    this.push("}\n\n"); // class Unknown
    for (const constField of constantFields) {
      this.push(`object ${constField.name.text} : ${className.name}() {\n`);
      const kindExpr = `${className.name}_Kind.CONST_${constField.name.text}`;
      this.push(`override val kind get() = ${kindExpr};\n`);
      this.push(`}\n\n`);
    }
    for (const valueField of valueFields) {
      const valueType = valueField.type!;
      const wrapClassName =
        "wrap" +
        convertCase(valueField.name.text, "lower_underscore", "UpperCamel");
      const initializerType = typeSpeller
        .getKotlinType(valueType, "initializer")
        .toString();
      const frozenType = typeSpeller
        .getKotlinType(valueType, "frozen")
        .toString();
      this.pushEol();
      if (initializerType === frozenType) {
        this.push(`class ${wrapClassName}(\n`);
        this.push(`val value: ${initializerType},\n`);
        this.push(`) : ${qualifiedName}() {\n`);
      } else {
        this.push(`class ${wrapClassName} private constructor (\n`);
        this.push(`val value: ${frozenType},\n`);
        this.push(`) : ${qualifiedName}() {\n`);
        this.push("constructor(\n");
        this.push(`value: ${initializerType},`);
        this.push(
          `): this(${this.toFrozenExpression("value", valueType)}) {}\n\n`,
        );
      }
      const kindExpr = `${className.name}_Kind.VAL_${convertCase(valueField.name.text, "lower_underscore", "UPPER_UNDERSCORE")}`;
      this.push(`override val kind get() = ${kindExpr};\n`);
      this.push("}\n\n");
    }

    this.push(`abstract val kind: ${className.name}_Kind;\n\n`);

    this.push("companion object {\n");
    this.push("val UNKNOWN = Unknown._create(null);\n\n");
    for (const valueField of valueFields) {
      const type = valueField.type!;
      if (type.kind !== "record") {
        continue;
      }
      const structLocation = typeSpeller.recordMap.get(type.key)!;
      const struct = structLocation.record;
      if (struct.recordType !== "struct") {
        continue;
      }
      const structClassName = getClassName(structLocation);
      const createFunName =
        "create" +
        convertCase(valueField.name.text, "lower_underscore", "UpperCamel");
      const wrapFunName =
        "wrap" +
        convertCase(valueField.name.text, "lower_underscore", "UpperCamel");
      this.push('@kotlin.Suppress("UNUSED_PARAMETER")\n');
      this.push(`fun ${createFunName}(\n`);
      this.push(
        "_mustNameArguments: _MustNameArguments =\n_MustNameArguments,\n",
      );
      for (const field of struct.fields) {
        const type = typeSpeller.getKotlinType(field.type!, "initializer");
        this.push(`${field.name.text}: ${type},\n`);
      }
      this.push(`) = ${wrapFunName}(\n`);
      this.push(`${structClassName.qualifiedName}(\n`);
      for (const field of struct.fields) {
        this.push(`${field.name.text} = ${field.name.text},\n`);
      }
      this.push(")\n");
      this.push(");\n\n");
    }
    this.push("private val serializerImpl =\n");
    this.push(
      `soia.internal.EnumSerializer.create<${className.name}, ${className.name}.Unknown>(\n`,
    );
    this.push("UNKNOWN,\n");
    this.push(`{ ${className.name}.Unknown._create(it) },\n`);
    this.push(") { it._unrecognized };\n\n");
    this.push("val SERIALIZER = soia.Serializer(serializerImpl);\n\n");
    this.push("init {\n");
    for (const constField of constantFields) {
      this.push("serializerImpl.addConstantField(\n");
      this.push(`${constField.number},\n`);
      this.push(`"${constField.name.text}",\n`);
      this.push(`${constField.name.text},\n`);
      this.push(");\n");
    }
    for (const valueField of valueFields) {
      const serializerExpression = typeSpeller.getSerializerExpression(
        valueField.type!,
      );
      const wrapClassName =
        "wrap" +
        convertCase(valueField.name.text, "lower_underscore", "UpperCamel");
      this.push("serializerImpl.addValueField(\n");
      this.push(`${valueField.number},\n`);
      this.push(`"${valueField.name.text}",\n`);
      this.push(`${wrapClassName}::class.java,\n`);
      this.push(`${serializerExpression},\n`);
      this.push(`{ ${wrapClassName}(it) },\n`);
      this.push(") { it.value };\n");
    }
    this.push("serializerImpl.finalizeEnum();\n");
    this.push("}\n"); // init
    this.push("}\n\n"); // companion object

    // Write the classes for the records nested in `record`.
    const nestedRecords = record.record.nestedRecords.map(
      (r) => recordMap.get(r.key)!,
    );
    this.writeClassesForRecords(nestedRecords);
    this.push("}\n\n");
  }

  private writeMethod(method: Method): void {
    const { typeSpeller } = this;
    const methodName = method.name.text;
    const requestType = typeSpeller.getKotlinType(
      method.requestType!,
      "frozen",
    );
    const requestSerializerExpr = typeSpeller.getSerializerExpression(
      method.requestType!,
    );
    const responseType = typeSpeller.getKotlinType(
      method.responseType!,
      "frozen",
    );
    const responseSerializerExpr = typeSpeller.getSerializerExpression(
      method.responseType!,
    );
    this.push(
      `val ${methodName}: soia.Method<\n${requestType},\n${responseType},\n> = soia.Method(\n`,
    );
    this.push(`"${methodName}",\n`);
    this.push(`${method.number},\n`);
    this.push(requestSerializerExpr + ",\n");
    this.push(responseSerializerExpr + ",\n");
    this.push(");\n\n");
  }

  private writeConstant(constant: Constant): void {
    const { typeSpeller } = this;
    const name = constant.name.text;
    const type = typeSpeller.getKotlinType(constant.type!, "frozen");
    const serializerExpression = typeSpeller.getSerializerExpression(
      constant.type!,
    );
    const jsonStringLiteral = JSON.stringify(
      JSON.stringify(constant.valueAsDenseJson),
    );
    this.push(`val ${name}: ${type} =\n`);
    this.push(serializerExpression);
    this.push(`.fromJsonCode(${jsonStringLiteral});\n\n`);
  }

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
            return "okio.ByteString.EMPTY";
        }
      }
      case "array": {
        const itemType = this.typeSpeller.getKotlinType(type.item, "frozen");
        if (type.key) {
          const { keyType } = type.key;
          let kotlinKeyType = this.typeSpeller.getKotlinType(keyType, "frozen");
          if (keyType.kind === "record") {
            kotlinKeyType += "_Kind";
          }
          return `soia.internal.emptyKeyedList<${itemType}, ${kotlinKeyType}>()`;
        } else {
          return `soia.internal.emptyFrozenList<${itemType}>()`;
        }
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
        if (type.key) {
          const path = type.key.path.map((f) => f.name.text).join(".");
          if (itemToFrozenExpr === "it") {
            return `soia.internal.toKeyedList(${inputExpr}, "${path}", { it.${path} })`;
          } else {
            return `soia.internal.toKeyedList(${inputExpr}, "${path}", { it.${path} }, { ${itemToFrozenExpr} })`;
          }
        } else {
          if (itemToFrozenExpr === "it") {
            return `soia.internal.toFrozenList(${inputExpr})`;
          } else {
            return `soia.internal.toFrozenList(${inputExpr}, { ${itemToFrozenExpr} })`;
          }
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
    const indentUnit = "    ";
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
