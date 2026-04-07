package com.soprasteria.migration.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.github.ajalt.mordant.terminal.Terminal
import com.soprasteria.github.GitHubClient
import com.soprasteria.github.organization.GitHubOrganization
import com.soprasteria.migration.domain.Member
import com.soprasteria.migration.domain.Membership
import com.soprasteria.migration.domain.MigrationContext
import com.soprasteria.migration.domain.MigrationError
import com.soprasteria.migration.domain.Plan
import com.soprasteria.migration.domain.Repository
import com.soprasteria.migration.domain.RepositoryCollaborator
import com.soprasteria.migration.domain.Team
import com.soprasteria.migration.domain.createPlan
import kotlinx.coroutines.runBlocking

data class PlanOptions(
    val sourceToken: String,
    val targetToken: String,
    val source: String,
    val destination: String,
    val strategy: Strategy,
    val output: String?,
    val blacklist: List<String>,
    val parentTeamName: String?,
)

enum class Strategy {
    Merge,
    Prefix,
}

class PlanService(
    private val terminal: Terminal,
) {
    fun generatePlan(options: PlanOptions): Either<MigrationError, Plan> =
        either {
            val sourceClient = GitHubClient.create(options.sourceToken)
            val targetClient = GitHubClient.create(options.targetToken)

            val sourceOrganization =
                ensureNotNull(
                    runBlocking { sourceClient.organizations.get(options.source).getOrNull() },
                ) { MigrationError.OrganizationNotFound(options.source) }

            val destinationOrganization =
                ensureNotNull(
                    runBlocking { targetClient.organizations.get(options.destination).getOrNull() },
                ) { MigrationError.OrganizationNotFound(options.destination) }

            terminal.info(
                "Refreshing state for source organization ${options.source} " +
                    "and destination organization ${options.destination}",
            )

            val repositories =
                getRepositories(sourceClient, sourceOrganization)
                    .filterNot { it.name in options.blacklist }
            val teams = getTeams(sourceClient, sourceOrganization, options.blacklist)
            val members =
                runBlocking { sourceClient.organizations.getMembers(sourceOrganization).getOrNull() }
                    .orEmpty()
                    .map { Member(it.id, it.login) }

            val parentTeam =
                options.parentTeamName?.let { slug ->
                    runBlocking { targetClient.organizations.getTeam(destinationOrganization, slug).getOrNull() }
                }

            val existingMembers =
                runBlocking {
                    targetClient.organizations.getMembers(destinationOrganization).getOrNull()
                }.orEmpty()
            val existingRepositories =
                runBlocking {
                    targetClient.organizations.getRepositories(destinationOrganization).getOrNull()
                }.orEmpty()
            val existingTeams =
                runBlocking {
                    targetClient.organizations.getTeams(destinationOrganization).getOrNull()
                }.orEmpty()

            createPlan(
                MigrationContext(
                    sourceOrganization = sourceOrganization,
                    destinationOrganization = destinationOrganization,
                    sourceMembers = members,
                    sourceRepositories = repositories,
                    sourceTeams = teams,
                    destinationMembers = existingMembers,
                    destinationRepositories = existingRepositories,
                    destinationTeams = existingTeams,
                    parentTeam = parentTeam,
                    strategy = options.strategy,
                ),
            )
        }

    private fun getRepositories(
        client: GitHubClient,
        organization: GitHubOrganization,
    ): List<Repository> {
        val ghRepos = runBlocking { client.organizations.getRepositories(organization).getOrNull() }.orEmpty()
        return ghRepos.map { repository ->
            val collaborators =
                runBlocking {
                    client.repositories.getDirectCollaborators(repository).getOrNull()
                }.orEmpty().map { RepositoryCollaborator(it.id, it.login, it.roleName) }
            Repository(repository, collaborators)
        }
    }

    private fun getTeams(
        client: GitHubClient,
        organization: GitHubOrganization,
        repositoryBlacklist: List<String>,
    ): List<Team> {
        val ghTeams = runBlocking { client.organizations.getTeams(organization).getOrNull() }.orEmpty()
        return ghTeams.map { team ->
            val teamMembers =
                runBlocking { client.teams.getMembers(team).getOrNull() }
                    .orEmpty()
                    .map { it.login }
            val teamRepositories =
                runBlocking { client.teams.getRepositories(team).getOrNull() }
                    .orEmpty()
                    .filterNot { it.name in repositoryBlacklist }
                    .map { Membership(it.name, it.permissions?.permission?.value ?: "pull") }

            Team(team = team, members = teamMembers, memberships = teamRepositories)
        }
    }
}
