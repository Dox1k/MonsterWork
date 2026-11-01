package com.shiftcalendar.model

import kotlin.random.Random

enum class CanRarity(val label: String, val weight: Int) {
    COMMON("Обычная", 65), RARE("Редкая", 25), EPIC("Эпическая", 9), LEGENDARY("Легендарная", 1)
}

data class CanReward(val flavor: String, val colors: List<Long>, val rarity: CanRarity)

private val flavors = listOf(
    "Клубника", "Лимон", "Черника", "Арбуз", "Виноград", "Апельсин", "Мята", "Кола"
)
private val palettes = listOf(
    listOf(0xFFE53935, 0xFFFFCDD2), listOf(0xFFFDD835, 0xFFFFF9C4),
    listOf(0xFF3949AB, 0xFFC5CAE9), listOf(0xFF43A047, 0xFFC8E6C9),
    listOf(0xFF8E24AA, 0xFFE1BEE7), listOf(0xFFFB8C00, 0xFFFFE0B2),
    listOf(0xFF00897B, 0xFFB2DFDB), listOf(0xFF5D4037, 0xFFD7CCC8)
)

fun drawCan(random: Random = Random.Default): CanReward {
    val roll = random.nextInt(100)
    val rarity = when {
        roll < 65 -> CanRarity.COMMON
        roll < 90 -> CanRarity.RARE
        roll < 99 -> CanRarity.EPIC
        else -> CanRarity.LEGENDARY
    }
    val index = random.nextInt(flavors.size)
    return CanReward(flavors[index], palettes[index], rarity)
}

fun encodeReward(reward: CanReward): String = listOf(
    reward.flavor,
    reward.colors.joinToString(",") { java.lang.Long.toHexString(it) },
    reward.rarity.name
).joinToString("|")

fun decodeReward(value: String): CanReward? = runCatching {
    val parts = value.split('|')
    require(parts.size == 3)
    CanReward(
        flavor = parts[0],
        colors = parts[1].split(',').map { java.lang.Long.parseLong(it, 16) },
        rarity = CanRarity.valueOf(parts[2])
    )
}.getOrNull()

