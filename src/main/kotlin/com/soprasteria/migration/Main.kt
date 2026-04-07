package com.soprasteria.migration

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.mordant.terminal.Terminal
import com.soprasteria.migration.command.ApplyCommand
import com.soprasteria.migration.command.MainCommand
import com.soprasteria.migration.command.PlanCommand
import com.soprasteria.migration.service.ApplyService
import com.soprasteria.migration.service.PlanService

fun main(args: Array<String>) {
    val terminal = Terminal()
    val planService = PlanService(terminal)
    val applyService = ApplyService(terminal)

    MainCommand()
        .subcommands(
            PlanCommand(terminal, planService),
            ApplyCommand(terminal, planService, applyService),
        ).main(args)
}
