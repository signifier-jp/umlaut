# umlaut

A Clojure tool that receives a umlaut schema and outputs code. You can download umlaut using clojars:

[![Clojars Project](https://img.shields.io/clojars/v/umlaut.svg)](https://clojars.org/umlaut)

You may also be interested in umlaut's CLI tool, lein-umlaut:
[![Clojars Project](https://img.shields.io/clojars/v/lein-umlaut.svg)](https://clojars.org/lein-umlaut)


## Schema language

`umlaut` tool expects to receive a `umlaut` schema. A `umlaut` schema is general-purpose schema definition language that has been designed to provide support for generating models in several different languages in a flexible way.

Let's go over `umlaut`'s syntax and characteristics.

## Usage

You can interact with this tool in two ways:
- adding the `lein-plugin` as a plugin to your project and using as a CLI;
- adding umlaut as a library dependency and call its functions directly;

In order to umlaut with the CLI, please visit its project page for further instructions: https://github.com/workco/lein-umlaut

In order to use it as library, every generator implements a `gen` methods. Dig into the source code of inside the `umlaut/src/generators` folder for more details

## General

The primitive types are: `["String" "Float" "Integer" "Boolean" "DateTime", "ID"]`. If an attribute has a type that is not primitive, it must be properly declared.
If you try to declare and attribute that is not primitive or declared, an error will be thrown.

By default, all declared fields are *non-null*. Nullable fields must have the symbol `?` after the attribute type.

Identifiers are validated but this regex: `[_A-Za-z][_0-9A-Za-z]*` and they can not be present on chapter "List of reserved words" of this document.

### Attributes and methods
Inside of types and interfaces, you can declare fields.

#### Field
Umlaut calls `field` either a method or an attribute:

To represent a method, declare a filed with parameters, like this:
`identifier(identifier: type, ..., identifier: type): type`

A field without parameters (attribute), is declared like this:
`identifier: type`

All fields must have a type (primitive or not). You can add arity after the type to indicate a collection of items of that same type, like this: `identifier: type[min..max]` or `identifier(...): type[min..max]`. You can use the optional modifier `?` after any one of the types to indicate a nullable field.

A few more examples:
```
  firstName: String
  middleName: String?
  lastName: String  // This is a comment in the schema source code
  dob: DateTime {
    @doc "This is a documentation comment in the dob field"
  }
  age: Float
  friends: String[0..n]
  setFirstName(name: String): String
  computeAge(dateOfBirth: DateTime, today: DateTime): Integer
```

### type

You can define new types by using the `type` reserved word.
```
type Person {
  id: ID
  name: String
  friends: String[0..n]
  isEmployed: Boolean
  email: String?
}
```

- `friends` is an example of a collection.
- `email` is a nullable attribute.
- All attributes are primitives in this example.
- The list of reserved words should be observed before naming a type.

### interface
You can also declare an `interface`, usually to create composition of models.

```
interface Person {
  id: ID
  name: String
  email: String?
}

interface Human {
  temperature: Float
}

type Employee : Person Human {
  salary: Float
  changeSalary(newSalary: Float): Float
}
```

- `umlaut` supports single/multiple inheritance, in this example `Employee` inherits from `Person` and `Human`. So `Employee` will have all the fields of its parent types, plus its own.
- An interface cannot inherit fields from other interfaces.
- A type can only inherit fields from an interface.
- Inheriting for multiple interfaces (multiple inheritance) is allowed.
- The list of reserved words should be observed before naming an interface.

### enum

You can create enums like this:
```
enum RelationshipStatus {
  Single
  Married
}

type Person {
  name: String
  status: RelationshipStatus
}
```

- Enums can also be used to create GraphQL unions, in order to do that, you need to add an annotation:
`@lang/lacinia identifier union` above the enum definition. In that case, the contents of the enum need to be other custom types.
- The list of reserved words should be observed before naming an enum.

### Annotations
Annotations above a declaration are used by the code generators.
Annotations have one of these two forms: `@<space> <key> [value]` or `@<space> [value]`. The second format is used only for documentation purposes.
In this notation `[value]` is represented between brackets, since you can add more than on value to it. However, this should only be done after understanding a given generator requirement.
```
@lang/lacinia identifier query
type QueryRoot {
  getFriends(): Person[0..n]
}
```

You can document your schema using the `@doc` space. This can be done above the type/interface/enum definition, like this:
```
@doc "This is my new type"
type New {
  ...
}
```

You can also document specific fields:
```
@doc "This is my new type"
type New {
  a: String {
    @doc "This is the comment of field a inside type New"
  }
}
```

See Lacinia documentation for more information.

# Generators

## Spec

Umlaut can generate spec code based on the schema. This is a more complex generator that expects several parameters.
Please read the `lein-umlaut` plugin help for the proper usage.

- You can have a `@lang/spec validator <name>` above a type to add a custom validator function for that type. This custom validator function needs to be implemented in a common validator file.
- Interfaces do not generate spec code, they are replaced by `s/or` of the types that implement that interface.

## Diagram

We use dot language to create diagram images. In order to build a diagram, you need to use the `diagram` keyword in the umlaut file.

```
enum RelationshipStatus {
  Single
  Married
}

type Person {
  name: String
  status: RelationshipStatus
}

diagram filename {
  (Person)!
}
```

The above example generates this diagram:

![filename](./resources/filename.png)

- A file named `all.png` is always created with all the types defined in the umlaut document.
- The file created is `filename.png` because of the identifier in the `diagram` keyword.
- The diagram includes the `RelationshipStatus` box because of the `!` used in the `(Person)` group. This tells umlaut to recursively draw all boxes that compose `Person`. If the `!` was omitted, the diagram would have a single `Person` box.
- You can have as many diagrams definitions/combinations as you want, just give them different names.

You can specify the colors of a box like this:
```
@lang/dot color <color>
```

This annotation needs to be above the definition of a type/enum/interface. The available colors are defined here: http://www.graphviz.org/doc/info/colors.html

To use this generator you must have [graphviz](http://www.graphviz.org/) installed; this package is available for Mac via [Homebrew](http://brewformulas.org/Graphviz) and Windows via [direct download](http://www.graphviz.org/Download_windows.php).

## Lacinia

Lacinia is a graphQL library for schemas. It's an [EDN](https://github.com/edn-format/edn) file with all the types, queries, inputs, mutations, and enums.

```
enum RelationshipStatus {
  Single
  Married
}

type Person {
  name: String?
  status: RelationshipStatus
  salary: Float {
    @doc "The annotation below assigns the resolve-salary resolver to the salary field"
    @lang/lacinia resolver resolve-salary
  }
}

@lang/lacinia identifier query
type QueryRoot {
  getFriends(): Person[0..n]
}
```

Will generate:
```
{:objects
 {:Person
  {:fields
   {:name {:type String},
    :status {:type (non-null :RelationshipStatus)},
    :salary {:type (non-null Float), :resolver :resolve-salary}},
   :implements []},
  :Profession
  {:fields {:name {:type (non-null String)}}, :implements []}},
 :enums {:RelationshipStatus {:values ["Single" "Married"]}},
 :interfaces {},
 :queries
 {:getFriends {:type (non-null (list (non-null :Person))), :args {}}}}

```

#### Lacinia annotations

Lacinia generator uses annotations heavily. In the above example, we used `@lang/lacinia identifier query` to indicate that `getFriends` should be placed under the `queries` key.

The following annotations are also valid for the lacinia generator. They should all be placed above the type definition that they are affecting.
```
@lang/lacinia resolver resolve-type  // Default type structure from resolve-type method
@lang/lacinia identifier query  // Will place the next type definition under queries key
@lang/lacinia identifier mutation  // Will place the next type definition under mutations key
@lang/lacinia identifier input  // Will place the next type definition under input-objects key
```

Just like documentation annotations, you can set resolver to specific fields. This should be done inline, beside the field definition, like this:

```
type Person {
  name: String {
    @doc "Name should hold the entire name, first name + last name"
    @lang/lacinia resolver resolve-name
  }
}
```

You can also add annotations at a global level like this:
```
annotations {
  @lang/lacinia union SearchResult Type1 Type2
}
```

For now, this is only used to create unions in lacinia.

#### List of reserved words:

These words are reserved for any of the supported generators and should not be used when defining a new type/interface/enum or field.
- node
- edge
- graph
- digraph
- subgraph
- strict