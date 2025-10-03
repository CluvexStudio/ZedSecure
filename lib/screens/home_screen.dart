import 'package:fluent_ui/fluent_ui.dart';
import 'package:provider/provider.dart';
import 'package:zedsecure/services/v2ray_service.dart';
import 'package:zedsecure/theme/app_theme.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  bool _isConnecting = false;

  @override
  Widget build(BuildContext context) {
    return Consumer<V2RayService>(
      builder: (context, v2rayService, child) {
        final isConnected = v2rayService.isConnected;
        final activeConfig = v2rayService.activeConfig;
        final status = v2rayService.currentStatus;

        return ScaffoldPage(
          content: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    constraints: const BoxConstraints(maxWidth: 500),
                    padding: const EdgeInsets.all(32),
                    decoration: AppTheme.glassDecoration(
                      borderRadius: 24,
                      opacity: 0.05,
                    ),
                    child: Column(
                      children: [
                        Stack(
                          alignment: Alignment.center,
                          children: [
                            Container(
                              width: 160,
                              height: 160,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                gradient: RadialGradient(
                                  colors: [
                                    (isConnected
                                            ? AppTheme.connectedGreen
                                            : Colors.blue)
                                        .withOpacity(0.3),
                                    (isConnected
                                            ? AppTheme.connectedGreen
                                            : Colors.blue)
                                        .withOpacity(0.1),
                                    Colors.transparent,
                                  ],
                                ),
                              ),
                            ),
                            GestureDetector(
                              onTap: _isConnecting
                                  ? null
                                  : () => _handleConnectionToggle(v2rayService),
                              child: AnimatedContainer(
                                duration: const Duration(milliseconds: 300),
                                width: 120,
                                height: 120,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  gradient: LinearGradient(
                                    colors: isConnected
                                        ? [
                                            AppTheme.connectedGreen,
                                            AppTheme.connectedGreen.withOpacity(
                                              0.7,
                                            ),
                                          ]
                                        : [
                                            AppTheme.primaryGradientStart,
                                            AppTheme.primaryGradientEnd,
                                          ],
                                    begin: Alignment.topLeft,
                                    end: Alignment.bottomRight,
                                  ),
                                  boxShadow: [
                                    BoxShadow(
                                      color:
                                          (isConnected
                                                  ? AppTheme.connectedGreen
                                                  : Colors.blue)
                                              .withOpacity(0.5),
                                      blurRadius: 20,
                                      spreadRadius: 5,
                                    ),
                                  ],
                                ),
                                child: Center(
                                  child: _isConnecting
                                      ? const SizedBox(
                                          width: 40,
                                          height: 40,
                                          child: ProgressRing(),
                                        )
                                      : Icon(
                                          isConnected
                                              ? FluentIcons.plug_disconnected
                                              : FluentIcons.plug_connected,
                                          size: 50,
                                          color: Colors.white,
                                        ),
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 24),
                        Text(
                          isConnected ? 'Connected' : 'Disconnected',
                          style: TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.w600,
                            color: isConnected
                                ? AppTheme.connectedGreen
                                : Colors.grey[100],
                          ),
                        ),
                        const SizedBox(height: 16),
                        if (activeConfig != null) ...[
                          Text(
                            activeConfig.remark,
                            style: const TextStyle(fontSize: 18),
                            textAlign: TextAlign.center,
                          ),
                          const SizedBox(height: 8),
                          Text(
                            '${activeConfig.address}:${activeConfig.port}',
                            style: TextStyle(
                              fontSize: 14,
                              color: Colors.grey[100],
                            ),
                          ),
                          const SizedBox(height: 8),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 12,
                              vertical: 6,
                            ),
                            decoration: BoxDecoration(
                              color: Colors.blue.withOpacity(0.2),
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: Text(
                              activeConfig.protocolDisplay,
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.blue,
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  if (isConnected && status != null)
                    Container(
                      constraints: const BoxConstraints(maxWidth: 500),
                      padding: const EdgeInsets.all(24),
                      decoration: AppTheme.glassDecoration(
                        borderRadius: 16,
                        opacity: 0.05,
                      ),
                      child: Column(
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceAround,
                            children: [
                              _buildStatCard(
                                'Upload',
                                AppTheme.formatSpeed(status.uploadSpeed),
                                FluentIcons.up,
                                Colors.green,
                              ),
                              _buildStatCard(
                                'Download',
                                AppTheme.formatSpeed(status.downloadSpeed),
                                FluentIcons.down,
                                Colors.blue,
                              ),
                            ],
                          ),
                          const SizedBox(height: 16),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceAround,
                            children: [
                              _buildStatCard(
                                'Uploaded',
                                AppTheme.formatBytes(status.upload),
                                FluentIcons.cloud_upload,
                                Colors.orange,
                              ),
                              _buildStatCard(
                                'Downloaded',
                                AppTheme.formatBytes(status.download),
                                FluentIcons.cloud_download,
                                Colors.purple,
                              ),
                            ],
                          ),
                          const SizedBox(height: 16),
                          Text(
                            'Duration: ${_formatDuration(status.duration)}',
                            style: TextStyle(
                              fontSize: 16,
                              color: Colors.grey[100],
                            ),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildStatCard(
    String label,
    String value,
    IconData icon,
    Color color,
  ) {
    return Expanded(
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 4),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: color.withOpacity(0.1),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: color.withOpacity(0.3)),
        ),
        child: Column(
          children: [
            Icon(icon, color: color, size: 24),
            const SizedBox(height: 8),
            Text(
              label,
              style: TextStyle(fontSize: 12, color: Colors.grey[100]),
            ),
            const SizedBox(height: 4),
            Text(
              value,
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: color,
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _formatDuration(String duration) {
    try {
      final parts = duration.split(':');
      if (parts.length == 3) {
        return '${parts[0]}h ${parts[1]}m ${parts[2]}s';
      }
      return duration;
    } catch (e) {
      return duration;
    }
  }

  Future<void> _handleConnectionToggle(V2RayService service) async {
    setState(() {
      _isConnecting = true;
    });

    try {
      if (service.isConnected) {
        await service.disconnect();
      } else {
        final selectedConfig = await service.loadSelectedConfig();
        if (selectedConfig == null) {
          final configs = await service.loadConfigs();
          if (configs.isEmpty) {
            if (mounted) {
              await displayInfoBar(
                context,
                builder: (context, close) {
                  return const InfoBar(
                    title: Text('No Servers'),
                    content: Text('Please add servers from the Servers tab'),
                    severity: InfoBarSeverity.warning,
                  );
                },
                duration: const Duration(seconds: 3),
              );
            }
          } else {
            if (mounted) {
              await displayInfoBar(
                context,
                builder: (context, close) {
                  return const InfoBar(
                    title: Text('No Server Selected'),
                    content: Text(
                      'Please select a server from the Servers tab',
                    ),
                    severity: InfoBarSeverity.info,
                  );
                },
                duration: const Duration(seconds: 3),
              );
            }
          }
        } else {
          final success = await service.connect(selectedConfig);
          if (mounted) {
            await displayInfoBar(
              context,
              builder: (context, close) {
                return InfoBar(
                  title: Text(success ? 'Connected' : 'Connection Failed'),
                  content: Text(
                    success
                        ? 'Connected to ${selectedConfig.remark}'
                        : 'Failed to connect to server',
                  ),
                  severity: success
                      ? InfoBarSeverity.success
                      : InfoBarSeverity.error,
                );
              },
              duration: const Duration(seconds: 2),
            );
          }
        }
      }
    } finally {
      if (mounted) {
        setState(() {
          _isConnecting = false;
        });
      }
    }
  }
}
