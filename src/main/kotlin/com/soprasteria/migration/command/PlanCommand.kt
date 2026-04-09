package com.soprasteria.migration.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.terminal.Terminal
import com.soprasteria.migration.domain.PlanRenderer
import com.soprasteria.migration.service.PlanService
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException

class PlanCommand(
    private val terminal: Terminal,
    private val planService: PlanService,
) : CliktCommand() {
    private val migrationOptions by MigrationOptions()

    private val output by option(
        "--output",
        "-o",
        help = "Write the plan to a file instead of (or in addition to) printing it",
    )

    /**
     * Steps:
     * 1) Get repositories, teams and members for the source organisation
     * 2) Create teams in the destination organisation
     * 3) Invite members to organisation (and teams if any)
     * 4) Get team permissions on repositories
     * 5) Get contributors and their permissions on repositories
     * 6) Transfer repositories to new organisation
     * 7) Add teams and members to repositories
     */

    override fun run() {
        runBlocking {
            planService
                .generatePlan(migrationOptions.toPlanOptions(output))
                .onLeft {
                    terminal.danger(it.message)
                    return@runBlocking
                }.onRight { plan ->
                    if (plan.repositories.isEmpty() && plan.members.isEmpty() && plan.teams.isEmpty()) {
                        terminal.warning("Nothing to migrate — all resources are already present in the destination or excluded by the blacklist")
                        return@runBlocking
                    }

                    val planText = PlanRenderer().render(plan)
                    terminal.println(planText)

                    output?.let { path ->
                        try {
                            File(path).writeText(planText)
                            terminal.success("Plan written to $path")
                        } catch (e: IOException) {
                            terminal.danger("Failed to write plan to $path: ${e.message}")
                        }
                    }
                }
        }
    }
}
