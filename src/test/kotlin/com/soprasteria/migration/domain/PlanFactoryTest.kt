package com.soprasteria.migration.domain

import com.soprasteria.github.organization.GitHubOrganization
import com.soprasteria.github.organization.GitHubOrganizationMember
import com.soprasteria.github.repository.GitHubRepository
import com.soprasteria.github.team.GitHubTeam
import com.soprasteria.migration.service.Strategy
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class PlanFactoryTest :
    DescribeSpec({

        val sourceOrg =
            mockk<GitHubOrganization> {
                every { login } returns "source-org"
                every { name } returns "Source Org"
            }
        val destOrg =
            mockk<GitHubOrganization> {
                every { login } returns "dest-org"
                every { name } returns "Dest Org"
            }

        fun member(
            id: Int,
            login: String,
        ) = Member(id, login)

        fun repository(
            id: Int,
            name: String,
        ): Repository {
            val ghRepo =
                mockk<GitHubRepository> {
                    every { this@mockk.id } returns id
                    every { this@mockk.name } returns name
                    every { fullName } returns "source-org/$name"
                }
            return Repository(ghRepo, emptyList())
        }

        fun team(
            id: Int,
            name: String,
            slug: String,
            members: List<String> = emptyList(),
        ): Team =
            Team(
                sourceId = id,
                name = name,
                description = null,
                slug = slug,
                privacy = null,
                members = members,
                memberships = emptyList(),
            )

        fun destTeam(name: String): GitHubTeam =
            mockk {
                every { this@mockk.name } returns name
            }

        fun destMember(login: String): GitHubOrganizationMember =
            mockk {
                every { this@mockk.login } returns login
            }

        fun destRepo(name: String): GitHubRepository =
            mockk {
                every { this@mockk.name } returns name
            }

        @Suppress("LongParameterList")
        fun context(
            sourceMembers: List<Member> = emptyList(),
            sourceRepositories: List<Repository> = emptyList(),
            sourceTeams: List<Team> = emptyList(),
            destinationMembers: List<GitHubOrganizationMember> = emptyList(),
            destinationRepositories: List<GitHubRepository> = emptyList(),
            destinationTeams: List<GitHubTeam> = emptyList(),
            parentTeam: GitHubTeam? = null,
            strategy: Strategy = Strategy.Merge,
        ) = MigrationContext(
            sourceOrganization = sourceOrg,
            destinationOrganization = destOrg,
            sourceMembers = sourceMembers,
            sourceRepositories = sourceRepositories,
            sourceTeams = sourceTeams,
            destinationMembers = destinationMembers,
            destinationRepositories = destinationRepositories,
            destinationTeams = destinationTeams,
            parentTeam = parentTeam,
            strategy = strategy,
        )

        describe("createPlan") {

            describe("member filtering") {
                it("excludes members already in the destination organization") {
                    val plan =
                        createPlan(
                            context(
                                sourceMembers = listOf(member(1, "alice"), member(2, "bob"), member(3, "carol")),
                                destinationMembers = listOf(destMember("bob")),
                            ),
                        )

                    plan.members.map { it.login } shouldContainExactly listOf("alice", "carol")
                }

                it("includes all members when destination organization is empty") {
                    val plan =
                        createPlan(
                            context(
                                sourceMembers = listOf(member(1, "alice"), member(2, "bob")),
                            ),
                        )

                    plan.members shouldHaveSize 2
                }

                it("returns no members when all are already present") {
                    val plan =
                        createPlan(
                            context(
                                sourceMembers = listOf(member(1, "alice")),
                                destinationMembers = listOf(destMember("alice")),
                            ),
                        )

                    plan.members.shouldBeEmpty()
                }
            }

            describe("repository name prefixing") {
                it("prefixes repository name with source org login when a conflict exists") {
                    val plan =
                        createPlan(
                            context(
                                sourceRepositories = listOf(repository(1, "my-repo")),
                                destinationRepositories = listOf(destRepo("my-repo")),
                            ),
                        )

                    plan.repositories.single().name shouldBe "source-org-my-repo"
                }

                it("keeps repository name unchanged when there is no conflict") {
                    val plan =
                        createPlan(
                            context(
                                sourceRepositories = listOf(repository(1, "my-repo")),
                            ),
                        )

                    plan.repositories.single().name shouldBe "my-repo"
                }

                it("only prefixes the conflicting repositories, not all") {
                    val plan =
                        createPlan(
                            context(
                                sourceRepositories = listOf(repository(1, "conflict-repo"), repository(2, "new-repo")),
                                destinationRepositories = listOf(destRepo("conflict-repo")),
                            ),
                        )

                    plan.repositories.map { it.name } shouldContainExactly
                        listOf("source-org-conflict-repo", "new-repo")
                }
            }

            describe("team strategy — Merge") {
                it("links source team to existing destination team with same name") {
                    val existing = destTeam("backend")
                    val plan =
                        createPlan(
                            context(
                                sourceTeams = listOf(team(1, "backend", "backend")),
                                destinationTeams = listOf(existing),
                            ),
                        )

                    plan.teams.single().destination shouldBe existing
                }

                it("performs case-insensitive team name matching") {
                    val existing = destTeam("Backend")
                    val plan =
                        createPlan(
                            context(
                                sourceTeams = listOf(team(1, "backend", "backend")),
                                destinationTeams = listOf(existing),
                            ),
                        )

                    plan.teams.single().destination shouldBe existing
                }

                it("leaves destination null for teams that do not exist in destination") {
                    val plan =
                        createPlan(
                            context(
                                sourceTeams = listOf(team(1, "new-team", "new-team")),
                            ),
                        )

                    plan.teams.single().destination shouldBe null
                }
            }

            describe("team strategy — Prefix") {
                it("prefixes team name with source org login when a conflict exists") {
                    val plan =
                        createPlan(
                            context(
                                sourceTeams = listOf(team(1, "backend", "backend")),
                                destinationTeams = listOf(destTeam("backend")),
                                strategy = Strategy.Prefix,
                            ),
                        )

                    plan.teams.single().name shouldBe "source-org-backend"
                }

                it("does not prefix team name when there is no conflict") {
                    val plan =
                        createPlan(
                            context(
                                sourceTeams = listOf(team(1, "new-team", "new-team")),
                                strategy = Strategy.Prefix,
                            ),
                        )

                    plan.teams.single().name shouldBe "new-team"
                }

                it("sets destination to null when using prefix strategy (new team is created)") {
                    val plan =
                        createPlan(
                            context(
                                sourceTeams = listOf(team(1, "backend", "backend")),
                                destinationTeams = listOf(destTeam("backend")),
                                strategy = Strategy.Prefix,
                            ),
                        )

                    plan.teams.single().destination shouldBe null
                }
            }

            describe("parentTeam") {
                it("passes through a non-null parentTeam") {
                    val parent = mockk<GitHubTeam>()
                    val plan =
                        createPlan(
                            context(parentTeam = parent),
                        )

                    plan.parentTeam shouldBe parent
                }

                it("accepts a null parentTeam") {
                    val plan =
                        createPlan(
                            context(parentTeam = null),
                        )

                    plan.parentTeam shouldBe null
                }
            }
        }
    })
