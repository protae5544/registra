package com.chb.form.ui
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
@Composable
fun SectionTitle(title: String, sub: String? = null) { Column(Modifier.padding(top = 10.dp, bottom = 2.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable
fun ChoiceCard( label: String, sub: String? = null, selected: Boolean, onClick: () -> Unit ) { val hap = LocalHapticFeedback.current
Surface( onClick = { hap.performHapticFeedback(HapticFeedbackType.TextHandleMove);
onClick() }, shape = RoundedCornerShape(14.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null, modifier = Modifier.fillMaxWidth() ) { Row( Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically ) { Column(Modifier.weight(1f)) { Text( label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal )
sub?.let { Text( it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant ) } }
AnimatedVisibility(selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) { Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) } } } }
@Composable
fun Field( label: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, icon: ImageVector? = null, error: String? = null, imeAction: ImeAction = ImeAction.Next ) { OutlinedTextField( value = value, onValueChange = onChange, label = { Text(label) }, leadingIcon = icon?.let { { Icon(it, null) } }, isError = error != null, supportingText = error?.let { { Text(it) } }, singleLine = true, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = imeAction), modifier = Modifier.fillMaxWidth() ) }
@Composable
fun ReviewRow(label: String, value: String, ok: Boolean = value.isNotBlank()) { Row( Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top ) { Text( label, Modifier.width(132.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant )
Text( value.ifBlank { "— ยังไม่ได้กรอก —"
}, style = MaterialTheme.typography.bodyMedium, fontWeight = if (ok) FontWeight.Medium else FontWeight.Normal, color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f) ) } }
