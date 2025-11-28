[![npm](https://img.shields.io/npm/v/soia-kotlin-gen)](https://www.npmjs.com/package/soia-kotlin-gen)
[![build](https://github.com/gepheum/soia-kotlin-gen/workflows/Build/badge.svg)](https://github.com/gepheum/soia-kotlin-gen/actions)

# Soia's Kotlin code generator

Official plugin for generating Kotlin code from [.soia](https://github.com/gepheum/soia) files.

## Installation

From your project's root directory, run `npm i --save-dev soia-kotlin-gen`.

In your `soia.yml` file, add the following snippet under `generators`:
```yaml
  - mod: soia-kotlin-gen
    config: {}
```

The `npm run soiac` command will now generate .kt files within the `soiagen` directory.

The generated Kotlin code has a runtime dependency on `land.soia:soia-kotlin-client`. Add this line to your `build.gradle.kts` file in the `dependencies` section:

```kotlin
implementation("land.soia:soia-kotlin-client:1.1.4")  // Pick the latest version
```

For more information, see this Kotlin project [example](https://github.com/gepheum/soia-kotlin-example).

## Kotlin generated code guide

The examples below are for the code generated from [this](https://github.com/gepheum/soia-kotlin-example/blob/main/soia-src/user.soia) .soia file.

### Referring to generated symbols

```kotlin
// Import the given symbols from the Kotlin module generated from "user.soia"
import soiagen.user.User
import soiagen.user.UserRegistry
import soiagen.user.SubscriptionStatus
import soiagen.user.TARZAN

// Now you can use: TARZAN, User, UserRegistry, SubscriptionStatus, etc.
```

### Frozen struct classes

For every struct S in the .soia file, soia generates a frozen (deeply immutable) class `S` and a mutable class `S.Mutable`.

```kotlin
// Construct a frozen User.
val john =
    User(
        userId = 42,
        name = "John Doe",
        quote = "Coffee is just a socially acceptable form of rage.",
        pets =
            listOf(
                User.Pet(
                    name = "Dumbo",
                    heightInMeters = 1.0f,
                    picture = "🐘",
                ),
            ),
        subscriptionStatus = SubscriptionStatus.FREE,
        // foo = "bar",
        // ^ Does not compile: 'foo' is not a field of User
    )

assert(john.name == "John Doe")

// john.name = "John Smith";
// ^ Does not compile: all the properties are read-only
```

#### Partial construction

```kotlin
// With .partial(), you don't need to specify all the fields of the struct.
val jane =
    User.partial(
        userId = 43,
        name = "Jane Doe",
        pets =
            listOf(
                User.Pet.partial(
                    name = "Fido",
                    picture = "🐶",
                ),
            ),
    )

// Missing fields are initialized to their default values.
assert(jane.quote == "")

// User.partial() with no arguments returns an instance of User with all
// fields set to their default values.
assert(User.partial().pets.isEmpty())
```

#### Creating modified copies

```kotlin
// User.copy() creates a shallow copy of the struct with the specified fields
// modified.
val evilJohn =
    john.copy(
        name = "Evil John",
        quote = "I solemnly swear I am up to no good.",
    )
assert(evilJohn.name == "Evil John")
assert(evilJohn.userId == 42)
```

### Mutable struct classes

`User.Mutable` is a dataclass similar to User except it is mutable.

```kotlin
val lyla = User.Mutable()
lyla.userId = 44
lyla.name = "Lyla Doe"

val userHistory = UserHistory.Mutable()
userHistory.user = lyla
// ^ The right-hand side of the assignment can be either frozen or mutable.
```

#### Mutable accessors

```kotlin
// The 'mutableUser' getter provides access to a mutable version of 'user'.
// If 'user' is already mutable, it returns it directly.
// If 'user' is frozen, it creates a mutable shallow copy, assigns it to
// 'user', and returns it.

// The user is currently 'lyla', which is mutable.
assert(userHistory.mutableUser === lyla)
// Now assign a frozen User to 'user'.
userHistory.user = john
// Since 'john' is frozen, mutableUser makes a mutable shallow copy of it.
userHistory.mutableUser.name = "John the Second"
assert(userHistory.user.name == "John the Second")
assert(userHistory.user.userId == 42)

// Similarly, 'mutablePets' provides access to a mutable version of 'pets'.
// It returns the existing list if already mutable, or creates and returns a
// mutable shallow copy.
lyla.mutablePets.add(
    User.Pet(
        name = "Simba",
        heightInMeters = 0.4f,
        picture = "🦁",
    ),
)
lyla.mutablePets.add(User.Pet.Mutable(name = "Cupcake"))

// lyla.pets.add(User.Pet.Mutable(name = "Cupcake"));
// ^ Does not compile: 'User.pets' is read-only
```

### Converting between frozen and mutable structs

```kotlin
// toMutable() does a shallow copy of the frozen struct, so it's cheap. All
// the properties of the copy hold a frozen value.
val evilJaneBuilder = jane.toMutable()
evilJaneBuilder.name = "Evil Jane"
evilJaneBuilder.mutablePets.add(
    User.Pet(
        name = "Shadow",
        heightInMeters = 0.5f,
        picture = "🐺",
    ),
)

// toFrozen() recursively copies the mutable values held by properties of the
// object.
val evilJane = evilJaneBuilder.toFrozen()

assert(evilJane.name == "Evil Jane")
assert(evilJane.userId == 43)
```

#### Type aliases for frozen or mutable

```kotlin
// 'User_OrMutable' is a type alias for the sealed class that both 'User' and
// 'User.Mutable' implement.
val greet: (User_OrMutable) -> Unit = {
    println("Hello, $it")
}

greet(jane)
// Hello, Jane Doe
greet(lyla)
// Hello, Lyla Doe
```

### Enum classes

Soia generates a deeply immutable Kotlin class for every enum in the .soia file. This class is *not* a Kotlin enum, although the syntax for referring to constants is similar.

```kotlin
val someStatuses =
    listOf(
        // The UNKNOWN constant is present in all Soia enums even if it is not
        // declared in the .soia file.
        SubscriptionStatus.UNKNOWN,
        SubscriptionStatus.FREE,
        SubscriptionStatus.PREMIUM,
        // Soia generates one subclass {VariantName}Wrapper for every wrapper
        // variant. The constructor of this subclass expects the value to
        // wrap.
        SubscriptionStatus.TrialWrapper(
            SubscriptionStatus.Trial(
                startTime = Instant.now(),
            ),
        ),
        // Same as above (^), with a more concise syntax.
        // Available when the wrapped value is a struct.
        SubscriptionStatus.createTrial(
            startTime = Instant.now(),
        ),
    )
```

### Conditions on enums

```kotlin
assert(john.subscriptionStatus == SubscriptionStatus.FREE)

// UNKNOWN is the default value for enums.
assert(jane.subscriptionStatus == SubscriptionStatus.UNKNOWN)

val now = Instant.now()
val trialStatus: SubscriptionStatus =
    SubscriptionStatus.TrialWrapper(
        Trial(startTime = now),
    )

assert(
    trialStatus is SubscriptionStatus.TrialWrapper &&
        trialStatus.value.startTime == now,
)
```

#### Branching on enum variants

```kotlin
val getInfoText: (SubscriptionStatus) -> String = {
    when (it) {
        SubscriptionStatus.FREE -> "Free user"
        SubscriptionStatus.PREMIUM -> "Premium user"
        is SubscriptionStatus.TrialWrapper -> "On trial since ${it.value.startTime}"
        is SubscriptionStatus.Unknown -> "Unknown subscription status"
    }
}

println(getInfoText(john.subscriptionStatus))
// "Free user"
```

### Serialization

Every frozen struct class and enum class has a static `serializer` property which can be used for serializing and deserializing instances of the class.

```kotlin
val serializer = User.serializer

// Serialize 'john' to dense JSON.
println(serializer.toJsonCode(john))
// [42,"John Doe","Coffee is just a socially acceptable form of rage.",[["Dumbo",1.0,"🐘"]],[1]]

// Serialize 'john' to readable JSON.
println(serializer.toJsonCode(john, JsonFlavor.READABLE))
// {
//   "user_id": 42,
//   "name": "John Doe",
//   "quote": "Coffee is just a socially acceptable form of rage.",
//   "pets": [
//     {
//       "name": "Dumbo",
//       "height_in_meters": 1.0,
//       "picture": "🐘"
//     }
//   ],
//   "subscription_status": "FREE"
// }

// The dense JSON flavor is the flavor you should pick if you intend to
// deserialize the value in the future. Soia allows fields to be renamed,
// and because field names are not part of the dense JSON, renaming a field
// does not prevent you from deserializing the value.
// You should pick the readable flavor mostly for debugging purposes.

// Serialize 'john' to binary format.
val johnBytes = serializer.toBytes(john)

// The binary format is not human readable, but it is slightly more compact
// than JSON, and serialization/deserialization can be a bit faster in
// languages like C++. Only use it when this small performance gain is
// likely to matter, which should be rare.
```

### Deserialization

```kotlin
// Use fromJson(), fromJsonCode() and fromBytes() to deserialize.
val reserializedJohn: User =
    serializer.fromJsonCode(serializer.toJsonCode(john))
assert(reserializedJohn.equals(john))

// fromJson/fromJsonCode can deserialize both dense and readable JSON
val reserializedEvilJohn: User =
    serializer.fromJsonCode(
        serializer.toJsonCode(john, JsonFlavor.READABLE),
    )
assert(reserializedEvilJohn.equals(evilJohn))

val reserializedJane: User =
    serializer.fromBytes(serializer.toBytes(jane))
assert(reserializedJane.equals(jane))
```

### Constants

```kotlin
println(TARZAN)
// User(
//   userId = 123,
//   name = "Tarzan",
//   quote = "AAAAaAaAaAyAAAAaAaAaAyAAAAaAaAaA",
//   pets = listOf(
//     User.Pet(
//       name = "Cheeta",
//       heightInMeters = 1.67F,
//       picture = "🐒",
//     ),
//   ),
//   subscriptionStatus = SubscriptionStatus.TrialWrapper(
//     SubscriptionStatus.Trial(
//       startTime = Instant.ofEpochMillis(
//         // 2025-04-02T11:13:29Z
//         1743592409000L
//       ),
//     )
//   ),
// )
```

### Keyed lists

```kotlin
// In the .soia file:
//   struct UserRegistry {
//     users: [User|user_id];
//   }

val userRegistry = UserRegistry(users = listOf(john, jane, evilJohn))

// find() returns the user with the given key (specified in the .soia file).
// In this example, the key is the user id.
// The first lookup runs in O(N) time, and the following lookups run in O(1)
// time.
assert(userRegistry.users.findByKey(43) === jane)

// If multiple elements have the same key, the last one is returned.
assert(userRegistry.users.findByKey(42) === evilJohn)
assert(userRegistry.users.findByKey(100) == null)
```

### Frozen lists and copies

```kotlin
// Since all Soia objects are deeply immutable, all lists contained in a
// Soia object are also deeply immutable.
// This section helps understand when lists are copied and when they are
// not.
val pets: MutableList<Pet> =
    mutableListOf(
        Pet.partial(name = "Fluffy", picture = "🐶"),
        Pet.partial(name = "Fido", picture = "🐻"),
    )

val jade =
    User.partial(
        name = "Jade",
        pets = pets,
        // ^ 'pets' is mutable, so Soia makes an immutable shallow copy of it
    )

assert(pets == jade.pets)
assert(pets !== jade.pets)

val jack =
    User.partial(
        name = "Jack",
        pets = jade.pets,
        // ^ 'jade.pets' is already immutable, so Soia does not make a copy
    )

assert(jack.pets === jade.pets)
```

### Soia services

#### Starting a soia service on an HTTP server

Full example [here](https://github.com/gepheum/soia-kotlin-example/blob/main/src/main/kotlin/examples/startservice/StartService.kt).

#### Sending RPCs to a soia service

Full example [here](https://github.com/gepheum/soia-kotlin-example/blob/main/src/main/kotlin/examples/callservice/CallService.kt).

### Reflection

Reflection allows you to inspect a soia type at runtime.

```kotlin
println(
    User.typeDescriptor
        .fields
        .map { field -> field.name }
        .toList(),
)
// [user_id, name, quote, pets, subscription_status]

// A type descriptor can be serialized to JSON and deserialized later.
val typeDescriptor =
    TypeDescriptor.parseFromJsonCode(
        User.serializer.typeDescriptor.asJsonCode(),
    )

assert(typeDescriptor is StructDescriptor)
assert((typeDescriptor as StructDescriptor).fields.size == 5)

// The 'allStringsToUpperCase' function uses reflection to convert all the
// strings contained in a given Soia value to upper case.
// See the implementation at
// https://github.com/gepheum/soia-kotlin-example/blob/main/src/main/kotlin/AllStringsToUpperCase.kt
println(allStringsToUpperCase(TARZAN, User.typeDescriptor))
// User(
// userId = 123,
// name = "TARZAN",
// quote = "AAAAAAAAAAYAAAAAAAAAAYAAAAAAAAAA",
// pets = listOf(
//     User.Pet(
//     name = "CHEETA",
//     heightInMeters = 1.67F,
//     picture = "🐒",
//     ),
// ),
// subscriptionStatus = SubscriptionStatus.TrialWrapper(
//     SubscriptionStatus.Trial(
//     startTime = Instant.ofEpochMillis(
//         // 2025-04-02T11:13:29Z
//         1743592409000L
//     ),
//     )
// ),
// )
```

## Java codegen versus Kotlin codegen

While Java and Kotlin code can interoperate seamlessly, Soia provides separate code generators for each language to leverage their unique strengths and idioms. For instance, the Kotlin generator utilizes named parameters for struct construction, whereas the Java generator employs the builder pattern. 

Although it's technically feasible to use Kotlin-generated code in a Java project (or vice versa), doing so results in an API that feels unnatural and cumbersome in the calling language. For the best developer experience, use the code generator that matches your project's primary language.

Note that both the Java and Kotlin generated code share the same runtime dependency: `land.soia:soia-kotlin-client`.
