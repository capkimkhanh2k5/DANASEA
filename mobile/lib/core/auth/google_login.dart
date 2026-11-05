import 'package:google_sign_in/google_sign_in.dart';
import 'auth_session.dart';

/// Gets a Google ID token; the backend remains responsible for verification.
class GoogleLogin {
  static Future<void>? _initialization;

  static Future<String?> idToken() async {
    const serverId = String.fromEnvironment(
      'GOOGLE_SERVER_CLIENT_ID',
      defaultValue: '183371974774-75djkakuuigpidlj4ovfrcdmmmt39rm5.apps.googleusercontent.com',
    );
    const clientId = String.fromEnvironment('GOOGLE_IOS_CLIENT_ID');
    if (serverId.isEmpty) {
      throw AuthFailure('Chưa cấu hình đăng nhập Google cho ứng dụng.');
    }
    try {
      await (_initialization ??= GoogleSignIn.instance
          .initialize(
            serverClientId: serverId,
            clientId: clientId.isEmpty ? null : clientId,
          )
          .catchError((Object error) {
            _initialization = null;
            throw error;
          }));
      final account = await GoogleSignIn.instance.authenticate();
      final token = account.authentication.idToken;
      if (token == null || token.isEmpty) {
        throw AuthFailure(
          'Không lấy được thông tin xác thực Google. Vui lòng thử lại.',
        );
      }
      return token;
    } on GoogleSignInException catch (e) {
      if (e.code == GoogleSignInExceptionCode.canceled) {
        throw AuthFailure(
          'Google báo đã hủy đăng nhập. Nếu bạn vừa chọn tài khoản, cần kiểm tra cấu hình Android (package name và SHA-1) trên Google Cloud.',
        );
      }
      throw AuthFailure(
        'Không thể đăng nhập Google (${e.code.name}). Vui lòng thử lại hoặc dùng email.',
      );
    }
  }
}
