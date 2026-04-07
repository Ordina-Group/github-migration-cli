package com.soprasteria.migration.service

import arrow.core.Either
import com.github.ajalt.mordant.terminal.Terminal
import com.soprasteria.github.GitHubClient
import com.soprasteria.github.organization.GitHubOrganization
import com.soprasteria.github.repository.Permission
import com.soprasteria.github.team.GitHubTeam
import com.soprasteria.github.team.TeamPrivacy
import com.soprasteria.migration.domain.Membership
import com.soprasteria.migration.domain.MigrationError
import com.soprasteria.migration.domain.Plan
import com.soprasteria.migration.domain.Team
import kotlinx.coroutines.runBlocking

class ApplyService(
    private val terminal: Terminal,
) {
    fun apply(
        plan: Plan,
        token: String,
    ) {
        val client = GitHubClient.create(token)
        val errors =
            transferRepositories(plan, client) +
                inviteMembers(plan, client) +
                migrateTeams(plan, client)

        errors.forEach { terminal.danger(it.message) }

        if (errors.isEmpty()) {
            terminal.success("Migration completed successfully")
        } else {
            terminal.warning("Migration completed with ${errors.size} error(s)")
        }
    }

    private fun transferRepositories(
        plan: Plan,
        client: GitHubClient,
    ): List<MigrationError> =
        plan.repositories.mapNotNull { repository ->
            Either
                .catch {
                    runBlocking {
                        client.repositories.transfer(
                            plan.sourceOrganization.login,
                            repository.originalName,
                            plan.destinationOrganization.login,
                        )
                    }
                }.mapLeft { MigrationError.RepositoryTransferFailed(repository.name, it.message ?: "unknown") }
                .leftOrNull()
        }

    private fun inviteMembers(
        plan: Plan,
        client: GitHubClient,
    ): List<MigrationError> =
        plan.members.mapNotNull { member ->
            Either
                .catch {
                    runBlocking { client.organizations.invite(plan.destinationOrganization, member.id) }
                }.mapLeft { MigrationError.MemberInviteFailed(member.login, it.message ?: "unknown") }
                .leftOrNull()
        }

    private fun migrateTeams(
        plan: Plan,
        client: GitHubClient,
    ): List<MigrationError> =
        plan.teams.flatMap { team ->
            migrateTeam(client, plan.destinationOrganization, team, plan.parentTeam)
        }

    private fun migrateTeam(
        client: GitHubClient,
        destinationOrganization: GitHubOrganization,
        team: Team,
        parentTeam: GitHubTeam?,
    ): List<MigrationError> =
        Either
            .catch {
                runBlocking {
                    team.destination ?: client.organizations
                        .createTeam(
                            destinationOrganization,
                            team.name,
                            team.description ?: "",
                            team.privacy?.toTeamPrivacy() ?: TeamPrivacy.Secret,
                            parentTeam?.id,
                        ).getOrThrow()
                }
            }.mapLeft { MigrationError.TeamCreationFailed(team.name, it.message ?: "unknown") }
            .fold(
                ifLeft = { listOf(it) },
                ifRight = { destinationTeam ->
                    addTeamMembers(client, team, destinationTeam) +
                        addTeamRepositories(client, team, destinationTeam)
                },
            )

    private fun addTeamMembers(
        client: GitHubClient,
        team: Team,
        destinationTeam: GitHubTeam,
    ): List<MigrationError> =
        team.members.mapNotNull { member ->
            Either
                .catch { runBlocking { client.teams.addMember(destinationTeam, member) } }
                .mapLeft { MigrationError.TeamMemberAddFailed(member, team.name, it.message ?: "unknown") }
                .leftOrNull()
        }

    private fun addTeamRepositories(
        client: GitHubClient,
        team: Team,
        destinationTeam: GitHubTeam,
    ): List<MigrationError> =
        team.memberships.mapNotNull { membership ->
            Either
                .catch {
                    runBlocking {
                        client.teams.addRepository(destinationTeam, membership.repositoryName, membership.toPermission())
                    }
                }.mapLeft {
                    MigrationError.TeamRepositoryAddFailed(team.name, membership.repositoryName, it.message ?: "unknown")
                }.leftOrNull()
        }
}

private fun String.toTeamPrivacy(): TeamPrivacy? = TeamPrivacy.values().find { it.value == this }

private fun Membership.toPermission(): Permission = Permission.values().find { it.value == this.permission } ?: Permission.Pull
