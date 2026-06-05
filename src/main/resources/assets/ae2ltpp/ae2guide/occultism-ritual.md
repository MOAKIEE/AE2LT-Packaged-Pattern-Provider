---
navigation:
  parent: index.md
  title: Ritual
  icon: occultism_ritual_packaged_core
  position: 160
item_ids:
- ae2ltpp:occultism_ritual_packaged_core
---

# Occultism Ritual

**Packaged Core:** <ItemLink id="ae2ltpp:occultism_ritual_packaged_core" />
**Mod:** Occultism

Automates Occultism rituals. Place the provider against the **Golden Ritual Bowl** that starts the
ritual. The provider drops the ritual's main item into that bowl and the ingredients onto the surrounding
sacrificial bowls, then lets the ritual run.

## ⚠ Set up the ritual first

* You must have the full ritual ready: the correct **pentacle drawn** and the **sacrificial bowls** placed
  around the Golden Ritual Bowl, exactly as the ritual requires.
* Rituals take in-world time and have visible effects; the provider automates the item placement (and can
  proxy the sacrifice / item-use cost), but the ritual itself still plays out normally.

## Output extraction

When the ritual finishes, output extraction only checks the vertical column above the **Golden Ritual
Bowl**. It looks 1, 2, then 3 blocks above the bowl for a loaded, upside-down **Sacrificial Bowl**, and
pulls the first allowed stack from that bowl's item slot.

The provider does not extract from the Golden Ritual Bowl itself or from the surrounding Sacrificial Bowls
used for ritual ingredients.
