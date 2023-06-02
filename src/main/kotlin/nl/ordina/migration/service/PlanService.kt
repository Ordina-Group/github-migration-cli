package nl.ordina.migration.service

import com.github.ajalt.mordant.terminal.Terminal
import nl.ordina.github.GitHubClient
import nl.ordina.github.organization.GitHubOrganization
import nl.ordina.migration.domain.Member
import nl.ordina.migration.domain.Membership
import nl.ordina.migration.domain.Plan
import nl.ordina.migration.domain.Repository
import nl.ordina.migration.domain.RepositoryCollaborator
import nl.ordina.migration.domain.Team

data class PlanOptions(
    val token: String,
    val source: String,
    val destination: String,
    val strategy: Strategy,
    val output: String?
)

enum class Strategy {
    Merge,
    Prefix
}

class PlanService(private val terminal: Terminal) {

    fun generatePlan(options: PlanOptions): Plan {
        val source = options.source
        val destination = options.destination

        val client = GitHubClient.create(options.token)
        val sourceOrganization = getOrganization(client, options.source)
        val destinationOrganization = getOrganization(client, options.destination)

        terminal.info(
            "Refreshing state for source organization $source and destination organization $destination"
        )

        val repositories = getRepositories(sourceOrganization)
        val teams = getTeams(sourceOrganization)

        val members = sourceOrganization.getMembers()
            .map { Member(it.id, it.login) }

        val existingMembers = destinationOrganization.getMembers()
        val existingRepositories = destinationOrganization.getRepositories()
        val existingTeams = destinationOrganization.getTeams()

        return Plan.create(
            sourceOrganization,
            destinationOrganization,
            members,
            repositories,
            teams,
            existingMembers,
            existingRepositories,
            existingTeams,
            options.strategy
        )
    }

    private fun getOrganization(client: GitHubClient, name: String): GitHubOrganization =
        client.getOrganization(name) ?: throw OrganizationNotFoundException(name)

    private fun getRepositories(organization: GitHubOrganization): List<Repository> =
        organization.getRepositories()
            .map { repository ->
                val collaborators = repository
                    .getDirectCollaborators()
                    .map { RepositoryCollaborator(it.id, it.login, it.role_name) }

                repository.getTeams().map {
                }

                Repository(repository, collaborators)
            }

    private fun getTeams(organization: GitHubOrganization): List<Team> =
        organization.getTeams()
            .map { team ->
                val teamMembers = team.getMembers().map { it.login }
                val teamRepositories = team
                    .getRepositories()
                    .map { Membership(it.name, it.permissions?.permission?.value ?: "pull") }

                Team(
                    team = team,
                    members = teamMembers,
                    memberships = teamRepositories
                )
            }
}

class OrganizationNotFoundException(name: String) : Throwable("Organization with name $name could not be found")
