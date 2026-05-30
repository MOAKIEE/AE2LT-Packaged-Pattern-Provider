---
navigation:
  parent: index.md
  title: Imbuement Chamber
  icon: ars_imbuement_adapter
  position: 40
---

# Imbuement Chamber

**Adapter card:** <ItemLink id="ae2ltpp:ars_imbuement_adapter" />
**Mod:** Ars Nouveau

Automates Imbuement Chamber recipes. Place the provider against the Chamber, with **Arcane Pedestals
right next to it** (within 1 block). The reagent goes into the Chamber, the catalyst items go on the
pedestals, and the result is returned when imbuing finishes.

## ⚠ Catalysts are recycled

Ars does **not** consume the pedestal items for an Imbuement recipe — it only checks that they are
present. So this adapter treats them as **reusable catalysts**: it places them on the pedestals for
the craft, then **pulls them back into your ME network together with the finished product**.

You still need the catalyst items in stock (so they can be placed), but you don't lose them — encode
the pattern's inputs to include both the consumed reagent and the catalyst items, and the catalysts
come straight back.
