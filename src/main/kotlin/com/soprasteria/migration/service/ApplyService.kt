package com.soprasteria.migration.service

import com.github.ajalt.mordant.terminal.Terminal
import com.soprasteria.github.ApiResult
import com.soprasteria.github.GitHubApiException
import com.soprasteria.github.GitHubClient
import com.soprasteria.github.organization.GitHubOrganization
import com.soprasteria.github.repository.Permission
import com.soprasteria.github.team.GitHubTeam
import com.soprasteria.github.team.TeamPrivacy
import com.soprasteria.migration.domain.Membership
import com.soprasteria.migration.domain.MigrationError
import com.soprasteria.migration.domain.Plan
import com.soprasteria.migration.domain.Team
import kotlinx.coroutines.CancellationException

private data class OperationSummary(
    val attempted: Int,
    val skipped: Int = 0,
    val errors: List<MigrationError>,
) {
    val succeeded: Int get() = attempted - skipped - errors.size
}

class ApplyService(
    private val terminal: Terminal,
    private val clientFactory: (String) -> GitHubClient = GitHubClient::create,
) {
    suspend fun apply(
        plan: Plan,
        sourceToken: String,
        targetToken: String,
    ) {
        val sourceClient = clientFactory(sourceToken)
        val targetClient = clientFactory(targetToken)

        if (!preflight(plan, sourceClient, targetClient)) return

        val repoSummary = transferRepositories(plan, sourceClient, targetClient)
        val memberSummary = inviteMembers(plan, targetClient)
        val teamSummary = migrateTeams(plan, targetClient)

        val allErrors = repoSummary.errors + memberSummary.errors + teamSummary.errors
        allErrors.forEach { terminal.danger(it.message) }

        printSummary(repoSummary, memberSummary, teamSummary)
    }

    private suspend fun preflight(
        plan: Plan,
        sourceClient: GitHubClient,
        targetClient: GitHubClient,
    ): Boolean {
        val sourceAccessible = sourceClient.organizations.get(plan.sourceOrganization.login).isFound()
        val targetAccessible = targetClient.organizations.get(plan.destinationOrganization.login).isFound()

        if (!sourceAccessible) {
            terminal.danger(
                "Pre-flight failed: source organisation '${plan.sourceOrganization.login}' " +
                    "is not accessible with the provided source token",
            )
        }
        if (!targetAccessible) {
            terminal.danger(
                "Pre-flight failed: destination organisation '${plan.destinationOrganization.login}' " +
                    "is not accessible with the provided target token",
            )
        }
        return sourceAccessible && targetAccessible
    }

    private suspend fun transferRepositories(
        plan: Plan,
        sourceClient: GitHubClient,
        targetClient: GitHubClient,
    ): OperationSummary {
        val alreadyPresent =
            targetClient.organizations
                .getRepositories(plan.destinationOrganization)
                .getOrNull()
                .orEmpty()
                .map { it.name }
                .toSet()

        val toTransfer = plan.repositories.filter { it.name !in alreadyPresent }
        val skipped = plan.repositories.size - toTransfer.size

        if (skipped > 0) {
            terminal.info("Skipping $skipped ${if (skipped == 1) "repository" else "repositories"} already present in destination")
        }

        val errors =
            toTransfer.mapNotNull { repository ->
                safeRun({ MigrationError.RepositoryTransferFailed(repository.name, it) }) {
                    sourceClient.repositories.transfer(
                        plan.sourceOrganization.login,
                        repository.originalName,
                        plan.destinationOrganization.login,
                    )
                }
            }

        return OperationSummary(attempted = plan.repositories.size, skipped = skipped, errors = errors)
    }

    private suspend fun inviteMembers(
        plan: Plan,
        targetClient: GitHubClient,
    ): OperationSummary {
        val errors =
            plan.members.mapNotNull { member ->
                val result = safeApiResult({ MigrationError.MemberInviteFailed(member.login, it) }) {
                    targetClient.organizations.invite(plan.destinationOrganization, member.id)
                }
                result
            }
        return OperationSummary(attempted = plan.members.size, errors = errors)
    }

    private suspend fun migrateTeams(
        plan: Plan,
        targetClient: GitHubClient,
    ): OperationSummary {
        val errors = plan.teams.flatMap { team ->
            migrateTeam(targetClient, plan.destinationOrganization, team, plan.parentTeam)
        }
        return OperationSummary(attempted = plan.teams.size, errors = errors)
    }

    private suspend fun migrateTeam(
        targetClient: GitHubClient,
        destinationOrganization: GitHubOrganization,
        team: Team,
        parentTeam: GitHubTeam?,
    ): List<MigrationError> {
        var creationError: MigrationError? = null
        val destinationTeam: GitHubTeam? =
            try {
                team.destination
                    ?: targetClient.organizations
                        .createTeam(
                            destinationOrganization,
                            team.name,
                            team.description ?: "",
                            team.privacy?.toTeamPrivacy() ?: TeamPrivacy.Secret,
                            parentTeam?.id,
                        ).getOrThrow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: GitHubApiException) {
                creationError = MigrationError.TeamCreationFailed(team.name, e.message ?: "unknown")
                null
            } catch (e: Exception) {
                creationError = MigrationError.TeamCreationFailed(team.name, e.javaClass.simpleName)
                null
            }

        if (destinationTeam == null) return listOfNotNull(creationError)

        return addTeamMembers(targetClient, team, destinationTeam) +
            addTeamRepositories(targetClient, team, destinationTeam)
    }

    private suspend fun addTeamMembers(
        targetClient: GitHubClient,
        team: Team,
        destinationTeam: GitHubTeam,
    ): List<MigrationError> =
        team.members.mapNotNull { member ->
            safeRun({ MigrationError.TeamMemberAddFailed(member, team.name, it) }) {
                targetClient.teams.addMember(destinationTeam, member)
            }
        }

    private suspend fun addTeamRepositories(
        targetClient: GitHubClient,
        team: Team,
        destinationTeam: GitHubTeam,
    ): List<MigrationError> =
        team.memberships.mapNotNull { membership ->
            safeRun({ MigrationError.TeamRepositoryAddFailed(team.name, membership.repositoryName, it) }) {
                targetClient.teams.addRepository(destinationTeam, membership.repositoryName, membership.toPermission())
            }
        }

    private fun printSummary(
        repos: OperationSummary,
        members: OperationSummary,
        teams: OperationSummary,
    ) {
        val allErrors = repos.errors + members.errors + teams.errors
        terminal.println()
        terminal.println("Migration summary")
        terminal.println("  Repositories  ${repos.succeeded} transferred, ${repos.skipped} skipped, ${repos.errors.size} failed")
        terminal.println("  Members       ${members.succeeded} invited, ${members.errors.size} failed")
        terminal.println("  Teams         ${teams.succeeded} migrated, ${teams.errors.size} failed")

        if (allErrors.isEmpty()) {
            terminal.success("Migration completed successfully")
        } else {
            terminal.warning("Migration completed with ${allErrors.size} error(s)")
        }
    }
}

/**
 * Runs [block] (Unit-returning), catches exceptions, and returns a [MigrationError] on failure
 * or `null` on success. [CancellationException] is always re-thrown.
 */
private suspend fun safeRun(
    mapError: (String) -> MigrationError,
    block: suspend () -> Unit,
): MigrationError? =
    try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: GitHubApiException) {
        mapError(e.message ?: "unknown")
    } catch (e: Exception) {
        mapError(e.javaClass.simpleName)
    }

/**
 * Calls [block] returning an [ApiResult], and returns a [MigrationError] on failure or `null`
 * on success. [CancellationException] is always re-thrown.
 */
private suspend fun <T> safeApiResult(
    mapError: (String) -> MigrationError,
    block: suspend () -> ApiResult<T>,
): MigrationError? =
    try {
        val result = block()
        if (result is ApiResult.Failure) mapError(result.exception.message ?: "unknown") else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        mapError(e.javaClass.simpleName)
    }

private fun String.toTeamPrivacy(): TeamPrivacy? = TeamPrivacy.values().find { it.value == this }

private fun Membership.toPermission(): Permission = Permission.values().find { it.value == this.permission } ?: Permission.Pull
