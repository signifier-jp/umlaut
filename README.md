# umlaut

A Clojure tool that receives a umlaut schema and outputs code.

## Schema language

`umlaut` tool expects to receive a `umlaut` schema. A `umlaut` schema is general-purpose schema definition language that has been designed to provide support for generating models in several different languages in a flexible way.

Let's go over `umlaut`'s syntax and characteristics.

## General

The primitive types are: `["String" "Float" "Integer" "Boolean" "DateTime", "ID"]`. If an attribute has a type that is not primitive, it must be properly declared.

By default, all declared fields are *non-null*. Nullable fields must have the symbol `?` after the attribute type.

Umlaut receives a list of files as input. It will open all files with extension `umlaut` and load the schema into memory. One declaration can use the types defined in any other `umlaut` file. No need for includes!

Identifiers are valid if they are valid in this regex: `[_A-Za-z][_0-9A-Za-z]*`.

Comments are indicated by `//`.

### Attributes and methods
Inside of types and interfaces, you can declare fields.

#### Field
Field represents two concepts on object oriented languages: attributes and methods. To represent a method, declare a filed with parameters. To represent an attribute, declare a method that doesn't parameters. All fields must have a type (primitive or not), and are declared like this: `identifier: type`. You can add arity after the type to indicate a collection of items of that same type, like this: `identifier: type[min..max]`. Examples:
```
  firstName: String
  middleName: String?
  lastName: String  // This is a comment in the schema source code
  dob: DateTime {
    @doc "This is a documentation comment in the dob field"
  }
  age: Float
  friends: String[0..n]
```

We call methods attributes that can receive parameters, perform some computation and return an arbitrary type. The have the following form: `identifier(identifier: type, ..., identifier: type): type`.

There is no limit in the number of parameters a method can receive. You can use the optional modifier `?` after any one of the types to indicate optionality. The return type of the method must always be specified. Examples:
```
  getFirstName(): String
  setFirstName(name: String): String
  computeAge(dateOfBirth: DateTime, today: DateTime): Integer
```

Note that `field(): type` is exactly the same of `field: type`.

### type
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
- A type can inherit fields from an interface, but it can't from another type. Multiple inheritance is allowed.
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

- The list of reserved words should be observed before naming an enum.

### Annotations
Annotations above the type declaration are used by a some code generators.
Annotations have one of these two forms: `@<space> key [value]` or `@<space> [value]`. The second format is used only for documentation purposes.
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