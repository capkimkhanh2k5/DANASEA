import 'package:vendor_mobile/core/l10n/app_localizations.dart';

import 'package:flutter/material.dart';

import '../theme/app_colors.dart';


import '../theme/app_typography.dart';



class VendorTextField extends StatelessWidget {

  final String label;

  final String? hint;

  final TextEditingController? controller;

  final String? initialValue;

  final ValueChanged<String>? onChanged;

  final FormFieldValidator<String>? validator;

  final TextInputType keyboardType;

  final bool obscureText;

  final bool readOnly;

  final IconData? prefixIcon;

  final Widget? suffixIcon;

  final String? helperText;

  final int maxLines;

  final bool isRequired;

  final String? suffixText;



  const VendorTextField({

    super.key,

    required this.label,

    this.hint,

    this.controller,

    this.initialValue,

    this.onChanged,

    this.validator,

    this.keyboardType = TextInputType.text,

    this.obscureText = false,

    this.readOnly = false,

    this.prefixIcon,

    this.suffixIcon,

    this.helperText,

    this.maxLines = 1,

    this.isRequired = false,

    this.suffixText,

  });



  @override

  Widget build(BuildContext context) {

    return Column(

      crossAxisAlignment: CrossAxisAlignment.start,

      children: [

        Row(

          mainAxisAlignment: MainAxisAlignment.spaceBetween,

          children: [

            Expanded(

              child: LocalizedText(

                label,

                style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppColors.onSurface),

                maxLines: 1,

                overflow: TextOverflow.ellipsis,

              ),

            ),

            const SizedBox(width: 4),

            if (isRequired)

              LocalizedText(

                'Bắt buộc',

                style: AppTypography.labelSm(color: AppColors.primary),

              )

            else if (readOnly)

              LocalizedText(

                'Chỉ đọc',

                style: AppTypography.labelSm(color: AppColors.tertiary),

              ),

          ],

        ),

        const SizedBox(height: 6),

        TextFormField(

          controller: controller,

          initialValue: initialValue,

          onChanged: onChanged,

          validator: validator == null ? null : (value) {

            final error = validator!(value);

            return error == null ? null : tr(context, error);

          },

          keyboardType: keyboardType,

          obscureText: obscureText,

          readOnly: readOnly,

          maxLines: maxLines,

          style: TextStyle(

            fontSize: 14,

            fontWeight: FontWeight.w500,

            color: readOnly ? AppColors.onSurfaceVariant : AppColors.onSurface,

          ),

          decoration: InputDecoration(

            hintText: hint == null ? null : tr(context, hint!),

            hintStyle: AppTypography.bodyMd(color: AppColors.outline),

            filled: true,

            fillColor: readOnly ? AppColors.surfaceContainer : AppColors.surfaceContainerLow,

            prefixIcon: prefixIcon != null

                ? Icon(prefixIcon, color: AppColors.secondary, size: 20)

                : null,

            suffixIcon: suffixIcon,

            suffixText: suffixText == null ? null : tr(context, suffixText!),

            suffixStyle: AppTypography.bodyMd(color: AppColors.onSurfaceVariant),

            contentPadding: EdgeInsets.symmetric(

              horizontal: 18,

              vertical: maxLines > 1 ? 14 : 14,

            ),

            border: OutlineInputBorder(

              borderRadius: BorderRadius.circular(12),

              borderSide: BorderSide(color: AppColors.borderSubtle.withValues(alpha: 0.8), width: 1.0),

            ),

            enabledBorder: OutlineInputBorder(

              borderRadius: BorderRadius.circular(12),

              borderSide: BorderSide(color: AppColors.borderSubtle.withValues(alpha: 0.8), width: 1.0),

            ),

            focusedBorder: OutlineInputBorder(

              borderRadius: BorderRadius.circular(12),

              borderSide: const BorderSide(color: AppColors.secondary, width: 1.5),

            ),

            errorBorder: OutlineInputBorder(

              borderRadius: BorderRadius.circular(12),

              borderSide: const BorderSide(color: AppColors.error, width: 1.5),

            ),

            helperText: helperText == null ? null : tr(context, helperText!),

            helperMaxLines: 2,

            helperStyle: AppTypography.bodySm(color: AppColors.tertiary),

          ),

        ),

      ],

    );

  }

}

