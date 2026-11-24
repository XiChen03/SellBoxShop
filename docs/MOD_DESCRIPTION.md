# SellBoxShop

A feature-rich economy and shop mod for Minecraft, available for **Forge 1.20.1** and **NeoForge 1.21.1**. It adds a complete built-in economy, flexible item pricing, player-owned shops, stock management, and a large optional "Extras" module.

---

## Features

### Economy
- Built-in economy with balance tracking, persistent player data, and leaderboards
- Optional VSS (ViScriptShop) / SDMShop currency integration
- Server-side persistence for balances, pending payouts, pricing, and shop data
- Admin commands for giving/setting balances and switching economy providers

### Item Pricing & Selling
- Flexible per-item pricing with config-based defaults
- Hotbar quick-sell and hotkey support
- NBT-sensitive pricing for items such as custom potions or quality variants
- English / pinyin search for faster item lookup
- Pricing GUI and in-game item picker

### Player Shops
- Create and manage your own shops
- Browse the public market and other players' shops
- Stock management and market pricing
- Admin edit mode for other players' shops
- Trade confirmation and blacklist protection

### Extras Module
- **Sign-in**: daily rewards, streak tracking, and milestone rewards
- **Mailbox**: system/player mail with item attachments and claim support
- **Transfer**: send money to other players
- **Exchange table**: define item-to-money exchange rates, with optional auto-conversion
- **Redeem codes**: single-use or limited-use codes with money rewards
- **Crates**: configurable crates, weighted pools, rarity, pity system, single draw and ten-pull with reel animation and skip
- **Sweeper Maid**: automatic ground-item cleaning, blacklist/whitelist, extra entity cleanup, dustbins, countdown, and configurable chat messages
- **Admin panel**: edit every Extras feature in-game, with searchable registry pickers

### UI & Themes
- Clean GUI with categories, trade confirmation, and command-based shop actions
- Built-in themes: dark, modern, light (white-pink), green, purple, orange
- Theme switching via GUI or `/sellbox theme <name>`
- Custom scrollbars, dropdowns, and item pickers

### Optional Integrations
- ViScriptShop 1.21.1 / SDMShop 1.20.1 currency
- Curios API
- JEI recipe/trade display
- FTB Quests 1.20.1 / 1.21.1 (quest-linked shop entries)

---

## Requirements

| Platform | Requirement |
|---|---|
| Forge 1.20.1 | Forge 47.x, Java 17 |
| NeoForge 1.21.1 | NeoForge 21.1.x, Java 21 |

### Optional dependency test versions

| Platform | Mod | Version |
|---|---|---|
| 1.20.1 | FTB Quests | `ftb-quests-forge-2001.4.14` |
| 1.21.1 | FTB Quests | `ftb-quests-neoforge-2101.1.27` |
| 1.21.1 | ViScriptShop | `ViScriptShop-1.21.1-1.1.8` |
| 1.20.1 | SDMShop | `SDMShop-1.20.1-6.11pre` |

---

## Commands

### Player commands (permission 0)

| Command | Description |
|---|---|
| `/sellbox help [page]` | Show command help |
| `/sellbox handprice` | Check the sell price of the item in your hand |
| `/sellbox sell` | Sell the item in your hand at its sell price |
| `/sellbox shoplist [page]` | View the player shop leaderboard |
| `/sellbox adminshop open` | Open the admin shop GUI (disabled while VSS/SDM economy is active unless shop override is enabled) |
| `/sellbox money` | Show your balance (also available as the `%sellboxmoney%` placeholder) |
| `/sellbox theme [name]` | View or switch the GUI theme |
| `/sellbox dustbin [index]` | Open a Sweeper Maid dustbin (numbering starts at 1; requires Extras enabled) |

### Admin commands (permission 2)

| Command | Description |
|---|---|
| `/sellbox leaderboard <page>` | View the economy leaderboard |
| `/sellbox getselltime` | Show the current auto-seller interval |
| `/sellbox setselltime <tick>` | Set the auto-seller interval (0–24000 ticks) |
| `/sellbox handset <price>` | Set the sell price of the item in your hand |
| `/sellbox handsetnbt <price>` | Set an NBT-sensitive sell price for the item in your hand |
| `/sellbox handremove` | Remove the sell price of the item in your hand |
| `/sellbox handremovenbt <price>` | Remove the NBT-sensitive sell price |
| `/sellbox delnbt` | Clear all NBT-sensitive sell prices |
| `/sellbox editpricing` | Open the item pricing GUI (creative mode recommended) |
| `/sellbox adminshop edit` | Toggle admin shop edit mode |
| `/sellbox adminshop <true/false>` | Enable/disable the admin shop while an external economy is active (VSS on 1.21.1, SDMShop on 1.20.1) |
| `/sellbox playershop edit` | Toggle admin editing of other players' shops |
| `/sellbox blacklist add <item>` | Add an item to the shop blacklist |
| `/sellbox blacklist remove <item>` | Remove an item from the shop blacklist |
| `/sellbox blacklist list` | List blacklisted items |
| `/sellbox eco give <player> <amount>` | Give money to a player |
| `/sellbox eco set <player> <amount>` | Set a player's balance |
| `/sellbox ViScriptShop <true/false>` | Switch between VSS economy and the built-in economy (1.21.1) |
| `/sellbox SDMShop <true/false>` | Switch between SDMShop economy and the built-in economy (1.20.1) |
| `/sellbox vault <true/false>` | View or switch Vault economy (hybrid servers only) |
| `/sellbox extras true/false` | Master switch for all Extras features, including the Sweeper Maid |
| `/sellbox extras admin on/off` | Toggle Extras admin mode |
| `/sellbox extras` | Show Extras module information (exchange entries, codes, player data) |
| `/sellbox reload` | Reload all configuration files |

### Forge 1.20.1 notes

- `/sellbox blacklist add` without an item argument blacklists the item in your hand.
- `/sellbox adminshop <true/false>` and `/sellbox SDMShop <true/false>` apply to SDMShop economy.
- `minecraft:heavy_core` is not available in 1.20.1 and is not part of the default Sweeper Maid whitelist.

### NeoForge 1.21.1 notes

- `/sellbox adminshop <true/false>` and `/sellbox ViScriptShop <true/false>` apply to VSS economy.
- The default Sweeper Maid whitelist includes `minecraft:nether_star` and `minecraft:heavy_core`.

---

## Configuration

- All configuration and player data are saved per world under `config/SellBox/`.
- Extras configuration lives in `config/SellBox/sellbox_extras.json`.
- GUI theme selection is stored in `config/SellBox/gui_theme.json`.
- Use `/sellbox reload` after editing configuration files.

---

## Notes

- VSS/SDM economy can disable the admin shop by default; enable shop override or use the relevant admin command if you want both.
- The Sweeper Maid obeys the global Extras switch: when Extras are disabled, it stops counting down, cleaning, and opening dustbins.
- Dustbin numbering is 1-based in commands, chat links, and the admin panel.

---

## Development

This module is mainly developed with AI as a tool. Bug reports and feedback are welcome.
