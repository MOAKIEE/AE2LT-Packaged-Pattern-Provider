---
navigation:
  parent: index.md
  title: 灌注室
  icon: ars_imbuement_packaged_core
  position: 40
item_ids:
- ae2ltpp:ars_imbuement_packaged_core
---

# 灌注室

**所需封包核心：** <ItemLink id="ae2ltpp:ars_imbuement_packaged_core" />
**来源模组：** Ars Nouveau（新生魔艺）

自动完成灌注室配方。把供应器贴着灌注室放置，并在它**紧邻（1 格内）放上奥术基座**。
核心材料放进灌注室、催化物放到基座上，灌注完成后取回产物。

## ⚠ 催化剂会被回收

灌注配方中，Ars **并不会消耗**基座上的物品，它只检查这些物品是否在场。因此本供应器把它们当作
**可复用的催化剂**：合成时把它们摆到基座上，完成后再把它们连同产物**一起退回你的 ME 网络**。

你仍然需要库存里有这些催化物（才能被摆上去），但不会损失它们——把样板的输入同时写上「会被消耗的核心材料」
和「催化物」，催化物会原样退回。

开始前灌注室和 1 格内所有奥术基座都必须为空；任意基座里有物品时不会开始。
