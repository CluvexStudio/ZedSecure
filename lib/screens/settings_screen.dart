import 'package:fluent_ui/fluent_ui.dart';
import 'package:provider/provider.dart';
import 'package:zedsecure/services/v2ray_service.dart';
import 'package:zedsecure/theme/app_theme.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:zedsecure/screens/per_app_proxy_screen.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool _autoConnect = false;
  bool _killSwitch = false;
  bool _darkMode = true;

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _autoConnect = prefs.getBool('auto_connect') ?? false;
      _killSwitch = prefs.getBool('kill_switch') ?? false;
      _darkMode = prefs.getBool('dark_mode') ?? true;
    });
  }

  Future<void> _saveSetting(String key, bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(key, value);
  }

  @override
  Widget build(BuildContext context) {
    return ScaffoldPage(
      header: const PageHeader(
        title: Text(
          'Settings',
          style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
        ),
      ),
      content: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _buildSection('General', [
            _buildSettingTile(
              'Auto Connect',
              'Automatically connect on app start',
              FluentIcons.play_solid,
              _autoConnect,
              (value) {
                setState(() {
                  _autoConnect = value;
                });
                _saveSetting('auto_connect', value);
              },
            ),
            _buildSettingTile(
              'Kill Switch',
              'Block internet if VPN disconnects',
              FluentIcons.shield_solid,
              _killSwitch,
              (value) {
                setState(() {
                  _killSwitch = value;
                });
                _saveSetting('kill_switch', value);
              },
            ),
          ]),
          const SizedBox(height: 24),
          _buildSection('Network', [
            _buildNavigationTile(
              'Per-App Proxy',
              'Choose which apps use VPN',
              FluentIcons.permissions,
              () {
                Navigator.push(
                  context,
                  FluentPageRoute(
                    builder: (context) => const PerAppProxyScreen(),
                  ),
                );
              },
            ),
          ]),
          const SizedBox(height: 24),
          _buildSection('Appearance', [
            _buildSettingTile(
              'Dark Mode',
              'Use dark theme',
              FluentIcons.clear_night,
              _darkMode,
              (value) {
                setState(() {
                  _darkMode = value;
                });
                _saveSetting('dark_mode', value);
              },
            ),
          ]),
          const SizedBox(height: 24),
          _buildSection('Data', [
            _buildActionTile(
              'Clear Server Cache',
              'Clear all cached server data',
              FluentIcons.clear,
              () => _clearCache(),
            ),
            _buildActionTile(
              'Clear All Data',
              'Reset all settings and servers',
              FluentIcons.delete,
              () => _clearAllData(),
            ),
          ]),
          const SizedBox(height: 24),
          _buildSection('About', [
            _buildInfoTile('App Name', 'Zed-Secure'),
            _buildInfoTile('Version', '1.0.0'),
            _buildInfoTile('Build', '1'),
          ]),
        ],
      ),
    );
  }

  Widget _buildSection(String title, List<Widget> children) {
    return Container(
      decoration: AppTheme.glassDecoration(borderRadius: 12, opacity: 0.05),
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 16),
          ...children,
        ],
      ),
    );
  }

  Widget _buildSettingTile(
    String title,
    String subtitle,
    IconData icon,
    bool value,
    ValueChanged<bool> onChanged,
  ) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: ListTile(
        leading: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: Colors.blue.withOpacity(0.2),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Center(child: Icon(icon, color: Colors.blue, size: 20)),
        ),
        title: Text(title),
        subtitle: Text(subtitle, style: const TextStyle(fontSize: 12)),
        trailing: ToggleSwitch(checked: value, onChanged: onChanged),
      ),
    );
  }

  Widget _buildActionTile(
    String title,
    String subtitle,
    IconData icon,
    VoidCallback onPressed,
  ) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: ListTile(
        leading: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: Colors.orange.withOpacity(0.2),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Center(child: Icon(icon, color: Colors.orange, size: 20)),
        ),
        title: Text(title),
        subtitle: Text(subtitle, style: const TextStyle(fontSize: 12)),
        trailing: Button(onPressed: onPressed, child: const Text('Execute')),
      ),
    );
  }

  Widget _buildNavigationTile(
    String title,
    String subtitle,
    IconData icon,
    VoidCallback onPressed,
  ) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: ListTile(
        leading: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: Colors.blue.withOpacity(0.2),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Center(child: Icon(icon, color: Colors.blue, size: 20)),
        ),
        title: Text(title),
        subtitle: Text(subtitle, style: const TextStyle(fontSize: 12)),
        trailing: const Icon(FluentIcons.chevron_right),
        onPressed: onPressed,
      ),
    );
  }

  Widget _buildInfoTile(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label),
          Text(value, style: const TextStyle(fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }

  Future<void> _clearCache() async {
    await showDialog(
      context: context,
      builder: (context) => ContentDialog(
        title: const Text('Clear Cache'),
        content: const Text(
          'This will clear all cached server data including ping results.',
        ),
        actions: [
          Button(
            onPressed: () {
              Navigator.pop(context);
            },
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              Navigator.pop(context);

              final service = Provider.of<V2RayService>(context, listen: false);
              service.clearPingCache();

              if (mounted) {
                await displayInfoBar(
                  context,
                  builder: (context, close) {
                    return const InfoBar(
                      title: Text('Cache Cleared'),
                      severity: InfoBarSeverity.success,
                    );
                  },
                  duration: const Duration(seconds: 2),
                );
              }
            },
            child: const Text('Clear'),
          ),
        ],
      ),
    );
  }

  Future<void> _clearAllData() async {
    await showDialog(
      context: context,
      builder: (context) => ContentDialog(
        title: const Text('Clear All Data'),
        content: const Text(
          'This will delete all servers, subscriptions, and settings. This action cannot be undone.',
        ),
        actions: [
          Button(
            onPressed: () {
              Navigator.pop(context);
            },
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              Navigator.pop(context);

              final service = Provider.of<V2RayService>(context, listen: false);
              await service.saveConfigs([]);
              await service.saveSubscriptions([]);
              service.clearPingCache();

              final prefs = await SharedPreferences.getInstance();
              await prefs.clear();

              if (mounted) {
                await displayInfoBar(
                  context,
                  builder: (context, close) {
                    return const InfoBar(
                      title: Text('All Data Cleared'),
                      severity: InfoBarSeverity.warning,
                    );
                  },
                  duration: const Duration(seconds: 2),
                );
              }
            },
            child: const Text('Clear All'),
          ),
        ],
      ),
    );
  }
}
