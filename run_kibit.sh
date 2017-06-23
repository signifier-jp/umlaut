# Avoid any file to uses spec
lein kibit --replace src/umlaut/parser.clj
lein kibit --replace src/umlaut/utils.clj
lein kibit --replace src/umlaut/models.clj
lein kibit --replace src/umlaut/generators/dot.clj
lein kibit --replace src/umlaut/generators/fixtures.clj
lein kibit --replace src/umlaut/generators/graphql.clj
lein kibit --replace src/umlaut/generators/lacinia.clj

