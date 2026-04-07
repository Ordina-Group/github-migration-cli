package com.soprasteria.migration.domain

import com.soprasteria.migration.service.Strategy

fun createPlan(context: MigrationContext): Plan {
    val sourceOrgLogin = context.sourceOrganization.login

    val teams =
        context.sourceTeams.map { team ->
            val destinationTeam = context.destinationTeams.find { it.name.equals(team.name, ignoreCase = true) }

            // If a team already exists we either merge with it or create a new one with a source-org prefix
            if (destinationTeam != null) {
                when (context.strategy) {
                    Strategy.Merge -> team.copy(destination = destinationTeam)
                    Strategy.Prefix -> team.copy(name = "$sourceOrgLogin-${team.name}")
                }
            } else {
                team
            }
        }

    val existingMembersLogins = context.destinationMembers.map { it.login }.toSet()

    // Only invite members not already part of the destination organization
    val members = context.sourceMembers.filterNot { it.login in existingMembersLogins }

    val destinationRepositoryNames = context.destinationRepositories.map { it.name }.toSet()

    // If a repository with the same name already exists, prefix with the source org login
    val repositories =
        context.sourceRepositories.map { repo ->
            if (repo.name in destinationRepositoryNames) {
                repo.copy(name = "$sourceOrgLogin-${repo.name}")
            } else {
                repo
            }
        }

    return Plan(
        context.sourceOrganization,
        context.destinationOrganization,
        members,
        repositories,
        teams,
        context.parentTeam,
    )
}
