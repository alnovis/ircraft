# Разделение passes и Proto-Wrapper 3.0.0

## Концепция

ircraft -- generic библиотека конвертации A -> X -> B с инжекцией эффектов на этапе X.

- **A** -- исходная модель (proto schema, OpenAPI spec, DB schema, любой DSL)
- **X** -- промежуточное представление (IR)
- **B** -- целевой код (Java, Kotlin, Scala, TypeScript, ...)

Аналогия: ircraft : proto-wrapper = LLVM : Clang.
ircraft предоставляет инфраструктуру. Потребитель -- бизнес-логику.

## Цель

1. ircraft -- чистая generic библиотека, ноль знания о proto-wrapper
2. proto-wrapper-plugin -- владеет всей бизнес-логикой, пишет свои passes на Java
3. Полный паритет с текущим JavaPoet выводом
4. Удаление зависимости JavaPoet из proto-wrapper-plugin
5. ircraft удобен для использования из Java (builder API)

## Ожидаемый результат

- ircraft proto dialect: чистый lowering SchemaOp -> InterfaceOp/ClassOp/EnumOp
- proto-wrapper-plugin: conflict resolution, version wrappers, VersionContext через свои passes
- Один путь генерации (без "legacy" vs "ircraft" альтернатив)
- Любой потребитель ircraft может создать свой pipeline без proto-wrapper артефактов

---

## Классификация passes

### Остаются в ircraft (generic protobuf lowering)

| Pass | Что делает | Почему generic |
|------|-----------|---------------|
| ProtoVerifierPass | Структурная валидация IR | Любая proto schema нуждается в валидации |
| ProtoToSemanticLowering | SchemaOp -> interfaces + abstract + impl per version | Базовый proto-to-OOP паттерн |
| HasMethodsPass | hasXxx() для optional, supportsXxx() для version-specific | Стандартная proto3 семантика |
| BuilderPass | Builder pattern (условный) | Generic OOP паттерн |
| WktConversionPass | Timestamp->Instant, Duration->Duration | Стандартный protobuf WKT маппинг |
| ValidationAnnotationsPass | @NotNull на non-optional message-type getters | Стандартный Bean Validation |

### Переезжают в plugin (бизнес-логика proto-wrapper, реализация на Java)

| Pass | Что делает | Почему proto-wrapper |
|------|-----------|---------------------|
| ConflictResolutionPass | getXxxEnum(), getXxxBytes(), getXxxMessage() | Dual-accessor для конфликтов типов между версиями |
| ProtoWrapperPass | ProtoWrapper interface (getTypedProto, getWrapperVersionId, toBytes) | Центральная абстракция proto-wrapper |
| CommonMethodsPass | equals/hashCode/toString с version ID, toBytes | Version-aware identity обёрток |
| VersionContextPass | VersionContext interface + per-version factory | Multi-version factory pattern |
| ProtocolVersionsPass | ProtocolVersions constants class | Генерация констант версий |
| VersionConversionPass | asVersion(), getFieldsInaccessibleInVersion() | Кросс-версионная конвертация |
| SchemaMetadataPass | SchemaInfoVx per version | Runtime метаданные |

### Новые passes (нет аналога в ircraft, только JavaPoet)

| Pass | JavaPoet источник | Назначение |
|------|------------------|-----------|
| ConflictEnumEnrichmentPass | ConflictEnumGenerator | fromProtoValue(), fromProtoValueOrDefault() |
| SchemaDiffPass | SchemaDiffGenerator | SchemaDiffVxToVy transition classes |
| StructConverterPass | StructConverterGenerator | Утилита для Struct/Value/ListValue |

---

## Пробелы IrcraftBridge

| Пробел | Текущее состояние | Исправление |
|--------|------------------|-------------|
| conflictEnums | Всегда пустой | Конвертировать MergedSchema.getConflictEnums() |
| versionSyntax | Пустая map | Маппить Java ProtoSyntax -> Scala ProtoSyntax |
| equivalentEnumMappings | Теряется | Сохранить как атрибут SchemaOp |
| version-specific field types | Только unified тип | Добавить per-version типы в атрибуты FieldOp |
| WKT info на полях | Частично | Заполнить WellKnownType из MergedField |

---

## Архитектура: до и после

### До (текущая)

```
ProtoToCodePipeline (13 passes в ircraft)
  |
ProtoToJavaPipeline (тонкая обёртка, ircraft)
  |
IrcraftGenerator (proto-wrapper-plugin)
```

ircraft знает о ProtoWrapper, VersionContext, конфликтных типах.

### После (целевая)

```
GenericProtoToCodePipeline (6 generic passes, ircraft)
  |
  | pipeline.andThen(pass1).andThen(pass2)...
  |
ProtoWrapperPipeline (generic + 7+ Java passes, proto-wrapper-plugin)
  |
IrcraftGenerator (proto-wrapper-plugin)
```

ircraft ничего не знает о proto-wrapper. Plugin компонует свой pipeline.

---

## Фазы миграции

### Фаза 1: Пробелы IrcraftBridge (~1 день)

Без изменения поведения. IR становится полным представлением MergedSchema.

| Задача | Компонент | Файл |
|--------|-----------|------|
| Заполнить conflictEnums | plugin | `proto-wrapper-core/.../ircraft/IrcraftBridge.java` |
| Заполнить versionSyntax | plugin | `proto-wrapper-core/.../ircraft/IrcraftBridge.java` |
| Добавить equivalentEnumMappings | plugin | `proto-wrapper-core/.../ircraft/IrcraftBridge.java` |
| Заполнить WKT на FieldOp | plugin | `proto-wrapper-core/.../ircraft/IrcraftBridge.java` |
| Добавить version-specific типы | plugin | `proto-wrapper-core/.../ircraft/IrcraftBridge.java` |
| Тест полноты конвертации | plugin | `proto-wrapper-core/.../ircraft/IrcraftBridgeCompletenessTest.java` (new) |

Только plugin меняется. ircraft без изменений.

**Проверка:** `IrcraftBridgeCompletenessTest`, существующие 8 ircraft integration тестов.

---

### Фаза 2: Java-friendly Builder API в ircraft (~2 дня)

Тонкий слой Java builders поверх Scala case classes. Не ломает Scala API.
Одноразовая инвестиция -- упрощает написание passes на Java для ЛЮБОГО потребителя.

| Задача | Компонент | Файл |
|--------|-----------|------|
| Builder для InterfaceOp | ircraft | `dialects/semantic/.../ops/InterfaceOp.scala` (extend companion) |
| Builder для ClassOp | ircraft | `dialects/semantic/.../ops/ClassOp.scala` (extend companion) |
| Builder для MethodOp | ircraft | `dialects/semantic/.../ops/MethodOp.scala` (extend companion) |
| Builder для FileOp | ircraft | `dialects/semantic/.../ops/FileOp.scala` (extend companion) |
| Builder для EnumClassOp | ircraft | `dialects/semantic/.../ops/EnumClassOp.scala` (extend companion) |
| Java-friendly helpers для Module, PassResult | ircraft | `ircraft-core/.../Module.scala`, `Pass.scala` (extend) |
| Java-friendly TypeRef factory methods | ircraft | `ircraft-core/.../TypeRef.scala` (extend) |
| Тесты interop из Java | ircraft | `ircraft-core/src/test/java/...` (new, Java test) |

Пример использования из Java:
```java
var iface = InterfaceOp.builder("ProtoWrapper")
    .addMethod(MethodOp.builder("getTypedProto", TypeRef.named("Message"))
        .modifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
        .build())
    .addMethod(MethodOp.builder("toBytes", TypeRef.BYTES())
        .modifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
        .build())
    .build();
```

Builders возвращают immutable Scala objects. Конверсия коллекций скрыта внутри.

**Проверка:** Java interop тест -- создать InterfaceOp, ClassOp, MethodOp из Java,
проверить что результат эквивалентен Scala конструкторам. `sbt test` green.

---

### Фаза 3: Разделение ProtoToCodePipeline (~1 день)

Создать generic pipeline в ircraft с точкой расширения.

| Задача | Компонент | Файл |
|--------|-----------|------|
| Создать GenericProtoToCodePipeline (6 passes) | ircraft | `dialects/proto/.../pipeline/GenericProtoToCodePipeline.scala` (new) |
| Expose genericPipeline для downstream | ircraft | там же |
| Пометить ProtoToCodePipeline как legacy | ircraft | `dialects/proto/.../pipeline/ProtoToCodePipeline.scala` |
| Тест generic pipeline | ircraft | `dialects/proto/src/test/.../GenericPipelineSuite.scala` (new) |

Только ircraft. Plugin не меняется. Legacy pipeline работает.

**Проверка:** `GenericPipelineSuite` -- generic passes дают interfaces + abstract + impl,
но НЕТ ProtoWrapper, VersionContext. Все 331 тестов проходят.

---

### Фаза 4: ProtoWrapperPipeline в plugin (~3 дня)

Переписать 7 passes на Java. Композировать свой pipeline.

| Задача | Компонент | Файл |
|--------|-----------|------|
| ConflictResolutionPass на Java | plugin | `proto-wrapper-core/.../ircraft/passes/ConflictResolutionPass.java` (new) |
| ProtoWrapperPass на Java | plugin | `proto-wrapper-core/.../ircraft/passes/ProtoWrapperPass.java` (new) |
| CommonMethodsPass на Java | plugin | `proto-wrapper-core/.../ircraft/passes/CommonMethodsPass.java` (new) |
| VersionContextPass на Java | plugin | `proto-wrapper-core/.../ircraft/passes/VersionContextPass.java` (new) |
| ProtocolVersionsPass на Java | plugin | `proto-wrapper-core/.../ircraft/passes/ProtocolVersionsPass.java` (new) |
| VersionConversionPass на Java | plugin | `proto-wrapper-core/.../ircraft/passes/VersionConversionPass.java` (new) |
| SchemaMetadataPass на Java | plugin | `proto-wrapper-core/.../ircraft/passes/SchemaMetadataPass.java` (new) |
| ProtoWrapperPipeline (компоновка) | plugin | `proto-wrapper-core/.../ircraft/passes/ProtoWrapperPipeline.java` (new) |
| Обновить IrcraftGenerator | plugin | `proto-wrapper-core/.../ircraft/IrcraftGenerator.java` |
| Тест паритета | plugin | `proto-wrapper-core/.../ircraft/IrcraftParityTest.java` (new) |

Passes используют builder API из Фазы 2. Каждый pass -- отдельный Java класс,
реализует `io.alnovis.ircraft.core.Pass`.

ProtoWrapperPipeline:
```java
public Pipeline build(LoweringConfig config, Emitter emitter) {
    Pipeline base = new GenericProtoToCodePipeline(config, emitter).genericPipeline();
    return base
        .andThen(new ConflictResolutionPass())
        .andThen(new ProtoWrapperPass())
        .andThen(new CommonMethodsPass())
        .andThen(new VersionContextPass())
        .andThen(new ProtocolVersionsPass())
        .andThen(new VersionConversionPass())
        .andThen(new SchemaMetadataPass());
}
```

**Проверка:** `IrcraftParityTest` -- сравнить вывод JavaPoet vs ircraft pipeline.
8 integration тестов. `sbt test` green. `mvn test` green.

---

### Фаза 5: Новые артефакты для полного паритета (~2 дня)

| Задача | Компонент | Файл |
|--------|-----------|------|
| ConflictEnumEnrichmentPass | plugin | `proto-wrapper-core/.../ircraft/passes/ConflictEnumEnrichmentPass.java` (new) |
| SchemaDiffPass | plugin | `proto-wrapper-core/.../ircraft/passes/SchemaDiffPass.java` (new) |
| StructConverterPass | plugin | `proto-wrapper-core/.../ircraft/passes/StructConverterPass.java` (new) |
| Добавить в ProtoWrapperPipeline | plugin | `proto-wrapper-core/.../ircraft/passes/ProtoWrapperPipeline.java` |
| Golden тесты | plugin | `proto-wrapper-golden-tests/` (extend) |

Все passes на Java в plugin. Используют builder API ircraft.

**Проверка:** Golden тесты -- все типы артефактов покрыты.

---

### Фаза 6: Удалить proto-wrapper passes из ircraft (~1 день)

ircraft proto dialect становится generic.

| Задача | Компонент | Файл |
|--------|-----------|------|
| Удалить ConflictResolutionPass | ircraft | `dialects/proto/.../lowering/` (delete) |
| Удалить ProtoWrapperPass | ircraft | `dialects/proto/.../lowering/` (delete) |
| Удалить CommonMethodsPass | ircraft | `dialects/proto/.../lowering/` (delete) |
| Удалить VersionContextPass | ircraft | `dialects/proto/.../lowering/` (delete) |
| Удалить ProtocolVersionsPass | ircraft | `dialects/proto/.../lowering/` (delete) |
| Удалить VersionConversionPass | ircraft | `dialects/proto/.../lowering/` (delete) |
| Удалить SchemaMetadataPass | ircraft | `dialects/proto/.../lowering/` (delete) |
| Обновить ProtoToCodePipeline (только generic) | ircraft | `dialects/proto/.../pipeline/` |
| Перенести тесты удалённых passes | ircraft | tests (delete/move) |
| Обновить pipeline wrappers | ircraft | `pipelines/proto-to-java/`, `proto-to-kotlin/`, `proto-to-scala/` |

Тесты proto-wrapper passes (ConflictResolutionSuite, InfraPassesSuite, Batch4Suite)
удаляются из ircraft. Plugin тесты покрывают эту логику.

**Проверка:** `sbt test` -- ircraft CI чистый. `mvn test` -- plugin работает.
В `ircraft/dialects/proto/` нет proto-wrapper концепций.

---

### Фаза 7: Удалить JavaPoet (~1 день)

| Задача | Компонент | Файл |
|--------|-----------|------|
| Маршрутизация через IrcraftGenerator | plugin | `GenerateMojo.java`, `GenerateTask.kt` |
| Deprecate GenerationOrchestrator | plugin | `GenerationOrchestrator.java` |
| Удалить JavaPoet из pom.xml | plugin | `pom.xml` |
| Очистить deprecated методы GeneratorConfig | plugin | `GeneratorConfig.java` |

Только plugin. ircraft не затрагивается.

**Проверка:** Maven + Gradle integration тесты. `mvn dependency:tree` -- нет JavaPoet.

---

## Оценка

| Фаза | Дни | Компоненты |
|------|-----|-----------|
| Фаза 1: Пробелы IrcraftBridge | 1 | plugin |
| Фаза 2: Java Builder API | 2 | ircraft |
| Фаза 3: Разделение pipeline | 1 | ircraft |
| Фаза 4: Passes на Java в plugin | 3 | plugin |
| Фаза 5: Новые артефакты | 2 | plugin |
| Фаза 6: Удалить passes из ircraft | 1 | ircraft |
| Фаза 7: Удалить JavaPoet | 1 | plugin |
| **Итого** | **11** | |

---

## Риски

| Риск | Влияние | Митигация |
|------|---------|----------|
| Java/Scala interop при трансформации IR | Среднее | Builder API закрывает 90% кейсов; IrcraftBridge доказывает работоспособность |
| Expression/Statement AST не покрывает все паттерны JavaPoet | Среднее | Добавить новые варианты; Expression.Literal для сложных snippet'ов |
| Различия форматирования вывода | Низкое | Функциональная эквивалентность; AST-level сравнение |
| Потеря данных в IrcraftBridge для edge cases | Высокое | Property-based тестирование: все типы полей, nested messages, сложные oneof |
| 7 passes на Java -- много boilerplate | Среднее | Builder API минимизирует; один pass = 50-100 строк Java vs 40-80 Scala |

---

## Критерии успеха

1. `sbt test` в ircraft -- все тесты проходят, нет proto-wrapper концепций в proto dialect
2. `mvn test` в proto-wrapper -- все тесты проходят, нет JavaPoet зависимости
3. Golden тесты совпадают (или функционально эквивалентны) с JavaPoet выводом
4. Любой потребитель ircraft может создать свой pipeline без proto-wrapper артефактов
5. proto-wrapper-plugin 3.0.0 использует только ircraft для генерации
6. Java Builder API документирован и протестирован из Java
