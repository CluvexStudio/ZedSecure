import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:zedsecure/theme/app_theme.dart';

class PerAppProxyScreen extends StatefulWidget {
  const PerAppProxyScreen({super.key});

  @override
  State<PerAppProxyScreen> createState() => _PerAppProxyScreenState();
}

class _PerAppProxyScreenState extends State<PerAppProxyScreen> {
  List<Map<String, dynamic>> _apps = [];
  List<String> _selectedApps = [];
  bool _isLoading = true;
  String _searchQuery = '';

  static const MethodChannel _appListChannel = MethodChannel(
    'com.zedsecure.vpn/app_list',
  );

  @override
  void initState() {
    super.initState();
    _loadApps();
  }

  Future<void> _loadApps() async {
    setState(() {
      _isLoading = true;
    });

    try {
      final prefs = await SharedPreferences.getInstance();
      _selectedApps = prefs.getStringList('blocked_apps') ?? [];

      final List<dynamic> result = await _appListChannel.invokeMethod(
        'getInstalledApps',
      );

      setState(() {
        _apps = result
            .map(
              (app) => {
                'packageName': app['packageName'] as String,
                'name': app['name'] as String,
                'isSystemApp': app['isSystemApp'] as bool,
              },
            )
            .toList();
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _saveSelection() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList('blocked_apps', _selectedApps);
  }

  List<Map<String, dynamic>> get _filteredApps {
    if (_searchQuery.isEmpty) return _apps;
    return _apps.where((app) {
      return app['name'].toString().toLowerCase().contains(
            _searchQuery.toLowerCase(),
          ) ||
          app['packageName'].toString().toLowerCase().contains(
            _searchQuery.toLowerCase(),
          );
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    return ScaffoldPage(
      header: PageHeader(
        title: const Text(
          'Per-App Proxy',
          style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
        ),
        commandBar: FilledButton(
          onPressed: () async {
            await _saveSelection();
            if (context.mounted) {
              await displayInfoBar(
                context,
                builder: (context, close) {
                  return const InfoBar(
                    title: Text('Saved'),
                    content: Text('App selection saved successfully'),
                    severity: InfoBarSeverity.success,
                  );
                },
                duration: const Duration(seconds: 2),
              );
            }
          },
          child: const Text('Save'),
        ),
      ),
      content: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Select apps to route through VPN',
                  style: TextStyle(fontSize: 14),
                ),
                const SizedBox(height: 12),
                TextBox(
                  placeholder: 'Search apps...',
                  prefix: const Padding(
                    padding: EdgeInsets.only(left: 12),
                    child: Icon(FluentIcons.search),
                  ),
                  onChanged: (value) {
                    setState(() {
                      _searchQuery = value;
                    });
                  },
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('${_selectedApps.length} apps selected'),
                Row(
                  children: [
                    Button(
                      onPressed: () {
                        setState(() {
                          _selectedApps = _apps
                              .map((app) => app['packageName'] as String)
                              .toList();
                        });
                      },
                      child: const Text('Select All'),
                    ),
                    const SizedBox(width: 8),
                    Button(
                      onPressed: () {
                        setState(() {
                          _selectedApps.clear();
                        });
                      },
                      child: const Text('Clear All'),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Expanded(
            child: _isLoading
                ? const Center(child: ProgressRing())
                : _filteredApps.isEmpty
                ? const Center(child: Text('No apps found'))
                : ListView.builder(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemCount: _filteredApps.length,
                    itemBuilder: (context, index) {
                      final app = _filteredApps[index];
                      final packageName = app['packageName'] as String;
                      final isSelected = _selectedApps.contains(packageName);

                      return Container(
                        margin: const EdgeInsets.only(bottom: 8),
                        decoration: AppTheme.glassDecoration(
                          borderRadius: 8,
                          opacity: isSelected ? 0.1 : 0.05,
                        ),
                        child: ListTile(
                          title: Text(app['name'] as String),
                          subtitle: Text(packageName),
                          trailing: Checkbox(
                            checked: isSelected,
                            onChanged: (value) {
                              setState(() {
                                if (value == true) {
                                  _selectedApps.add(packageName);
                                } else {
                                  _selectedApps.remove(packageName);
                                }
                              });
                            },
                          ),
                          onPressed: () {
                            setState(() {
                              if (isSelected) {
                                _selectedApps.remove(packageName);
                              } else {
                                _selectedApps.add(packageName);
                              }
                            });
                          },
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
