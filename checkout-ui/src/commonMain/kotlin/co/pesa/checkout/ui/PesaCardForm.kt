package co.pesa.checkout.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.pesa.checkout.CardBrand
import co.pesa.checkout.CardDetails
import co.pesa.checkout.CardField
import co.pesa.checkout.CardFieldError
import co.pesa.checkout.CardValidation
import co.pesa.checkout.CheckoutSession
import co.pesa.checkout.PesaStrings
import co.pesa.checkout.SavedCard
import co.pesa.checkout.SavedCardSecurityCode

/**
 * What the cardholder has typed so far. Keep it in the screen's own state
 * holder and call [clear] once the payment has been sent; [toCard] hands the
 * number to the SDK, which wipes it after the request.
 *
 * With [savedCards], the first one that has not expired is selected. While
 * [selectedSavedCard] is set, pay with its token and [toSavedSecurityCode];
 * otherwise pay with [toCard] and [savesCard].
 *
 * [strings] is the language of the form: [locale] from the host, then
 * [sessionLocale], then the device, then English.
 */
class PesaCardFormState(
    val needsEmail: Boolean = false,
    val savedCards: List<SavedCard> = emptyList(),
    val savedCardSecurityCode: SavedCardSecurityCode = SavedCardSecurityCode.NEVER,
    val canSaveCard: Boolean = false,
    locale: String? = null,
    val sessionLocale: String? = null,
) {
    constructor(session: CheckoutSession, locale: String? = null) :
        this(session.needsEmail, session.savedCards, session.savedCardSecurityCode, session.canSaveCard, locale, session.locale)

    val strings: PesaStrings = PesaStrings.resolve(locale, sessionLocale)

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

    /** [title] with the selected saved card, as "Pay £1.00 with •••• 0165"; [title] alone for a typed card. */
    fun payLabel(title: String): String =
        selectedSavedCard?.let { strings.titleWithSavedCard.replace("{title}", title).replace("{last4}", it.last4) } ?: title

    /** "Pay £2.00", or "Pay £2.00 with •••• 0165" while a saved card is selected, in [strings]. */
    fun payLabel(amount: String, currency: String): String = strings.payLabel(amount, currency, selectedSavedCard?.last4)

    internal fun dropExpiredSelection(nowYear: Int, nowMonth: Int) {
        if (selectedSavedCard?.isUsable(nowYear, nowMonth) == false) {
            select(savedCards.firstOrNull { it.isUsable(nowYear, nowMonth) })
        }
    }

    fun errors(nowYear: Int, nowMonth: Int, strings: PesaStrings = this.strings): List<CardFieldError> {
        val saved = selectedSavedCard
        if (saved != null) {
            return listOfNotNull(
                if (saved.isUsable(nowYear, nowMonth)) null else CardFieldError(CardField.EXPIRY, strings.cardExpired),
                savedSecurityCodeError(saved, strings),
                if (needsEmail) CardValidation.email(email, strings) else null,
            )
        }
        val exp = CardValidation.parseExpiry(expiry)
        return listOfNotNull(
            CardValidation.number(number, strings),
            exp?.let { CardValidation.expiry(it.first, it.second, nowYear, nowMonth, strings) }
                ?: CardFieldError(CardField.EXPIRY, strings.expiryInvalid),
            CardValidation.securityCode(securityCode, brand, strings),
            CardValidation.holderName(holderName, strings),
            if (needsEmail) CardValidation.email(email, strings) else null,
        )
    }

    private fun savedSecurityCodeError(card: SavedCard, strings: PesaStrings): CardFieldError? = when {
        !asksSavedSecurityCode -> null
        savedCardSecurityCode == SavedCardSecurityCode.OPTIONAL && savedSecurityCode.isEmpty() -> null
        else -> CardValidation.securityCode(savedSecurityCode, card.cardBrand, strings)
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
 * The strings for every Pesa composable below it. Null (the default) lets
 * each form use its state's [PesaCardFormState.strings].
 */
val LocalPesaStrings = staticCompositionLocalOf<PesaStrings?> { null }

/**
 * Native card fields in the app's Material theme. The number is grouped as
 * typed and the security code is masked; nothing here logs, copies or keeps
 * what is typed beyond [state].
 *
 * With saved cards the form is one list: a row per saved card, then "Use
 * another card". Expired cards are listed but cannot be chosen. The
 * selected card takes its security code on its own row unless the session
 * says `NEVER`; "Use another card" opens the card fields under it, inside
 * the same list.
 *
 * "Save this card for next time" is the last line of the card fields while
 * the session offers it. Pass [showSaveCardSwitch] false to place
 * [PesaSaveCardSwitch] yourself.
 *
 * The language is [locale] when set, else [LocalPesaStrings], else the
 * state's [PesaCardFormState.strings].
 */
@Composable
fun PesaCardForm(
    state: PesaCardFormState,
    nowYear: Int,
    nowMonth: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showSaveCardSwitch: Boolean = true,
    locale: String? = null,
) {
    val strings = locale?.let { remember(it, state) { PesaStrings.resolve(it, state.sessionLocale) } }
        ?: LocalPesaStrings.current
        ?: state.strings
    CompositionLocalProvider(LocalPesaStrings provides strings) {
        CardFormBody(state, strings, nowYear, nowMonth, modifier, enabled, showSaveCardSwitch)
    }
}

@Composable
private fun CardFormBody(
    state: PesaCardFormState,
    strings: PesaStrings,
    nowYear: Int,
    nowMonth: Int,
    modifier: Modifier,
    enabled: Boolean,
    showSaveCardSwitch: Boolean,
) {
    LaunchedEffect(state, nowYear, nowMonth) { state.dropExpiredSelection(nowYear, nowMonth) }
    val errors = state.errors(nowYear, nowMonth, strings).filter { it.field in state.touched }.associateBy { it.field }
    val touch = { f: CardField -> state.touched = state.touched + f }

    if (state.savedCards.isEmpty()) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TypedCardFields(state, strings, errors, enabled, touch)
            if (showSaveCardSwitch) PesaSaveCardSwitch(state, enabled = enabled)
        }
        return
    }

    val selected = state.selectedSavedCard
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.animateContentSize()) {
            state.savedCards.forEach { card ->
                SavedCardRow(
                    state = state,
                    strings = strings,
                    card = card,
                    usable = card.isUsable(nowYear, nowMonth),
                    selected = card.token == selected?.token,
                    error = errors[CardField.SECURITY_CODE]?.message,
                    enabled = enabled,
                    touch = touch,
                )
                Hairline()
            }
            PickerRow(
                selected = selected == null,
                enabled = enabled,
                onClick = { state.select(null) },
                leading = { PlusMark(MaterialTheme.colorScheme.onSurface) },
                title = strings.useAnotherCard,
            )
            val footer = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
            if (selected == null) {
                Column(footer, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TypedCardFields(state, strings, errors, enabled, touch)
                    if (showSaveCardSwitch) PesaSaveCardSwitch(state, enabled = enabled)
                }
            } else if (state.needsEmail) {
                Box(footer) { EmailField(state, strings, errors, enabled, touch) }
            }
        }
    }
}

/** "Save this card for next time", shown only while a typed card may be kept. */
@Composable
fun PesaSaveCardSwitch(state: PesaCardFormState, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (!state.canSaveCard || state.usesSavedCard) return
    val strings = LocalPesaStrings.current ?: state.strings
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().toggleable(value = state.saveCard, enabled = enabled, role = Role.Switch) { state.saveCard = it },
    ) {
        Text(strings.saveCard, modifier = Modifier.weight(1f))
        Switch(checked = state.saveCard, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun TypedCardFields(state: PesaCardFormState, strings: PesaStrings, errors: Map<CardField, CardFieldError>, enabled: Boolean, touch: (CardField) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.number,
            onValueChange = { v -> state.number = CardValidation.format(v); touch(CardField.NUMBER) },
            label = { Text(strings.cardNumber) },
            trailingIcon = if (state.brand == CardBrand.UNKNOWN) null else {
                { Text(state.brand.wire.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp)) }
            },
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
                label = { Text(strings.expiryFormat) },
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
                label = { Text(if (state.brand == CardBrand.AMEX) strings.cid else strings.cvv) },
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
            label = { Text(strings.holderName) },
            isError = CardField.HOLDER_NAME in errors,
            supportingText = errors[CardField.HOLDER_NAME]?.let { { Text(it.message) } },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrect = false),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.needsEmail) EmailField(state, strings, errors, enabled, touch)
    }
}

@Composable
private fun EmailField(state: PesaCardFormState, strings: PesaStrings, errors: Map<CardField, CardFieldError>, enabled: Boolean, touch: (CardField) -> Unit) {
    OutlinedTextField(
        value = state.email,
        onValueChange = { v -> state.email = v.take(50); touch(CardField.EMAIL) },
        label = { Text(strings.email) },
        isError = CardField.EMAIL in errors,
        supportingText = errors[CardField.EMAIL]?.let { { Text(it.message) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrect = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SavedCardRow(
    state: PesaCardFormState,
    strings: PesaStrings,
    card: SavedCard,
    usable: Boolean,
    selected: Boolean,
    error: String?,
    enabled: Boolean,
    touch: (CardField) -> Unit,
) {
    val asksCode = selected && state.asksSavedSecurityCode
    Column {
        PickerRow(
            selected = selected,
            enabled = enabled && usable,
            onClick = { state.select(card) },
            leading = { BrandChip(card.cardBrand) },
            title = "•••• ${card.last4}",
            subtitle = if (usable) strings.expiresOn(card.shortExpiry) else strings.expired,
            trailing = {
                if (asksCode) {
                    Spacer(Modifier.width(12.dp))
                    InlineSecurityCode(state, strings, card.cardBrand, error != null, enabled, touch)
                }
            },
        )
        if (asksCode && error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 68.dp, end = 16.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun PickerRow(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    val dim = Modifier.alpha(if (enabled) 1f else 0.45f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(dim.width(40.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(12.dp))
        Column(dim.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
        Spacer(Modifier.width(12.dp))
        RadioButton(selected = selected, onClick = null, enabled = enabled)
    }
}

@Composable
private fun InlineSecurityCode(state: PesaCardFormState, strings: PesaStrings, brand: CardBrand, isError: Boolean, enabled: Boolean, touch: (CardField) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val ring = when {
        isError -> colors.error
        focused -> colors.primary
        else -> Color.Transparent
    }
    val name = if (brand == CardBrand.AMEX) strings.cid else strings.cvv
    val label = if (state.savedCardSecurityCode == SavedCardSecurityCode.OPTIONAL) strings.securityCodeOptional else strings.securityCode
    BasicTextField(
        value = state.savedSecurityCode,
        onValueChange = { v -> state.savedSecurityCode = v.filter { it.isDigit() }.take(4); touch(CardField.SECURITY_CODE) },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrect = false),
        interactionSource = source,
        modifier = Modifier.width(76.dp).semantics { contentDescription = label },
        decorationBox = { input ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .heightIn(min = 38.dp)
                    .background(colors.surfaceVariant, shape)
                    .border(1.5.dp, ring, shape)
                    .padding(horizontal = 10.dp),
            ) {
                if (state.savedSecurityCode.isEmpty()) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
                input()
            }
        },
    )
}

@Composable
private fun Hairline() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** The brand on a white chip, so it reads the same on a dark surface. */
@Composable
private fun BrandChip(brand: CardBrand) {
    if (brand == CardBrand.UNKNOWN) {
        CardOutline(MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val shape = RoundedCornerShape(5.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 40.dp, height = 26.dp)
            .background(Color.White, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clearAndSetSemantics { contentDescription = brandName(brand) },
    ) {
        Text(brandMark(brand), color = brandInk(brand), fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun CardOutline(color: Color) {
    Canvas(Modifier.size(width = 30.dp, height = 20.dp)) {
        val s = size.width / 30f
        val line = Stroke(width = 1.4f * s, cap = StrokeCap.Round)
        drawRoundRect(color, topLeft = Offset(1f * s, 1.5f * s), size = Size(28f * s, 17f * s), cornerRadius = CornerRadius(3f * s), style = line)
        drawLine(color, Offset(1f * s, 6.5f * s), Offset(29f * s, 6.5f * s), strokeWidth = 1.4f * s, cap = StrokeCap.Round)
        drawLine(color, Offset(5f * s, 13.5f * s), Offset(11f * s, 13.5f * s), strokeWidth = 1.4f * s, cap = StrokeCap.Round)
    }
}

@Composable
private fun PlusMark(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val w = 2.dp.toPx()
        drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = w, cap = StrokeCap.Round)
    }
}

private fun brandMark(brand: CardBrand): String = when (brand) {
    CardBrand.VISA -> "VISA"
    CardBrand.MASTERCARD -> "MC"
    CardBrand.AMEX -> "AMEX"
    CardBrand.UNIONPAY -> "UP"
    CardBrand.UNKNOWN -> ""
}

private fun brandName(brand: CardBrand): String = when (brand) {
    CardBrand.VISA -> "Visa"
    CardBrand.MASTERCARD -> "Mastercard"
    CardBrand.AMEX -> "American Express"
    CardBrand.UNIONPAY -> "UnionPay"
    CardBrand.UNKNOWN -> "Card"
}

private fun brandInk(brand: CardBrand): Color = when (brand) {
    CardBrand.VISA -> Color(0xFF1A1F71)
    CardBrand.MASTERCARD -> Color(0xFFEB001B)
    CardBrand.AMEX -> Color(0xFF2E77BC)
    CardBrand.UNIONPAY -> Color(0xFFE21836)
    CardBrand.UNKNOWN -> Color(0xFF5F6368)
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
