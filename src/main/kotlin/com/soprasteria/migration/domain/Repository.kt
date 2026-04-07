package com.soprasteria.migration.domain

import com.soprasteria.github.repository.GitHubRepository

data class Repository(
    val id: Int,
    val name: String,
    val originalName: String,
    val fullName: String,
    val collaborators: List<RepositoryCollaborator>,
) {
    constructor(repository: GitHubRepository, collaborators: List<RepositoryCollaborator>) :
        this(repository.id, repository.name, repository.name, repository.fullName, collaborators)
}

data class RepositoryCollaborator(
    val id: Int,
    val username: String,
    val roleName: String,
)
