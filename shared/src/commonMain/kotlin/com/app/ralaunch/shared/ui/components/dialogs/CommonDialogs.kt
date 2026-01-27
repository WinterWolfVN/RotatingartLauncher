package com.app.ralaunch.shared.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 通用确认对话框
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    cancelText: String = "取消",
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDanger: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (isDanger) MaterialTheme.colorScheme.error else iconTint
                )
            }
        },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = if (isDanger) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelText)
            }
        }
    )
}

/**
 * 输入对话框
 */
@Composable
fun InputDialog(
    title: String,
    placeholder: String = "",
    initialValue: String = "",
    confirmText: String = "确定",
    cancelText: String = "取消",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    validator: (String) -> Boolean = { true },
    errorMessage: String = "输入无效"
) {
    var inputValue by remember { mutableStateOf(initialValue) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = inputValue,
                onValueChange = {
                    inputValue = it
                    isError = !validator(it)
                },
                placeholder = { Text(placeholder) },
                isError = isError,
                supportingText = if (isError) {
                    { Text(errorMessage) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validator(inputValue)) {
                        onConfirm(inputValue)
                        onDismiss()
                    } else {
                        isError = true
                    }
                }
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelText)
            }
        }
    )
}

/**
 * 加载对话框
 */
@Composable
fun LoadingDialog(
    message: String = "加载中...",
    onDismiss: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = onDismiss != null,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 选择对话框
 */
@Composable
fun <T> SelectionDialog(
    title: String,
    items: List<T>,
    selectedItem: T?,
    itemText: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    cancelText: String = "取消"
) {
    var currentSelection by remember(selectedItem) { mutableStateOf(selectedItem) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = item == currentSelection,
                            onClick = { currentSelection = item }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(itemText(item))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    currentSelection?.let {
                        onSelect(it)
                        onDismiss()
                    }
                },
                enabled = currentSelection != null
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelText)
            }
        }
    )
}

/**
 * 进度对话框
 */
@Composable
fun ProgressDialog(
    title: String,
    progress: Float,
    message: String = "",
    onCancel: (() -> Unit)? = null,
    cancelText: String = "取消"
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(min = 280.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                onCancel?.let {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = it,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(cancelText)
                    }
                }
            }
        }
    }
}

/**
 * 双因素验证对话框
 */
@Composable
fun TwoFactorDialog(
    type: TwoFactorType,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    strings: TwoFactorStrings = TwoFactorStrings()
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (type == TwoFactorType.EMAIL) Icons.Default.Email else Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                if (type == TwoFactorType.EMAIL) strings.emailTitle else strings.authenticatorTitle,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    if (type == TwoFactorType.EMAIL) strings.emailMessage else strings.authenticatorMessage,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = {
                        Text(if (type == TwoFactorType.EMAIL) strings.fourDigitCode else strings.sixDigitCode)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(code) },
                enabled = code.isNotBlank()
            ) {
                Text(strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

enum class TwoFactorType {
    EMAIL, AUTHENTICATOR
}

data class TwoFactorStrings(
    val emailTitle: String = "邮箱验证",
    val authenticatorTitle: String = "身份验证器",
    val emailMessage: String = "请输入发送到您邮箱的验证码",
    val authenticatorMessage: String = "请输入身份验证器中的验证码",
    val fourDigitCode: String = "4位验证码",
    val sixDigitCode: String = "6位验证码",
    val confirm: String = "确定",
    val cancel: String = "取消"
)
