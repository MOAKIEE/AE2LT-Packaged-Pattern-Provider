---
navigation:
  parent: index.md
  title: Runic Altar
  icon: botania_runic_altar_adapter
  position: 230
---

# Runic Altar

**Adapter card:** <ItemLink id="ae2ltpp:botania_runic_altar_adapter" />
**Mod:** Botania

Automates Runic Altar recipes. Place the provider against the Runic Altar and encode the recipe as a
pattern. The adapter feeds the inputs into the altar; once it has gathered enough mana the result is
produced and returned (you don't need to right-click with a wand).

## ⚠ Mana & recycled rune catalysts

* The altar needs **mana** from your mana spreaders — keep it supplied.
* Runic Altar recipes have two kinds of runes:
  * **Ingredients** (runes, petals, dusts) that are **consumed**.
  * **Catalyst runes** that are only required to be present and are **returned after the craft**.
* So include both in the pattern's inputs — the consumed ingredients are used up, while the catalyst
  runes come straight back into your ME network.
