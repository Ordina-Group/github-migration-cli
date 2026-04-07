package com.soprasteria.migration.command

import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.soprasteria.migration.service.PlanOptions
import com.soprasteria.migration.service.Strategy

class MigrationOptions : OptionGroup() {
    val token by option(
        "--token", "-t",
        help = "Token for both source and target GitHub API (use when a single token covers both organisations)",
    )

    val sourceToken by option(
        "--source-token",
        help = "Token for the source GitHub organisation API (overrides --token for source)",
    )

    val targetToken by option(
        "--target-token",
        help = "Token for the target GitHub organisation API (overrides --token for target)",
    )

    fun effectiveSourceToken(): String {
        if (sourceToken != null || targetToken != null) {
            return sourceToken ?: throw UsageError("--source-token is required when --target-token is specified")
        }
        return token ?: throw UsageError("Provide either --token or both --source-token and --target-token")
    }

    fun effectiveTargetToken(): String {
        if (sourceToken != null || targetToken != null) {
            return targetToken ?: throw UsageError("--target-token is required when --source-token is specified")
        }
        return token ?: throw UsageError("Provide either --token or both --source-token and --target-token")
    }

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
        PlanOptions(effectiveSourceToken(), effectiveTargetToken(), source, destination, strategy, output, blacklist, parentTeam)
}
