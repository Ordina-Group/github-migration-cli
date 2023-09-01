@file:Suppress("MaxLineLength")

package nl.ordina.migration.domain

import nl.ordina.github.organization.GitHubOrganization
import nl.ordina.github.organization.GitHubOrganizationMember
import nl.ordina.github.repository.GitHubRepository
import nl.ordina.github.team.GitHubTeam
import nl.ordina.migration.service.Strategy
import nl.ordina.migration.utility.bold
import nl.ordina.migration.utility.green
import nl.ordina.migration.utility.yellow

class Plan private constructor(
    private val sourceOrganization: GitHubOrganization,
    val destinationOrganization: GitHubOrganization,
    val members: List<Member>,
    val repositories: List<Repository>,
    val teams: List<Team>
) {
    companion object {
        // TODO Move this code outside of the data class
        @Suppress("LongParameterList")
        fun create(
            sourceOrganization: GitHubOrganization,
            destinationOrganization: GitHubOrganization,
            sourceMembers: List<Member>,
            sourceRepositories: List<Repository>,
            sourceTeams: List<Team>,
            destinationMembers: List<GitHubOrganizationMember>,
            destinationRepositories: List<GitHubRepository>,
            destinationTeams: List<GitHubTeam>,
            strategy: Strategy
        ): Plan {
            val teams = sourceTeams.map { team ->
                val destinationTeam = destinationTeams.find { it.name == team.name }

                // If a team already exists we either add the members to the existing team or create a team with the
                // source organization as prefix
                if (destinationTeam != null) {
                    when (strategy) {
                        Strategy.Merge -> team.copy(destination = destinationTeam)
                        Strategy.Prefix -> team.copy(name = "$sourceOrganization-${team.name}")
                    }
                } else {
                    team
                }
            }

            val existingMembersLogins = destinationMembers.map { it.login }

            // We only want to invite members that are not already part of the destination organization
            val members = sourceMembers.filter { !existingMembersLogins.contains(it.login) }

            val destinationRepositoryNames = destinationRepositories.map { it.name }

            val repositoryExists: (String) -> Boolean = destinationRepositoryNames::contains

            // If a repository with the same name already exists we add the source organisation as a prefix
            val repositories = sourceRepositories.map {
                if (repositoryExists(it.name)) {
                    it.copy(name = "$sourceOrganization-${it.name}")
                } else {
                    it
                }
            }

            return Plan(sourceOrganization, destinationOrganization, members, repositories, teams)
        }
    }

    private fun formatRepositoryCollaborator(collaborator: RepositoryCollaborator): String =
        "${"+".green}  Collaborator ${collaborator.username.bold} will be added with role ${collaborator.roleName.yellow}"

    private fun formatMember(member: Member): String =
        "# Member ${member.login.bold} will be ${"invited".yellow} to ${destinationOrganization.name.bold}"

    private fun formatRepository(repository: Repository): String {
        val builder = StringBuilder()

        builder.appendLine("# ${repository.name} will be ${"transferred".yellow}")

        repository
            .collaborators
            .map(::formatRepositoryCollaborator)
            .forEach { builder.appendLine("  $it") }

        return builder.toString()
    }

    private fun formatTeam(team: Team): String {
        val builder = StringBuilder()

        if (team.destination == null) {
            builder.appendLine("# ${team.name.bold} will be ${"created".green}")
        } else {
            builder.appendLine("# ${team.name.bold} will be ${"updated".yellow}")
        }

        team
            .members
            .map { "  ${"+".green} Member ${it.bold} will be added to team ${team.name.bold}" }
            .forEach(builder::appendLine)

        if (team.members.isNotEmpty()) {
            builder.appendLine()
        }

        team.memberships
            .map { "  ${"+".green} Team ${team.name.bold} will be added to repository ${it.repositoryName.bold} with permissions ${it.permission.bold.yellow}" }
            .forEach(builder::appendLine)

        return builder.toString()
    }

    override fun toString(): String {
        val builder = StringBuilder()

        builder.appendLine("Plan for merging resources from ${sourceOrganization.name} to ${destinationOrganization.name}")

        if (members.isNotEmpty()) {
            builder.appendLine(members.joinToString(separator = "\n", transform = ::formatMember))
        }

        if (repositories.isNotEmpty()) {
            builder.appendLine(repositories.joinToString(separator = "\n", transform = ::formatRepository))
        }

        if (teams.isNotEmpty()) {
            builder.appendLine(teams.joinToString(separator = "\n", transform = ::formatTeam))
        }

        return builder.toString()
    }
}
