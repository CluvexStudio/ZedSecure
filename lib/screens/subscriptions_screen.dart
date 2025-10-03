import 'package:fluent_ui/fluent_ui.dart';
import 'package:provider/provider.dart';
import 'package:zedsecure/services/v2ray_service.dart';
import 'package:zedsecure/models/subscription.dart';
import 'package:zedsecure/theme/app_theme.dart';

class SubscriptionsScreen extends StatefulWidget {
  const SubscriptionsScreen({super.key});

  @override
  State<SubscriptionsScreen> createState() => _SubscriptionsScreenState();
}

class _SubscriptionsScreenState extends State<SubscriptionsScreen> {
  List<Subscription> _subscriptions = [];
  Subscription? _suggestedSubscription;
  bool _isLoading = true;
  bool _isSuggestedActive = false;

  @override
  void initState() {
    super.initState();
    _initializeSuggestedSubscription();
    _loadSubscriptions();
  }

  void _initializeSuggestedSubscription() {
    _suggestedSubscription = Subscription(
      id: 'suggested_cloudflare_plus',
      name: 'Suggested - IRDevs',
      url:
          'https://raw.githubusercontent.com/darkvpnapp/CloudflarePlus/refs/heads/main/proxy',
      lastUpdate: DateTime.now(),
      configCount: 0,
    );
  }

  Future<void> _loadSubscriptions() async {
    setState(() {
      _isLoading = true;
    });

    final service = Provider.of<V2RayService>(context, listen: false);
    final subs = await service.loadSubscriptions();

    final hasSuggested = subs.any(
      (sub) => sub.id == 'suggested_cloudflare_plus',
    );
    if (hasSuggested) {
      _isSuggestedActive = true;
    }

    setState(() {
      _subscriptions = subs;
      _isLoading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return ScaffoldPage(
      header: PageHeader(
        title: const Text(
          'Subscriptions',
          style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
        ),
        commandBar: FilledButton(
          onPressed: _showAddSubscriptionDialog,
          child: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(FluentIcons.add, size: 16),
              SizedBox(width: 8),
              Text('Add Subscription'),
            ],
          ),
        ),
      ),
      content: _isLoading
          ? const Center(child: ProgressRing())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                if (!_isSuggestedActive && _suggestedSubscription != null) ...[
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Row(
                      children: [
                        Icon(FluentIcons.cloud, size: 16, color: Colors.orange),
                        const SizedBox(width: 8),
                        const Text(
                          'Suggested Subscription',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                  ),
                  _buildSuggestedSubscriptionCard(_suggestedSubscription!),
                  const SizedBox(height: 24),
                ],
                if (_subscriptions.isNotEmpty) ...[
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Row(
                      children: [
                        const Icon(FluentIcons.cloud_download, size: 16),
                        const SizedBox(width: 8),
                        Text(
                          'My Subscriptions (${_subscriptions.length})',
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                  ),
                  ..._subscriptions.map((sub) => _buildSubscriptionCard(sub)),
                ],
                if (_subscriptions.isEmpty && _isSuggestedActive)
                  Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const SizedBox(height: 100),
                        Icon(
                          FluentIcons.cloud,
                          size: 64,
                          color: Colors.grey[80],
                        ),
                        const SizedBox(height: 16),
                        const Text(
                          'No custom subscriptions',
                          style: TextStyle(fontSize: 18),
                        ),
                        const SizedBox(height: 8),
                        const Text(
                          'Add a subscription to get started',
                          style: TextStyle(fontSize: 14),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
    );
  }

  Widget _buildSuggestedSubscriptionCard(Subscription subscription) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            Colors.orange.withOpacity(0.1),
            Colors.yellow.withOpacity(0.05),
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.orange.withOpacity(0.3), width: 1),
      ),
      child: ListTile(
        leading: Container(
          width: 48,
          height: 48,
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [Colors.orange, Colors.yellow],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(12),
            boxShadow: [
              BoxShadow(
                color: Colors.orange.withOpacity(0.3),
                blurRadius: 8,
                spreadRadius: 2,
              ),
            ],
          ),
          child: const Center(
            child: Icon(
              FluentIcons.cloud_download,
              color: Colors.white,
              size: 24,
            ),
          ),
        ),
        title: Text(
          subscription.name,
          style: const TextStyle(fontWeight: FontWeight.bold),
        ),
        subtitle: const Text('Free servers'),
        trailing: FilledButton(
          onPressed: () => _activateSuggestedSubscription(subscription),
          child: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(FluentIcons.add, size: 14),
              SizedBox(width: 6),
              Text('Activate'),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSubscriptionCard(Subscription subscription) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: AppTheme.glassDecoration(borderRadius: 12, opacity: 0.05),
      child: ListTile(
        leading: Container(
          width: 48,
          height: 48,
          decoration: BoxDecoration(
            color: Colors.blue.withOpacity(0.2),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Center(
            child: Icon(FluentIcons.cloud, color: Colors.blue, size: 24),
          ),
        ),
        title: Text(
          subscription.name,
          style: const TextStyle(fontWeight: FontWeight.bold),
        ),
        subtitle: Text(
          '${subscription.configCount} servers • Updated ${_formatDate(subscription.lastUpdate)}',
        ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              icon: const Icon(FluentIcons.refresh),
              onPressed: () => _updateSubscription(subscription),
            ),
            const SizedBox(width: 8),
            IconButton(
              icon: const Icon(FluentIcons.delete),
              onPressed: () => _deleteSubscription(subscription),
            ),
          ],
        ),
      ),
    );
  }

  String _formatDate(DateTime date) {
    final now = DateTime.now();
    final difference = now.difference(date);

    if (difference.inDays > 0) {
      return '${difference.inDays}d ago';
    } else if (difference.inHours > 0) {
      return '${difference.inHours}h ago';
    } else if (difference.inMinutes > 0) {
      return '${difference.inMinutes}m ago';
    } else {
      return 'Just now';
    }
  }

  Future<void> _showAddSubscriptionDialog() async {
    final nameController = TextEditingController();
    final urlController = TextEditingController();

    await showDialog(
      context: context,
      builder: (context) => ContentDialog(
        title: const Text('Add Subscription'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Name'),
            const SizedBox(height: 8),
            TextBox(controller: nameController, placeholder: 'My Subscription'),
            const SizedBox(height: 16),
            const Text('URL'),
            const SizedBox(height: 8),
            TextBox(controller: urlController, placeholder: 'https://...'),
          ],
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
              if (nameController.text.isEmpty || urlController.text.isEmpty) {
                return;
              }

              Navigator.pop(context);

              final service = Provider.of<V2RayService>(context, listen: false);

              try {
                final subscriptionId = DateTime.now().millisecondsSinceEpoch
                    .toString();
                final configs = await service.parseSubscriptionUrl(
                  urlController.text,
                  subscriptionId: subscriptionId,
                );

                // The configs already have the subscriptionId from parseSubscriptionUrl
                final subscriptionConfigs = configs;

                final existingConfigs = await service.loadConfigs();
                final allConfigs = [...existingConfigs, ...subscriptionConfigs];
                await service.saveConfigs(allConfigs);

                final subscription = Subscription(
                  id: subscriptionId,
                  name: nameController.text,
                  url: urlController.text,
                  lastUpdate: DateTime.now(),
                  configCount: subscriptionConfigs.length,
                );

                _subscriptions.add(subscription);
                await service.saveSubscriptions(_subscriptions);
                await _loadSubscriptions();

                if (mounted) {
                  await displayInfoBar(
                    context,
                    builder: (context, close) {
                      return InfoBar(
                        title: const Text('Success'),
                        content: Text(
                          'Added ${subscriptionConfigs.length} servers',
                        ),
                        severity: InfoBarSeverity.success,
                      );
                    },
                    duration: const Duration(seconds: 3),
                  );
                }
              } catch (e) {
                if (mounted) {
                  await displayInfoBar(
                    context,
                    builder: (context, close) {
                      return InfoBar(
                        title: const Text('Error'),
                        content: Text(e.toString()),
                        severity: InfoBarSeverity.error,
                      );
                    },
                    duration: const Duration(seconds: 3),
                  );
                }
              }
            },
            child: const Text('Add'),
          ),
        ],
      ),
    );
  }

  Future<void> _activateSuggestedSubscription(Subscription subscription) async {
    final service = Provider.of<V2RayService>(context, listen: false);

    try {
      final configs = await service.parseSubscriptionUrl(
        subscription.url,
        subscriptionId: subscription.id,
      );

      // Add subscription ID to configs
      final subscriptionConfigs = configs
          .map(
            (config) => config.copyWith(
              source: 'subscription',
              subscriptionId: subscription.id,
            ),
          )
          .toList();

      final existingConfigs = await service.loadConfigs();
      final allConfigs = [...existingConfigs, ...subscriptionConfigs];
      await service.saveConfigs(allConfigs);

      final activatedSub = subscription.copyWith(
        lastUpdate: DateTime.now(),
        configCount: subscriptionConfigs.length,
      );

      _subscriptions.add(activatedSub);
      await service.saveSubscriptions(_subscriptions);

      setState(() {
        _isSuggestedActive = true;
      });

      if (mounted) {
        await displayInfoBar(
          context,
          builder: (context, close) {
            return InfoBar(
              title: const Text('Subscription Activated'),
              content: Text('Added ${subscriptionConfigs.length} servers'),
              severity: InfoBarSeverity.success,
            );
          },
          duration: const Duration(seconds: 2),
        );
      }
    } catch (e) {
      if (mounted) {
        await displayInfoBar(
          context,
          builder: (context, close) {
            return InfoBar(
              title: const Text('Activation Failed'),
              content: Text(e.toString()),
              severity: InfoBarSeverity.error,
            );
          },
          duration: const Duration(seconds: 3),
        );
      }
    }
  }

  Future<void> _updateSubscription(Subscription subscription) async {
    final service = Provider.of<V2RayService>(context, listen: false);

    try {
      final configs = await service.parseSubscriptionUrl(
        subscription.url,
        subscriptionId: subscription.id,
      );

      final existingConfigs = await service.loadConfigs();

      // Create a map of fullConfig to identify configs
      final newConfigMap = <String, bool>{};
      for (var config in configs) {
        newConfigMap[config.fullConfig] = true;
      }

      // Filter out subscription configs that belong to this subscription but are deleted
      final filteredConfigs = existingConfigs.where((config) {
        // Keep all manual configs
        if (config.source == 'manual') return true;

        // Keep subscription configs that don't belong to this subscription
        if (config.source == 'subscription' &&
            config.subscriptionId != subscription.id) {
          return true;
        }

        // For configs belonging to this subscription, only keep if they're in the new list
        if (config.source == 'subscription' &&
            config.subscriptionId == subscription.id) {
          return newConfigMap.containsKey(config.fullConfig);
        }

        return true;
      }).toList();

      // Add subscription source info and subscriptionId to new configs
      final subscriptionConfigs = configs
          .map(
            (config) => config.copyWith(
              source: 'subscription',
              subscriptionId: subscription.id,
            ),
          )
          .toList();

      final allConfigs = [...filteredConfigs, ...subscriptionConfigs];
      await service.saveConfigs(allConfigs);

      final updatedSub = subscription.copyWith(
        lastUpdate: DateTime.now(),
        configCount: subscriptionConfigs.length,
      );

      final index = _subscriptions.indexWhere((s) => s.id == subscription.id);
      if (index != -1) {
        _subscriptions[index] = updatedSub;
      }

      await service.saveSubscriptions(_subscriptions);
      await _loadSubscriptions();

      if (mounted) {
        await displayInfoBar(
          context,
          builder: (context, close) {
            return InfoBar(
              title: const Text('Updated'),
              content: Text('Updated ${subscriptionConfigs.length} servers'),
              severity: InfoBarSeverity.success,
            );
          },
          duration: const Duration(seconds: 2),
        );
      }
    } catch (e) {
      if (mounted) {
        await displayInfoBar(
          context,
          builder: (context, close) {
            return InfoBar(
              title: const Text('Error'),
              content: Text(e.toString()),
              severity: InfoBarSeverity.error,
            );
          },
          duration: const Duration(seconds: 3),
        );
      }
    }
  }

  Future<void> _deleteSubscription(Subscription subscription) async {
    await showDialog(
      context: context,
      builder: (context) => ContentDialog(
        title: const Text('Delete Subscription'),
        content: Text(
          'Are you sure you want to delete "${subscription.name}"?',
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

              _subscriptions.removeWhere((s) => s.id == subscription.id);

              final service = Provider.of<V2RayService>(context, listen: false);

              // Remove configs associated with this subscription
              final existingConfigs = await service.loadConfigs();
              final filteredConfigs = existingConfigs.where((config) {
                // Keep configs that don't belong to this subscription
                return !(config.source == 'subscription' &&
                    config.subscriptionId == subscription.id);
              }).toList();

              await service.saveConfigs(filteredConfigs);
              await service.saveSubscriptions(_subscriptions);
              await _loadSubscriptions();

              if (mounted) {
                await displayInfoBar(
                  context,
                  builder: (context, close) {
                    return const InfoBar(
                      title: Text('Deleted'),
                      severity: InfoBarSeverity.info,
                    );
                  },
                  duration: const Duration(seconds: 2),
                );
              }
            },
            child: const Text('Delete'),
          ),
        ],
      ),
    );
  }
}
