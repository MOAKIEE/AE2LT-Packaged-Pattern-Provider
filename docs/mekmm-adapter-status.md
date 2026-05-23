# Mekanism-MoreMachine 大型机器适配层 — 当前状态

## 概述

为 mekmm (Mekanism-MoreMachine) mod 的 6 台大型机器编写了适配层，使封包过载样板供应器能够自动向这些机器分发合成任务。

## 当前状态

| 机器 | 状态 | 输入类型 | 输出类型 |
|------|------|----------|----------|
| 大型电解分离器 | 正常工作 | Fluid | Chemical×2 |
| 大型反质子核合成器 | 已修复代码，待进游戏验证 | Item + Chemical | Item |
| 大型化学灌注器 | 已修复 Chemical capability，待进游戏验证 | Chemical×2 | Chemical |
| 大型旋转冷凝器 | 已修复代码，待进游戏验证 | Chemical 或 Fluid | Fluid 或 Chemical |
| 大型太阳能中子活化器 | 已修复代码，待进游戏验证 | Chemical | Chemical |
| 大型颜料混合器 | 已修复 Chemical capability，待进游戏验证 | Chemical×2 | Chemical |

## 问题分析

只有大型电解分离器此前能正常工作。该机器的输入是 Fluid（通过 NeoForge 原生 FluidHandler capability），不涉及 Mekanism 化学品输入。

其余 5 台机器的输入都涉及 Chemical 类型。说明 **化学品插入（insertion）** 环节存在问题。

2026-05-23 代码侧已定位并修复一个核心问题：`mekanism.common.capabilities.Capabilities.CHEMICAL`
不是 NeoForge `BlockCapability` 本身，而是 Mekanism 的 `MultiTypeCapability`，需要通过 `block()` 取出真正的
block capability 后再调用 `level.getCapability(...)`。该修复仍需进游戏验证 5 台 Chemical 输入机器是否恢复。

随后根据日志 `https://mclo.gs/dmznkEL` 继续定位到 3 个残留问题：

- Mekanism 1.21.1 的机器活动状态方法是 `getActive()`，不是旧代码查找的 `isActive()`。
- 大型旋转冷凝器的输入端口应走底层背面端口；旧代码把输入打到了左右输出端口。
- 大型太阳能中子活化器的输入端口位置正确，但访问侧应为左/右侧；旧代码用背面访问，背面只适合输出。
- 大型反质子核合成器的物品 handler slot 0 是化学品物品槽；旧代码只插 slot 0，导致实际材料输入槽没有机会接收物品。

### 已修复的问题

1. **`MekReflection.doLookup()` 中 CHEMICAL capability 解析错误**
   - 旧代码：直接把 `Capabilities.CHEMICAL` 字段当作 `BlockCapability`
   - 实际情况：Mekanism 1.21.x 中 `Capabilities.CHEMICAL` 是 `MultiTypeCapability`，真正的 block capability 在 `block()`
   - 影响：`getChemicalHandler()` 强转失败并返回 null，所有 Chemical 插入都会返回 0
   - 修复：新增兼容解析逻辑，字段本身是 `BlockCapability` 时直接用；字段是 carrier 时通过 `block()` 取出

2. **`AppmekReflection` 中 ChemicalStack 构造路径已调整**
   - appmek 1.6.3 的 `MekanismKey` 同时提供 `getStack()` 和 `withAmount(long)`
   - 当前代码使用 `key.getStack()` → `ChemicalStack.copyWithAmount(amount)` 构造带数量的 `ChemicalStack`
   - 该路径已核对源码，暂不作为主要故障点

3. **Mekanism active getter 反射方法名不兼容**
   - 旧代码：查找 `TileEntityMekanism.isActive()`
   - 实际情况：Mekanism 1.21.1 源码中是 `getActive()`
   - 影响：日志出现 `Failed to resolve TileEntityMekanism: ... isActive()`，适配层无法可靠判断机器是否正在运行
   - 修复：按 `getActive` → `isActive` 顺序兼容查找

4. **大型旋转冷凝器输入/输出端口混用**
   - 旧代码：Chemical 始终使用左侧 y=1 端口，Fluid 始终使用右侧 y=1 端口
   - 实际情况：输入必须从相对背面底层端口插入；输出才从左右 y=1 端口提取
   - 修复：新增旋转冷凝器 Chemical/Fluid 输入端口和输出端口的独立规格

5. **大型太阳能中子活化器输入访问侧错误**
   - 旧代码：后左端口从背面访问
   - 实际情况：MekMM 对该机器的 Chemical 插入限制为相对 LEFT/RIGHT，背面用于提取
   - 修复：输入端口改为后左位置、左侧访问；输出保持后右位置、背面访问

6. **大型反质子核合成器物品只插 slot 0**
   - 旧代码：`handler.insertItem(0, ...)`
   - 实际情况：slot 0 是 gasInputSlot，真正的物品输入槽在后续槽位
   - 修复：新增跨槽插入逻辑，模拟和执行都会按 handler 暴露的所有槽位依次尝试

### 当前注意事项

大型太阳能中子活化器本体仍然需要能看到天空才能真正处理配方。适配层只能解决输入/输出 capability 访问问题；如果机器在地下或顶部被方块遮挡，样板可以投料但机器不会加工。

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
├── MekCapabilityReflection.java # 纯反射 helper（MultiTypeCapability、方法兼容）
├── AppmekReflection.java       # appmek MekanismKey ↔ ChemicalStack 转换
├── MekPortLayout.java          # 端口位置计算
├── MekPortSpec.java            # 端口相对规格
├── MekPortSpecs.java           # MekMM 机器端口规格
├── MekItemInsertion.java       # 跨槽物品插入 helper
├── LargeNucleosynthesizerAdapter.java
├── LargeChemicalInfuserAdapter.java
├── LargeElectrolyticSeparatorAdapter.java
├── LargeRotaryCondensentratorAdapter.java
├── LargeSolarNeutronActivatorAdapter.java
└── LargePigmentMixerAdapter.java
```

## 下一步

1. 进游戏验证日志中是否不再出现 `Failed to resolve TileEntityMekanism: ... isActive()`
2. 测试大型旋转冷凝器两种模式：Chemical → Fluid、Fluid → Chemical
3. 测试大型太阳能中子活化器时保证顶部可见天空
4. 测试大型反质子核合成器时确认物品材料和 Chemical 都能进入机器
5. 如果仍失败，优先通过日志确认 `getChemicalHandler` 是返回 null（端口/方向问题）还是 `insertChemical`/`insertItem` 返回 0（配方/类型/容量问题）
