# SellBoxShop 源码索引

> 生成日期：2026-10-26。依据：对 `forge-1.20.1`（95 个类 / 19,607 行）与 `neoforge-1.21.1`（106 个类 / 20,123 行）全部 Java 源码的静态通读，**未编译、未运行**。
> 用途：定位「某功能在哪个类 / 这个类干什么 / 两端差在哪」。先看这里，再跳具体文件。

---

## 1. 速览

| 项目 | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| 源码根 | `forge-1.20.1/src/main/java/com/sellbox/mod` | `neoforge-1.21.1/src/main/java/com/sellbox/mod` |
| 类数 / 行数 | 95 / 19,607 | 106 / 20,123 |
| 加载器 | Forge 47.4.20 | NeoForge 21.1.220 |
| MC / Java / Gradle | 1.20.1 / 17 / 8.8 | 1.21.1 / 21 / 9.2.1 |
| mod_id / 名称 / 版本 | `sellbox` / SellBoxShop / 1.5.3 | 同 |
| 作者 | 曦辰（gradle.properties） | Xichen（`neoforge.mods.toml` 硬编码） |
| 产物 | `build/libs/SellBoxShop-1.5.3-Forge-1.20.1.jar` | `build/libs/SellBoxShop-1.5.3-NeoForge-1.21.1.jar` |
| 网络模型 | 单 `SimpleChannel` + 26 个内嵌 record | 一包一类 `CustomPacketPayload` + 注册表，共 24 个 |

补充事实：

- **这个目录不是 git 仓库**（`git status` → not a git repository），没有提交历史；`README.md` 记载原始工程备份在 `C:\AI\Forge-1.20.1-SellBox`、`C:\AI\NeoForge-1.21.1-SellBox`，本目录是「源码副本，修改不同步」。
- `build/` 已存在，最后一次构建产物时间 2026-10-25 11:06（两端 jar 约 1.5 MB）。
- 两端 `build.gradle` 都在构建结束后把 jar 复制到 `C:\Users\Administrator\Desktop\坚果云`（绝对路径写死，换机器/换用户名会失败）。

---

## 2. 目录与构建

```
sellbox/
├── README.md
├── docs/PROJECT_INDEX.md          # 本文件
├── forge-1.20.1/                  # Java 17
│   ├── build.gradle  gradle.properties  settings.gradle  gradlew(.bat)
│   ├── libs/curios-forge-1.20.1-api.jar   # 本地依赖（compileOnly）
│   └── src/main/{java,resources}
└── neoforge-1.21.1/               # Java 21
    ├── build.gradle  gradle.properties  settings.gradle  gradlew(.bat)
    ├── run/                       # 开发运行目录（已有 junit 缓存）
    └── src/main/{java,resources}
```

构建（各自目录内）：

```bash
cd forge-1.20.1    && .\gradlew.bat build     # 产出 + 复制到桌面坚果云
cd neoforge-1.21.1 && .\gradlew.bat build
```

依赖清单：

| 依赖 | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| JEI | `mezz.jei:jei-1.20.1-forge:15.20.0.134`（compileOnly + runtimeOnly，cursemaven maven） | `mezz.jei:jei-1.21.1-*:19.27.0.336`（common-api + neoforge-api compileOnly，完整包走 localRuntime） |
| Curios | `libs/curios-forge-1.20.1-api.jar`（compileOnly，本地 jar） | `top.theillusivec4.curios:curios-neoforge:9.5.1+1.21.1`（api compileOnly + runtimeOnly） |
| FTB Quests | 仅 `mods.toml` 可选依赖 `[2001.4,)`，代码纯反射 | 仅 `neoforge.mods.toml` 可选依赖，纯反射 |
| SDMShop | `mods.toml` 可选 `sdmshop [6.11,)`，`SDMShopIntegration` 反射 | 无（改为 VSS / `viscript_shop`） |
| VSS（viScriptShop） | 无 | `neoforge.mods.toml` optional `viscript_shop`，`VSSIntegration` 反射 |
| NEZNAMY TAB | `TabIntegration` 反射注册占位符 | 同 |

---

## 3. 包的职责（两端结构一致，共 16 个包）

| 包 | 职责 | 代表类 |
|---|---|---|
| `com.sellbox.mod`（根） | 模组入口、生命周期、服务端 tick 事件、客户端注册 | `SellBoxMod`、`SellBoxEvents`、`SellBoxModClient` |
| `api` | 对外扩展点：经济实现注入 + 文本占位符 | `IEconomy`、`SellBoxEconomyAPI`、`SellBoxPlaceholders` |
| `block` | 出货箱方块与 BlockEntity（自动收购结算） | `SellBoxBlock`、`SellBoxBlockEntity` |
| `client` | 纯客户端渲染/按键（不得被公共类引用） | `BalanceOverlay`、`ClientBlockShortcuts` |
| `command` | `/sellbox` 指令树 | `ModCommands` |
| `config` | 全局配置（ForgeConfigSpec / properties）与收购价、离线待发放持久化 | `SellBoxConfig` |
| `gui` | 出货箱容器菜单/界面 + 全局主题与定价界面 | `SellBoxMenu`、`SellBoxScreen`、`GuiTheme`、`PricingScreen` |
| `integration` | 可选模组反射桥接 | `CuriosIntegration`、`FTBQuestsIntegration`、`TabIntegration` |
| `integration/jei` | 把商店条目呈现为 JEI 配方 | `ShopJeiPlugin`、`ShopTradeCategory`、`JeiBridge` |
| `item` | 货币类物品与方块物品 | `MoneyItem`、`GoldCoinItem`、`CoinBagItem` |
| `mixin` | 槽位堆叠上限修补 | `SlotMixin` |
| `network` | 网络层（Forge：`ModNetwork`；NeoForge：`ModPayloads` + 各 Payload） | 见 §6 |
| `playershop` | 玩家商店：市场/备货台方块、容器、界面、数据 | `PlayerShopRegistry`、`PlayerShopConfig`、`PlayerShopScreen` |
| `registry` | DeferredRegister 汇总 | `ModBlocks`、`ModItems`、`ModMenuTypes` |
| `shop` | 管理员商店：条目模型、配置、交易、限量、经济、全部编辑界面 | `ShopEntry`、`ShopConfig`、`ShopScreen`、`SellBoxEconomy` |
| `util` | 拼音搜索、排序、字体自适应、物品图标/取样、酒类变体 | `PinyinUtil`、`ItemSortUtil`、`ItemVariantUtil` |

---

## 4. 类清单

标注：无标记 = 两端都有（实现细节可能不同）；**【F】** = 仅 Forge；**【N】** = 仅 NeoForge。

### 4.1 根包

- **SellBoxMod** — `@Mod` 主入口。构造器注册 6 组 DeferredRegister + `PlayerShopRegistry.init` + 网络层 + 事件总线；`onServerStarting` 决定数据目录（`globalPricing`/`globalShop` 为真用 `config/SellBox/`，否则用存档内 `config/SellBox/`）并初始化各模块；`onServerStopped` 统一 `flushSave` 所有脏数据。NeoForge 侧额外在此用匿名 `IEconomy` 包一层 `SellBoxEconomy`（因为 1.21.1 的 `SellBoxEconomy` 不再 implements 接口）。
- **SellBoxEvents** — 唯一的 `@EventBusSubscriber`：Shift+左键让归属者自毁方块；`LevelTickEvent.Pre` 里依次做「收据刷写 → 到点自动出货 → 每秒同步余额 → 每 600 tick 存盘 → 定期刷新玩家商店库存缓存」；负责离线收益二次登录补发、Curios 钱币归还重试。`refreshStockCache`/`getCachedStocks` 提供库存计数缓存。
- **SellBoxModClient** — 客户端注册：K 键（`key.sellbox.shop`）、三个 `MenuScreen`（`SELL_BOX`→`SellBoxScreen`、`MARKET_MENU`→`MarketScreen`、`STOCK_MENU`→`StockScreen`）、`InventoryShopButton`、HUD 余额；内嵌 `Tooltips` 静态类给物品加价格 tooltip（NBT 精确价优先）。

### 4.2 `api`

- **IEconomy** — 经济抽象：`getBalance/hasBalance/addBalance/deductBalance/setBalance/formatMoney/sendBalanceSync/isAvailable`（Forge 版签名略有差异）。
- **SellBoxEconomyAPI** — `volatile IEconomy` 单例持有者，`get()/set()`；第三方模组替换经济的入口。
- **SellBoxPlaceholders** — `%key%` 正则替换；内置 `%sellboxmoney%`/`%sellbox_money%`；未识别占位符原样保留。
- **SellBoxPlaceholderEvent** — 占位符查询事件（Forge 可取消式 `setValue/hasValue`；NeoForge 发布到 `NeoForge.EVENT_BUS`）。

### 4.3 `block`

- **SellBoxBlock** — 出货箱方块：`FACING` 朝向、`use` 打开 `SellBoxMenu`、`onDestroyedByPlayer` 校验归属。
- **SellBoxBlockEntity**（约 790 行，核心）— 54 格容器：槽 52 只收成书（账本/收据）、槽 53 只收钱袋；`sellItems()` 按 `SellBoxConfig` 价格整箱结算，产出 `SoldEntry(名称,数量,总值,物品ID)`；`flushPendingReceipts` 把同 tick 多箱结果合成一份收据（Title + 聊天 + 写入成书，上限 100 页）；`tryCollectWithCoinBag` 支持钱袋取款；`LOADED_BOXES` 自维护跨维度已加载表（不依赖区块加载器）；`MAX_SAVEABLE_STACK=99` 防存档 codec 失败。

### 4.4 `client`

- **BalanceOverlay** — 左上角余额 HUD：仅当 Curios 装备了货币时显示，数据取网络层 `clientBalance`。（NeoForge 走 `RegisterGuiLayersEvent`，Forge 走 `RenderGuiOverlayEvent.Post`。）
- **ClientBlockShortcuts** — 创造 + OP2 时 Shift+右键出货箱直接开 `PricingScreen`；单独成类是为了不让 `net.minecraft.client` 进服务端加载路径。
- **InventoryShopButton** — **【F，NeoForge 挪到 `shop` 包】**：物品栏界面副手槽上方（77,62，16px）叠加商店图标，点击等同按 K。

### 4.5 `command`

- **ModCommands**（约 610 行）— `/sellbox` 全部子命令：`help`（分页 5）、`money`、`sell`、`handset/handsetnbt/handremove/handremovenbt/delnbt/handprice`（收购价）、`setselltime/getselltime`、`editpricing`、`adminshop open|edit`、`playershop edit`、`blacklist add|remove|list`、`shoplist`、`leaderboard`、`eco give|set`、`reload`、`SDMShop`。NeoForge 侧用 `HELP_DATA` 表驱动（含权限等级与 `""/shop/vss` 可见性）。

### 4.6 `config`

- **SellBoxConfig**（约 500 行）— 全局配置 + 收购价内存表（`PRICES: Map<Item,Integer>`、`PRICES_NBT: Map<String,Integer>`，键 `itemId|SNBT`）+ 离线待发放 `PENDING: Map<UUID,Integer>`。Forge 用 `ForgeConfigSpec`（`sellTime` 5–24000、`useSdmCurrency`/`useVssCurrency`、`globalPricing`、`globalShop`）；NeoForge 自解析 `sellbox-common.properties`。持久化见 §5.4。

### 4.7 `gui`

- **SellBoxMenu** — 出货箱容器菜单：54 格（52 只收成书、53 只收钱袋），`quickMoveStack` 双向搬运。
- **SellBoxScreen** — `AbstractContainerScreen<SellBoxMenu>`，贴原版 `generic_54.png`；创造模式标题上方挂「定价管理」按钮。
- **PricingScreen**（约 540–570 行）— 收购价管理：物品列表 + NBT 精确价分区 + 搜索/排序/筛选（`FilterMode{ALL,PRICED,UNPRICED,NBT}`）+ 多选，提交走价格包。
- **GuiTheme** — 主题单例：约 60 个 ARGB `int` 字段（面板/按钮/分类/网格/文字/槽位/输入框/滚动条/右键菜单/余额）+ `registry`（`default/modern/light/green/purple/orange`）+ `ThemeFactory`；`init(configDir)` 读 `config/SellBox/gui_theme.json`（内容仅 `{"theme":name}`），`setByName` 即时写盘。**注意**：各界面构造时 `GuiTheme.get()` 取快照，运行中切主题不影响已打开的界面。
- **ModernTheme** — 「现代」深蓝灰半透明配色（`extends GuiTheme`）。
- **ThemePresets** — `dark()/light()/green()/purple()/orange()` 五套静态配色工厂。
- **ThemeSwitchScreen** — 主题切换界面（列出 registry，`isPauseScreen`）。**当前无任何实例化点**（见 §9）。

### 4.8 `integration`

- **CuriosIntegration** — Curios 反射桥：注册货币饰品、`createCurioProvider`、装备/查询/登录归还（`restoreMoneyOnLogin`）。
- **TabIntegration** — NEZNAMY TAB 占位符注册/注销（`%sellboxmoney%`）。
- **FTBQuestsIntegration**（约 310 行）— FTB Quests 只读查询：章节/任务列表、`isQuestCompleted`、`getQuestNameById`；注释记录了 2001.x/2101.x 的 `getRawTitle`/`TranslationManager` 兼容坑。
- **SDMShopIntegration** — **【F】** SDMShop 经济反射桥（`getMoney/addMoney/removeMoney/setMoney/sendLeaderboard`），启动时按多个类名探测。
- **VSSIntegration** — **【N】** viScriptShop 经济桥，1.21.1 取代 SDM。
- **JEIIntegration** — **【F】** JEI 探测与配方推送（`init/onShopEntriesUpdated/findRelatedEntries`）。

### 4.9 `integration/jei`

- **ShopJeiPlugin** — `IModPlugin`（UID `sellbox:shop_jei_plugin`）：注册 `shop_trade` 配方类型与分类，`setCategoryRecipes` 静态灌数据；NeoForge 侧由 `ShopScreen` 打开时经 `JeiBridge.updateShopRecipes` 推送，Forge 侧登录后用 `SyncAllShopEntriesPacket` 下发全量。
- **ShopTradeCategory** — `AbstractRecipeCategory<ShopTradeRecipe>`（116×68）：画输入/输出槽、箭头、价格。
- **ShopTradeRecipe** — 配方 record（`inputs/outputs/tradeLabel/detail/category/iconOnly`）。
- **JeiBridge** — 对 `ShopJeiPlugin` 的反射软包装，无 JEI 时静默降级（避免类加载失败）。

### 4.10 `item`

- **MoneyItem** — 纸币：可戴头盔栏或 Curios 饰品栏显示余额，Shift+右键查余额（200 tick CD）。
- **GoldCoinItem** — 金币：Shift+右键把手中金币按数量入账；右键看排行榜。
- **CoinBagItem** — 钱袋：容量 `MAX_CAPACITY=10_000_000`，金额存 `CUSTOM_DATA.coins`（Forge 为 NBT `coins`）；重写 `overrideStackedOnOther/overrideOtherStackedOnMe` 支持拖入取出。
- **CuriosMoneyItem** — **【F】** `extends MoneyItem implements ICurioItem`，Forge 侧让饰品栏直接持有货币（NeoForge 并入 `MoneyItem`）。
- **SellBoxBlockItem** — 出货箱 BlockItem：放置时写 owner，tooltip 说明容量/账本。

### 4.11 `mixin`

- **SlotMixin** — 唯一 mixin：`@Inject(method="getMaxStackSize", at=HEAD, cancellable)` 后返回 `stack.getMaxStackSize()`，把「槽位上限 ∩ 物品上限」改成「物品自身上限」，让 54 格容器能收下 >64 堆叠。
  - **注册差异**：Forge `sellbox.mixins.json` 的 `mixins` 含 `SlotMixin` 且声明 `refmap`；**NeoForge 的 `sellbox.mixins.json` 里 `mixins` 是空数组**，`SlotMixin.java` 实际未被加载（见 §9）。

### 4.12 `network`

- **ModNetwork** — **【F】** 1,867 行巨类：`PROTOCOL_VERSION="2"`、`CHANNEL=NetworkRegistry.newSimpleChannel(sellbox:main)`；26 个内嵌 record、客户端缓存字段、批量下发助手，并实现服务端 `executeTrade`（真正的交易逻辑）。清单见 §6.1。
- **ClientPacketHandler** — **【F】** 客户端处理集中处：关屏 + `DIE` 音效、`ActionMessagePacket` + `GOOD` 音效、`ShopDataPacket`→`ShopScreen.updateData`、JEI 全量条目推送等。清单见 §6.1。
- **ModPayloads** — **【N】** 网络中枢：`PayloadRegistrar.versioned("1.0.0")` 注册 24 个 Payload，持有 `clientPrices/clientBalance/clientConfigSynced/clientVssEnabled/clientShopOverride/clientEditMode/clientAdminEdit` 与 `clientHandlers`；内嵌 `CloseScreenPayload`、`SyncBalancePayload`、`RequestBalancePayload`、`SyncAllPricesPayload` 及 `sendBalanceToPlayer/sendCloseScreen`。
- **ModClientPayloads** — **【N】** 客户端处理：把下行包绑到 `ShopScreen.updateData` / `PlayerShopScreen.updateData` / `QuestPickerScreen` / `PricingScreen` / 开关界面 + 音效。
- **PricingPayload** — **【N】** 价格包容器：`OpenPricingPayload`、`SetPricesPayload`、`SetNbtPricesPayload`、`DeleteNbtPricesPayload`。

### 4.13 `playershop`

- **PlayerShopRegistry** — 玩家商店独立注册组：`market`/`stock` 方块、BE、物品、`MARKET_MENU/STOCK_MENU/PLAYER_SHOP_MENU`、两个 `IItemHandler` capability、加进创造标签页。
- **MarketBlock** — 市场方块：右键发「打开玩家商店界面」，`onRemove` 掉落内容物。
- **MarketBlockEntity**（`WorldlyContainer`，54 格）— 收货款 `depositEarnedItem`、写收据 `writeReceipt`、按面限制自动化插入；`MARKET_POSITIONS` 按 owner UUID 记坐标，交易时定位。
- **StockBlock / StockBlockEntity** — 备货台：54 格纯存储，玩家商店取货来源；记录 owner。
- **MarketMenu / StockMenu** — 对应容器菜单（市场 52/53 槽有限制）。
- **MarketScreen / StockScreen** — 对应容器界面。
- **MarketBlockItem / StockBlockItem** — 方块物品（放置写 owner + tooltip）。
- **PlayerShopMenu** — 菜单壳，只持 `marketPos`；`handleTrade` 已 `@Deprecated` 且无调用者（no-op 死代码）。
- **PlayerShopScreen**（约 1,100 行）— 玩家商店主界面：左侧全服店铺列表（含离线/未加载标记）、右侧商品网格、搜索、改店名、上架/复制/删除。
- **ModernPlayerShopScreen** — **【N】** 现代风格玩家商店，350 行，**无调用点**。
- **PlayerTradeConfirmScreen** — 购买确认（数量/总价/限购提示）。
- **PlayerShopItemPickerScreen**（约 1,100–1,200 行）— 上架选物品：拼音/首字母搜索、酒类变体、从背包取样。
- **PlayerShopConfig** — 玩家商店数据：`PlayerShopInfo(shopName, items)`、`ShopStats(totalEarnings, totalItemsSold)`；`sellbox_playershops.json` + `sellbox_shopstats.json`；`addItem/removeItem/updateItem/moveItem*`、`addEarnings`、`flushSave`。
- **PlayerShopBlacklist** — **【F】** 玩家商店黑名单（`Set<ResourceLocation>`，`sellbox_blacklist.json`），在命令与编辑处理里生效。NeoForge 侧没有这个类，黑名单并入 `PlayerShopConfig`（`sellbox_blacklist.txt`）。
- **PlayerShopOpenHandler** — **【F】** 服务端组包：汇总全部店铺 + 库存缓存 + 在线店主名 → `PlayerShopDataPacket`。
- **PlayerShopEditHandler** — **【F】** 处理玩家商店编辑包：黑名单校验、OP2 + `adminEdit` 才允许改他人店铺。
- `playershop/network` **【N】** — 6 个 Payload，见 §6.2。

### 4.14 `registry`

- **ModBlocks** — `sell_box`（Forge `strength(-1,3600000)` 不可破坏）。
- **ModItems** — `sell_box`、`gold_coin`、`money`、`coin_bag`。
- **ModBlockEntities** — `sell_box` BE 类型。
- **ModMenuTypes** — `SELL_BOX`、`SHOP`（NeoForge 侧用 `IMenuTypeExtension.create`）。
- **ModSounds / ModSoundEvents** — 两个 UI 音效 `good`、`die`（**1.21.1 改名为 `ModSoundEvents`**）。
- **ModCreativeTabs** — `sellbox_tab`；`addToSellBoxTab(...)`/`EXTRA_ITEMS` 供玩家商店方块追加。

### 4.15 `shop`

- **ShopEntry** — 条目模型 record。Forge 18 个分量、NeoForge 19 个（`nbt` 从 `CompoundTag` 变 `String`（组件补丁 JSON），新增 `barterGoodsStrict`）；`TradeType{BUY,SELL}`、`entryType` ∈ 物品/命令/经验/以物易物；工厂 `item/barter/command/xp`、`createStack`、`matches/matchesOffer`、`getDisplayName`。
- **ShopConfig**（约 295 行）— 商店持久化：`LinkedHashMap<分类名, List<ShopEntry>>` + `editMode/dirty`；分类与条目 CRUD、上下移、复制；手写字段映射（`trade_type/entry_type/extra_data/...`），空文件自动建默认商店。
- **TradeLimitTracker** — 交易限量：key = `分类\u0001itemId\u0001tradeType\u0001price\u0001entryType`（改价即视为新条目，额度归零，注释说明是刻意设计）；`GLOBAL_USED`/`PLAYER_USED` 双表；`remaining/record/isExhausted/snapshotUsed`；`sellbox_trade_limits.json`。
- **SellBoxEconomy** — 内置经济：`ConcurrentHashMap<UUID,Integer>` + `sellbox_economy.dat`；SDM/VSS 模式下转发外部经济；排行榜分页（8/页，可点击翻页）。**Forge `implements IEconomy`，NeoForge 不再实现接口**（由 `SellBoxMod` 匿名适配）。
- **TradeHandler** — **【F】** 静态 `executeTrade(ServerPlayer, ShopEntry, int)`，command/xp/barter/BUY/SELL 分支——**无任何调用者**（真正实现是 `ModNetwork.executeTrade`）。
- **ShopEditHandler** — **【F】** 服务端处理商店编辑包（权限 ≥2），switch 分类/条目动作后回推配置。
- **ShopMenu** — 商店容器菜单：`openCategory` 下发 `ShopDataPacket`（条目+余额+分类表+限量快照）；`handleTrade` 含 barter 分支但**无调用者**。
- **ShopScreen**（约 1,020 行）— 管理员商店主界面：420×260，左分类栏 100px，拼音搜索、滚动、`ShopUI` 缩放、编辑模式右键菜单、限量徽标；`updateData(...)`、`isLimitExhausted`、`refreshFromConfig`。
- **ModernShopScreen / ModernTradeConfirmScreen** — 主题化商店与确认框，**无外部调用点**。
- **TradeConfirmScreen** — 主交易确认：数量、总价、余额校验，确认后发交易包。
- **SellBoxEconomy 之外的经济入口**：`ShopUI`（`BASE_HEIGHT=460`，`getScale=clamp(h/460,0.78,2.5)`）、`ShopButton`、`ModernButton`、`ShopKeyHandler`。
- **ShopItemPickerScreen**（约 1,060–1,210 行）— 全物品选择器：拼音搜索、排序、酒类变体、`ItemSampleStacks` 取样。
- **PlayerInventoryPickerScreen** — 只读背包网格选择器（`Consumer<ItemStack>` 回调）。
- **PickItemScreen** — 轻量物品列表选择。
- **AddCategoryScreen / EditCategoryScreen** — 分类新建/改名。
- **AddItemTypeScreen** — 选条目类型（物品/命令/经验/以物易物）。
- **AddXpScreen** — 经验条目（XP 数 + 价格）。
- **AddCommandScreen** — 命令条目（命令文本 + 价格 + 需 OP + 图标）。
- **SetItemLimitScreen** — 限量设置（`LimitMode`：无限/全体/单人）。
- **QuestPickerScreen** — FTB 前置任务选择（请求/同步/提交三个任务包）。
- `shop/network` **【N】** — 10 个 Payload，见 §6.2。

### 4.16 `util`

- **PinyinUtil**（约 244 行）— 高频汉字→拼音表；`toPinyin/getFirstLetterString/matches/getFirstLetter`，中文搜索基础。
- **FontUtil** — 文本自适应：`getAutoFitScale/drawAutoFitText/drawAutoFitCentered`。
- **ItemSortUtil** — `SortMode{NAME,ID,MOD,FIRST_LETTER}` + 通用比较器。
- **ItemVariantUtil** — 酒类变体：反射取 `BrewLevel` 或按 namespace/词根识别，`DataComponents.CUSTOM_DATA` 存 0~6 级 + 中文品质名；产出 `id|SNBT` 价格键。
- **ItemIcons** — 统一 `render(g,stack,x,y,seed)`；注释解释为何不能用 `renderFakeItem`（entity=null、seed=0 会让部分模组动态图标变黑框）。
- **ItemSampleStacks** — 物品「代表性栈」缓存：创造栏缓存 + 玩家背包优先，解决裸 `new ItemStack` 缺组件导致黑图标。

---

## 5. 关键流程

### 5.1 管理员商店交易（两端各自的真实链路）

**Forge**：`ShopScreen`（或 `ModernShopScreen`）选条目 → `TradeConfirmScreen` 选数量 → `ModNetwork.CHANNEL.sendToServer(new TradePacket(...))` → `TradePacket.handle` → `ModNetwork.executeTrade(...)`：先查 `TradeLimitTracker` 剩余额度 → 按 `entryType` 分派 command / xp / barter / BUY / SELL → `SellBoxEconomy` 扣加（SDM 模式转发 `SDMShopIntegration`）→ 发货或扣货 → `TradeLimitTracker.record` → 回推余额与限量快照；失败发 `CloseScreenPacket(提示 key)`。

**NeoForge**：同样从 `ShopScreen` 出发，但确认后发 `TradePayload` → `TradePayload.handle`（`enqueueWork` 到服务端主线程）→ 在 `ShopConfig` 里按「itemId + tradeType + 单价 + nbt」匹配条目（客户端附带的 NBT 只能命中非严格条目，防作弊）→ `executeTrade` 分派 → 余额经 `SellBoxEconomy`（VSS 时转发）→ 回推 `ShopDataPayload` 刷新额度。

> 两端都存在**未被调用的同名交易实现**：Forge 的 `shop/TradeHandler.executeTrade`、两端 `ShopMenu.handleTrade`、Forge `PlayerShopMenu.handleTrade`（已 @Deprecated）。改动交易逻辑时以网络层入口为准。

### 5.2 出货箱自动结算

1. `SellBoxEvents` 的 `LevelTickEvent.Pre` 到点（`SellBoxConfig.sellTime`）触发所有已加载出货箱。
2. `SellBoxBlockEntity.sellItems()` 逐格按配置价结算，价格来源 `SellBoxConfig`（NBT 精确价优先，`id|SNBT` 键）。
3. 同一 tick 的多个箱子合并成一份 `PendingReceipt` → `flushPendingReceipts`：Title + 聊天文本 + 写入槽 52 的成书（上限 100 页）。
4. 玩家离线则记入 `PENDING`（`sellbox_pending.dat`），下次登录补发；钱袋在场则直接入袋（`tryCollectWithCoinBag`）。

### 5.3 玩家商店

- 三个方块：`market`（买家下单）、`stock`（店主备货）、`sell_box`（收购箱）。
- 数据：`PlayerShopConfig` 存「店主 UUID → (店名, `List<ShopEntry>`)」+ 统计；`StockBlockEntity` 是实时库存来源。
- 购买：`PlayerShopScreen` → 交易包（Forge `PlayerShopTradePacket` / NeoForge `PlayerShopTradePayload`）→ 校验店主与 `marketPos` 归属（不符时按 owner 反查市场方块）→ 限量/前置任务校验 → 扣买家、加店主（离线进 pending）→ 从备货台扣货 → 写收据书 → `addEarnings`。
- 编辑：选择器界面 → 编辑包 → 服务端处理（黑名单校验（仅 Forge）+ OP2 管理员代改）。
- 数据下发：汇总全部店铺 + 库存计数 + 在线店主名。

### 5.4 持久化文件总表

默认目录 `config/SellBox/`；`globalPricing=false` / `globalShopConfig=false` 时改为 `<存档>/config/SellBox/`（两端规则相同）。

| 文件 | 内容 | 端 |
|---|---|---|
| `sellbox-common.properties` | 全局配置（sellTime、货币模式、global* 开关） | **仅 NeoForge**（Forge 用 `ForgeConfigSpec`，由 Forge 生成 toml） |
| `sellbox_shop.json` | 管理员商店全部分类与条目（Gson pretty，LinkedHashMap 保序） | 两端 |
| `sellbox_prices.txt` | 收购价，行格式 `物品ID=价格`，NBT 级 `物品ID\|SNBT=价格`，支持 `#`/`//` 注释 | 两端 |
| `sellbox_pending.dat` | 离线待发放（DataOutputStream：数量 + UUID + 金额） | 两端 |
| `sellbox_economy.dat` | 内置经济余额表 | 两端 |
| `sellbox_playershops.json` / `sellbox_shopstats.json` | 玩家店铺条目 / 统计 | 两端 |
| `sellbox_trade_limits.json` | 交易限量计数（全局 + 每人） | 两端 |
| `gui_theme.json` | 主题名，内容 `{"theme":"modern"}` | 两端 |
| `sellbox_blacklist.json` | 玩家商店黑名单 | **仅 Forge** |
| `sellbox_blacklist.txt` | 每行一个物品 ID | **仅 NeoForge**（并入 `PlayerShopConfig`） |
| `sellbox_vss.flag` / `sellbox_shopoverride.flag` | VSS 经济开关 / 商店覆盖开关（内容为布尔串） | **仅 Forge**（NeoForge 存在 properties 里） |

### 5.5 主题与缩放

`GuiTheme` 单例（约 60 个颜色字段）+ `ThemePresets`（5 套）+ `ModernTheme`；`gui_theme.json` 只存名字，启动时 `setByName` 还原并即时写盘。界面在构造时取 `GuiTheme.get()` 快照，因此切主题对已开界面不生效。整体缩放由 `ShopUI.getScale(height)` 统一（基准 460，0.78~2.5），各界面按比例算尺寸。

### 5.6 软依赖集成矩阵

| 模组 | 用途 | 机制 | 缺失时表现 |
|---|---|---|---|
| JEI | 商店条目显示为 `shop_trade` 配方 | 编译期 API（Forge 还反射 `JEIIntegration`） | `JeiBridge` 静默降级 |
| Curios | 货币放饰品栏显示余额 | 反射（Forge 另有 `CuriosMoneyItem` 直接实现） | 只影响饰品栏玩法 |
| FTB Quests | 条目解锁前置任务 | 全反射，只读查询 | `QuestPickerScreen` 空列表 |
| SDMShop（Forge） | 外部经济 | 反射多个候选类名 | 回落内置经济 |
| VSS / viScriptShop（NeoForge） | 外部经济 | 反射 `ViScriptShopServerUtil` | 回落内置经济 |
| NEZNAMY TAB | 计分板/TAB 显示余额 | 反射注册占位符 | 无占位符 |

---

## 6. 网络层

### 6.1 Forge 1.20.1：`ModNetwork` 26 个包（`sellbox:main`，协议版本 "2"）

服务端 → 客户端（14）：`SyncBalancePacket`（余额）、`SyncAllPricesPacket`（全量收购价）、`CloseScreenPacket`（关屏+提示）、`ActionMessagePacket`（提示+音效）、`ShopDataPacket`（条目+余额+分类表+限量快照）、`OpenShopScreenPacket`、`SyncShopConfigPacket`（编辑态/权限）、`SyncBlacklistPacket`（黑名单）、`SyncAdminEditPacket`（管理员代改开关）、`SyncAllShopEntriesPacket`（JEI 全量）、`SyncQuestListPacket`（FTB 任务树）、`PlayerShopDataPacket`（全部店铺+库存）、`OpenPlayerShopScreenPacket`、`OpenPricingPacket`。

客户端 → 服务端（12）：`SetPricesPacket`（收购价）、`ShopOpenPacket`（请求分类数据）、`TradePacket`、`ShopEditPacket`、`PlayerShopOpenPacket`、`PlayerShopTradePacket`、`PlayerShopNamePacket`、`PlayerShopEditPacket`、`RequestQuestListPacket`、`SetEntryQuestPacket`、`SetEntryLimitPacket`、`RequestBalancePacket`。

### 6.2 NeoForge 1.21.1：24 个 Payload（`versioned("1.0.0")`）

客户端 → 服务端（14）：`ShopOpenPayload`、`TradePayload`、`ShopEditPayload`、`PricingPayload.SetPricesPayload` / `SetNbtPricesPayload` / `DeleteNbtPricesPayload`、`RequestBalancePayload`、`RequestQuestListPacket`、`SetEntryQuestPacket`、`SetEntryLimitPacket`、`PlayerShopOpenPayload`、`PlayerShopTradePayload`、`PlayerShopEditPayload`、`PlayerShopNamePayload`。

服务端 → 客户端（10）：`ShopDataPayload`、`OpenShopScreenPayload`、`SyncShopConfigPayload`（VSS 开关/商店覆盖/编辑态/管理员态四合一）、`SyncBalancePayload`、`SyncAllPricesPayload`、`OpenPricingPayload`、`CloseScreenPayload`（关屏+音效+提示）、`SyncQuestListPacket`、`PlayerShopDataPayload`、`OpenPlayerShopScreenPayload`。

### 6.3 对应关系

- `ModNetwork` + `ClientPacketHandler` → `ModPayloads`（注册表/状态）+ `ModClientPayloads`（下行处理）+ 一包一类的 Payload。
- 交易与编辑的处理从 `ModNetwork`/`*Handler` 静态方法搬进 Payload 的 `handle`。
- Forge 独有 4 个服务端→客户端包：`SyncBlacklistPacket`、`SyncAdminEditPacket`、`SyncAllShopEntriesPacket`、`ActionMessagePacket`。NeoForge 侧只保留了管理员态（并入 `SyncShopConfigPayload`），JEI 改为打开界面时经 `JeiBridge` 推送，提示并入 `CloseScreenPayload`，**黑名单没有对应下发包**。
- Forge 1 个 `SetPricesPacket` 在 NeoForge 拆成 3 个（普通价 / NBT 价 / 删 NBT 价）。

---

## 7. 资源与本地化

```
assets/sellbox/
├── blockstates/      sell_box, market, stock
├── models/block/     同名 3 个
├── models/item/      sell_box, market, stock, gold_coin, money, coin_bag
├── textures/block/   sellbox 六面 + sellbox.png + 1shou/1zheng/2shou/2zheng/qianhou/shangxia/zuo/you.png
├── textures/item/    coin, money, qiandai, sellbox1
├── textures/gui/     coin.png
├── lang/             zh_cn.json、en_us.json（约 177 / 176 条键）
└── sounds/           good.ogg、die.ogg（sounds.json 定义 good / die）
data/sellbox/         recipes|recipe（sell_box、coin_bag、market、stock、money）、loot_tables|loot_table/blocks/sell_box
data/curios/tags/item/  13 个饰品栏位标签（head/necklace/ring/belt/body/bracelet/back/charm/feet/hands/extra/trinkets/curio）
```

i18n 命名空间：`itemGroup.sellbox`、`block.sellbox.*`、`item.sellbox.*`、`gui.sellbox.*`、`gui.shop.*`、`message.sellbox.*`、`command.sellbox.*`、`tooltip.sellbox.*`、`key.sellbox.shop` / `key.categories.sellbox`。

**NeoForge 侧残留**：`assets/examplemod/lang/en_us.json`（628 B，模板文件）被打进 jar；同时 `neoforge.mods.toml` 里没有 Forge 那份 `mod_authors/mod_description` 占位符，作者名硬编码为 `Xichen`。

---

## 8. 两端差异

### 8.1 结构差异（类级别）

- **仅 Forge（11）**：`ModNetwork`、`ClientPacketHandler`、`TradeHandler`、`ShopEditHandler`、`PlayerShopOpenHandler`、`PlayerShopEditHandler`、`PlayerShopBlacklist`、`CuriosMoneyItem`、`JEIIntegration`、`SDMShopIntegration`、`ModSounds`。
- **仅 NeoForge（22）**：`ModPayloads`、`ModClientPayloads`、`PricingPayload`、`shop/network/` 10 个、`playershop/network/` 6 个、`ModernPlayerShopScreen`、`VSSIntegration`、`ModSoundEvents`。
- 改名/移位：`ModSounds`→`ModSoundEvents`、`SDMShopIntegration`→`VSSIntegration`、`client/InventoryShopButton`→`shop/InventoryShopButton`、Forge 的 4 个 `*Handler` 并入 NeoForge 的 Payload。

### 8.2 数据模型迁移

| 主题 | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| 条目 NBT | `ShopEntry.nbt : CompoundTag`、`barterNbt`，匹配 `isSameItemSameTags` | `ShopEntry.nbt : String`（`DataComponentPatch` JSON）+ `barterOfferNbt` + 新增 `barterGoodsStrict`，匹配 `isSameItemSameComponents` |
| 钱袋金额 | NBT `coins` | `DataComponents.CUSTOM_DATA` 的 `coins` |
| 收据书 | 手写 pages NBT | `DataComponents.WRITTEN_BOOK_CONTENT` |
| 自定义名 | `setHoverName`/NBT | `DataComponents.CUSTOM_NAME` |
| 注册 | `DeferredRegister` + Forge 事件 | `DeferredRegister` + NeoForge 事件；菜单用 `IMenuTypeExtension.create` |
| 配置 | `ForgeConfigSpec` | 自解析 `sellbox-common.properties` |

### 8.3 其他不一致

- `SellBoxEconomy`：Forge 实现 `IEconomy`；NeoForge 不实现，由 `SellBoxMod` 匿名适配后注入 `SellBoxEconomyAPI`。
- 玩家商店黑名单：Forge 有独立类 + 下发客户端 + 编辑时校验；NeoForge 只在 `PlayerShopConfig` 里存取，**编辑流程未校验、也未下发**。
- 语言文件键集合不同：Forge 有 `command.sellbox.nbt_prices_cleared`、`gui.sellbox.inventory_shop_button`、4 个 `message.sellbox.trade_success_*`、`key.categories.sellbox`、`message.sellbox.sdm_not_found`；NeoForge 独有 `message.sellbox.{barter_success,buy_success,sell_success,trade_success,trade_not_found,vss_not_found,not_owner_market,not_owner_stock}`。
- 打包：Forge jar 含 `pack.mcmeta`（94 B）；**NeoForge 的 `src/main/resources` 与产物 jar 里都没有 `pack.mcmeta`**。

---

## 9. 已知问题与待办

按影响排序（均为静态检查结论，未实测复现）：

1. **NeoForge 侧 mixin 未生效** — `neoforge-1.21.1/src/main/resources/sellbox.mixins.json` 的 `mixins` 为空数组，`SlotMixin` 虽存在但不会加载；两端 `SlotMixin` 代码完全相同。若 1.21.1 需要 >64 堆叠的行为，这是遗漏。
2. **NeoForge 无 `pack.mcmeta`** — 1.21.1 的资源包通常要求 jar 根目录有该文件；Forge 侧有。需实际启动确认资源（贴图/语言）是否完整加载。
3. **1.21.1 的 `examplemod` 语言文件被打包** — 会在 `assets/examplemod/lang/en_us.json` 留下模板键。
4. **死代码** — 两端 `ShopMenu.handleTrade`、Forge `PlayerShopMenu.handleTrade`、Forge `TradeHandler` 均无调用者；`ModernShopScreen`、`ModernTradeConfirmScreen`、`ModernPlayerShopScreen`、`ThemeSwitchScreen` 四个界面没有实例化点（现代 UI 与主题切换无入口）。
5. **NeoForge 黑名单未接编辑流程** — `PlayerShopConfig.isBlacklisted` 只在命令里有入口，`PlayerShopEditPayload` 未调用，且没有下发客户端的包，等于 1.21.1 上黑名单功能不完整。
6. **NeoForge 全局配置分支不可达** — `PlayerShopConfig.init(configDir)`、`SellBoxEconomy.init(configDir)` 已实现但没有调用点，`SellBoxMod` 只调 `initPerWorld`；同理 Forge 的 `TradeLimitTracker` 全局入口。
7. **旧 NBT 模型残留** — `ShopItemPickerScreen`、`PlayerShopItemPickerScreen` 内仍有 `record ItemInfo(..., CompoundTag variantNbt, ...)`，与 `ShopEntry` 的组件补丁 JSON 模型并存，转换可能丢数据。
8. **网络状态挂在静态字段** — `ModPayloads.client*`（Forge `ModNetwork.client*`）是全局静态，重连/换服依赖 `SyncShopConfigPayload` 覆盖，无显式重置。
9. **构建脚本硬编码路径** — `into 'C:\Users\Administrator\Desktop\坚果云'`，换用户或换机器会写失败。
10. **两端不同步风险** — 本目录是副本，`README.md` 明确写「修改不同步」；两端手工对齐，容易出现 §8.3 那类偏差。

---

## 10. 检索速查

```bash
# 找类定义
grep -rn "class ShopScreen\|record ShopEntry" forge-1.20.1/src neoforge-1.21.1/src

# 找网络包处理入口
grep -rn "handle(" neoforge-1.21.1/src/main/java/com/sellbox/mod/network neoforge-1.21.1/src/main/java/com/sellbox/mod/shop/network

# 找配置键 / 语言键
grep -rn "gui.sellbox\." forge-1.20.1/src/main/resources/assets/sellbox/lang/zh_cn.json

# 找持久化路径
grep -rn "resolve(\"" forge-1.20.1/src/main/java/com/sellbox/mod/config

# 对比两端同名类差异
diff forge-1.20.1/src/main/java/com/sellbox/mod/shop/ShopScreen.java \
     neoforge-1.21.1/src/main/java/com/sellbox/mod/shop/ShopScreen.java
```

按需求定位：

| 想改的东西 | 去哪 |
|---|---|
| 出货箱收购价、自动出货 | `config/SellBoxConfig`、`block/SellBoxBlockEntity.sellItems` |
| 管理员商店条目/分类/交易 | `shop/ShopEntry`、`shop/ShopConfig`、`shop/ShopScreen`、网络层 `Trade*` |
| 玩家商店（市场/备货） | `playershop/*`（`PlayerShopRegistry`、`PlayerShopConfig`、`PlayerShopScreen`） |
| 界面配色/缩放 | `gui/GuiTheme`、`gui/ThemePresets`、`shop/ShopUI` |
| 余额与外部经济对接 | `shop/SellBoxEconomy`、`api/IEconomy`、`integration/{SDM,VSS}Integration` |
| 命令 | `command/ModCommands` |
| JEI 展示 | `integration/jei/*` |
| 交易限购 | `shop/TradeLimitTracker`、`shop/SetItemLimitScreen` |
| 任务前置解锁 | `integration/FTBQuestsIntegration`、`shop/QuestPickerScreen` |
