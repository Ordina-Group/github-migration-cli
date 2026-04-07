package com.soprasteria.migration.domain

import com.soprasteria.github.organization.GitHubOrganization
import com.soprasteria.github.team.GitHubTeam

data class Plan(
    val sourceOrganization: GitHubOrganization,
    val destinationOrganization: GitHubOrganization,
    val members: List<Member>,
    val repositories: List<Repository>,
    val teams: List<Team>,
    val parentTeam: GitHubTeam?,
)
