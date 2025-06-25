# BlockReports Plugin
**BlockReports** is a Minecraft Paper 1.21.6 plugin that prevents chat reporting by neutralising Minecraft's chat signature system and blocking report-related functionality.

## Installation
1. Download the latest release [here](https://github.com/Jelly-Pudding/BlockReports/releases/latest).
2. Place the `.jar` file in your Minecraft server's `plugins` folder.
3. Restart your server.

## Features
* **Strips chat signatures** - Converts signed chat messages to unsigned system messages, making reports unverifiable.
* **Hides warning popups** - Eliminates the annoying "Chat messages can't be verified" popup for players.
* **Neutralises chat sessions** - Intercepts chat session update packets and replaces public keys with null values to prevent secure signature establishment.
* **Prevents chat-related kicks** - Stops players from being kicked for signature validation issues.
* **Automatic server configuration** - Disables `enforce-secure-profile` in server properties (this setting forces players to have signed profiles to join).
* **Lightweight and efficient** - Minimal performance impact with targeted packet manipulation.

## Commands
* `/blockreports reload` - Reloads the plugin configuration.

## Permissions
* `blockreports.admin` - Allows use of BlockReports commands. (Default: op)

## Configuration
The plugin creates a `config.yml` file with the following options:

```yaml
# Strip chat signatures from outgoing chat packets
# This converts signed chat messages to unsigned system messages
strip-server-signatures: true

# Hide the secure chat warning popup
hide-secure-chat-warning: true

# Neutralise chat session update packets from clients  
# This intercepts incoming chat session packets and replaces the public key with null,
# preventing secure chat establishment while maintaining proper packet flow to avoid kick issues
neutralise-chat-sessions: true

# Prevent kicks related to chat reporting
# This handles various kick scenarios related to secure chat
prevent-chat-kicks: true

# Enable debug logging - only enable this for development
# as it's verbose.
enable-logging: true
```

## How It Works
BlockReports operates by:
1. **Intercepting outbound chat packets** and removing cryptographic signatures
2. **Spoofing the secure profile flag** in login packets to prevent client warnings
3. **Neutralising incoming chat session updates** to prevent signature key establishment
4. **Converting signed messages to system messages** that cannot be reported

This approach ensures chat reports become unverifiable and ineffective while maintaining full chat functionality.

## Support Me
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/K3K715TC1R)
