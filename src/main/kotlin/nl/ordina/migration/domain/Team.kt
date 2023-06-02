package nl.ordina.migration.domain

import nl.ordina.github.team.GitHubTeam

data class Team(
    val sourceId: Int,
    val destination: GitHubTeam? = null,
    val name: String,
    val description: String? = null,
    val slug: String,
    val privacy: String? = null,
    val memberships: List<Membership>,
    val members: List<String>
) {
    constructor(team: GitHubTeam, members: List<String>, memberships: List<Membership>) : this(
        sourceId = team.id,
        name = team.name,
        description = team.description,
        slug = team.slug,
        privacy = team.privacy,
        members = members,
        memberships = memberships
    )
}

data class Membership(val repositoryName: String, val permission: String)
