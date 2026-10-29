# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

The owner, logging daily income and expenses on an Android phone, and people moving over from the Flutter Paisa app who bring an existing JSON backup.

## Product Purpose

paisa+ is a personal finance tracker: accounts, transactions, categories with budgets, loans and card EMIs, recurring rules, and monthly reports. Success means logging an expense takes seconds and the month's position is readable at a glance.

## Positioning

A native Jetpack Compose rewrite of Paisa. Data stays on the device, with no account and no cloud. It reads Flutter Paisa JSON backups, and the settings keys keep the Flutter names so a migration loses nothing.

## Operating Context

Used one-handed in short sessions right after spending, and in longer sessions at month end to review reports and budgets. Migrants arrive with a backup file and restore it on first run.

## Capabilities and Constraints

- Kotlin, Jetpack Compose, Material 3 1.5 (Expressive), Room, DataStore, WorkManager, Hilt. minSdk 26, targetSdk 36.
- Local-only storage. The Flutter backup import (`data/BackupImporter.kt`) wipes and replaces all tables.
- English only, with no i18n yet.
- Not built yet (shown in Paisa, absent here): goals, assets, calendar heatmap, lending/borrowing, Google Drive backup, demo mode.

## Brand Commitments

- The name is "paisa+" and the app id is `com.paisaplus.compose`.
- The visual reference is the current Paisa design (`D:\Git forks\paisa\newdesign\`), which the user made binding for this redesign.

## Evidence on Hand

There are no user reviews, metrics or testimonials for this app. Paisa's store reviews belong to the original app and must not be shown as this app's.

## Product Principles

1. Recording money is the core action, so it is never more than one tap from Home.
2. Numbers are the content. Every amount is legible, correctly signed and currency-aware.
3. The user owns the data: offline first, backups explicit, nothing hidden behind accounts.
4. Ship only features that exist. No placeholders that imply missing capability.

## Accessibility & Inclusion

- Text contrast ≥4.5:1 in both themes.
- Touch targets ≥48dp.
- TalkBack labels on every control and amount.
- Usable at 200% font scale.
