# BlockReports Plugin
**BlockReports** is a Minecraft Paper 26.2 plugin that prevents chat reporting by neutralising Minecraft's chat signature system and blocking report-related functionality. It also works around client-side chat blocking (like how the UK stops some accounts from chatting) by providing command-based chat, private messaging and emotes that still work for affected players. Although it was custom built for [minecraftoffline.net](https://www.minecraftoffline.net), any server can use it.

## Installation
1. Download the latest release [here](https://github.com/Jelly-Pudding/BlockReports/releases/latest).
2. Place the `.jar` file in your Minecraft server's `plugins` folder.
3. Restart your server.

## Features
* **Strips chat signatures** - Converts signed chat messages to unsigned system messages which makes reports unverifiable.
* **Bypasses client-side chat filters** - Renders chat as `name: message` rather than `<name> message` which prevents the client-side "friends only" and blocked-player filters from hiding messages between players who aren't friends.
* **Hides warning popups** - Eliminates the annoying "Chat messages can't be verified" popup for players.
* **Neutralises chat sessions** - Intercepts chat session update packets and replaces public keys with null values to prevent secure signature establishment.
* **Prevents chat-related kicks** - Stops players from being kicked for signature validation issues.
* **Automatic server configuration** - Disables `enforce-secure-profile` in server properties (this setting forces players to have signed profiles to join).
* **Works around client-side chat blocking** - Some accounts are blocked from chatting client-side (for example UK players will see a message such as *"Sending chat messages is not allowed"*). For these players, there is a `/chat` command (alias `/c`) that sends a normal (unsigned and unreportable) chat message. Since a blocked account can't be detected server-side, the `chat-command-hint` configuration option shows a `/chat` hint on join to any player who hasn't sent a message yet.
* **Private messaging for blocked players** - Vanilla `/msg`, `/tell` and `/w` do not work for players with chatting blocked client-side as they carry a signed message argument so BlockReports provides its own `/msg`, `/tell`, `/w`, `/t` and `/r` commands that work for them. An unsigned `/me` emote command is also provided for the same reason.
* **Lightweight and efficient** - Minimal performance impact with targeted packet manipulation.

## Commands
* `/blockreports reload` - Reloads the plugin configuration.
* `/chat <message>` (alias `/c`) - Sends a chat message via a command which bypasses client-side chat blocking.
* `/msg <player> <message>` (aliases `/tell`, `/w`, `/t`) - Sends a private message. Works for players blocked from chatting unlike the vanilla command.
* `/r <message>` (alias `/reply`) - Replies to the last private message.
* `/me <action>` - Sends an emote/action message. Works for players blocked from chatting.

## Permissions
* `blockreports.admin` - Allows use of BlockReports commands. (Default: op)
* `blockreports.chat` - Allows sending messages with the `/chat` command. (Default: true)
* `blockreports.message` - Allows using the private message commands. (Default: true)
* `blockreports.me` - Allows using the `/me` emote command. (Default: true)

## Configuration
The plugin creates a `config.yml` file with the following options:

```yaml
# Strip chat signatures from outgoing chat packets.
# This converts signed chat messages to unsigned system messages.
strip-server-signatures: true

# Hide the secure chat warning popup.
hide-secure-chat-warning: true

# Neutralise chat session update packets from clients.
# This intercepts incoming chat session packets and replaces the public key with null,
# preventing secure chat establishment while maintaining proper packet flow to avoid kick issues.
neutralise-chat-sessions: true

# Prevent kicks related to chat reporting.
# This handles various kick scenarios related to secure chat.
prevent-chat-kicks: true

# Provide a /chat command (alias /c) for sending messages.
# Some accounts are blocked from chatting client-side (for example players in the UK).
# /chat <message> is sent as a normal (unsigned and unreportable) chat message.
enable-chat-command: true

# Hint players about /chat on join (until they send their first message).
# Useful for players blocked from chatting client-side.
chat-command-hint: true

# Enable debug logging - only enable this for development
# as it's verbose.
enable-logging: false
```

## How It Works
BlockReports operates by:
1. **Intercepting outbound chat packets** and removing cryptographic signatures.
2. **Pretends the enforce-secure-profile flag is true** in login packets to prevent client warnings.
3. **Neutralising incoming chat session updates** to prevent signature key establishment.
4. **Converting signed messages to system messages** that cannot be reported.

This approach ensures chat reports become unverifiable and ineffective while maintaining full chat functionality.

## Support Me
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/K3K715TC1R)
