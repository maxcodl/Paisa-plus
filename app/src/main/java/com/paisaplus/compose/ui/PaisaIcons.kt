package com.paisaplus.compose.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.paisaplus.compose.data.CardType

/**
 * Stored icons are Material Design Icons (MdiIcons) codepoints from the Flutter app.
 * Mapping mirrors the Flutter curated MDI -> Material Symbols table (lib/src/core/theme/stored_icon.dart).
 */
private val mdi: List<Triple<String, Int, ImageVector>> = listOf(
    Triple("home", 0xf02dc, Icons.Rounded.Home),
    Triple("homeOutline", 0xf06a1, Icons.Rounded.Home),
    Triple("homeVariant", 0xf02de, Icons.Rounded.Home),
    Triple("silverwareForkKnife", 0xf0a70, Icons.Rounded.Restaurant),
    Triple("food", 0xf025a, Icons.Rounded.Restaurant),
    Triple("foodForkDrink", 0xf05f2, Icons.Rounded.Restaurant),
    Triple("foodApple", 0xf025b, Icons.Rounded.LocalGroceryStore),
    Triple("cart", 0xf0110, Icons.Rounded.ShoppingCart),
    Triple("cartOutline", 0xf0111, Icons.Rounded.ShoppingCart),
    Triple("basket", 0xf0076, Icons.Rounded.ShoppingBasket),
    Triple("shopping", 0xf049a, Icons.Rounded.ShoppingBag),
    Triple("shoppingOutline", 0xf11d5, Icons.Rounded.ShoppingBag),
    Triple("bus", 0xf00e7, Icons.Rounded.DirectionsBus),
    Triple("car", 0xf010b, Icons.Rounded.DirectionsCar),
    Triple("carSide", 0xf07ab, Icons.Rounded.DirectionsCar),
    Triple("taxi", 0xf04ff, Icons.Rounded.LocalTaxi),
    Triple("train", 0xf052c, Icons.Rounded.Train),
    Triple("bike", 0xf00a3, Icons.Rounded.PedalBike),
    Triple("airplane", 0xf001d, Icons.Rounded.Flight),
    Triple("gasStation", 0xf0298, Icons.Rounded.LocalGasStation),
    Triple("medicalBag", 0xf06ef, Icons.Rounded.MedicalServices),
    Triple("hospital", 0xf0ff6, Icons.Rounded.LocalHospital),
    Triple("hospitalBox", 0xf02e0, Icons.Rounded.LocalHospital),
    Triple("pill", 0xf0402, Icons.Rounded.Medication),
    Triple("heart", 0xf02d1, Icons.Rounded.Favorite),
    Triple("heartPulse", 0xf05f6, Icons.Rounded.MonitorHeart),
    Triple("school", 0xf0474, Icons.Rounded.School),
    Triple("book", 0xf00ba, Icons.AutoMirrored.Rounded.MenuBook),
    Triple("movie", 0xf0381, Icons.Rounded.Movie),
    Triple("movieOpen", 0xf0fce, Icons.Rounded.Movie),
    Triple("gamepadVariant", 0xf0297, Icons.Rounded.SportsEsports),
    Triple("music", 0xf075a, Icons.Rounded.MusicNote),
    Triple("cash", 0xf0114, Icons.Rounded.Payments),
    Triple("cashMultiple", 0xf0116, Icons.Rounded.Payments),
    Triple("cashMinus", 0xf1260, Icons.Rounded.Payments),
    Triple("cashPlus", 0xf1261, Icons.Rounded.Payments),
    Triple("cashClock", 0xf1a91, Icons.Rounded.Schedule),
    Triple("accountCash", 0xf1097, Icons.Rounded.RequestQuote),
    Triple("accountCashOutline", 0xf1098, Icons.Rounded.RequestQuote),
    Triple("bank", 0xf0070, Icons.Rounded.AccountBalance),
    Triple("bankOutline", 0xf0e80, Icons.Rounded.AccountBalance),
    Triple("creditCard", 0xf0fef, Icons.Rounded.CreditCard),
    Triple("creditCardOutline", 0xf019b, Icons.Rounded.CreditCard),
    Triple("creditCardChip", 0xf190f, Icons.Rounded.CreditCard),
    Triple("wallet", 0xf0584, Icons.Rounded.AccountBalanceWallet),
    Triple("walletOutline", 0xf0bdd, Icons.Rounded.AccountBalanceWallet),
    Triple("piggyBank", 0xf1007, Icons.Rounded.Savings),
    Triple("chartLine", 0xf012a, Icons.AutoMirrored.Rounded.ShowChart),
    Triple("trendingUp", 0xf0535, Icons.AutoMirrored.Rounded.TrendingUp),
    Triple("flash", 0xf0241, Icons.Rounded.Bolt),
    Triple("lightningBolt", 0xf140b, Icons.Rounded.Bolt),
    Triple("water", 0xf058c, Icons.Rounded.WaterDrop),
    Triple("wifi", 0xf05a9, Icons.Rounded.Wifi),
    Triple("cellphone", 0xf011c, Icons.Rounded.Smartphone),
    Triple("phone", 0xf03f2, Icons.Rounded.Call),
    Triple("tshirtCrew", 0xf0a7b, Icons.Rounded.Checkroom),
    Triple("hanger", 0xf02c8, Icons.Rounded.Checkroom),
    Triple("gift", 0xf0e44, Icons.Rounded.Redeem),
    Triple("briefcase", 0xf00d6, Icons.Rounded.Work),
    Triple("dumbbell", 0xf01e6, Icons.Rounded.FitnessCenter),
    Triple("paw", 0xf03e9, Icons.Rounded.Pets),
    Triple("tag", 0xf04f9, Icons.Rounded.Sell),
    Triple("star", 0xf04ce, Icons.Rounded.Star),
    Triple("bell", 0xf009a, Icons.Rounded.Notifications),
    Triple("calendar", 0xf00ed, Icons.Rounded.CalendarMonth),
    Triple("cog", 0xf0493, Icons.Rounded.Settings),
    Triple("account", 0xf0004, Icons.Rounded.Person),
    Triple("accountGroup", 0xf0849, Icons.Rounded.Group),
    Triple("coffee", 0xf0176, Icons.Rounded.Coffee),
    Triple("beer", 0xf0098, Icons.Rounded.SportsBar),
    Triple("glassCocktail", 0xf0356, Icons.Rounded.LocalBar),
    Triple("pizza", 0xf0409, Icons.Rounded.LocalPizza),
    Triple("cake", 0xf00e9, Icons.Rounded.Cake),
    Triple("bed", 0xf02e3, Icons.Rounded.Bed),
    Triple("umbrella", 0xf054a, Icons.Rounded.BeachAccess),
    Triple("wrench", 0xf05b7, Icons.Rounded.Build),
    Triple("laptop", 0xf0322, Icons.Rounded.Laptop),
    Triple("television", 0xf0502, Icons.Rounded.Tv),
    Triple("fileDocument", 0xf0219, Icons.Rounded.Description),
    Triple("receipt", 0xf0824, Icons.AutoMirrored.Rounded.ReceiptLong),
    Triple("swapHorizontal", 0xf04e1, Icons.Rounded.SwapHoriz),
    Triple("shape", 0xf0831, Icons.Rounded.Category),
    Triple("shapeOutline", 0xf0832, Icons.Rounded.Category),
    Triple("plus", 0xf0415, Icons.Rounded.Add),
    Triple("magnify", 0xf0349, Icons.Rounded.Search),
    Triple("arrowRight", 0xf0054, Icons.AutoMirrored.Rounded.ArrowForward),
)

private val byCode = mdi.associate { it.second to it.third }

const val MDI_TAG = 0xf04f9
const val MDI_HOME = 0xf02dc

fun paisaIcon(codePoint: Int, fallback: ImageVector = Icons.Rounded.Sell): ImageVector = byCode[codePoint] ?: fallback

/** One entry per distinct glyph, for the icon picker. */
val pickerIcons: List<Pair<Int, ImageVector>> = mdi.distinctBy { it.third }.map { it.second to it.third }

/** Codepoint the Flutter app stores for a new account of this type (CardType.icon). */
val CardType.mdiCode: Int
    get() = when (this) {
        CardType.CASH -> 0xf0116
        CardType.BANK -> 0xf0070
        CardType.WALLET -> 0xf0584
        CardType.CREDIT -> 0xf190f
    }
