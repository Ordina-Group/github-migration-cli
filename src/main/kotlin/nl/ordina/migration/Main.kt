package nl.ordina.migration

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.mordant.terminal.Terminal
import nl.ordina.migration.command.ApplyCommand
import nl.ordina.migration.command.MainCommand
import nl.ordina.migration.command.PlanCommand
import nl.ordina.migration.service.PlanService

fun main(args: Array<String>) {
    val terminal = Terminal()

    val planService = PlanService(terminal)
    val planCommand = PlanCommand(terminal, planService)

    val applyCommand = ApplyCommand(terminal, planService)

    MainCommand().subcommands(planCommand, applyCommand).main(args)
}
