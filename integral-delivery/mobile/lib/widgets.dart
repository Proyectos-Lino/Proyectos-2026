import 'package:flutter/material.dart';

import 'state.dart';

const ink = Color(0xFF1D3038);
const muted = Color(0xFF6E7C81);
const canvas = Color(0xFFF6F6F2);

class BusinessMark extends StatelessWidget {
  final AppState app;
  const BusinessMark(this.app, {super.key});
  @override
  Widget build(BuildContext context) {
    final url = '${app.business['logo_url'] ?? ''}';
    final icon = Icon(Icons.storefront_outlined, color: app.accent);
    return SizedBox(
      width: 38,
      height: 38,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(10),
        child: url.isEmpty
            ? icon
            : Image.network(
                '${app.server}$url',
                fit: BoxFit.contain,
                errorBuilder: (_, e, s) => icon,
              ),
      ),
    );
  }
}

IconData categoryIcon(dynamic value) => switch (value) {
  'basket' => Icons.shopping_basket_outlined,
  'home' => Icons.chair_outlined,
  'devices' => Icons.headphones_outlined,
  'spa' => Icons.spa_outlined,
  'build' => Icons.handyman_outlined,
  _ => Icons.storefront_outlined,
};
Color statusColor(String status) => switch (status) {
  'delivered' => const Color(0xFF21846B),
  'cancelled' => const Color(0xFFC54F51),
  'en_route' => const Color(0xFF397BA5),
  'pending' => const Color(0xFFAF791F),
  _ => const Color(0xFF6B64A6),
};

void toast(BuildContext context, String text, {bool error = false}) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(
      SnackBar(
        content: Text(text),
        backgroundColor: error ? const Color(0xFFB44740) : ink,
        behavior: SnackBarBehavior.floating,
      ),
    );
}

Future<void> perform(
  BuildContext context,
  Future<void> Function() action, {
  String? success,
}) async {
  try {
    await action();
    if (context.mounted && success != null) toast(context, success);
  } catch (e) {
    if (context.mounted) toast(context, errorText(e), error: true);
  }
}

class ActionButton extends StatefulWidget {
  final String label;
  final IconData? icon;
  final Future<void> Function()? onPressed;
  final bool secondary;
  const ActionButton(
    this.label, {
    super.key,
    this.icon,
    this.onPressed,
    this.secondary = false,
  });
  @override
  State<ActionButton> createState() => _ActionButtonState();
}

class _ActionButtonState extends State<ActionButton> {
  bool busy = false;
  @override
  Widget build(BuildContext context) {
    final child = busy
        ? const SizedBox(
            width: 20,
            height: 20,
            child: CircularProgressIndicator(strokeWidth: 2),
          )
        : Row(
            mainAxisAlignment: MainAxisAlignment.center,
            mainAxisSize: MainAxisSize.min,
            children: [
              if (widget.icon != null) ...[
                Icon(widget.icon, size: 20),
                const SizedBox(width: 8),
              ],
              Flexible(child: Text(widget.label, textAlign: TextAlign.center)),
            ],
          );
    Future<void> run() async {
      setState(() => busy = true);
      try {
        await widget.onPressed?.call();
      } catch (e) {
        if (context.mounted) toast(context, errorText(e), error: true);
      } finally {
        if (mounted) setState(() => busy = false);
      }
    }

    return SizedBox(
      height: 52,
      child: widget.secondary
          ? OutlinedButton(
              onPressed: busy || widget.onPressed == null ? null : run,
              child: child,
            )
          : FilledButton(
              onPressed: busy || widget.onPressed == null ? null : run,
              child: child,
            ),
    );
  }
}

class Sheet extends StatelessWidget {
  final String title;
  final List<Widget> children;
  const Sheet(this.title, this.children, {super.key});
  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.fromLTRB(
      24,
      24,
      24,
      MediaQuery.viewInsetsOf(context).bottom + 24,
    ),
    child: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  title,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
              ),
              IconButton(
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.close),
              ),
            ],
          ),
          const SizedBox(height: 18),
          ...children,
        ],
      ),
    ),
  );
}

Future<T?> sheet<T>(BuildContext context, Widget child) =>
    showModalBottomSheet<T>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.white,
      builder: (_) => child,
    );

class Field extends StatelessWidget {
  final TextEditingController controller;
  final String label;
  final bool secret;
  final int lines;
  final TextInputType? type;
  final String? hint;
  final ValueChanged<String>? onChanged;
  const Field(
    this.controller,
    this.label, {
    super.key,
    this.secret = false,
    this.lines = 1,
    this.type,
    this.hint,
    this.onChanged,
  });
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 16),
    child: TextField(
      controller: controller,
      obscureText: secret,
      maxLines: lines,
      keyboardType: type,
      onChanged: onChanged,
      decoration: InputDecoration(labelText: label, hintText: hint),
    ),
  );
}

class SectionTitle extends StatelessWidget {
  final String title;
  final String? subtitle;
  final Widget? trailing;
  const SectionTitle(this.title, {super.key, this.subtitle, this.trailing});
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 12),
    child: Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: Theme.of(context).textTheme.titleLarge
                    ?.copyWith(fontWeight: FontWeight.w800),
              ),
              if (subtitle != null) ...[
                const SizedBox(height: 5),
                Text(subtitle!, style: const TextStyle(color: muted)),
              ],
            ],
          ),
        ),
        if (trailing != null) trailing!,
      ],
    ),
  );
}

class EmptyState extends StatelessWidget {
  final String title, description;
  final IconData icon;
  final Widget? action;
  const EmptyState(
    this.title,
    this.description, {
    super.key,
    this.icon = Icons.inbox_outlined,
    this.action,
  });
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 52, horizontal: 24),
    child: Column(
      children: [
        Container(
          padding: const EdgeInsets.all(22),
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.primary.withValues(alpha: .08),
            shape: BoxShape.circle,
          ),
          child: Icon(
            icon,
            size: 40,
            color: Theme.of(context).colorScheme.primary,
          ),
        ),
        const SizedBox(height: 20),
        Text(
          title,
          style: Theme.of(context).textTheme.titleLarge,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 8),
        Text(
          description,
          textAlign: TextAlign.center,
          style: const TextStyle(color: muted),
        ),
        if (action != null) ...[const SizedBox(height: 20), action!],
      ],
    ),
  );
}

class StatusBadge extends StatelessWidget {
  final String value;
  const StatusBadge(this.value, {super.key});
  @override
  Widget build(BuildContext context) {
    final color = statusColor(value);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: .1),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        statuses[value] ?? value,
        style: TextStyle(
          color: color,
          fontSize: 12,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

class ProductArt extends StatelessWidget {
  final AppState app;
  final Json product;
  final double height;
  final bool large;
  const ProductArt(
    this.app,
    this.product, {
    super.key,
    this.height = 125,
    this.large = false,
  });
  @override
  Widget build(BuildContext context) {
    final colors = [
      const Color(0xFFF5E9D7),
      const Color(0xFFE1EBE2),
      const Color(0xFFE2EAF0),
      const Color(0xFFEFE3E6),
    ];
    final index = (product['category_id'] as int? ?? 1) - 1;
    final placeholder = Container(
      height: height,
      decoration: BoxDecoration(color: colors[index.abs() % colors.length]),
      child: Center(
        child: Icon(
          categoryIcon(product['category_icon']),
          size: large ? 78 : 52,
          color: ink.withValues(alpha: .65),
        ),
      ),
    );
    final url = product['image_url'] ?? '';
    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: url.toString().isEmpty
          ? placeholder
          : Image.network(
              '${app.server}$url',
              height: height,
              width: double.infinity,
              fit: BoxFit.cover,
              errorBuilder: (_, e, s) => placeholder,
            ),
    );
  }
}

class Metric extends StatelessWidget {
  final String label, value;
  final IconData icon;
  final Color? color;
  const Metric(this.label, this.value, this.icon, {super.key, this.color});
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(18),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(20),
      border: Border.all(color: const Color(0xFFE8EBE5)),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(
          icon,
          color: color ?? Theme.of(context).colorScheme.primary,
          size: 23,
        ),
        const SizedBox(height: 12),
        FittedBox(
          fit: BoxFit.scaleDown,
          child: Text(
            value,
            style: const TextStyle(
              fontSize: 25,
              fontWeight: FontWeight.w800,
              color: ink,
            ),
          ),
        ),
        const SizedBox(height: 4),
        Flexible(
          child: Text(
            label,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(color: muted, fontSize: 12),
          ),
        ),
      ],
    ),
  );
}

Future<String?> askText(
  BuildContext context,
  String title, {
  String label = 'Descripción',
  String initial = '',
  bool secret = false,
}) async {
  final c = TextEditingController(text: initial);
  final result = await sheet<String>(
    context,
    Sheet(title, [
      Field(c, label, secret: secret, lines: secret ? 1 : 3),
      ActionButton(
        'Continuar',
        onPressed: () async {
          Navigator.pop(context, c.text.trim());
        },
      ),
    ]),
  );
  // La animación de cierre aún puede utilizar el controlador.
  Future<void>.delayed(const Duration(seconds: 1), c.dispose);
  return result;
}
