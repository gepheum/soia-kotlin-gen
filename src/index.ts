// TODO: add comments

import {
  type CodeGenerator,
  type Constant,
  Doc,
  type Field,
  type Method,
  type Module,
  type RecordKey,
  type RecordLocation,
  type ResolvedType,
  convertCase,
  simpleHash,
} from "skir-internal";
import { z } from "zod";
import { Namer, toEnumConstantName } from "./naming.js";
import { TypeSpeller } from "./type_speller.js";

const Config = z.object({
  packagePrefix: z
    .string()
    .regex(/^([a-z_$][a-z0-9_$]*\.)*$/)
    .optional(),
});

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
        path: module.path.replace(/\.skir$/, ".kt"),
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
    config: Config,
  ) {
    this.packagePrefix = config.packagePrefix ?? "";
    this.namer = new Namer(this.packagePrefix);
    this.typeSpeller = new TypeSpeller(recordMap, this.namer);
  }

  generate(): string {
    // http://patorjk.com/software/taag/#f=Doom&t=Do%20not%20edit
    this.push(
      `@file:Suppress("ktlint")

      //  ______                        _               _  _  _
      //  |  _  \\                      | |             | |(_)| |
      //  | | | |  ___    _ __    ___  | |_    ___   __| | _ | |_
      //  | | | | / _ \\  | '_ \\  / _ \\ | __|  / _ \\ / _\` || || __|
      //  | |/ / | (_) | | | | || (_) || |_  |  __/| (_| || || |_ 
      //  |___/   \\___/  |_| |_| \\___/  \\__|  \\___| \\__,_||_| \\__|
      //

      // To install the Soia client library, add:
      //   implementation("build.skir:skir-client:latest.release")
      // to your build.gradle.kts file

      `,
      `package ${this.packagePrefix}skirout.`,
      this.inModule.path.replace(/\.skir$/, "").replace("/", "."),
      ";\n\n",
      "import build.skir.internal.MustNameArguments as _MustNameArguments;\n",
      "import build.skir.internal.UnrecognizedFields as _UnrecognizedFields;\n",
      "import build.skir.internal.UnrecognizedVariant as _UnrecognizedVariant;\n\n",
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
        this.writeClassForEnum(record);
      }
    }
  }

  private writeClassesForStruct(struct: RecordLocation): void {
    const { namer, typeSpeller } = this;
    const { recordMap } = typeSpeller;
    const { fields } = struct.record;
    const className = namer.getClassName(struct);
    const { qualifiedName } = className;
    this.push(`sealed interface ${className.name}_OrMutable {\n`);
    for (const field of fields) {
      const fieldName = namer.structFieldToKotlinName(field);
      const allRecordsFrozen = field.isRecursive === "hard";
      const type = typeSpeller.getKotlinType(
        field.type!,
        "maybe-mutable",
        allRecordsFrozen,
      );
      this.push(commentify(docToCommentText(field.doc)));
      this.push(`val ${fieldName}: ${type};\n`);
    }
    this.push(`\nfun toFrozen(): ${qualifiedName};\n`);
    this.push(
      "}\n\n", // class _OrMutable
      commentify([docToCommentText(struct.record.doc), "\nDeeply immutable."]),
      '@kotlin.Suppress("UNUSED_PARAMETER")\n',
      `class ${className.name} private constructor(\n`,
    );
    for (const field of fields) {
      const fieldName = namer.structFieldToKotlinName(field);
      const type = typeSpeller.getKotlinType(field.type!, "frozen");
      if (field.isRecursive === "hard") {
        this.push(`private val __${fieldName}: ${type}?,\n`);
      } else {
        this.push(`override val ${fieldName}: ${type},\n`);
      }
    }
    this.push(
      `private val _unrecognizedFields: _UnrecognizedFields<${qualifiedName}>? =\n`,
      "null,\n",
      `): ${qualifiedName}_OrMutable {\n`,
    );
    for (const field of fields) {
      if (field.isRecursive === "hard") {
        const fieldName = namer.structFieldToKotlinName(field);
        const defaultExpr = this.getDefaultExpression(field.type!);
        this.push(
          `override val ${fieldName} get() = __${fieldName} ?: ${defaultExpr};\n`,
        );
      }
    }
    this.pushEol();
    this.push(
      "constructor(\n",
      "_mustNameArguments: _MustNameArguments =\n_MustNameArguments,\n",
    );
    for (const field of fields) {
      const fieldName = namer.structFieldToKotlinName(field);
      const type = typeSpeller.getKotlinType(field.type!, "initializer");
      this.push(`${fieldName}: ${type},\n`);
    }
    this.push(
      `_unrecognizedFields: _UnrecognizedFields<${qualifiedName}>? =\n`,
      "null,\n",
      "): this(\n",
    );
    for (const field of fields) {
      const fieldName = namer.structFieldToKotlinName(field);
      this.push(this.toFrozenExpression(fieldName, field.type!), ",\n");
    }
    this.push(
      "_unrecognizedFields,\n",
      ") {}\n\n",
      '@kotlin.Deprecated("Already frozen", kotlin.ReplaceWith("this"))\n',
      "override fun toFrozen() = this;\n\n",
      "/** Returns a mutable shallow copy of this instance */\n",
      `fun toMutable() = Mutable(\n`,
    );
    for (const field of fields) {
      const fieldName = namer.structFieldToKotlinName(field);
      this.push(`${fieldName} = this.${fieldName},\n`);
    }
    this.push(");\n\n");

    if (fields.length) {
      this.push(
        "/** Returns a shallow copy of this instance with the specified fields replaced. */\n",
        "fun copy(\n",
        "_mustNameArguments: _MustNameArguments =\n_MustNameArguments,\n",
      );
      for (const field of fields) {
        const fieldName = namer.structFieldToKotlinName(field);
        const type = typeSpeller.getKotlinType(field.type!, "initializer");
        this.push(`${fieldName}: ${type} =\nthis.${fieldName},\n`);
      }
      this.push(`) = ${qualifiedName}(\n`);
      for (const field of fields) {
        const fieldName = namer.structFieldToKotlinName(field);
        this.push(this.toFrozenExpression(fieldName, field.type!), ",\n");
      }
      this.push(
        "this._unrecognizedFields,\n",
        ");\n\n",
        '@kotlin.Deprecated("No point in creating an exact copy of an immutable object", kotlin.ReplaceWith("this"))\n',
        "fun copy() = this;\n\n",
      );
    }
    this.push(
      "override fun equals(other: kotlin.Any?): kotlin.Boolean {\n",
      `return this === other || (other is ${qualifiedName}`,
      fields
        .map(
          (f) =>
            ` && this.${namer.structFieldToKotlinName(f)} == other.${namer.structFieldToKotlinName(f)}`,
        )
        .join(""),
      ");\n",
      "}\n\n",
      "override fun hashCode(): kotlin.Int {\n",
      "return kotlin.collections.listOf<kotlin.Any?>(",
      fields.map((f) => `this.${namer.structFieldToKotlinName(f)}`).join(", "),
      ").hashCode();\n",
      "}\n\n",
      "override fun toString(): kotlin.String {\n",
      "return build.skir.internal.toStringImpl(\n",
      "this,\n",
      `${qualifiedName}.serializerImpl,\n`,
      ")\n",
      "}\n\n",
    );
    this.push(
      `/** Mutable version of [${className.name}]. */\n`,
      `class Mutable internal constructor(\n`,
      "_mustNameArguments: _MustNameArguments =\n_MustNameArguments,\n",
    );
    for (const field of fields) {
      const fieldName = namer.structFieldToKotlinName(field);
      const allRecordsFrozen = !!field.isRecursive;
      const type = typeSpeller.getKotlinType(
        field.type!,
        "maybe-mutable",
        allRecordsFrozen,
      );
      const defaultExpr = this.getDefaultExpression(field.type!);
      this.push(`override var ${fieldName}: ${type} =\n${defaultExpr},\n`);
    }
    this.push(
      `internal var _unrecognizedFields: _UnrecognizedFields<${qualifiedName}>? =\n`,
      "null,\n",
      `): ${qualifiedName}_OrMutable {\n`,
      "/** Returns a deeply immutable copy of this instance */\n",
      `override fun toFrozen() = ${qualifiedName}(\n`,
    );
    for (const field of fields) {
      const fieldName = namer.structFieldToKotlinName(field);
      this.push(`${fieldName} = this.${fieldName},\n`);
    }
    this.push(
      "_unrecognizedFields = this._unrecognizedFields,\n", //
      `);\n\n`,
    );
    this.writeMutableGetters(fields);
    this.push(
      "}\n\n",
      "companion object {\n",
      "private val default =\n",
      `${qualifiedName}(\n`,
    );
    for (const field of fields) {
      this.push(
        field.isRecursive === "hard"
          ? "null"
          : this.getDefaultExpression(field.type!),
        ",\n",
      );
    }
    this.push(
      ");\n\n",
      "/** Returns an instance with all fields set to their default values. */\n",
      "fun partial() = default;\n\n",
      "/**\n",
      ` * Creates a new instance of [${className.name}].\n`,
      " * Unlike the constructor, does not require all fields to be specified.\n",
      " * Missing fields will be set to their default values.\n",
      " */\n",
      "fun partial(\n",
      "_mustNameArguments: _MustNameArguments =\n_MustNameArguments,\n",
    );
    for (const field of fields) {
      const fieldName = namer.structFieldToKotlinName(field);
      const type = typeSpeller.getKotlinType(field.type!, "initializer");
      const defaultExpr = this.getDefaultExpression(field.type!);
      this.push(`${fieldName}: ${type} =\n${defaultExpr},\n`);
    }
    this.push(`) = ${qualifiedName}(\n`);
    for (const field of fields) {
      const fieldName = namer.structFieldToKotlinName(field);
      this.push(`${fieldName} = ${fieldName},\n`);
    }
    this.push(
      "_unrecognizedFields = null,\n",
      ");\n\n",
      "private val serializerImpl = build.skir.internal.StructSerializer(\n",
      `recordId = "${getRecordId(struct)}",\n`,
      `doc = ${toKotlinStringLiteral(struct.record.doc.text)},\n`,
      "defaultInstance = default,\n",
      "newMutableFn = { it?.toMutable() ?: Mutable() },\n",
      "toFrozenFn = { it.toFrozen() },\n",
      "getUnrecognizedFields = { it._unrecognizedFields },\n",
      "setUnrecognizedFields = { m, u -> m._unrecognizedFields = u },\n",
      ");\n\n",
      `/** Serializer for [${className.name}] instances. */\n`,
      "val serializer = build.skir.internal.makeSerializer(serializerImpl);\n\n",
      `/** Describes the [${className.name}] type. Provides runtime introspection capabilities. */\n`,
      "val typeDescriptor get() = serializerImpl.typeDescriptor;\n\n",
      "init {\n",
    );
    for (const field of fields) {
      const fieldName = namer.structFieldToKotlinName(field);
      this.push(
        "serializerImpl.addField(\n",
        `"${field.name.text}",\n`,
        `"${fieldName}",\n`,
        `${field.number},\n`,
        `${typeSpeller.getSerializerExpression(field.type!)},\n`,
        `${toKotlinStringLiteral(field.doc.text)},\n`,
        `{ it.${fieldName} },\n`,
        `{ mut, v -> mut.${fieldName} = v },\n`,
        ");\n",
      );
    }
    for (const removedNumber of struct.record.removedNumbers) {
      this.push(`serializerImpl.addRemovedNumber(${removedNumber});\n`);
    }
    this.push("serializerImpl.finalizeStruct();\n", "}\n", "}\n");

    // Write the classes for the records nested in `record`.
    const nestedRecords = struct.record.nestedRecords.map(
      (r) => recordMap.get(r.key)!,
    );
    this.writeClassesForRecords(nestedRecords);

    this.push("}\n\n");
  }

  private writeMutableGetters(fields: readonly Field[]): void {
    const { namer, typeSpeller } = this;
    for (const field of fields) {
      if (field.isRecursive) {
        continue;
      }
      const type = field.type!;
      const fieldName = namer.structFieldToKotlinName(field);
      const mutableGetterName =
        "mutable" + convertCase(field.name.text, "UpperCamel");
      const mutableType = typeSpeller.getKotlinType(field.type!, "mutable");
      const accessor = `this.${fieldName}`;
      let bodyLines: string[] = [];
      if (type.kind === "array") {
        bodyLines = [
          "return when (value) {\n",
          "is build.skir.internal.MutableList -> value;\n",
          "else -> {\n",
          "value = build.skir.internal.MutableList(value);\n",
          `${accessor} = value;\n`,
          "value;\n",
          "}\n",
          "}\n",
        ];
      } else if (type.kind === "record") {
        const record = this.typeSpeller.recordMap.get(type.key)!;
        if (record.record.recordType === "struct") {
          const structQualifiedName = namer.getClassName(record).qualifiedName;
          bodyLines = [
            "return when (value) {\n",
            `is ${structQualifiedName} -> {\n`,
            "value = value.toMutable();\n",
            `${accessor} = value;\n`,
            "return value;\n",
            "}\n",
            `is ${structQualifiedName}.Mutable -> value;\n`,
            "}\n",
          ];
        }
      }
      if (bodyLines.length) {
        this.push(
          "/**\n",
          ` * If the value of [${fieldName}] is already mutable, returns it as-is.\n`,
          ` * Otherwise, makes a mutable copy, assigns it back to [${fieldName}] and returns it.\n`,
          ` */\n`,
          `val ${mutableGetterName}: ${mutableType} get() {\n`,
          `var value = ${accessor};\n`,
        );
        for (const line of bodyLines) {
          this.push(line);
        }
        this.push("}\n\n");
      }
    }
  }

  private writeClassForEnum(record: RecordLocation): void {
    const { namer, typeSpeller } = this;
    const { recordMap } = typeSpeller;
    const { fields: variants } = record.record;
    const constantVariants = variants.filter((v) => !v.type);
    const wrapperVariants = variants.filter((v) => v.type);
    const className = namer.getClassName(record);
    const qualifiedName = className.qualifiedName;
    this.push(
      `sealed class ${className.name} private constructor() {\n`,
      "enum class Kind {\n", //
      "UNKNOWN,\n",
    );
    for (const variant of constantVariants) {
      this.push(`${variant.name.text}_CONST,\n`);
    }
    for (const variant of wrapperVariants) {
      this.push(
        convertCase(variant.name.text, "UPPER_UNDERSCORE"),
        "_WRAPPER,\n",
      );
    }
    this.push(
      "}\n\n",
      'class Unknown @kotlin.Deprecated("For internal use", kotlin.ReplaceWith("',
      qualifiedName,
      '.UNKNOWN")) internal constructor(\n',
      `internal val _unrecognized: _UnrecognizedVariant<${qualifiedName}>?,\n`,
      `) : ${qualifiedName}() {\n`,
      "override val kind get() = Kind.UNKNOWN;\n\n",
      "override fun equals(other: kotlin.Any?): kotlin.Boolean {\n",
      "return other is Unknown;\n",
      "}\n\n",
      "override fun hashCode(): kotlin.Int {\n",
      "return -900601970;\n",
      "}\n\n",
      "}\n\n", // class Unknown
    );
    for (const constantVariant of constantVariants) {
      const kindExpr = `Kind.${constantVariant.name.text}_CONST`;
      const constantName = toEnumConstantName(constantVariant);
      this.push(
        `object ${constantName} : ${qualifiedName}() {\n`,
        `override val kind get() = ${kindExpr};\n\n`,
        "init {\n",
        "_maybeFinalizeSerializer();\n",
        "}\n",
        `}\n\n`, // object
      );
    }
    for (const wrapperVariant of wrapperVariants) {
      const valueType = wrapperVariant.type!;
      const wrapperClassName =
        convertCase(wrapperVariant.name.text, "UpperCamel") + "Wrapper";
      const initializerType = typeSpeller
        .getKotlinType(valueType, "initializer")
        .toString();
      const frozenType = typeSpeller
        .getKotlinType(valueType, "frozen")
        .toString();
      this.pushEol();
      if (initializerType === frozenType) {
        this.push(
          `class ${wrapperClassName}(\n`,
          `val value: ${initializerType},\n`,
          `) : ${qualifiedName}() {\n`,
        );
      } else {
        this.push(
          `class ${wrapperClassName} private constructor (\n`,
          `val value: ${frozenType},\n`,
          `) : ${qualifiedName}() {\n`,
          "constructor(\n",
          `value: ${initializerType},\n`,
          `): this(${this.toFrozenExpression("value", valueType)}) {}\n\n`,
        );
      }
      const kindExpr =
        "Kind." +
        convertCase(wrapperVariant.name.text, "UPPER_UNDERSCORE") +
        "_WRAPPER";
      this.push(
        `override val kind get() = ${kindExpr};\n\n`,
        "override fun equals(other: kotlin.Any?): kotlin.Boolean {\n",
        `return other is ${qualifiedName}.${wrapperClassName} && value == other.value;\n`,
        "}\n\n",
        "override fun hashCode(): kotlin.Int {\n",
        "return this.value.hashCode() + ",
        String(simpleHash(wrapperVariant.name.text) | 0),
        ";\n",
        "}\n\n",
        "}\n\n", // class
      );
    }

    this.push(
      "abstract val kind: Kind;\n\n",
      "override fun toString(): kotlin.String {\n",
      "return build.skir.internal.toStringImpl(\n",
      "this,\n",
      `${qualifiedName}._serializerImpl,\n`,
      ")\n",
      "}\n\n",
      "companion object {\n",
      'val UNKNOWN = @kotlin.Suppress("DEPRECATION") Unknown(null);\n\n',
    );
    for (const wrapperVariant of wrapperVariants) {
      const type = wrapperVariant.type!;
      if (type.kind !== "record") {
        continue;
      }
      const structLocation = typeSpeller.recordMap.get(type.key)!;
      const struct = structLocation.record;
      if (struct.recordType !== "struct") {
        continue;
      }
      const structClassName = namer.getClassName(structLocation);
      const createFunName =
        "create" + convertCase(wrapperVariant.name.text, "UpperCamel");
      const wrapperClassName =
        convertCase(wrapperVariant.name.text, "UpperCamel") + "Wrapper";
      this.push(
        '@kotlin.Suppress("UNUSED_PARAMETER")\n',
        `fun ${createFunName}(\n`,
        "_mustNameArguments: _MustNameArguments =\n_MustNameArguments,\n",
      );
      for (const field of struct.fields) {
        const fieldName = namer.structFieldToKotlinName(field);
        const type = typeSpeller.getKotlinType(field.type!, "initializer");
        this.push(`${fieldName}: ${type},\n`);
      }
      this.push(
        `) = ${wrapperClassName}(\n`,
        `${structClassName.qualifiedName}(\n`,
      );
      for (const field of struct.fields) {
        const fieldName = namer.structFieldToKotlinName(field);
        this.push(`${fieldName} = ${fieldName},\n`);
      }
      this.push(")\n", ");\n\n");
    }
    this.push(
      "private val _serializerImpl =\n",
      `build.skir.internal.EnumSerializer.create<${qualifiedName}, Unknown>(\n`,
      `recordId = "${getRecordId(record)}",\n`,
      `doc = ${toKotlinStringLiteral(record.record.doc.text)},\n`,
      "getKindOrdinal = { it.kind.ordinal },\n",
      "kindCount = Kind.values().size,\n",
      "unknownInstance = UNKNOWN,\n",
      'wrapUnrecognized = { @kotlin.Suppress("DEPRECATION") Unknown(it) },\n',
      "getUnrecognized = { it._unrecognized },\n)",
      ";\n\n",
      "val serializer = build.skir.internal.makeSerializer(_serializerImpl);\n\n",
      "val typeDescriptor get() = _serializerImpl.typeDescriptor;\n\n",
      "init {\n",
    );
    for (const constantVariant of constantVariants) {
      this.push(toEnumConstantName(constantVariant), ";\n");
    }
    this.push("_maybeFinalizeSerializer();\n");
    this.push(
      "}\n\n", // init
      `private var _finalizationCounter = 0;\n\n`,
      "private fun _maybeFinalizeSerializer() {\n",
      "_finalizationCounter += 1;\n",
      `if (_finalizationCounter == ${constantVariants.length + 1}) {\n`,
    );
    for (const variant of constantVariants) {
      this.push(
        "_serializerImpl.addConstantVariant(\n",
        `${variant.number},\n`,
        `"${variant.name.text}",\n`,
        `Kind.${variant.name.text}_CONST.ordinal,\n`,
        `${toKotlinStringLiteral(variant.doc.text)},\n`,
        `${toEnumConstantName(variant)},\n`,
        ");\n",
      );
    }
    for (const variant of wrapperVariants) {
      const serializerExpression = typeSpeller.getSerializerExpression(
        variant.type!,
      );
      const wrapperClassName =
        convertCase(variant.name.text, "UpperCamel") + "Wrapper";
      const kindConstName =
        convertCase(variant.name.text, "UPPER_UNDERSCORE") + "_WRAPPER";
      this.push(
        "_serializerImpl.addWrapperVariant(\n",
        `${variant.number},\n`,
        `"${variant.name.text}",\n`,
        `Kind.${kindConstName}.ordinal,\n`,
        `${serializerExpression},\n`,
        `${toKotlinStringLiteral(variant.doc.text)},\n`,
        `{ ${wrapperClassName}(it) },\n`,
        ") { it.value };\n",
      );
    }
    for (const removedNumber of record.record.removedNumbers) {
      this.push(`_serializerImpl.addRemovedNumber(${removedNumber});\n`);
    }
    this.push(
      "_serializerImpl.finalizeEnum();\n",
      "}\n",
      "}\n", // maybeFinalizeSerializer
      "}\n\n", // companion object
    );

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
      commentify(docToCommentText(method.doc)),
      `val ${methodName}: build.skir.service.Method<\n${requestType},\n${responseType},\n> by kotlin.lazy {\n`,
      "build.skir.service.Method(\n",
      `"${methodName}",\n`,
      `${method.number},\n`,
      requestSerializerExpr + ",\n",
      responseSerializerExpr + ",\n",
      toKotlinStringLiteral(method.doc.text) + ",\n",
      ")\n",
      "}\n\n",
    );
  }

  private writeConstant(constant: Constant): void {
    const { typeSpeller } = this;
    const name = constant.name.text;
    const type = constant.type!;
    const kotlinType = typeSpeller.getKotlinType(type, "frozen");
    const tryGetKotlinConstLiteral: () => string | undefined = () => {
      if (type.kind !== "primitive") {
        return undefined;
      }
      const { valueAsDenseJson } = constant;
      switch (type.primitive) {
        case "bool":
          return JSON.stringify(!!valueAsDenseJson);
        case "int32":
        case "string":
          return JSON.stringify(valueAsDenseJson);
        case "int64":
          return `${valueAsDenseJson}L`;
        case "uint64":
          return `${valueAsDenseJson}UL`;
        case "float32": {
          if (valueAsDenseJson === "NaN") {
            return "Float.NaN";
          } else if (valueAsDenseJson === "Infinity") {
            return "Float.POSITIVE_INFINITY";
          } else if (valueAsDenseJson === "-Infinity") {
            return "Float.NEGATIVE_INFINITY";
          } else {
            return JSON.stringify(valueAsDenseJson) + "F";
          }
        }
        case "float64": {
          if (valueAsDenseJson === "NaN") {
            return "Double.NaN";
          } else if (valueAsDenseJson === "Infinity") {
            return "Double.POSITIVE_INFINITY";
          } else if (valueAsDenseJson === "-Infinity") {
            return "Double.NEGATIVE_INFINITY";
          } else {
            return JSON.stringify(valueAsDenseJson);
          }
        }
        default:
          return undefined;
      }
    };
    this.push(commentify(docToCommentText(constant.doc)));
    const kotlinConstLiteral = tryGetKotlinConstLiteral();
    if (kotlinConstLiteral !== undefined) {
      this.push(
        `const val ${name}: ${kotlinType} = ${kotlinConstLiteral};\n\n`,
      );
    } else {
      const serializerExpression = typeSpeller.getSerializerExpression(
        constant.type!,
      );
      const jsonStringLiteral = JSON.stringify(
        JSON.stringify(constant.valueAsDenseJson),
      );
      this.push(
        `val ${name}: ${kotlinType} by kotlin.lazy {\n`,
        serializerExpression,
        `.fromJsonCode(${jsonStringLiteral})\n`,
        "}\n\n",
      );
    }
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
        break;
      }
      case "array": {
        const itemType = this.typeSpeller.getKotlinType(type.item, "frozen");
        if (type.key) {
          const { keyType } = type.key;
          let kotlinKeyType = this.typeSpeller.getKotlinType(keyType, "frozen");
          if (keyType.kind === "record") {
            kotlinKeyType += ".Kind";
          }
          return `build.skir.internal.emptyKeyedList<${itemType}, ${kotlinKeyType}>()`;
        } else {
          return `build.skir.internal.emptyFrozenList<${itemType}>()`;
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
            return `${kotlinType}.partial()`;
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
    const { namer } = this;
    switch (type.kind) {
      case "primitive": {
        return inputExpr;
      }
      case "array": {
        const itemToFrozenExpr = this.toFrozenExpression("it", type.item);
        if (type.key) {
          const path = type.key.path
            .map((f) => namer.structFieldToKotlinName(f.name.text))
            .join(".");
          if (itemToFrozenExpr === "it") {
            return `build.skir.internal.toKeyedList(${inputExpr}, "${path}", { it.${path} })`;
          } else {
            return `build.skir.internal.toKeyedList(${inputExpr}, "${path}", { it.${path} }, { ${itemToFrozenExpr} })`;
          }
        } else {
          if (itemToFrozenExpr === "it") {
            return `build.skir.internal.toFrozenList(${inputExpr})`;
          } else {
            return `build.skir.internal.toFrozenList(${inputExpr}, { ${itemToFrozenExpr} })`;
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

  private push(...code: string[]): void {
    this.code += code.join("");
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
    const peakTop = (): string => contextStack.at(-1)!;
    const getMatchingLeftBracket = (r: "}" | ")" | "]" | ">"): string => {
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
      const indent =
        indentUnit.repeat(contextStack.length) +
        (line.startsWith("*") ? " " : "");
      result += `${indent}${line.trimEnd()}\n`;
      if (line.startsWith("/") || line.startsWith("*")) {
        // A comment.
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
  private readonly packagePrefix: string;
  private readonly namer: Namer;
  private code = "";
}

function getRecordId(struct: RecordLocation): string {
  const modulePath = struct.modulePath;
  const qualifiedRecordName = struct.recordAncestors
    .map((r) => r.name.text)
    .join(".");
  return `${modulePath}:${qualifiedRecordName}`;
}

function toKotlinStringLiteral(input: string): string {
  // Escape special characters for Kotlin string literals
  const escaped = input
    .replace(/\\/g, "\\\\") // Escape backslashes
    .replace(/"/g, '\\"') // Escape double quotes
    .replace(/\n/g, "\\n") // Escape newlines
    .replace(/\r/g, "\\r") // Escape carriage returns
    .replace(/\t/g, "\\t") // Escape tabs
    .replace(/\$/g, "\\$"); // Escape $ to prevent unwanted interpolation
  return `"${escaped}"`;
}

function commentify(textOrLines: string | readonly string[]): string {
  const text = (
    typeof textOrLines === "string" ? textOrLines : textOrLines.join("\n")
  )
    .trim()
    .replace(/\n{3,}/g, "\n\n")
    .replace("*/", "* /");
  if (text.length <= 0) {
    return "";
  }
  const lines = text.split("\n");
  if (lines.length === 1) {
    return `/** ${text} */\n`;
  } else {
    return ["/**\n", ...lines.map((line) => ` * ${line}\n`), " */\n"].join("");
  }
}

function docToCommentText(doc: Doc): string {
  return doc.pieces
    .map((p) => {
      switch (p.kind) {
        case "text":
          return p.text;
        case "reference":
          return "`" + p.referenceRange.text.slice(1, -1) + "`";
      }
    })
    .join("");
}

export const GENERATOR = new KotlinCodeGenerator();
