package com.soprasteria.migration.domain

sealed class MigrationError(
    open val message: String,
) {
    data class OrganizationNotFound(
        val name: String,
    ) : MigrationError("Organization '$name' could not be found")

    data class ParentTeamNotFound(
        val slug: String,
    ) : MigrationError("Parent team '$slug' not found in destination organisation")

    data class RepositoryTransferFailed(
        val repositoryName: String,
        val cause: String,
    ) : MigrationError("Failed to transfer repository '$repositoryName': $cause")

    data class MemberInviteFailed(
        val login: String,
        val cause: String,
    ) : MigrationError("Failed to invite member '$login': $cause")

    data class TeamCreationFailed(
        val teamName: String,
        val cause: String,
    ) : MigrationError("Failed to create team '$teamName': $cause")

    data class TeamMemberAddFailed(
        val member: String,
        val teamName: String,
        val cause: String,
    ) : MigrationError("Failed to add member '$member' to team '$teamName': $cause")

    data class TeamRepositoryAddFailed(
        val teamName: String,
        val repositoryName: String,
        val cause: String,
    ) : MigrationError("Failed to add team '$teamName' to repository '$repositoryName': $cause")
}
