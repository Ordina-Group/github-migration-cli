package com.soprasteria.migration.service

import com.github.ajalt.mordant.terminal.Terminal
import com.soprasteria.github.ApiResult
import com.soprasteria.github.GitHubClient
import com.soprasteria.github.OrganizationService
import com.soprasteria.github.RepositoryService
import com.soprasteria.github.TeamService
import com.soprasteria.github.organization.GitHubOrganization
import com.soprasteria.github.organization.GitHubOrganizationMember
import com.soprasteria.github.repository.GitHubRepository
import com.soprasteria.github.team.GitHubTeam
import com.soprasteria.migration.domain.MigrationError
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

class PlanServiceTest :
    DescribeSpec({

        fun org(login: String) =
            mockk<GitHubOrganization> {
                every { this@mockk.login } returns login
                every { name } returns login
            }

        fun member(
            id: Int,
            login: String,
        ) = mockk<GitHubOrganizationMember> {
            every { this@mockk.id } returns id
            every { this@mockk.login } returns login
        }

        fun repo(name: String) =
            mockk<GitHubRepository> {
                every { this@mockk.id } returns name.hashCode()
                every { this@mockk.name } returns name
                every { fullName } returns "source/$name"
                every { owner } returns "source"
            }

        fun orgService(
            org: GitHubOrganization,
            repos: List<GitHubRepository> = emptyList(),
            teams: List<GitHubTeam> = emptyList(),
            members: List<GitHubOrganizationMember> = emptyList(),
            parentTeamSlug: String? = null,
            parentTeam: GitHubTeam? = null,
        ): OrganizationService {
            val orgLogin = org.login
            return mockk<OrganizationService>().also { svc ->
                coEvery { svc.get(orgLogin) } returns ApiResult.Found(org)
                coEvery { svc.getRepositories(org) } returns ApiResult.Found(repos)
                coEvery { svc.getTeams(org) } returns ApiResult.Found(teams)
                coEvery { svc.getMembers(org) } returns ApiResult.Found(members)
                if (parentTeamSlug != null) {
                    coEvery { svc.getTeam(org, parentTeamSlug) } returns
                        if (parentTeam != null) ApiResult.Found(parentTeam) else ApiResult.NotFound
                }
            }
        }

        fun client(
            orgSvc: OrganizationService,
            repoSvc: RepositoryService =
                mockk {
                    coEvery { getDirectCollaborators(any<GitHubRepository>()) } returns ApiResult.Found(emptyList())
                },
            teamSvc: TeamService =
                mockk {
                    coEvery { getMembers(any<GitHubTeam>()) } returns ApiResult.Found(emptyList())
                    coEvery { getRepositories(any<GitHubTeam>()) } returns ApiResult.Found(emptyList())
                },
        ): GitHubClient =
            mockk {
                every { organizations } returns orgSvc
                every { repositories } returns repoSvc
                every { teams } returns teamSvc
            }

        val terminal = mockk<Terminal>(relaxed = true)

        val baseOptions =
            PlanOptions(
                sourceToken = "src-tok",
                targetToken = "tgt-tok",
                source = "source-org",
                destination = "dest-org",
                strategy = Strategy.Merge,
                output = null,
                blacklist = emptyList(),
                parentTeamName = null,
            )

        describe("organisation validation") {
            it("returns OrganizationNotFound when source org is not accessible") {
                val failingSvc =
                    mockk<OrganizationService>().also { svc ->
                        coEvery { svc.get("source-org") } returns ApiResult.NotFound
                    }
                val sourceOrg = org("source-org")
                val destOrg = org("dest-org")
                val service =
                    PlanService(terminal) { token ->
                        if (token == "src-tok") client(failingSvc) else client(orgService(destOrg))
                    }

                val result = service.generatePlan(baseOptions)

                result.isLeft() shouldBe true
                result.leftOrNull().shouldBeInstanceOf<MigrationError.OrganizationNotFound>()
            }

            it("returns OrganizationNotFound when destination org is not accessible") {
                val sourceOrg = org("source-org")
                val failingSvc =
                    mockk<OrganizationService>().also { svc ->
                        coEvery { svc.get("dest-org") } returns ApiResult.NotFound
                    }
                val service =
                    PlanService(terminal) { token ->
                        if (token == "src-tok") client(orgService(sourceOrg)) else client(failingSvc)
                    }

                val result = service.generatePlan(baseOptions)

                result.isLeft() shouldBe true
                result.leftOrNull().shouldBeInstanceOf<MigrationError.OrganizationNotFound>()
            }
        }

        describe("parent team validation") {
            it("returns ParentTeamNotFound when the slug does not exist in destination") {
                val sourceOrg = org("source-org")
                val destOrg = org("dest-org")
                val service =
                    PlanService(terminal) { token ->
                        if (token == "src-tok") {
                            client(orgService(sourceOrg))
                        } else {
                            client(orgService(destOrg, parentTeamSlug = "missing-parent", parentTeam = null))
                        }
                    }

                val result = service.generatePlan(baseOptions.copy(parentTeamName = "missing-parent"))

                result.isLeft() shouldBe true
                result.leftOrNull().shouldBeInstanceOf<MigrationError.ParentTeamNotFound>()
            }
        }

        describe("blacklist validation") {
            it("warns when a blacklist entry matches no repository") {
                val warnings = mutableListOf<String>()
                val warnTerminal =
                    mockk<Terminal>(relaxed = true).also { t ->
                        every { t.warning(capture(warnings)) } returns Unit
                    }
                val sourceOrg = org("source-org")
                val destOrg = org("dest-org")
                val service =
                    PlanService(warnTerminal) { token ->
                        if (token == "src-tok") client(orgService(sourceOrg)) else client(orgService(destOrg))
                    }

                service.generatePlan(baseOptions.copy(blacklist = listOf("nonexistent-repo")))

                warnings.any { it.contains("nonexistent-repo") } shouldBe true
            }
        }

        describe("plan generation") {
            it("excludes blacklisted repositories") {
                val sourceOrg = org("source-org")
                val destOrg = org("dest-org")
                val service =
                    PlanService(terminal) { token ->
                        if (token == "src-tok") {
                            client(orgService(sourceOrg, repos = listOf(repo("keep-me"), repo("exclude-me"))))
                        } else {
                            client(orgService(destOrg))
                        }
                    }

                val plan = service.generatePlan(baseOptions.copy(blacklist = listOf("exclude-me"))).getOrNull()!!

                plan.repositories shouldHaveSize 1
                plan.repositories.first().name shouldBe "keep-me"
            }

            it("filters out members already present in destination") {
                val sourceOrg = org("source-org")
                val destOrg = org("dest-org")
                val alice = member(1, "alice")
                val bob = member(2, "bob")
                val existingBob = member(2, "bob")
                val service =
                    PlanService(terminal) { token ->
                        if (token == "src-tok") {
                            client(orgService(sourceOrg, members = listOf(alice, bob)))
                        } else {
                            client(orgService(destOrg, members = listOf(existingBob)))
                        }
                    }

                val plan = service.generatePlan(baseOptions).getOrNull()!!

                plan.members shouldHaveSize 1
                plan.members.first().login shouldBe "alice"
            }

            it("generates an empty member list when all members are already in destination") {
                val sourceOrg = org("source-org")
                val destOrg = org("dest-org")
                val alice = member(1, "alice")
                val service =
                    PlanService(terminal) { token ->
                        if (token == "src-tok") {
                            client(orgService(sourceOrg, members = listOf(alice)))
                        } else {
                            client(orgService(destOrg, members = listOf(alice)))
                        }
                    }

                val plan = service.generatePlan(baseOptions).getOrNull()!!

                plan.members.shouldBeEmpty()
            }
        }
    })
