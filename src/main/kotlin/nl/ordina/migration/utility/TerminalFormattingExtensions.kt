package nl.ordina.migration.utility

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles

val String.bold: String
    get() = TextStyles.bold(this)

val String.green: String
    get() = TextColors.green(this)

val String.yellow: String
    get() = TextColors.yellow(this)
