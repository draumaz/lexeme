# Lexeme

An Android Wiktionary reader in Kotlin and Jetpack Compose, styled with Material 3 Expressive.
Type a word to get the full English Wiktionary entry, laid out as cards:

- every language on the page, with a pinned language switcher that follows your scroll position
- senses with numbered shape badges, usage examples, quotations that fold away
- declension, conjugation and other inflection tables drawn as real grids (rowspan and colspan handled)
- stroke-order animations lifted into their own card at the top of the language
- audio pronunciations, IPA, images (line art inverts in dark mode)
- etymology trees drawn left to right as branches, with merging roots kept as real forks
- collapsible translation boxes and term lists as tappable chips; every word link opens in the app

## Build

```sh
./gradlew installDebug        # debug build (xyz.draumaz.lexeme.debug, amber icon), installs alongside release
./gradlew assembleRelease     # R8-minified release, signed with the debug key (no release keystore yet)
./gradlew testDebugUnitTest   # JVM tests for the parser and table grid
```

`LEXEME_SAMPLES=/dir ./gradlew testDebugUnitTest` also parses every `<word>.html` in `/dir` (the
`parse.text` of `action=parse`) and prints an outline of the result, which is handy when
Wiktionary changes its markup.

## Layout

- `data/WiktionaryParser.kt` turns `action=parse` HTML into the plain document model in `data/Model.kt`.
  It walks the DOM generically and only special-cases headword lines, inflection tables, NavFrames,
  audio, etymology trees and stroke order, so anything it doesn't recognise still comes through as text.
- `data/WiktionaryApi.kt` handles fetching (with case-variant retries), opensearch suggestions and random pages.
- `ui/` holds the Compose UI: `LexemeRoot` (page stack, predictive back, floating toolbar),
  `EntryScreen`, `HomeScreen`, `SearchBar`, plus `ui/blocks/` with one renderer per block type.

Content comes from [Wiktionary](https://en.wiktionary.org) under CC BY-SA 4.0.
