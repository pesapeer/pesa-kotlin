package co.pesa.checkout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import co.pesa.checkout.CardBrand
import co.pesa.checkout.CardDetails
import co.pesa.checkout.CardField
import co.pesa.checkout.CardFieldError
import co.pesa.checkout.CardValidation
import co.pesa.checkout.CheckoutSession
import co.pesa.checkout.SavedCard
import co.pesa.checkout.SavedCardSecurityCode

/**
 * What the cardholder has typed so far. Keep it in the screen's own state
 * holder and call [clear] once the payment has been sent; [toCard] hands the
 * number to the SDK, which wipes it after the request.
 *
 * With [savedCards], the first one is selected. While [selectedSavedCard] is
 * set, pay with its token and [toSavedSecurityCode]; otherwise pay with
 * [toCard] and [savesCard].
 */
class PesaCardFormState(
    val needsEmail: Boolean = false,
    val savedCards: List<SavedCard> = emptyList(),
    val savedCardSecurityCode: SavedCardSecurityCode = SavedCardSecurityCode.OPTIONAL,
    val canSaveCard: Boolean = false,
) {
    constructor(session: CheckoutSession) :
        this(session.needsEmail, session.savedCards, session.savedCardSecurityCode, session.canSaveCard)

    var selectedSavedCard by mutableStateOf(savedCards.firstOrNull())
        private set
    var savedSecurityCode by mutableStateOf("")
    var saveCard by mutableStateOf(false)
    var number by mutableStateOf("")
    var expiry by mutableStateOf("")
    var securityCode by mutableStateOf("")
    var holderName by mutableStateOf("")
    var email by mutableStateOf("")
    var touched by mutableStateOf(setOf<CardField>())

    val brand: CardBrand get() = CardBrand.detect(number)
    val usesSavedCard: Boolean get() = selectedSavedCard != null
    val asksSavedSecurityCode: Boolean get() = usesSavedCard && savedCardSecurityCode != SavedCardSecurityCode.NEVER
    val savesCard: Boolean get() = canSaveCard && saveCard && !usesSavedCard

    /** Selects a saved card, or null for "Use another card". */
    fun select(card: SavedCard?) {
        selectedSavedCard = card
        savedSecurityCode = ""
        touched = emptySet()
    }

    fun errors(nowYear: Int, nowMonth: Int): List<CardFieldError> {
        val saved = selectedSavedCard
        if (saved != null) {
            return listOfNotNull(
                savedSecurityCodeError(saved),
                if (needsEmail) CardValidation.email(email) else null,
            )
        }
        val exp = CardValidation.parseExpiry(expiry)
        return listOfNotNull(
            CardValidation.number(number),
            exp?.let { CardValidation.expiry(it.first, it.second, nowYear, nowMonth) }
                ?: CardFieldError(CardField.EXPIRY, "Enter the expiry as MM/YY."),
            CardValidation.securityCode(securityCode, brand),
            CardValidation.holderName(holderName),
            if (needsEmail) CardValidation.email(email) else null,
        )
    }

    private fun savedSecurityCodeError(card: SavedCard): CardFieldError? = when {
        !asksSavedSecurityCode -> null
        savedCardSecurityCode == SavedCardSecurityCode.OPTIONAL && savedSecurityCode.isEmpty() -> null
        else -> CardValidation.securityCode(savedSecurityCode, card.cardBrand)
    }

    fun complete(nowYear: Int, nowMonth: Int): Boolean = errors(nowYear, nowMonth).isEmpty()

    fun toCard(): CardDetails {
        val (m, y) = requireNotNull(CardValidation.parseExpiry(expiry)) { "expiry is not valid" }
        return CardDetails(number.toCharArray(), m, y, securityCode.toCharArray(), holderName.trim())
    }

    /** The saved card's security code to send, or null when there is none to send. */
    fun toSavedSecurityCode(): CharArray? =
        if (asksSavedSecurityCode && savedSecurityCode.isNotEmpty()) savedSecurityCode.toCharArray() else null

    fun clear() {
        number = ""
        expiry = ""
        securityCode = ""
        savedSecurityCode = ""
    }
}

/**
 * Native card fields in the app's Material theme. The number is grouped as
 * typed and the security code is masked; nothing here logs, copies or keeps
 * what is typed beyond [state].
 */
@Composable
fun PesaCardForm(
    state: PesaCardFormState,
    nowYear: Int,
    nowMonth: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val errors = state.errors(nowYear, nowMonth).filter { it.field in state.touched }.associateBy { it.field }
    fun touch(f: CardField) { state.touched = state.touched + f }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.savedCards.isNotEmpty()) {
            SavedCardPicker(state, enabled)
        }
        val saved = state.selectedSavedCard
        if (saved != null && state.asksSavedSecurityCode) {
            OutlinedTextField(
                value = state.savedSecurityCode,
                onValueChange = { v -> state.savedSecurityCode = v.filter { it.isDigit() }.take(4); touch(CardField.SECURITY_CODE) },
                label = { Text(securityCodeLabel(saved.cardBrand, state.savedCardSecurityCode)) },
                isError = CardField.SECURITY_CODE in errors,
                supportingText = errors[CardField.SECURITY_CODE]?.let { { Text(it.message) } },
                singleLine = true,
                enabled = enabled,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrect = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (saved == null) {
            OutlinedTextField(
                value = state.number,
                onValueChange = { v -> state.number = CardValidation.format(v); touch(CardField.NUMBER) },
                label = { Text("Card number") },
                trailingIcon = { Text(state.brand.wire.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp)) },
                isError = CardField.NUMBER in errors,
                supportingText = errors[CardField.NUMBER]?.let { { Text(it.message) } },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrect = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.expiry,
                    onValueChange = { v -> state.expiry = formatExpiry(v); touch(CardField.EXPIRY) },
                    label = { Text("MM/YY") },
                    isError = CardField.EXPIRY in errors,
                    supportingText = errors[CardField.EXPIRY]?.let { { Text(it.message) } },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrect = false),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.securityCode,
                    onValueChange = { v -> state.securityCode = v.filter { it.isDigit() }.take(4); touch(CardField.SECURITY_CODE) },
                    label = { Text(if (state.brand == CardBrand.AMEX) "CID" else "CVV") },
                    isError = CardField.SECURITY_CODE in errors,
                    supportingText = errors[CardField.SECURITY_CODE]?.let { { Text(it.message) } },
                    singleLine = true,
                    enabled = enabled,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrect = false),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = state.holderName,
                onValueChange = { v -> state.holderName = v.take(26); touch(CardField.HOLDER_NAME) },
                label = { Text("Name on card") },
                isError = CardField.HOLDER_NAME in errors,
                supportingText = errors[CardField.HOLDER_NAME]?.let { { Text(it.message) } },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrect = false),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.canSaveCard) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().toggleable(value = state.saveCard, enabled = enabled, role = Role.Switch) { state.saveCard = it },
                ) {
                    Text("Save this card for next time", modifier = Modifier.weight(1f))
                    Switch(checked = state.saveCard, onCheckedChange = null, enabled = enabled)
                }
            }
        }
        if (state.needsEmail) {
            OutlinedTextField(
                value = state.email,
                onValueChange = { v -> state.email = v.take(50); touch(CardField.EMAIL) },
                label = { Text("Email for your receipt") },
                isError = CardField.EMAIL in errors,
                supportingText = errors[CardField.EMAIL]?.let { { Text(it.message) } },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrect = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SavedCardPicker(state: PesaCardFormState, enabled: Boolean) {
    Column {
        state.savedCards.forEach { card ->
            PickerRow(selected = state.selectedSavedCard == card, enabled = enabled, onClick = { state.select(card) }) {
                Text(card.brand.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
                Text("•••• ${card.last4}", modifier = Modifier.weight(1f))
                Text(card.expiry, style = MaterialTheme.typography.bodySmall)
            }
        }
        PickerRow(selected = state.selectedSavedCard == null, enabled = enabled, onClick = { state.select(null) }) {
            Text("Use another card", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PickerRow(selected: Boolean, enabled: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        content()
    }
}

private fun securityCodeLabel(brand: CardBrand, rule: SavedCardSecurityCode): String {
    val name = if (brand == CardBrand.AMEX) "CID" else "CVV"
    return if (rule == SavedCardSecurityCode.OPTIONAL) "$name (optional)" else name
}

internal fun formatExpiry(raw: String): String {
    val d = raw.filter { it.isDigit() }.take(4)
    return if (d.length > 2) d.substring(0, 2) + "/" + d.substring(2) else d
}

/**
 * Wraps the card screen so it cannot be captured: FLAG_SECURE on Android.
 * iOS has no equivalent flag; apps blur or hide the fields on
 * `UIApplication.userDidTakeScreenshotNotification` and on
 * `applicationWillResignActive`, which the app owns, not the SDK.
 */
@Composable
expect fun PesaSecureScreen(content: @Composable () -> Unit)
