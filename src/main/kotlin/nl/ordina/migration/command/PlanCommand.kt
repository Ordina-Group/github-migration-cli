package nl.ordina.migration.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.mordant.terminal.Terminal
import nl.ordina.migration.service.PlanOptions
import nl.ordina.migration.service.PlanService
import nl.ordina.migration.service.Strategy

class PlanCommand(private val terminal: Terminal, private val planService: PlanService) : CliktCommand() {
    private val token by option(
        "--token",
        "-t",
        help = "Token used for requesting the GitHub api"
    ).required()

    private val source by option(
        "--source",
        "-s",
        help = "The source GitHub organisation to migrate from"
    ).required()

    private val destination by option(
        "--destination",
        "-d",
        help = "The destination GitHub organisation to migrate to"
    ).required()

    private val strategy by option(
        "--strategy",
        help = "Strategy to use when a resource already exists on the destination organization"
    ).enum<Strategy>().default(Strategy.Merge)

    private val output by option("--output", "-o")

    private val blacklist by option(
        "--blacklist",
        help = "Name of the repository that you do not want to migrate, can be defined multiple times."
    ).multiple()

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
        println(blacklist)

        val options = PlanOptions(token, source, destination, strategy, output, blacklist)
        val plan = planService.generatePlan(options)

        terminal.println(plan)
    }
}
