# SellBoxShop

Forge 1.20.1 与 NeoForge 1.21.1 双端模组，源码整合目录。

> 完整代码索引（包职责、逐类说明、关键流程、两端差异、待办）：[docs/PROJECT_INDEX.md](docs/PROJECT_INDEX.md)
>
> 发布用完整功能与指令说明：[docs/MOD_DESCRIPTION.md](docs/MOD_DESCRIPTION.md)

## 目录结构

```
SellBox/
├── forge-1.20.1/      # Forge 1.20.1 版（Java 17）
│   ├── src/
│   ├── build.gradle
│   ├── gradle.properties
│   └── libs/          # curios API 等本地 jar
└── neoforge-1.21.1/   # NeoForge 1.21.1 版（Java 21）
    ├── src/
    ├── build.gradle
    └── gradle.properties
```

## 构建

分别在子目录执行：

```bash
# Forge 1.20.1
cd forge-1.20.1
.\gradlew.bat build

# NeoForge 1.21.1
cd neoforge-1.21.1
.\gradlew.bat build
```

产物输出到各自 `build/libs/`，并自动复制到坚果云桌面目录。

## 两端差异要点

| 项目 | Forge 1.20.1 | NeoForge 1.21.1 |
|------|-------------|-----------------|
| Java | 17 | 21 |
| 网络包 | `ModNetwork.CHANNEL` | `ModPayloads` |
| NBT | `getTag()/setTag()` | DataComponents |
| 鼠标滚轮 | `mouseScrolled(x,y,d)` | `mouseScrolled(x,y,dxy)` |
| 配置存储 | 存档级 `{world}/config/SellBox/` | 同左 |
| TAB 集成 | `TabIntegration`（反射） | `TabIntegration`（反射） |
