package com.soprasteria.migration.domain

import com.soprasteria.migration.utility.bold
import com.soprasteria.migration.utility.green
import com.soprasteria.migration.utility.yellow

class PlanRenderer {
    fun render(plan: Plan): String =
        buildString {
            appendLine(
                "Plan for merging resources from " +
                    "${plan.sourceOrganization.name} to ${plan.destinationOrganization.name}",
            )

            if (plan.members.isNotEmpty()) {
                appendLine(plan.members.joinToString(separator = "\n") { renderMember(plan, it) })
            }

            if (plan.repositories.isNotEmpty()) {
                appendLine(plan.repositories.joinToString(separator = "\n", transform = ::renderRepository))
            }

            if (plan.teams.isNotEmpty()) {
                appendLine(plan.teams.joinToString(separator = "\n", transform = ::renderTeam))
            }
        }

    private fun renderMember(
        plan: Plan,
        member: Member,
    ): String = "# Member ${member.login.bold} will be ${"invited".yellow} to ${plan.destinationOrganization.name?.bold}"

    private fun renderRepositoryCollaborator(collaborator: RepositoryCollaborator): String {
        val role = collaborator.roleName.yellow
        return "${"+".green}  Collaborator ${collaborator.username.bold} will be added with role $role"
    }

    private fun renderRepository(repository: Repository): String =
        buildString {
            if (repository.originalName != repository.name) {
                appendLine(
                    "# ${repository.originalName} already exists in destination — " +
                        "will be transferred as ${"${repository.name}".yellow}",
                )
            } else {
                appendLine("# ${repository.name} will be ${"transferred".yellow}")
            }
            repository.collaborators
                .map(::renderRepositoryCollaborator)
                .forEach { appendLine("  $it") }
        }

    private fun renderTeam(team: Team): String =
        buildString {
            if (team.destination == null) {
                appendLine("# ${team.name.bold} will be ${"created".green}")
            } else {
                appendLine("# ${team.name.bold} will be ${"updated".yellow}")
            }

            team.members
                .map { "  ${"+".green} Member ${it.bold} will be added to team ${team.name.bold}" }
                .forEach(::appendLine)

            if (team.members.isNotEmpty()) appendLine()

            team.memberships.forEach {
                val line =
                    "  ${"+".green} Team ${team.name.bold} will be added to " +
                        "repository ${it.repositoryName.bold} with permissions ${it.permission.bold.yellow}"
                appendLine(line)
            }
        }
}
