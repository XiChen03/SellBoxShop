<div align="center">

# SellBox - 出货箱

**一个功能丰富的 Minecraft Forge 1.20.1 经济与交易模组**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.4.20-orange)](https://files.minecraftforge.net/)
[![Java](https://img.shields.io/badge/Java-17-blue)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-red)](LICENSE.txt)

</div>

---

## 功能概览

### 出货箱（Sell Box）

- 放置后右键打开 GUI，放入已定价的物品即可**自动出售**换取金钱
- 最后一个槽位放入**钱袋**，收益自动存入钱袋
- 支持**离线收益**，重新上线后通过收据领取
- 支持**批量定价管理**（GUI / 命令），按名称、ID、首字母排序搜索
- 支持**NBT 精确定价**（同物品不同属性不同价格）
- Shift + 左键可回收出货箱，支持**所有者保护**

### 管理员商店（Admin Shop）

- 服务端 GUI 商店编辑器，管理员可自由**添加 / 编辑 / 删除**商品
- 支持**多分类**管理，分类支持排序、重命名
- 交易类型：**购买 / 出售 / 以物易物 / 指令 / 经验**
- 客户端快捷键打开商店界面，支持搜索和数量选择
- **JEI 集成**：在 JEI 中查看商店物品的价格与交易信息

### 玩家市场（Player Market）

- 玩家放置**市场方块**后可自定义商店名，其他玩家浏览购买
- **备货台**（Stock Block）用于存放商品库存，需放置在同一区块内
- 支持**以物易物**和**金钱购买**两种交易模式
- 库存自动同步，区块加载范围内可正常交易

### 经济系统

- **内置经济**：玩家余额管理，支持排行榜查询
- **SDMShop 桥接**：自动对接 SDMShop 货币系统（可选）
- **钞票**：Shift + 右键查看余额，可装备到 Curios 饰品栏显示 HUD
- **金币**：Shift + 右键转换为金钱，右键查看排行榜
- **钱袋**：存储金币的容器，支持在出货箱/市场中自动填充

---

## 方块与物品

| 物品 | 合成配方 | 说明 |
|------|----------|------|
| 出货箱 | `8 钻石 + 1 箱子` | 放入物品自动出售 |
| 市场 | `8 钻石 + 1 钱袋` | 玩家交易市场 |
| 备货台 | `8 钻石 + 4 箱子` | 市场库存容器 |
| 金币 | - | 货币单位 |
| 钞票 | - | 查看余额 |
| 钱袋 | - | 存储金币 |

---

## 指令

| 指令 | 说明 | 权限 |
|------|------|------|
| `/sellbox setprice <金额>` | 设置手持物品价格 | OP |
| `/sellbox setpricenbt <金额>` | 按 NBT 精确设置价格 | OP |
| `/sellbox removeprice` | 移除手持物品价格 | OP |
| `/sellbox handprice` | 查看手持物品价格 | OP |
| `/sellbox reload` | 重载定价配置 | OP |
| `/sellbox pricing` | 打开 GUI 定价管理 | OP（创造模式） |
| `/sellbox shopedit` | 切换商店编辑模式 | OP |
| `/sellbox sell` | 直接出售手持物品 | 所有玩家 |
| `/sellbox balance` | 查看余额 | 所有玩家 |
| `/sellbox leaderboard` | 查看排行榜 | 所有玩家 |

---

## 快捷键

| 按键 | 功能 |
|------|------|
| **O**（默认） | 打开管理员商店 |
| Shift + 左键出货箱 | 回收出货箱 |
| Shift + 左键市场/备货台 | 回收方块 |
| Shift + 右键钞票 | 查看余额 |
| Shift + 右键金币 | 转换为金钱 |
| 右键金币 | 查看排行榜 |

---

## 依赖模组

| 模组 | 必选 | 说明 |
|------|:----:|------|
| [Forge](https://files.minecraftforge.net/) | ✅ | Minecraft Forge 47.4.20+ |
| [Curios](https://www.curseforge.com/minecraft/mc-mods/curios) | ✗ | 饰品栏显示余额 |
| [SDMShop](https://www.curseforge.com/minecraft/mc-mods/sdmshop) | ✗ | 经济系统桥接 |
| [FTB Quests](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge) | ✗ | 任务联动 |
| [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) | ✗ | 商店配方查看（仅客户端） |

---

## 安装

1. 安装 [Minecraft Forge 1.20.1](https://files.minecraftforge.net/)
2. 下载最新的 `SellBox-SDMShop-*-Forge-1.20.1.jar`
3. 将 JAR 文件放入 `.minecraft/mods/` 目录
4. 启动游戏

---

## 构建

需要 JDK 17+。

```bash
# Windows
.\gradlew.bat reobfJar

# Linux / macOS
./gradlew reobfJar
```

构建产物在 `build/libs/` 目录。

---

## 项目结构

```
src/main/java/com/sellbox/mod/
├── api/                 # 经济 API 接口
├── block/               # 出货箱方块 & 方块实体
├── client/              # 客户端 HUD 叠加层
├── command/             # 指令注册
├── config/              # 模组配置
├── gui/                 # 出货箱 GUI（菜单 & 界面）
├── integration/         # 第三方模组集成
│   └── jei/            # JEI 配方集成
├── item/                # 物品（金币/钞票/钱袋）
├── mixin/               # Mixin 注入
├── network/             # 网络包处理
├── playershop/          # 玩家市场系统
├── registry/            # 方块/物品/菜单/音效注册
├── shop/                # 管理员商店系统
├── util/                # 工具类（拼音/排序/字体）
├── SellBoxEvents.java   # 事件监听
├── SellBoxMod.java      # 模组主入口
└── SellBoxModClient.java # 客户端初始化

src/main/resources/
├── META-INF/mods.toml   # 模组元数据
├── assets/sellbox/      # 材质/模型/语言/音效
└── data/sellbox/        # 配方/战利品表
```

---

## 许可证

本项目为闭源项目，保留所有权利。详见 [LICENSE.txt](LICENSE.txt)。

## 作者

**曦辰**（XiChen03）
