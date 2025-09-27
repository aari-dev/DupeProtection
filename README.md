# AntiDupe

Advanced Minecraft Paper plugin for detecting item duplication exploits through sophisticated tracking and analysis.

## Features

- **Item Fingerprinting**: SHA-256 based item identification system
- **Real-time Duplicate Detection**: Instant detection of identical items
- **Comprehensive History Tracking**: Full audit trail of item actions
- **High-Performance Architecture**: Optimized for large servers with minimal overhead
- **Asynchronous Processing**: Non-blocking operations to maintain server TPS
- **Memory-Efficient Storage**: FastUtil collections for optimal memory usage

## Commands

### `/id check`
Displays the unique ID of the item in your main hand. Automatically registers new items and warns about potential duplicates.

### `/id lookup <id>`
Shows all items with the same fingerprint as the specified item ID, revealing potential duplicates.

### `/item history [id]`
Displays the complete action history for an item. If no ID is specified, uses the item in your main hand.

## Permissions

- `antidupe.admin` - Access to all AntiDupe commands (default: op)

## Technical Details

### Performance Optimizations
- **FastUtil Collections**: Specialized collections for primitive types
- **Async File I/O**: Non-blocking disk operations using NIO.2
- **SHA-256 Fingerprinting**: Cryptographic hashing for reliable item identification
- **Action Throttling**: Prevents spam logging while maintaining accuracy
- **Memory-Mapped Storage**: Efficient data persistence

### Architecture
- **ItemRegistry**: Core tracking system with thread-safe operations
- **ItemIdentifier**: Cryptographic fingerprinting utility
- **Async Listeners**: Event processing without blocking game thread
- **Persistent Storage**: Binary format for minimal disk footprint

## Building

```bash
./gradlew shadowJar
```

Output JAR will be in `build/libs/AntiDupe-1.0.0.jar`

## Installation

1. Place the JAR in your server's `plugins/` directory
2. Restart the server
3. Plugin will automatically create necessary data files

## Data Storage

The plugin stores tracking data in `plugins/AntiDupe/items.dat` using an optimized binary format. Data is automatically persisted asynchronously to prevent server lag.

## Requirements

- Minecraft 1.21.1
- Paper Server
- Java 21+

## Performance Impact

AntiDupe is designed for zero-impact operation:
- Async processing prevents TPS drops
- Memory-efficient data structures
- Minimal event processing overhead
- Smart action throttling