package com.paisaplus.compose.data


/**
 * Card groups billed and paid as ONE statement (normalized limitGroup names).
 * "ICICI combined" (MMT 1007 & 4006): the bill payment lands on whichever card it was made from, so each
 * card's own balance is meaningless (one shows +45k, the other −69k); only the group's numbers are real.
 * Groups not listed (e.g. "Federal combined") bill and pay each card separately, so per-card balances hold.
 */
val SHARED_PAYMENT_GROUPS = setOf("icici combined")

fun groupKey(a: AccountEntity): String? = a.limitGroup?.trim()?.lowercase()?.ifEmpty { null }

/** Two or more credit cards sharing a limitGroup. [usage]: group limit (max), owed and EMI summed once per card. */
data class CardGroup(val name: String, val members: List<AccountEntity>, val usage: CreditUsage) {
    val sharedPayments get() = name.lowercase() in SHARED_PAYMENT_GROUPS
    val ids get() = members.map { it.id }.toSet()
}

/** [a]'s group; null when [a] is not a credit card, has no limitGroup, or is its group's only card. */
fun cardGroup(a: AccountEntity, all: List<AccountEntity>, loans: List<LoanEntity>): CardGroup? {
    if (a.cardType != CardType.CREDIT) return null
    val key = groupKey(a) ?: return null
    val members = all.filter { it.cardType == CardType.CREDIT && groupKey(it) == key }.sortedBy { it.id }
    if (members.size < 2) return null
    // Computed from the first member so every member yields the same group.
    val usage = creditUsage(members.first(), all, loans) ?: return null
    return CardGroup(members.first().limitGroup!!.trim(), members, usage)
}

/** Every transaction on the group's cards, newest first; each keeps its own accountId. */
fun groupTransactions(expenses: List<ExpenseEntity>, g: CardGroup): List<ExpenseEntity> =
    expenses.filter { it.accountId in g.ids }.sortedByDescending { it.time }

/** In a shared-payment group a bill payment (Transfer income) belongs to the group, not the card it hit. */
fun isGroupPayment(e: ExpenseEntity, g: CardGroup, transfers: Set<Long>) =
    g.sharedPayments && e.type == TransactionType.INCOME && e.categoryId in transfers

enum class CardNetwork(val label: String) { VISA("Visa"), MASTERCARD("Mastercard"), RUPAY("RuPay") }

/** The network picked in the edit sheet, else the one named in the card's name or bank ("Federal RuPay Card 0584"). */
fun cardNetwork(a: AccountEntity): CardNetwork? {
    a.network?.let { n -> CardNetwork.entries.firstOrNull { it.name == n }?.let { return it } }
    val s = "${a.name} ${a.bankName}".lowercase()
    return when {
        "rupay" in s -> CardNetwork.RUPAY
        Regex("""\bvisa\b""").containsMatchIn(s) -> CardNetwork.VISA
        Regex("""\bmaster ?card\b|\bmaster\b""").containsMatchIn(s) -> CardNetwork.MASTERCARD
        else -> null
    }
}
