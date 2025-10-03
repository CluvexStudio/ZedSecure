import 'package:fluent_ui/fluent_ui.dart';
import 'package:provider/provider.dart';
import 'package:zedsecure/services/v2ray_service.dart';
import 'package:zedsecure/models/v2ray_config.dart';
import 'package:zedsecure/theme/app_theme.dart';
import 'package:flutter/services.dart';
import 'package:qr_flutter/qr_flutter.dart';

class ServersScreen extends StatefulWidget {
  const ServersScreen({super.key});

  @override
  State<ServersScreen> createState() => _ServersScreenState();
}

class _ServersScreenState extends State<ServersScreen> {
  List<V2RayConfig> _configs = [];
  bool _isLoading = true;
  bool _isSorting = false;
  String _searchQuery = '';
  final Map<String, int?> _pingResults = {};
  String? _selectedConfigId;

  @override
  void initState() {
    super.initState();
    _loadConfigs();
    _loadSelectedConfig();
  }

  Future<void> _loadSelectedConfig() async {
    final service = Provider.of<V2RayService>(context, listen: false);
    final selected = await service.loadSelectedConfig();
    if (selected != null) {
      setState(() {
        _selectedConfigId = selected.id;
      });
    }
  }

  Future<void> _loadConfigs() async {
    setState(() {
      _isLoading = true;
    });

    final service = Provider.of<V2RayService>(context, listen: false);
    final configs = await service.loadConfigs();

    setState(() {
      _configs = configs;
      _isLoading = false;
    });
  }

  Future<void> _pingAllServers() async {
    setState(() {
      _isSorting = true;
      _pingResults.clear();
    });

    final service = Provider.of<V2RayService>(context, listen: false);
    
    for (int i = 0; i < _configs.length; i++) {
      final config = _configs[i];
      try {
        final ping = await service.getServerDelay(config);
        if (mounted) {
          setState(() {
            _pingResults[config.id] = ping ?? -1;
          });
        }
      } catch (e) {
        if (mounted) {
          setState(() {
            _pingResults[config.id] = -1;
          });
        }
      }
      
      await Future.delayed(const Duration(milliseconds: 100));
    }

    if (mounted) {
      _sortByPing();
      setState(() {
        _isSorting = false;
      });
    }
  }

  void _sortByPing() {
    setState(() {
      _configs.sort((a, b) {
        final pingA = _pingResults[a.id] ?? 999999;
        final pingB = _pingResults[b.id] ?? 999999;
        
        if (pingA == -1 && pingB == -1) return 0;
        if (pingA == -1) return 1;
        if (pingB == -1) return -1;
        
        return pingA.compareTo(pingB);
      });
    });
  }

  List<V2RayConfig> get _filteredConfigs {
    if (_searchQuery.isEmpty) return _configs;
    return _configs.where((config) {
      return config.remark.toLowerCase().contains(_searchQuery.toLowerCase()) ||
          config.address.toLowerCase().contains(_searchQuery.toLowerCase()) ||
          config.configType.toLowerCase().contains(_searchQuery.toLowerCase());
    }).toList();
  }

  List<V2RayConfig> get _manualConfigs {
    return _filteredConfigs.where((config) => config.source == 'manual').toList();
  }

  List<V2RayConfig> get _subscriptionConfigs {
    return _filteredConfigs.where((config) => config.source == 'subscription').toList();
  }

  Future<void> _importFromClipboard() async {
    try {
      final clipboardData = await Clipboard.getData(Clipboard.kTextPlain);
      if (clipboardData == null || clipboardData.text == null || clipboardData.text!.isEmpty) {
        if (mounted) {
          await displayInfoBar(
            context,
            builder: (context, close) {
              return const InfoBar(
                title: Text('Empty Clipboard'),
                content: Text('Please copy a config first'),
                severity: InfoBarSeverity.warning,
              );
            },
            duration: const Duration(seconds: 2),
          );
        }
        return;
      }

      final service = Provider.of<V2RayService>(context, listen: false);
      final config = await service.parseConfigFromClipboard(clipboardData.text!);

      if (config != null) {
        await _loadConfigs();
        if (mounted) {
          await displayInfoBar(
            context,
            builder: (context, close) {
              return InfoBar(
                title: const Text('Config Added'),
                content: Text('${config.remark} added successfully'),
                severity: InfoBarSeverity.success,
              );
            },
            duration: const Duration(seconds: 2),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        await displayInfoBar(
          context,
          builder: (context, close) {
            return InfoBar(
              title: const Text('Import Failed'),
              content: Text(e.toString()),
              severity: InfoBarSeverity.error,
            );
          },
          duration: const Duration(seconds: 3),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return ScaffoldPage(
      header: PageHeader(
        title: const Text('Servers', style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold)),
        commandBar: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            FilledButton(
              onPressed: _importFromClipboard,
              child: const Icon(FluentIcons.paste, size: 16),
            ),
            const SizedBox(width: 8),
            FilledButton(
              onPressed: _isSorting ? null : _pingAllServers,
              child: _isSorting
                  ? const SizedBox(width: 20, height: 20, child: ProgressRing())
                  : const Icon(FluentIcons.sort, size: 16),
            ),
            const SizedBox(width: 8),
            FilledButton(
              onPressed: _loadConfigs,
              child: const Icon(FluentIcons.refresh, size: 16),
            ),
          ],
        ),
      ),
      content: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: TextBox(
              placeholder: 'Search servers...',
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
          ),
          Expanded(
            child: _isLoading
                ? const Center(child: ProgressRing())
                : _filteredConfigs.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(FluentIcons.server, size: 64, color: Colors.grey[80]),
                            const SizedBox(height: 16),
                            const Text(
                              'No servers found',
                              style: TextStyle(fontSize: 18),
                            ),
                            const SizedBox(height: 8),
                            const Text(
                              'Add servers from Subscriptions',
                              style: TextStyle(fontSize: 14),
                            ),
                          ],
                        ),
                      )
                    : ListView(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        children: [
                          if (_manualConfigs.isNotEmpty) ...[
                            Padding(
                              padding: const EdgeInsets.only(bottom: 12, top: 8),
                              child: Row(
                                children: [
                                  const Icon(FluentIcons.edit, size: 16),
                                  const SizedBox(width: 8),
                                  Text(
                                    'Manual Configs (${_manualConfigs.length})',
                                    style: const TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            ..._manualConfigs.map((config) => _buildServerCard(config)),
                            const SizedBox(height: 24),
                          ],
                          if (_subscriptionConfigs.isNotEmpty) ...[
                            Padding(
                              padding: const EdgeInsets.only(bottom: 12, top: 8),
                              child: Row(
                                children: [
                                  const Icon(FluentIcons.cloud_download, size: 16),
                                  const SizedBox(width: 8),
                                  Text(
                                    'Subscription Configs (${_subscriptionConfigs.length})',
                                    style: const TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            ..._subscriptionConfigs.map((config) => _buildServerCard(config)),
                          ],
                        ],
                      ),
          ),
        ],
      ),
    );
  }

  Widget _buildServerCard(V2RayConfig config) {
    final ping = _pingResults[config.id];
    final service = Provider.of<V2RayService>(context, listen: false);
    final isConnected = service.activeConfig?.id == config.id;
    final isSelected = _selectedConfigId == config.id;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: AppTheme.glassDecoration(
        borderRadius: 12, 
        opacity: isConnected ? 0.15 : (isSelected ? 0.1 : 0.05),
      ),
      child: ListTile(
        leading: Container(
          width: 48,
          height: 48,
          decoration: BoxDecoration(
            color: AppTheme.getPingColor(ping).withValues(alpha: 0.2),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Center(
            child: Icon(
              _getProtocolIcon(config.configType),
              color: AppTheme.getPingColor(ping),
              size: 24,
            ),
          ),
        ),
        title: Text(
          config.remark,
          style: const TextStyle(fontWeight: FontWeight.bold),
        ),
        subtitle: Text('${config.address}:${config.port} • ${config.protocolDisplay}'),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (ping != null && ping >= 0)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: AppTheme.getPingColor(ping).withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Text(
                  '${ping}ms',
                  style: TextStyle(
                    color: AppTheme.getPingColor(ping),
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            if (ping != null && ping == -1)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: Colors.grey.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: const Text(
                  '-1 ms',
                  style: TextStyle(
                    color: Colors.grey,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            if (ping == null)
              IconButton(
                icon: const Icon(FluentIcons.speed_high),
                onPressed: () => _pingSingleServer(config),
              ),
            const SizedBox(width: 8),
            if (!isConnected)
              IconButton(
                icon: Icon(
                  isSelected ? FluentIcons.radio_btn_on : FluentIcons.radio_btn_off,
                  color: isSelected ? Colors.blue : null,
                ),
                onPressed: () => _handleSelectConfig(config),
              ),
            const SizedBox(width: 8),
            IconButton(
              icon: Icon(
                isConnected ? FluentIcons.plug_disconnected : FluentIcons.plug_connected,
              ),
              onPressed: () => _handleConnect(config),
            ),
            // Add menu button for manual configs
            if (config.source == 'manual')
              IconButton(
                icon: const Icon(FluentIcons.more),
                onPressed: () => _showManualConfigMenu(config),
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _handleSelectConfig(V2RayConfig config) async {
    setState(() {
      _selectedConfigId = config.id;
    });

    final service = Provider.of<V2RayService>(context, listen: false);
    await service.saveSelectedConfig(config);

    if (mounted) {
      await displayInfoBar(
        context,
        builder: (context, close) {
          return InfoBar(
            title: const Text('Server Selected'),
            content: Text('${config.remark} is now selected'),
            severity: InfoBarSeverity.success,
          );
        },
        duration: const Duration(seconds: 2),
      );
    }
  }

  IconData _getProtocolIcon(String type) {
    switch (type.toLowerCase()) {
      case 'vmess':
        return FluentIcons.shield;
      case 'vless':
        return FluentIcons.shield_solid;
      case 'trojan':
        return FluentIcons.security_group;
      case 'shadowsocks':
        return FluentIcons.lock_solid;
      default:
        return FluentIcons.server;
    }
  }

  Future<void> _pingSingleServer(V2RayConfig config) async {
    final service = Provider.of<V2RayService>(context, listen: false);
    final ping = await service.getServerDelay(config);
    setState(() {
      _pingResults[config.id] = ping;
    });
  }

  Future<void> _handleConnect(V2RayConfig config) async {
    final service = Provider.of<V2RayService>(context, listen: false);

    if (service.activeConfig?.id == config.id) {
      await service.disconnect();
      if (mounted) {
        await displayInfoBar(
          context,
          builder: (context, close) {
            return const InfoBar(
              title: Text('Disconnected'),
              severity: InfoBarSeverity.info,
            );
          },
          duration: const Duration(seconds: 2),
        );
      }
    } else {
      if (service.isConnected) {
        await service.disconnect();
      }

      final success = await service.connect(config);
      if (mounted) {
        await displayInfoBar(
          context,
          builder: (context, close) {
            return InfoBar(
              title: Text(success ? 'Connected' : 'Connection Failed'),
              content: Text(
                success ? 'Connected to ${config.remark}' : 'Failed to connect to server',
              ),
              severity: success ? InfoBarSeverity.success : InfoBarSeverity.error,
            );
          },
          duration: const Duration(seconds: 2),
        );
      }
    }
  }

  Future<void> _showManualConfigMenu(V2RayConfig config) async {
    await showDialog(
      context: context,
      builder: (context) => ContentDialog(
        title: Text(config.remark),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: Icon(FluentIcons.copy),
              title: const Text('Copy Config Link'),
              onPressed: () {
                Navigator.pop(context);
                _copyConfigLink(config);
              },
            ),
            ListTile(
              leading: Icon(FluentIcons.camera),
              title: const Text('Show QR Code'),
              onPressed: () {
                Navigator.pop(context);
                _showQRCode(config);
              },
            ),
            ListTile(
              leading: Icon(FluentIcons.delete),
              title: const Text('Delete'),
              onPressed: () {
                Navigator.pop(context);
                _deleteConfig(config);
              },
            ),
          ],
        ),
        actions: [
          Button(
            onPressed: () {
              Navigator.pop(context);
            },
            child: const Text('Cancel'),
          ),
        ],
      ),
    );
  }

  Future<void> _copyConfigLink(V2RayConfig config) async {
    final service = Provider.of<V2RayService>(context, listen: false);
    await service.copyToClipboard(config.fullConfig);
    
    if (mounted) {
      await displayInfoBar(
        context,
        builder: (context, close) {
          return const InfoBar(
            title: Text('Copied'),
            content: Text('Config link copied to clipboard'),
            severity: InfoBarSeverity.success,
          );
        },
        duration: const Duration(seconds: 2),
      );
    }
  }

  Future<void> _showQRCode(V2RayConfig config) async {
    await _displayQRCode(config.fullConfig);
  }

  Future<void> _deleteConfig(V2RayConfig config) async {
    await showDialog(
      context: context,
      builder: (context) => ContentDialog(
        title: const Text('Delete Config'),
        content: Text('Are you sure you want to delete "${config.remark}"?'),
        actions: [
          Button(
            onPressed: () {
              Navigator.pop(context);
            },
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              Navigator.pop(context); // Close dialog
              await _performDeleteConfig(config);
            },
            child: const Text('Delete'),
          ),
        ],
      ),
    );
  }

  Future<void> _performDeleteConfig(V2RayConfig config) async {
    final service = Provider.of<V2RayService>(context, listen: false);
    
    // Load all configs
    final allConfigs = await service.loadConfigs();
    
    // Remove the selected config
    final updatedConfigs = allConfigs.where((c) => c.id != config.id).toList();
    
    // Save the updated list
    await service.saveConfigs(updatedConfigs);
    
    // Reload the configs in the UI
    await _loadConfigs();
    
    if (mounted) {
      await displayInfoBar(
        context,
        builder: (context, close) {
          return const InfoBar(
            title: Text('Deleted'),
            content: Text('Config deleted successfully'),
            severity: InfoBarSeverity.info,
          );
        },
        duration: const Duration(seconds: 2),
      );
    }
  }

  Future<void> _displayQRCode(String configString) async {
    await showDialog(
      context: context,
      builder: (context) => ContentDialog(
        title: const Text('QR Code'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              QrImageView(
                data: configString,
                version: QrVersions.auto,
                size: 200.0,
              ),
              const SizedBox(height: 16),
              const Text(
                'Scan this QR code with your VPN app',
                style: TextStyle(fontSize: 14),
              ),
            ],
          ),
        ),
        actions: [
          Button(
            onPressed: () {
              Navigator.pop(context);
            },
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }
}

