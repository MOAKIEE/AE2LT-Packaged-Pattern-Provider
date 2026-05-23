# Mekanism-MoreMachine 大型机器适配层 — 当前状态

## 概述

为 mekmm (Mekanism-MoreMachine) mod 的 6 台大型机器编写了适配层，使封包过载样板供应器能够自动向这些机器分发合成任务。

## 当前状态

| 机器 | 状态 | 输入类型 | 输出类型 |
|------|------|----------|----------|
| 大型电解分离器 | 正常工作 | Fluid | Chemical×2 |
| 大型反质子核合成器 | 不工作 | Item + Chemical | Item |
| 大型化学灌注器 | 不工作 | Chemical×2 | Chemical |
| 大型旋转冷凝器 | 不工作 | Chemical 或 Fluid | Fluid 或 Chemical |
| 大型太阳能中子活化器 | 不工作 | Chemical | Chemical |
| 大型颜料混合器 | 不工作 | Chemical×2 | Chemical |

## 问题分析

只有大型电解分离器能正常工作。该机器的输入是 Fluid（通过 NeoForge 原生 FluidHandler capability），不涉及 Mekanism 化学品输入。

其余 5 台机器的输入都涉及 Chemical 类型。说明 **化学品插入（insertion）** 环节存在问题。

### 已修复的问题

1. **`MekReflection.doLookup()` 中 `.block()` 调用错误**
   - 旧代码：`Capabilities.CHEMICAL` → `.block()` → `BlockCapability`
   - 实际情况：NeoForge 1.21.1 中 `Capabilities.CHEMICAL` 已经直接是 `BlockCapability`，无需 `.block()`
   - 影响：`NoSuchMethodException` 导致整个 doLookup 失败（单一 try 块），所有化学品相关反射全部为 null
   - 修复：移除 `.block()` 调用，拆分为独立 try 块

2. **`AppmekReflection` 中 `withAmount(long)` 方法不存在**
   - 旧代码：尝试反射 `MekanismKey.withAmount(long)`
   - 实际情况：`MekanismKey` 没有 `withAmount` 方法
   - 修复：改为 `key.getStack()` → `ChemicalStack.copyWithAmount(amount)`

### 当前疑似问题

电解分离器的输出提取（Chemical extraction）能正常工作，说明：
- `chemicalBlockCapability` 已正确解析
- `getChemicalHandler()` 能获取到 handler
- `extractChemical()` / `getChemicalInTank()` 等方法正常

但化学品输入仍然失败，可能原因：

1. **`MekanismKey.getStack()` 方法可能不存在**
   - appmek 1.6.3 中 `MekanismKey` 可能没有 `getStack()` 方法
   - 可能的正确方法名：`toStack(long)` 或需要通过 `getChemical()` + `new ChemicalStack(chemical, amount)` 构造
   - 如果 `getStack()` 查找失败，`toChemicalStackWithAmount()` 始终返回 null → inserter 返回 0

2. **端口位置计算可能不正确**
   - 化学品输入端口的坐标或访问方向可能有误
   - 电解分离器的流体输入端口正确，但其他机器的化学品端口未验证

## 调试方法

日志中搜索 `[ae2ltpp]` 前缀。关键日志点：

- `MekReflection` 初始化：
  - `Resolved CHEMICAL capability: ...` — 确认 capability 解析成功
  - `Resolved all chemical handler methods` — 确认 handler API 解析成功
  - `Failed to resolve ...` — 显示具体哪个反射失败

- `AppmekReflection` 初始化：
  - `Resolved MekanismKey class and getStack()` — 确认 MekanismKey 解析成功
  - `Failed to resolve MekanismKey: ...` — 如果 getStack() 不存在会在这里报错

- 运行时：
  - `getChemicalHandler: chemicalBlockCapability is null` — capability 未解析
  - `getChemicalHandler: no handler at X side Y` — 端口位置或方向错误
  - `Nucleosynthesizer plan: ...` — 核合成器 plan() 的具体失败点

启用 DEBUG 级别日志：在 `log4j2.xml` 中添加：
```xml
<Logger name="com.moakiee.ae2lt" level="DEBUG" />
```

## 文件结构

```
src/main/java/com/moakiee/ae2lt/packaged/logic/multiblock/mekmm/
├── MekReflection.java          # Mekanism 反射工具（capability、状态检查）
├── AppmekReflection.java       # appmek MekanismKey ↔ ChemicalStack 转换
├── MekPortLayout.java          # 端口位置计算
├── LargeNucleosynthesizerAdapter.java
├── LargeChemicalInfuserAdapter.java
├── LargeElectrolyticSeparatorAdapter.java
├── LargeRotaryCondensentratorAdapter.java
├── LargeSolarNeutronActivatorAdapter.java
└── LargePigmentMixerAdapter.java
```

## 下一步

1. 启用 DEBUG 日志，确认 `AppmekReflection` 初始化是否成功（`getStack()` 是否存在）
2. 如果 `getStack()` 不存在，需要找到 appmek 1.6.3 中 `MekanismKey` 创建 `ChemicalStack` 的正确方法
3. 确认化学品输入端口位置是否正确（通过日志观察 `getChemicalHandler` 是否返回 null）
