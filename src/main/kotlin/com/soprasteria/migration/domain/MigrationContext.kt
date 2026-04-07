package com.soprasteria.migration.domain

import com.soprasteria.github.organization.GitHubOrganization
import com.soprasteria.github.organization.GitHubOrganizationMember
import com.soprasteria.github.repository.GitHubRepository
import com.soprasteria.github.team.GitHubTeam
import com.soprasteria.migration.service.Strategy

data class MigrationContext(
    val sourceOrganization: GitHubOrganization,
    val destinationOrganization: GitHubOrganization,
    val sourceMembers: List<Member>,
    val sourceRepositories: List<Repository>,
    val sourceTeams: List<Team>,
    val destinationMembers: List<GitHubOrganizationMember>,
    val destinationRepositories: List<GitHubRepository>,
    val destinationTeams: List<GitHubTeam>,
    val parentTeam: GitHubTeam?,
    val strategy: Strategy,
)
