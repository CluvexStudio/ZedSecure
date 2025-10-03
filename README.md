# Zed-Secure VPN

A professional VPN Android application built with Flutter and Kotlin, featuring support for V2Ray/Xray protocols.

## Features

- 🔒 **Multiple Protocols**: VMess, VLESS, Trojan, Shadowsocks
- 🌐 **Transport Types**: TCP, WebSocket, HTTP/2, gRPC, QUIC
- 📊 **Real-time Statistics**: Upload/Download speed, connection time
- 🎯 **Per-App Proxy**: Route specific apps through VPN
- 🔄 **Subscription Management**: Auto-update from subscription URLs
- 📱 **Modern UI**: Fluent Design with Glassmorphism effects
- ⚡ **Server Ping Testing**: Test latency for all servers
- 📋 **Clipboard Import**: Import configs directly from clipboard

## Screenshots

_Add screenshots here_

## Tech Stack

- **Frontend**: Flutter 3.x with Fluent UI
- **Backend**: Kotlin
- **VPN Core**: Xray Core 25.3.6 (via flutter_v2ray)
- **Architecture**: Hybrid (Flutter UI + Kotlin Native Layer)

## Requirements

- Android 7.0 (API 24) or higher
- Flutter SDK 3.7.2+
- Android NDK 27.0.12077973

## Installation

### Download APK

Download the latest APK from [Releases](https://github.com/CluvexStudio/ZedSecure/releases)

### Build from Source

1. Clone the repository:
```bash
git clone https://github.com/YOUR_USERNAME/zedsecure.git
cd zedsecure
```

2. Install dependencies:
```bash
flutter pub get
```

3. Build APK:
```bash
# For ARM64 (recommended)
flutter build apk --release --target-platform android-arm64

# For ARMv7
flutter build apk --release --target-platform android-arm

# For Universal APK (all architectures)
flutter build apk --release
```

## Configuration

### Supported Config Formats

- **VMess**: `vmess://base64-encoded-json`
- **VLESS**: `vless://uuid@host:port?params#remark`
- **Trojan**: `trojan://password@host:port?params#remark`
- **Shadowsocks**: `ss://base64(method:password@host:port)#remark`

### Adding Servers

1. **From Subscription**: Add subscription URL in Subscriptions tab
2. **From Clipboard**: Copy config and tap Paste button in Servers tab
3. **Manual**: Add individual server configs

## Features in Detail

### Per-App Proxy
Route only specific apps through VPN while others use direct connection.

### Ping Testing
Test server latency with ICMP and TCP ping methods. Automatically sort servers by lowest ping.

### Subscription Management
- Auto-update servers from subscription URLs
- Separate manual and subscription configs
- Suggested subscription

## Project Structure

```
zedsecure/
├── android/                    # Android native code
│   └── app/src/main/kotlin/   # Kotlin implementation
│       ├── PingService.kt     # Native ping implementation
│       ├── PingMethodChannel.kt
│       ├── AppListMethodChannel.kt
│       └── SettingsMethodChannel.kt
├── lib/
│   ├── models/                # Data models
│   ├── screens/               # UI screens
│   ├── services/              # Business logic
│   └── theme/                 # App theme
└── local_packages/
    └── flutter_v2ray_client/  # V2Ray integration
```

## Dependencies

- `fluent_ui: ^4.11.5` - Microsoft Fluent Design
- `glassmorphism: ^3.0.0` - Glassmorphic effects
- `flutter_v2ray_client` - Xray Core integration
- `provider: ^6.1.1` - State management
- `shared_preferences: ^2.2.2` - Local storage
- `connectivity_plus: ^5.0.0` - Network monitoring

## Security

- All sensitive data is encrypted using SharedPreferences
- No user data is collected or transmitted
- Open source - audit the code yourself

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)** with additional terms.

### Key License Terms:
- ✅ **Attribution Required**: Must credit CluvexStudio and link to this repository
- ✅ **Open Source**: Any derivative work must be open source under GPL-3.0
- ✅ **Share Alike**: Modifications must be shared under the same license
- ⚠️ **Commercial Use**: Requires explicit written permission from CluvexStudio
- ❌ **Prohibited**: Removing credits, claiming as your own, privacy violations

**TL;DR**: You can use, modify, and distribute this code freely, but you MUST:
1. Keep it open source
2. Credit the original author (CluvexStudio)
3. Link to: https://github.com/CluvexStudio/ZedSecure
4. Share your modifications under GPL-3.0

For commercial use or custom licensing, contact via GitHub Issues.

See the [LICENSE](LICENSE) file for full legal terms.

## Acknowledgments

- [flutter_v2ray](https://pub.dev/packages/flutter_v2ray) - V2Ray integration
- [Xray-core](https://github.com/XTLS/Xray-core) - VPN core
- [Fluent UI](https://pub.dev/packages/fluent_ui) - UI framework

## Disclaimer

This application is for educational purposes only. Users are responsible for complying with local laws and regulations.

## Support

If you find this project helpful, please give it a ⭐ on GitHub!

## Contact

- GitHub: [https://github.com/CluvexStudio/ZedSecure)
- Issues: [GitHub Issues](https://github.com/CluvexStudio/ZedSecure/issues)
- 
Telegram Channel: https://t.me/CluvexStudio
