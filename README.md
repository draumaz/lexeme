# Lexeme

An Android Wiktionary reader in Kotlin and Jetpack Compose, styled with Material 3 Expressive.
Type a word to get the full English Wiktionary entry, every language on the page, laid out as
cards that bounce into place.

<p align="center">
  <img src="docs/screenshots/home.webp" width="260" alt="Home screen: the Lexeme wordmark, a Surprise me button, suggested words and a scalloped search button">
  &nbsp;
  <img src="docs/screenshots/entry.webp" width="260" alt="The entry for serendipity, with its etymology tree and floating toolbar">
  &nbsp;
  <img src="docs/screenshots/search.webp" width="260" alt="Searching from the home screen: the search bar sits above the keyboard and suggestions open upward">
</p>

## What it shows

<table>
  <tr>
    <td width="300"><img src="docs/screenshots/table.webp" width="280" alt="German declension table for Hund"></td>
    <td>
      <h3>Inflection tables as real grids</h3>
      Declensions and conjugations keep their row and column spans, header cells and footnotes,
      and wide tables scroll sideways. Muted cells (articles in German) stay muted.
    </td>
  </tr>
  <tr>
    <td width="300"><img src="docs/screenshots/strokes.webp" width="280" alt="Stroke order card for 水 with the animated GIF and a frame strip"></td>
    <td>
      <h3>Stroke order up front</h3>
      Stroke-order animations and diagrams are lifted out of wherever they sit on the page into
      their own card at the top of the language. Black-on-transparent line art inverts in dark mode.
    </td>
  </tr>
  <tr>
    <td width="300"><img src="docs/screenshots/tree.webp" width="280" alt="Etymology tree for butterfly, with roots merging"></td>
    <td>
      <h3>Etymology trees that branch</h3>
      Ancestors run left to right on leaf-shaped cards joined by twigs, merging roots curve into
      one another, and the tree grows in when it first appears. The page's own word blooms at the tip.
    </td>
  </tr>
  <tr>
    <td width="300"><img src="docs/screenshots/picker.webp" width="280" alt="The language picker open on 水, listing languages and their parts of speech"></td>
    <td>
      <h3>Every language, one tap away</h3>
      A pinned pill names the language you're reading and how far down the page you are. Tap it
      for a list of every language with its parts of speech, and jump straight there.
    </td>
  </tr>
</table>

Also on the page:
- senses with numbered shape badges, usage examples, and quotations that fold away
- audio pronunciations, IPA, and images
- collapsible translation boxes, and term lists as tappable chips
- word links that open inside the app, including links to a language section of another page

## Getting around

- Search from the scalloped button at the bottom of the home screen. The bar rides on top of the
  keyboard, suggestions open upward, and the row Enter will open is highlighted, so you can
  just type and press search.
- On an entry, a floating toolbar has home, a random word, back to top, share and "Open on
  Wiktionary". It tucks into its search button while you read down and returns when you scroll up.
- Back, including the predictive back gesture, shrinks the page under your finger and returns
  you where you left off.
- "Look up in Lexeme" appears in other apps' text selection menus, and en.wiktionary.org links open in the app.

## Settings and light mode

<p align="center">
  <img src="docs/screenshots/entry_light.webp" width="260" alt="The entry for chat in light mode">
  &nbsp;
  <img src="docs/screenshots/settings_light.webp" width="260" alt="Settings in light mode">
  &nbsp;
  <img src="docs/screenshots/settings.webp" width="260" alt="Settings in dark mode">
</p>

The gear in the corner of the home screen opens settings:
- **Material You colors:** turn the wallpaper-based ("Monet") colours on or off. When off, Lexeme uses its own ink-blue palette.
- **Theme:** system, light or dark.
- **Clear recent lookups.**
- **About:** links to the author and this repository.

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
- `data/SettingsStore.kt` and `data/HistoryStore.kt` keep preferences and recent lookups in SharedPreferences.
- `ui/` holds the Compose UI: `LexemeRoot` (page stack, predictive back, floating toolbar, search bars),
  `EntryScreen`, `HomeScreen`, `SearchBar`, `SettingsScreen`, plus `ui/blocks/` with one renderer per block type.

## Credits

Content comes from [Wiktionary](https://en.wiktionary.org) under CC BY-SA 4.0.
The interface is set in [Google Sans Flex](https://fonts.google.com/specimen/Google+Sans+Flex) and the
wordmark in [Noto Serif](https://fonts.google.com/noto/specimen/Noto+Serif), both under the SIL Open Font
License (see `third_party/fonts/`).
