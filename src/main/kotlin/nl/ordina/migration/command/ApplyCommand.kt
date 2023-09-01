package nl.ordina.migration.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.mordant.terminal.Terminal
import nl.ordina.github.organization.GitHubOrganization
import nl.ordina.migration.domain.Team
import nl.ordina.migration.service.PlanOptions
import nl.ordina.migration.service.PlanService
import nl.ordina.migration.service.Strategy

class ApplyCommand(private val terminal: Terminal, private val planService: PlanService) : CliktCommand() {
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

    private val blacklist by option(
        "--blacklist",
        help = "Name of the repository that you do not want to migrate, can be defined multiple times."
    ).multiple()

    override fun run() {
        val options = PlanOptions(token, source, destination, strategy, null, blacklist)
        val plan = planService.generatePlan(options)

        terminal.println(plan)

        val response = terminal.prompt("Do you wish to apply these changes", choices = listOf("yes", "no"))

        if (response == null || response.lowercase() != "yes") {
            terminal.warning("Migration has been cancelled")
            return
        }

        plan.repositories.map { repository -> repository.transfer(destination) }
        plan.members.map { member -> plan.destinationOrganization.invite(member.id) }
        plan.teams.map { team -> handleTeam(plan.destinationOrganization, team) }
    }

    private fun handleTeam(destinationOrganization: GitHubOrganization, team: Team) {
        // If the destination id is null we need to create a team
        val destinationTeam = team.destination ?: destinationOrganization.createTeam(team.name, team.description)

        team.members.forEach(destinationTeam::addMember)
        team.memberships.forEach { membership ->
            destinationTeam.addRepository(membership.repositoryName, membership.permission)
        }
    }
}
