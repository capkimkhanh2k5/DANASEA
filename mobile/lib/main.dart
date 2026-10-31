import 'features/auth/presentation/screens/session_gate.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'core/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'core/theme/app_theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppLanguage.instance.initialize();
  runApp(const DanaSeaApp());
}

class DanaSeaApp extends StatelessWidget {
  const DanaSeaApp({super.key});

  static final _navigatorObserver = _AppNavigatorObserver();

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: AppLanguage.instance,
      builder: (context, child) => MaterialApp(
        navigatorObservers: [_navigatorObserver],
        locale: AppLanguage.instance.locale,
        supportedLocales: const [Locale('vi'), Locale('en')],
        localizationsDelegates: GlobalMaterialLocalizations.delegates,
        title: 'DanaSea',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.lightTheme,
        home: const SessionGate(),
        builder: (context, child) => _GlobalKeyboardDismissWrapper(
          child: child ?? const SizedBox.shrink(),
        ),
      ),
    );
  }
}

class _AppNavigatorObserver extends NavigatorObserver {
  @override
  void didPop(Route<dynamic> route, Route<dynamic>? previousRoute) {
    FocusManager.instance.primaryFocus?.unfocus();
    super.didPop(route, previousRoute);
  }

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    FocusManager.instance.primaryFocus?.unfocus();
    super.didPush(route, previousRoute);
  }
}

class _GlobalKeyboardDismissWrapper extends StatefulWidget {
  final Widget child;
  const _GlobalKeyboardDismissWrapper({required this.child});

  @override
  State<_GlobalKeyboardDismissWrapper> createState() =>
      _GlobalKeyboardDismissWrapperState();
}

class _GlobalKeyboardDismissWrapperState
    extends State<_GlobalKeyboardDismissWrapper> with WidgetsBindingObserver {
  double _lastBottomInset = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeMetrics() {
    super.didChangeMetrics();
    final view = View.maybeOf(context);
    final currentBottomInset = view?.viewInsets.bottom ?? 0;
    // When the soft keyboard was open and is now closed (e.g. via back button / gesture):
    if (_lastBottomInset > 0 && currentBottomInset == 0) {
      FocusManager.instance.primaryFocus?.unfocus();
    }
    _lastBottomInset = currentBottomInset;
  }

  @override
  Widget build(BuildContext context) {
    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: (event) {
        final currentFocus = FocusManager.instance.primaryFocus;
        if (currentFocus != null && currentFocus.hasFocus) {
          final renderObject = currentFocus.context?.findRenderObject();
          if (renderObject is RenderBox) {
            final localPos = renderObject.globalToLocal(event.position);
            if (!renderObject.paintBounds.contains(localPos)) {
              currentFocus.unfocus();
            }
          }
        }
      },
      child: widget.child,
    );
  }
}
