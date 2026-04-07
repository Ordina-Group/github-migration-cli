package com.soprasteria.migration.command

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.soprasteria.migration.service.PlanOptions
import com.soprasteria.migration.service.Strategy

class MigrationOptions : OptionGroup() {
    val token by option("--token", "-t", help = "Token used for requesting the GitHub api").required()

    val source by option(
        "--source",
        "-s",
        help = "The source GitHub organisation to migrate from",
    ).required()

    val destination by option(
        "--destination",
        "-d",
        help = "The destination GitHub organisation to migrate to",
    ).required()

    val strategy by option(
        "--strategy",
        help = "Strategy to use when a resource already exists on the destination organization",
    ).enum<Strategy>().default(Strategy.Merge)

    val blacklist by option(
        "--blacklist",
        help = "Name of the repository to exclude from migration; can be specified multiple times",
    ).multiple()

    val parentTeam by option(
        "--parent-team",
        help = "Slug of the parent team that all newly created teams will be nested under",
    )

    fun toPlanOptions(output: String? = null): PlanOptions =
        PlanOptions(token, source, destination, strategy, output, blacklist, parentTeam)
}
