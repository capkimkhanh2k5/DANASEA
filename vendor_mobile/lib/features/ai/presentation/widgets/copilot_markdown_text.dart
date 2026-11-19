import 'package:flutter/material.dart';

class CopilotMarkdownText extends StatelessWidget {
  final String content;
  final Color textColor;
  final double fontSize;

  const CopilotMarkdownText({
    super.key,
    required this.content,
    this.textColor = const Color(0xFF1E293B),
    this.fontSize = 13.5,
  });

  @override
  Widget build(BuildContext context) {
    if (content.trim().isEmpty) return const SizedBox.shrink();

    final lines = content.split('\n');
    final widgets = <Widget>[];

    for (int i = 0; i < lines.length; i++) {
      final line = lines[i].trimRight();
      if (line.trim().isEmpty) {
        widgets.add(const SizedBox(height: 6));
        continue;
      }

      final trimmed = line.trim();

      // Heading 1, 2, 3
      if (trimmed.startsWith('### ')) {
        widgets.add(Padding(
          padding: const EdgeInsets.only(top: 6, bottom: 2),
          child: Text(
            trimmed.substring(4),
            style: TextStyle(
              fontSize: fontSize + 1,
              fontWeight: FontWeight.bold,
              color: textColor,
            ),
          ),
        ));
      } else if (trimmed.startsWith('## ') || trimmed.startsWith('# ')) {
        final text = trimmed.startsWith('## ') ? trimmed.substring(3) : trimmed.substring(2);
        widgets.add(Padding(
          padding: const EdgeInsets.only(top: 8, bottom: 3),
          child: Text(
            text,
            style: TextStyle(
              fontSize: fontSize + 2,
              fontWeight: FontWeight.bold,
              color: textColor,
            ),
          ),
        ));
      } else if (trimmed.startsWith('- ') || trimmed.startsWith('* ') || trimmed.startsWith('• ')) {
        // Bullet item
        final bulletText = trimmed.substring(2);
        widgets.add(Padding(
          padding: const EdgeInsets.only(bottom: 4, left: 2),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                margin: const EdgeInsets.only(top: 6, right: 8),
                width: 5,
                height: 5,
                decoration: const BoxDecoration(
                  color: Color(0xFF0284C7),
                  shape: BoxShape.circle,
                ),
              ),
              Expanded(
                child: RichText(
                  text: TextSpan(
                    style: TextStyle(fontSize: fontSize, color: textColor, height: 1.45),
                    children: _parseInlineBold(bulletText, textColor, fontSize),
                  ),
                ),
              ),
            ],
          ),
        ));
      } else {
        // Regular paragraph with inline formatting
        widgets.add(Padding(
          padding: const EdgeInsets.only(bottom: 3),
          child: RichText(
            text: TextSpan(
              style: TextStyle(fontSize: fontSize, color: textColor, height: 1.45),
              children: _parseInlineBold(line, textColor, fontSize),
            ),
          ),
        ));
      }
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: widgets,
    );
  }

  List<InlineSpan> _parseInlineBold(String text, Color baseColor, double size) {
    final spans = <InlineSpan>[];
    final regex = RegExp(r'\*\*(.+?)\*\*');
    int lastIndex = 0;

    for (final match in regex.allMatches(text)) {
      if (match.start > lastIndex) {
        spans.add(TextSpan(
          text: text.substring(lastIndex, match.start),
          style: TextStyle(color: baseColor, fontSize: size),
        ));
      }
      final boldContent = match.group(1) ?? '';
      spans.add(TextSpan(
        text: boldContent,
        style: TextStyle(
          color: baseColor,
          fontSize: size,
          fontWeight: FontWeight.bold,
        ),
      ));
      lastIndex = match.end;
    }

    if (lastIndex < text.length) {
      spans.add(TextSpan(
        text: text.substring(lastIndex),
        style: TextStyle(color: baseColor, fontSize: size),
      ));
    }

    return spans;
  }
}
