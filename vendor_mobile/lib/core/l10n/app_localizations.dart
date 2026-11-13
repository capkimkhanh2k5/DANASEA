import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'translations.dart';

/// Device preference, independent of account data and navigation state.
class AppLanguage extends ChangeNotifier {
  AppLanguage._();
  static final instance = AppLanguage._();
  static const preferenceKey = 'danasea.locale';
  Locale _locale = const Locale('vi');
  SharedPreferences? _preferences;
  Locale get locale => _locale;
  String get code => _locale.languageCode;

  Future<void> initialize() async {
    _preferences = await SharedPreferences.getInstance();
    final saved = _preferences!.getString(preferenceKey);
    _locale = Locale(saved == 'en' ? 'en' : 'vi');
    notifyListeners();
  }

  Future<void> setLanguage(String code) async {
    if (code != 'vi' && code != 'en') return;
    if (_locale.languageCode != code) {
      _locale = Locale(code);
      notifyListeners();
    }
    await _preferences?.setString(preferenceKey, code);
  }
}

String tr(BuildContext context, String source) =>
    translate(source, Localizations.localeOf(context).languageCode);

/// Translate only catalogued UI phrases; unknown content is preserved.
/// Templates are anchored and sorted by specificity, so e.g. a guest count
/// cannot shadow an order summary. Captured values are never used as regexes.
String translate(String source, String language, [int depth = 0]) {
  if (language != 'en' || depth > 4) return source;
  final exact = englishTranslations[source];
  if (exact != null) return exact;
  final amount = RegExp(r'^([0-9.,]+)đ$').firstMatch(source);
  if (amount != null) return '${amount[1]} VND';
  for (final template in _templates) {
    final match = template.pattern.firstMatch(source);
    if (match == null) continue;
    return template.english.replaceAllMapped(RegExp(r'\{(\d+)\}'), (token) {
      final index = template.indices.indexOf(int.parse(token[1]!));
      return index < 0
          ? token[0]!
          : translate(match[index + 1]!, language, depth + 1);
    });
  }
  return source;
}

final _templates =
    englishTranslations.entries
        .where((entry) => RegExp(r'\{\d+\}').hasMatch(entry.key))
        .map(_TranslationTemplate.new)
        .toList()
      ..sort((a, b) => b.specificity.compareTo(a.specificity));

class _TranslationTemplate {
  _TranslationTemplate(MapEntry<String, String> entry) : english = entry.value {
    final buffer = StringBuffer('^');
    var end = 0;
    for (final match in RegExp(r'\{(\d+)\}').allMatches(entry.key)) {
      buffer.write(RegExp.escape(entry.key.substring(end, match.start)));
      buffer.write('(.*?)');
      indices.add(int.parse(match[1]!));
      end = match.end;
    }
    buffer.write(RegExp.escape(entry.key.substring(end)));
    buffer.write(r'$');
    pattern = RegExp(buffer.toString(), dotAll: true);
    specificity = entry.key.replaceAll(RegExp(r'\{\d+\}'), '').length;
  }
  final String english;
  final indices = <int>[];
  late final RegExp pattern;
  late final int specificity;
}

/// Localizes at display time, keeping stored values, filters and API fields
/// unchanged. Depending on Localizations also updates const widgets in routes
/// and dialogs without recreating their state.
class LocalizedText extends StatelessWidget {
  const LocalizedText(
    this.data, {
    super.key,
    this.style,
    this.strutStyle,
    this.textAlign,
    this.textDirection,
    this.locale,
    this.softWrap,
    this.overflow,
    this.textScaler,
    this.maxLines,
    this.semanticsLabel,
    this.textWidthBasis,
    this.textHeightBehavior,
    this.selectionColor,
  });
  final String data;
  final TextStyle? style;
  final StrutStyle? strutStyle;
  final TextAlign? textAlign;
  final TextDirection? textDirection;
  final Locale? locale;
  final bool? softWrap;
  final TextOverflow? overflow;
  final TextScaler? textScaler;
  final int? maxLines;
  final String? semanticsLabel;
  final TextWidthBasis? textWidthBasis;
  final TextHeightBehavior? textHeightBehavior;
  final Color? selectionColor;

  @override
  Widget build(BuildContext context) => Text(
    tr(context, data),
    style: style,
    strutStyle: strutStyle,
    textAlign: textAlign,
    textDirection: textDirection,
    locale: locale,
    softWrap: softWrap,
    overflow: overflow,
    textScaler: textScaler,
    maxLines: maxLines,
    semanticsLabel: semanticsLabel == null
        ? null
        : tr(context, semanticsLabel!),
    textWidthBasis: textWidthBasis,
    textHeightBehavior: textHeightBehavior,
    selectionColor: selectionColor,
  );
}
