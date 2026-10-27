package co.pesa.checkout.ui

import androidx.compose.runtime.Composable

// No window flag on iOS. The usual approach is a UITextField with
// isSecureTextEntry layered under the content, or hiding the fields on
// UIApplication.userDidTakeScreenshotNotification; both belong to the app.
@Composable
actual fun PesaSecureScreen(content: @Composable () -> Unit) = content()
