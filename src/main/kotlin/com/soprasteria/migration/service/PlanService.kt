package com.soprasteria.migration.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.github.ajalt.mordant.terminal.Terminal
import com.soprasteria.github.ApiResult
import com.soprasteria.github.GitHubClient
import com.soprasteria.github.GitHubApiException
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

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

private data class FetchResult(
    val repositories: List<Repository>,
    val teams: List<Team>,
    val sourceMembers: List<Member>,
    val existingMembers: List<com.soprasteria.github.organization.GitHubOrganizationMember>,
    val existingRepositories: List<com.soprasteria.github.repository.GitHubRepository>,
    val existingTeams: List<com.soprasteria.github.team.GitHubTeam>,
    val parentTeam: com.soprasteria.github.team.GitHubTeam?,
)

class PlanService(
    private val terminal: Terminal,
    private val clientFactory: (String) -> GitHubClient = GitHubClient::create,
) {
    suspend fun generatePlan(options: PlanOptions): Either<MigrationError, Plan> =
        either {
            val sourceClient = clientFactory(options.sourceToken)
            val targetClient = clientFactory(options.targetToken)

            val sourceOrganization =
                ensureNotNull(
                    apiCall("fetching source organisation '${options.source}'") {
                        sourceClient.organizations.get(options.source)
                    },
                ) { MigrationError.OrganizationNotFound(options.source) }

            val destinationOrganization =
                ensureNotNull(
                    apiCall("fetching destination organisation '${options.destination}'") {
                        targetClient.organizations.get(options.destination)
                    },
                ) { MigrationError.OrganizationNotFound(options.destination) }

            terminal.info("Fetching state for ${options.source} → ${options.destination}…")

            val fetched: FetchResult =
                coroutineScope {
                    val reposJob = async { getRepositories(sourceClient, sourceOrganization) }
                    val teamsJob = async { getTeams(sourceClient, sourceOrganization, options.blacklist) }
                    val sourceMembersJob =
                        async {
                            apiCall("fetching members for '${sourceOrganization.login}'") {
                                sourceClient.organizations.getMembers(sourceOrganization)
                            }.orEmpty().map { Member(it.id, it.login) }
                        }
                    val existingMembersJob =
                        async {
                            apiCall("fetching existing members for '${destinationOrganization.login}'") {
                                targetClient.organizations.getMembers(destinationOrganization)
                            }.orEmpty()
                        }
                    val existingReposJob =
                        async {
                            apiCall("fetching existing repositories for '${destinationOrganization.login}'") {
                                targetClient.organizations.getRepositories(destinationOrganization)
                            }.orEmpty()
                        }
                    val existingTeamsJob =
                        async {
                            apiCall("fetching existing teams for '${destinationOrganization.login}'") {
                                targetClient.organizations.getTeams(destinationOrganization)
                            }.orEmpty()
                        }
                    val parentTeamJob =
                        async {
                            options.parentTeamName?.let { slug ->
                                apiCall("fetching parent team '$slug'") {
                                    targetClient.organizations.getTeam(destinationOrganization, slug)
                                }
                            }
                        }

                    val allSourceRepos = reposJob.await()
                    validateBlacklist(options.blacklist, allSourceRepos)

                    FetchResult(
                        repositories = allSourceRepos.filterNot { it.name in options.blacklist },
                        teams = teamsJob.await(),
                        sourceMembers = sourceMembersJob.await(),
                        existingMembers = existingMembersJob.await(),
                        existingRepositories = existingReposJob.await(),
                        existingTeams = existingTeamsJob.await(),
                        parentTeam = parentTeamJob.await(),
                    )
                }

            if (options.parentTeamName != null && fetched.parentTeam == null) {
                raise(MigrationError.ParentTeamNotFound(options.parentTeamName))
            }

            terminal.info(
                "Found ${fetched.repositories.size} repositories, " +
                    "${fetched.sourceMembers.size} members, " +
                    "${fetched.teams.size} teams in '${options.source}'",
            )

            createPlan(
                MigrationContext(
                    sourceOrganization = sourceOrganization,
                    destinationOrganization = destinationOrganization,
                    sourceMembers = fetched.sourceMembers,
                    sourceRepositories = fetched.repositories,
                    sourceTeams = fetched.teams,
                    destinationMembers = fetched.existingMembers,
                    destinationRepositories = fetched.existingRepositories,
                    destinationTeams = fetched.existingTeams,
                    parentTeam = fetched.parentTeam,
                    strategy = options.strategy,
                ),
            )
        }

    private suspend fun getRepositories(
        client: GitHubClient,
        organization: GitHubOrganization,
    ): List<Repository> {
        val ghRepos =
            apiCall("fetching repositories for '${organization.login}'") {
                client.organizations.getRepositories(organization)
            }.orEmpty()

        terminal.info("Fetching collaborators for ${ghRepos.size} repositories in '${organization.login}'…")

        return coroutineScope {
            ghRepos
                .map { ghRepo ->
                    async {
                        val collaborators =
                            apiCall("fetching collaborators for '${ghRepo.name}'") {
                                client.repositories.getDirectCollaborators(ghRepo)
                            }.orEmpty().map { RepositoryCollaborator(it.id, it.login, it.roleName) }
                        Repository(ghRepo, collaborators)
                    }
                }.awaitAll()
        }
    }

    private suspend fun getTeams(
        client: GitHubClient,
        organization: GitHubOrganization,
        repositoryBlacklist: List<String>,
    ): List<Team> {
        val ghTeams =
            apiCall("fetching teams for '${organization.login}'") {
                client.organizations.getTeams(organization)
            }.orEmpty()

        return coroutineScope {
            ghTeams
                .map { team ->
                    async {
                        val members =
                            apiCall("fetching members for team '${team.name}'") {
                                client.teams.getMembers(team)
                            }.orEmpty().map { it.login }
                        val memberships =
                            apiCall("fetching repositories for team '${team.name}'") {
                                client.teams.getRepositories(team)
                            }.orEmpty()
                                .filterNot { it.name in repositoryBlacklist }
                                .map { Membership(it.name, it.permissions?.permission?.value ?: "pull") }
                        Team(team = team, members = members, memberships = memberships)
                    }
                }.awaitAll()
        }
    }

    private fun validateBlacklist(
        blacklist: List<String>,
        repositories: List<Repository>,
    ) {
        if (blacklist.isEmpty()) return
        val repositoryNames = repositories.map { it.name }.toSet()
        val unmatched = blacklist.filterNot { it in repositoryNames }
        if (unmatched.isNotEmpty()) {
            terminal.warning("Blacklist ${if (unmatched.size == 1) "entry" else "entries"} did not match any repository: ${unmatched.joinToString()}")
        }
    }

    private suspend fun <T> apiCall(
        description: String,
        block: suspend () -> ApiResult<T>,
    ): T? {
        val result = withTimeoutOrNull(30.seconds) { block() }
        return when {
            result == null -> {
                terminal.warning("Timed out while $description")
                null
            }
            result is ApiResult.Failure -> {
                terminal.warning("Failed $description: ${result.exception.toSafeMessage()}")
                null
            }
            else -> result.getOrNull()
        }
    }
}

private fun GitHubApiException.toSafeMessage(): String = message ?: "unknown"
