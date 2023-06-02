package nl.ordina.migration.domain

import nl.ordina.github.repository.GitHubRepository

data class Repository(
    val id: Int,
    val name: String,
    val fullName: String,
    val collaborators: List<RepositoryCollaborator>,
    val destinationName: String?,
    private val repository: GitHubRepository
) {
    constructor(repository: GitHubRepository, collaborators: List<RepositoryCollaborator>) :
        this(repository.id, repository.name, repository.full_name, collaborators, null, repository)

    fun transfer(newOwner: String) = repository.transfer(newOwner)
}

data class RepositoryCollaborator(val id: Int, val username: String, val roleName: String)
