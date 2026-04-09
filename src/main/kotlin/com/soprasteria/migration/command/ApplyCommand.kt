package com.soprasteria.migration.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.mordant.terminal.Terminal
import com.soprasteria.migration.domain.PlanRenderer
import com.soprasteria.migration.service.ApplyService
import com.soprasteria.migration.service.PlanService
import kotlinx.coroutines.runBlocking

class ApplyCommand(
    private val terminal: Terminal,
    private val planService: PlanService,
    private val applyService: ApplyService,
) : CliktCommand() {
    private val migrationOptions by MigrationOptions()

    override fun run() {
        runBlocking {
            planService
                .generatePlan(migrationOptions.toPlanOptions())
                .onLeft {
                    terminal.danger(it.message)
                    return@runBlocking
                }.onRight { plan ->
                    if (plan.repositories.isEmpty() && plan.members.isEmpty() && plan.teams.isEmpty()) {
                        terminal.warning("Nothing to migrate — all resources are already present in the destination or excluded by the blacklist")
                        return@runBlocking
                    }

                    terminal.println(PlanRenderer().render(plan))

                    val response = terminal.prompt("Do you wish to apply these changes", choices = listOf("yes", "no"))

                    if (response == null || response.lowercase() != "yes") {
                        terminal.println("Migration has been cancelled")
                        return@runBlocking
                    }

                    applyService.apply(plan, migrationOptions.effectiveSourceToken(), migrationOptions.effectiveTargetToken())
                }
        }
    }
}
