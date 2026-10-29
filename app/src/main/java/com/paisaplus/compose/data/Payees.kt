package com.paisaplus.compose.data

/**
 * A payee is every transaction sharing a name, compared trimmed and case-insensitive ("Akbar Ali C K" =
 * "akbar ali c k "). No table: renaming the name renames the payee, and renaming onto an existing name merges.
 */
fun payeeKey(name: String) = name.trim().lowercase()

/** [name]'s transactions, in [expenses]' order (newest first as stored). */
fun payeeTransactions(expenses: List<ExpenseEntity>, name: String): List<ExpenseEntity> {
    val key = payeeKey(name)
    return expenses.filter { payeeKey(it.name) == key }
}

/** How many transactions renaming [from] to [to] would merge into; 0 = [to] is free (or only a case change). */
fun mergeCount(expenses: List<ExpenseEntity>, from: String, to: String): Int =
    if (payeeKey(from) == payeeKey(to)) 0 else payeeTransactions(expenses, to).size
