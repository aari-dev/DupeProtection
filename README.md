# DupeProtection

<div align="center">

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://www.minecraft.net/)
[![Paper API](https://img.shields.io/badge/Paper-1.21.1-blue.svg)](https://papermc.io/)
[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![GitHub release](https://img.shields.io/github/v/release/aari-dev/DupeProtection)](https://github.com/aari-dev/DupeProtection/releases)
[![Build Status](https://img.shields.io/github/actions/workflow/status/aari-dev/DupeProtection/build.yml)](https://github.com/aari-dev/DupeProtection/actions)

**Enterprise-grade Minecraft Paper plugin for advanced item duplication detection and prevention**

[Features](#-features) • [Installation](#-installation) • [Commands](#-commands) • [Configuration](#-configuration) • [Building](#-building-from-source) • [Support](#-support)

</div>

---

## 🚀 Features

### **Advanced Duplication Detection**
- **SHA-256 Fingerprinting**: Cryptographic item identification system for foolproof duplicate detection
- **Real-time Analysis**: Instant duplicate detection across all server activities with zero delay
- **Smart Tracking**: Monitors crafting, mining, smelting, enchanting, trading, and item movements
- **Auto-Alerts**: Immediate broadcast notifications to administrators when duplicates are detected
- **Fingerprint Matching**: Compares item metadata, enchantments, lore, and custom data for accurate identification

### **Professional Admin Tools**
- **Debug Mode**: Live ItemID display in item lore visible only to administrators
- **Comprehensive History**: Full audit trail of every item action with timestamps and player tracking
- **Bulk Operations**: Mass deletion of duplicate items across all online player inventories
- **Smart Tab Completion**: Intelligent command auto-completion with real-time duplicate ID suggestions
- **Performance Dashboard**: Real-time tracking statistics and item type analytics
- **Inventory Scanning**: Deep scan player inventories for suspicious duplicate patterns

### **Enterprise Performance**
- **Zero TPS Impact**: Fully asynchronous processing with non-blocking operations
- **Memory Optimized**: FastUtil primitive collections for maximum memory efficiency
- **Scalable Storage**: Binary file format with asynchronous I/O operations for large datasets
- **Configurable Throttling**: Customizable action limits and scan rates to prevent performance degradation
- **Thread-Safe Operations**: Concurrent data structures for multi-threaded safety

### **Intelligent Alert System**
- **Real-time Broadcasts**: Automatic alerts sent to all administrators when duplicates are created
- **Smart Filtering**: Configurable alert thresholds and ignored actions to reduce spam
- **Color-Coded Messages**: Fully customizable hex color support for all plugin messages
- **Permission-Based**: Granular permission system for different administrative levels

## 📋 Commands

### **Core Commands**
| Command | Description | Permission | Usage Example |
|---------|-------------|------------|---------------|
| `/id check` | Check ItemID of item in main hand | `antidupe.admin` | Hold item and run command |
| `/id lookup <id>` | Find all items with matching ItemID | `antidupe.admin` | `/id lookup 1634567890` |
| `/item history [id]` | Show complete item action history | `antidupe.admin` | `/item history` or `/item history 123` |

### **Administration Commands**
| Command | Description | Permission | Usage Example |
|---------|-------------|------------|---------------|
| `/antidupe reload` | Reload plugin configuration | `antidupe.admin` | `/antidupe reload` |
| `/antidupe stats` | Display tracking statistics | `antidupe.admin` | `/antidupe stats` |

### **Debug Commands**
| Command | Description | Permission | Usage Example |
|---------|-------------|------------|---------------|
| `/dupe mode` | Toggle debug mode (ItemIDs in lore) | `antidupe.admin` | `/dupe mode` |
| `/dupe alerts` | Toggle duplicate alert notifications | `antidupe.admin` | `/dupe alerts` |
| `/dupe scan [player]` | Scan inventory for duplicate items | `antidupe.admin` | `/dupe scan` or `/dupe scan PlayerName` |
| `/dupe test` | Test duplication detection on held item | `antidupe.admin` | Hold item and `/dupe test` |
| `/dupe clean` | Remove debug information from held item | `antidupe.admin` | `/dupe clean` |
| `/dupe delete <id>` | Delete all duplicate items by ItemID | `antidupe.admin` | `/dupe delete 1634567890` |

### **Permissions**
- `antidupe.admin` - Access to all DupeProtection commands (default: op)
- `antidupe.alerts` - Receive automatic duplicate alerts (default: op)

## 🔧 Installation

### **Requirements**
- **Minecraft Server**: 1.21.1+
- **Server Software**: Paper (Spigot not supported)
- **Java Version**: JDK 21 or higher
- **RAM**: Minimum 1GB available (recommended: 2GB+)

### **Installation Steps**
1. **Download** the latest release from [GitHub Releases](https://github.com/aari-dev/DupeProtection/releases)
2. **Stop** your Minecraft server
3. **Place** the `DupeProtection-x.x.x.jar` file in your server's `plugins/` directory
4. **Start** your server - the plugin will automatically generate default configuration files
5. **Configure** the plugin by editing `plugins/DupeProtection/config.yml`
6. **Reload** configuration with `/antidupe reload`

## ⚙️ Configuration

### **Color Customization**
DupeProtection supports full hex color customization:

```yaml
messages:
  # Use &#RRGGBB format for hex colors
  dupe-alert: "&#FF0000🚨 DUPE ALERT: &#AAFF00{player} &#FF0000may have duped items!"
  item-id: "&#AAFF00Item ID: &#FF0000{id}"
  duplicates-warning: "&#FF0000&lWARNING:&r &#FF0000{count} potential duplicates found!"
```

### **Performance Settings**
```yaml
settings:
  # Milliseconds between tracking actions per player (prevents spam)
  action-throttle-ms: 100
  
  # Maximum number of history entries to display  
  max-history-display: 10
  
  # Chance (1 in N) to track inventory click events
  inventory-scan-chance: 20
  
  # Enable automatic broadcast alerts
  broadcast-alerts: true
  
  # Minimum number of duplicates required to trigger alert
  min-duplicates-for-alert: 1
  
  # Actions that should NOT trigger alerts (reduce spam)
  ignored-alert-actions:
    - "LOGIN_SCAN"
    - "DEBUG_SCAN" 
    - "MOVED"
```

### **Alert Configuration**
```yaml
# Customize alert messages and behavior
messages:
  dupe-alert: "&#FF0000🚨 DUPE ALERT: &#AAFF00{player} &#FF0000detected with {count} duplicates!"
  
settings:
  broadcast-alerts: true                    # Enable/disable alerts
  min-duplicates-for-alert: 2              # Only alert when 2+ duplicates found
```

## 🛠️ Building from Source

### **Prerequisites**
- **Git**: For cloning the repository
- **JDK 21**: Java Development Kit 21 or higher
- **Gradle**: Included via Gradle Wrapper

### **Build Steps**
```bash
# Clone the repository
git clone https://github.com/aari-dev/DupeProtection.git
cd DupeProtection

# Build the plugin (Linux/Mac)
./gradlew clean shadowJar

# Build the plugin (Windows)
gradlew.bat clean shadowJar
```

**Output Location**: `build/libs/DupeProtection-1.0.0.jar`

### **Development Setup**
```bash
# Import into IntelliJ IDEA
./gradlew idea

# Import into Eclipse  
./gradlew eclipse

# Run tests
./gradlew test
```

## 📊 Technical Specifications

### **Architecture**
- **Storage Engine**: Custom binary format with async I/O
- **Concurrency**: Thread-safe operations with FastUtil collections
- **Memory Usage**: ~2MB base + ~50KB per 1,000 tracked items
- **CPU Impact**: <1% on modern hardware with async processing

### **Data Structures**
- **Item Database**: `Long2ObjectOpenHashMap` for O(1) lookups
- **History Tracking**: `ConcurrentHashMap` for thread-safe operations
- **Statistics**: `Object2LongOpenHashMap` for efficient counting

### **Security Features**
- **SHA-256 Hashing**: Cryptographically secure item fingerprinting
- **Permission System**: Granular access control
- **Action Logging**: Complete audit trail of all operations

## 🔌 API

DupeProtection provides a simple API for other plugins:

```java
// Get the plugin instance
DupeProtection plugin = (DupeProtection) Bukkit.getPluginManager().getPlugin("DupeProtection");

// Access the item registry
ItemRegistry registry = plugin.getItemRegistry();

// Register an item
long itemId = registry.registerItem(itemStack, "API_CREATED", "PluginName");

// Find duplicates
List<TrackedItem> duplicates = registry.findDuplicates(itemId);

// Get item history
List<ItemAction> history = registry.getItemHistory(itemId);
```

## 🤝 Contributing

We welcome contributions! Please follow these guidelines:

### **Getting Started**
1. **Fork** the repository on GitHub
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Follow** our coding standards (see `CONTRIBUTING.md`)
4. **Write** tests for new functionality
5. **Commit** changes with descriptive messages
6. **Push** to your branch (`git push origin feature/amazing-feature`)
7. **Create** a Pull Request with detailed description

### **Coding Standards**
- **Java 21** features and syntax
- **No comments** - self-documenting code only
- **Performance first** - all operations must be optimized
- **Async operations** - no blocking the main thread
- **FastUtil collections** for primitives

### **Issue Reporting**
When reporting bugs, please include:
- **Minecraft version** and **Paper build**
- **Plugin version** and **Java version**
- **Full error logs** with stack traces
- **Steps to reproduce** the issue
- **Expected vs actual behavior**

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for full details.

### **What this means:**
- ✅ **Commercial Use**: Use in commercial projects
- ✅ **Modification**: Modify the source code
- ✅ **Distribution**: Distribute original or modified versions
- ✅ **Private Use**: Use for personal projects
- ❗ **Attribution**: Must include original license and copyright

## 🆘 Support

### **Getting Help**
- **📖 Documentation**: Check this README and in-game `/dupe` help commands
- **🐛 Bug Reports**: [GitHub Issues](https://github.com/aari-dev/DupeProtection/issues)
- **💬 Discussion**: [GitHub Discussions](https://github.com/aari-dev/DupeProtection/discussions)
- **💬 Discord**: [Join our Discord](https://discord.gg/vFEeJkYZat)
- **📧 Direct Contact**: Create an issue on GitHub

### **Professional Support**
For enterprise deployments, custom modifications, or priority support, please contact us through GitHub Issues with the `enterprise-support` label.

---

<div align="center">

**Made with ❤️ for the Minecraft community**

[![Discord](https://img.shields.io/discord/YOUR_DISCORD_ID?color=7289da&label=Discord&logo=discord&logoColor=white)](https://discord.gg/vFEeJkYZat)
[![GitHub stars](https://img.shields.io/github/stars/aari-dev/DupeProtection?style=social)](https://github.com/aari-dev/DupeProtection/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/aari-dev/DupeProtection?style=social)](https://github.com/aari-dev/DupeProtection/network/members)

</div>