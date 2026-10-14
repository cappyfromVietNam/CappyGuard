<div align="center">

# 🛡️ CappyGuard

### High-Performance Server Security & Anti-Abuse System for Minecraft

**Protect your server against Force OP · Staff Abuse · Creative Exploits · Reconnaissance**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-green.svg)](https://papermc.io)
[![Platform](https://img.shields.io/badge/Platform-Paper%20%2F%20Purpur-blue.svg)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net)
[![Build](https://img.shields.io/badge/Build-Passing-brightgreen.svg)](#)

*Developed with ❤️ by **Cappy Community***

---

</div>

## 📖 Overview

**CappyGuard** is an all-in-one, ultra-lightweight security and anti-abuse plugin designed specifically for modern **Paper/Purpur** servers (Minecraft 1.21+). Engineered from the ground up with **Zero-Tick Performance Impact**, CappyGuard guarantees $O(1)$ fast lookups, non-blocking asynchronous disk logging, and async Discord notifications without dropping a single TPS.

Whether you run a competitive survival server, a creative network, or an SMP with staff members, CappyGuard ensures complete control over operator permissions, staff inventory movements, and server reconnaissance attempts.

---

## ✨ Key Features

### 🚫 1. Anti Force OP
- **Strict OP Whitelist:** Maintains an inviolable list of authorized Operators by UUID and username.
- **Player Command Guard:** Intercepts `/op` and `/minecraft:op` commands executed by unauthorized players.
- **Console & Backdoor Shield:** Blocks console-injected OP commands (`op <target>`) targeting non-whitelisted players.
- **Login & Runtime Scanner:** Automatically strips operator privileges from unauthorized users on join and through periodic background scans.
- **Enforcement Actions:** Configurable penalties (`DEOP`, `KICK`, or permanent `BAN`).

### 📦 2. Creative Monitor *(Staff Anti-Abuse)*
- **Container Tracking with Exact Coordinates:** Records the exact world, coordinates `(X, Y, Z)`, container type (Chests, Barrels, Shulker Boxes, Hoppers), item material, and quantity when staff deposit items taken from Creative mode.
- **Ender Chest Inspection:** Flags and logs whenever staff interact with Ender Chests while in Creative mode.
- **Drop & Transfer Detection:** Tags dropped creative items and tracks which player subsequently picks them up.
- **Gamemode Switch Audit:** Alerts when staff switch from Creative to Survival while retaining creative items, with an optional auto-inventory wipe (`clear-on-switch`).

### 🎭 3. Plugin Spoof *(Anti-Reconnaissance)*
- **Hides Real Server Plugins:** Prevents malicious clients from scanning your plugin list to discover known exploits or duplication glitches.
- **Paper Command Send Pruning:** Native Paper packet pruning that strips plugin inspection commands (`/pl`, `/plugins`, `/ver`, `/about`, `/?`) from client tab-completion trees before keystrokes are even registered.
- **Three Operation Modes:**
  - `HIDE`: Returns a standard unknown command error.
  - `FAKE`: Outputs a customizable fake plugin list (e.g., `CappyGuard, Essentials, WorldGuard`).
  - `CUSTOM_MESSAGE`: Displays a custom notification message.

### 🛑 4. Anti Admin Abuse
- **Command Rate Limiter:** Protects the server against staff rogue spam or compromised accounts with sliding-window rate limiting.
- **Self-Give Blocker:** Restricts staff from using `/give`, `/minecraft:give`, or `/item` commands to spawn items directly for themselves.

### 🎮 5. Anti Gamemode Abuse
- Restricts unauthorized staff from switching to `CREATIVE` or `SPECTATOR`.
- Supports per-player exemptions and allowed world lists (e.g., dedicated build worlds).

### ⛔ 6. Anti Dangerous Commands
- Intercepts catastrophic server commands such as `/stop`, `/restart`, `/reload`, `/ban`, `/ban-ip`, and aliases.
- Configurable bypass list and granular bypass permissions.

### 🛡️ 7. Deep Packet Guard *(PacketEvents Netty Inspection)*
- **GuiMove / InvMove Detection (Right Shift ClickGUI):** In vanilla Minecraft, the client code suppresses movement input whenever a GUI is open. Deep Packet Guard detects players clicking inventory or GUI slots while actively sprinting, jumping, or moving with forward momentum—catching ClickGUI movement hacks immediately.
- **Raw Network Command & Prefix Leak Drop:** Inspects raw `CHAT_MESSAGE` and `CHAT_COMMAND` packets right at the Netty pipeline level. Catches and silently drops client command leaks (`.killaura`, `.fly`, `.vclip`, `.bind`, `.baritone`, `#goto`, etc.) before they ever appear in public chat or reach Bukkit listeners.
- **Custom Payload & Mod Channel Sniffing:** Detects registration of cheat mod channels (`baritone:`, `meteor:`, `wurst:`, `liquidbounce:`) during connection handshake.

### 🔔 8. Discord Webhook & Embed Customization
- **100% Async Native HTTP Client:** Uses Java 21 `java.net.http.HttpClient` with zero external library bloat (<70 KB JAR).
- **Rich Embed Builder:** Fully customizable colors (`critical`, `warning`, `info`), avatar skin renders, and inline fields.
- **Emergency Mention:** Automatically pings a specified role ID when critical Force OP attempts occur.

### 📝 9. Asynchronous File Logging
- Batches and flushes security logs to `plugins/CappyGuard/logs/cappyguard.log` asynchronously, eliminating disk I/O lag on the main server thread.

---

## ⚡ Performance Architecture

| Optimization | Implementation | Benefit |
|---|---|---|
| **$O(1)$ Fast Lookups** | `HashSet<UUID>`, `HashSet<String>`, `EnumSet<Material>` | Immediate matching with zero iterating overhead |
| **Fail-Fast Early Exits** | Checked before string concatenations or allocations | Zero unnecessary heap memory pressure |
| **Async Disk I/O** | `ConcurrentLinkedQueue` with scheduled background batch flush | **0ms** main thread hang on disk writes |
| **Zero Heavy Dependencies** | Uses native Java 21 HTTP client and Paper Adventure | Smallest footprint possible (~62 KB) |

---

## 🔑 Granular Permission Hierarchy

CappyGuard features a comprehensive 4-tier permission architecture designed for seamless integration with permission managers like **LuckPerms**:

### 1. Administrative Commands
| Permission | Description | Default |
|---|---|:---:|
| `cappyguard.admin` | Access to the main `/cappyguard` administration command | `op` |
| `cappyguard.command.reload` | Permission to execute `/cg reload` | `op` |
| `cappyguard.command.status` | Permission to execute `/cg status` | `op` |
| `cappyguard.command.whitelist` | Permission to manage the OP whitelist (`/cg whitelist ...`) | `op` |

### 2. Monitored Staff *(Applied to staff roles to enforce rules)*
| Permission | Description | Default |
|---|---|:---:|
| `cappyguard.staff` | Marks player as staff (enforces all anti-abuse monitors) | `false` |
| `cappyguard.staff.creative` | Enforces Creative Monitor rules (containers, coords, drops) | `false` |
| `cappyguard.staff.gamemode` | Restricts unauthorized gamemode switching | `false` |
| `cappyguard.staff.ratelimit` | Enforces admin command rate limits | `false` |
| `cappyguard.staff.selfgive` | Blocks self-giving items using commands | `false` |
| `cappyguard.staff.commands` | Blocks dangerous server control commands | `false` |

### 3. In-Game Alert Subscriptions
| Permission | Description | Default |
|---|---|:---:|
| `cappyguard.alert` | Receive all security notifications in game | `op` |
| `cappyguard.alert.forceop` | Receive Force OP notifications | `op` |
| `cappyguard.alert.creative` | Receive Creative item placement and transfer alerts | `op` |
| `cappyguard.alert.abuse` | Receive Admin Abuse and Rate Limit alerts | `op` |
| `cappyguard.alert.spoof` | Receive Plugin Scan reconnaissance alerts | `op` |
| `cappyguard.alert.packet` | Receive Deep Packet Guard cheat alerts | `op` |

### 4. High-Level Bypass *(Server Owner Exemption)*
| Permission | Description | Default |
|---|---|:---:|
| `cappyguard.bypass.*` | Complete exemption from all security checks | `false` |
| `cappyguard.bypass.forceop` | Exempt from OP Whitelist checks | `false` |
| `cappyguard.bypass.gamemode` | Exempt from gamemode restrictions | `false` |
| `cappyguard.bypass.dangerous` | Exempt from dangerous commands blocking | `false` |
| `cappyguard.bypass.creative` | Exempt from Creative item monitoring | `false` |
| `cappyguard.bypass.ratelimit` | Exempt from command rate limiting | `false` |
| `cappyguard.bypass.spoof` | Allows viewing real plugins with `/plugins` or `/version` | `false` |
| `cappyguard.bypass.packet` | Exempt from Deep Packet Guard Netty checks | `false` |

---

## 💻 Commands

Main command alias: `/cg` or `/cappyguard`.

```
/cg help                      - Display help and command usage
/cg status                    - Display security modules status and statistics
/cg reload                    - Reload configuration, modules, and Discord webhooks
/cg whitelist list            - List all whitelisted operators
/cg whitelist add <player>    - Add a player or UUID to the OP whitelist
/cg whitelist remove <player> - Remove a player or UUID from the OP whitelist
```

---

## 🛠️ Installation

> [!IMPORTANT]
> **Dependency Requirement:** CappyGuard requires **[PacketEvents](https://modrinth.com/plugin/packetevents)** installed on your Paper/Purpur server.

1. Download **PacketEvents** and place it into your server's `plugins/` directory.
2. Download the latest `CappyGuard-1.0.0.jar` from [Releases](https://github.com/cappyboomc/CappyGuard/releases) or [Modrinth](#).
3. Place `CappyGuard-1.0.0.jar` into your server's `plugins/` directory.
4. Start or restart your server to generate `plugins/CappyGuard/config.yml`.
5. Open `plugins/CappyGuard/config.yml` and add your **UUID** to `op-whitelist.allowed`.
6. Run `/cg reload` in console.

---

## 🔨 Building from Source

CappyGuard requires **Java 21+** to build:

```bash
# Clone the repository
git clone https://github.com/cappyboomc/CappyGuard.git
cd CappyGuard

# Build the project with Gradle Wrapper
./gradlew clean build

# The compiled JAR will be located at:
# build/libs/CappyGuard-1.0.0.jar
```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).  
Copyright (c) 2026 **Cappy Community**