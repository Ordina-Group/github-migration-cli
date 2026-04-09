package com.soprasteria.migration.service

import com.github.ajalt.mordant.terminal.Terminal
import com.soprasteria.github.ApiResult
import com.soprasteria.github.GitHubClient
import com.soprasteria.github.OrganizationService
import com.soprasteria.github.RepositoryService
import com.soprasteria.github.TeamService
import com.soprasteria.github.organization.GitHubOrganization
import com.soprasteria.github.repository.GitHubRepository
import com.soprasteria.github.team.GitHubTeam
import com.soprasteria.migration.domain.Member
import com.soprasteria.migration.domain.MigrationError
import com.soprasteria.migration.domain.Plan
import com.soprasteria.migration.domain.Repository
import com.soprasteria.migration.domain.Team
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

class ApplyServiceTest :
    DescribeSpec({

        fun org(login: String) =
            mockk<GitHubOrganization> {
                every { this@mockk.login } returns login
                every { name } returns login
            }

        fun ghRepo(name: String) = mockk<GitHubRepository> { every { this@mockk.name } returns name }

        fun repo(name: String) = Repository(name.hashCode(), name, name, "source/$name", emptyList())

        val sourceOrg = org("source-org")
        val destOrg = org("dest-org")

        fun emptyPlan() =
            Plan(
                sourceOrganization = sourceOrg,
                destinationOrganization = destOrg,
                members = emptyList(),
                repositories = emptyList(),
                teams = emptyList(),
                parentTeam = null,
            )

        fun preflightOrgService(org: GitHubOrganization): OrganizationService {
            val orgLogin = org.login
            return mockk<OrganizationService>(relaxed = true).also { svc ->
                coEvery { svc.get(orgLogin) } returns ApiResult.Found(org)
                coEvery { svc.getRepositories(org) } returns ApiResult.Found(emptyList())
            }
        }

        fun fullClient(
            orgSvc: OrganizationService,
            repoSvc: RepositoryService = mockk(relaxed = true),
            teamSvc: TeamService = mockk(relaxed = true),
        ): GitHubClient =
            mockk {
                every { organizations } returns orgSvc
                every { repositories } returns repoSvc
                every { teams } returns teamSvc
            }

        val terminal = mockk<Terminal>(relaxed = true)

        describe("pre-flight") {
            it("aborts and reports error when source org is not accessible") {
                val dangerMessages = mutableListOf<String>()
                val dangerTerminal =
                    mockk<Terminal>(relaxed = true).also { t ->
                        every { t.danger(capture(dangerMessages)) } returns Unit
                    }
                val failSrc =
                    mockk<OrganizationService>().also { svc ->
                        coEvery { svc.get("source-org") } returns ApiResult.NotFound
                    }
                val service =
                    ApplyService(dangerTerminal) { token ->
                        if (token == "src") fullClient(failSrc) else fullClient(preflightOrgService(destOrg))
                    }

                service.apply(emptyPlan(), "src", "tgt")

                dangerMessages.any { it.contains("source") } shouldBe true
            }

            it("aborts and reports error when target org is not accessible") {
                val dangerMessages = mutableListOf<String>()
                val dangerTerminal =
                    mockk<Terminal>(relaxed = true).also { t ->
                        every { t.danger(capture(dangerMessages)) } returns Unit
                    }
                val failTgt =
                    mockk<OrganizationService>().also { svc ->
                        coEvery { svc.get("dest-org") } returns ApiResult.NotFound
                    }
                val service =
                    ApplyService(dangerTerminal) { token ->
                        if (token == "src") fullClient(preflightOrgService(sourceOrg)) else fullClient(failTgt)
                    }

                service.apply(emptyPlan(), "src", "tgt")

                dangerMessages.any { it.contains("destination") } shouldBe true
            }
        }

        describe("idempotency") {
            it("skips repositories already present in the destination") {
                val srcOrgSvc = preflightOrgService(sourceOrg)
                val tgtOrgSvc =
                    mockk<OrganizationService>().also { svc ->
                        coEvery { svc.get("dest-org") } returns ApiResult.Found(destOrg)
                        coEvery { svc.getRepositories(destOrg) } returns ApiResult.Found(listOf(ghRepo("my-repo")))
                    }
                val mockRepoSvc = mockk<RepositoryService>(relaxed = true)
                val service =
                    ApplyService(terminal) { token ->
                        if (token == "src") fullClient(srcOrgSvc, repoSvc = mockRepoSvc) else fullClient(tgtOrgSvc)
                    }

                service.apply(emptyPlan().copy(repositories = listOf(repo("my-repo"))), "src", "tgt")

                coVerify(exactly = 0) { mockRepoSvc.transfer(any(), any(), any()) }
            }
        }

        describe("error collection") {
            it("continues migrating teams even when a repository transfer fails") {
                val destTeam =
                    mockk<GitHubTeam> {
                        every { id } returns 99
                        every { name } returns "backend"
                        every { slug } returns "backend"
                        every { organization } returns "dest-org"
                    }
                val srcOrgSvc = preflightOrgService(sourceOrg)
                val tgtOrgSvc =
                    mockk<OrganizationService>().also { svc ->
                        coEvery { svc.get("dest-org") } returns ApiResult.Found(destOrg)
                        coEvery { svc.getRepositories(destOrg) } returns ApiResult.Found(emptyList())
                        coEvery { svc.createTeam(destOrg, any(), any(), any(), any()) } returns ApiResult.Found(destTeam)
                    }
                val failRepoSvc =
                    mockk<RepositoryService>().also { svc ->
                        coEvery { svc.transfer(any(), any(), any()) } throws RuntimeException("network error")
                    }
                val dangerMessages = mutableListOf<String>()
                val testTerminal =
                    mockk<Terminal>(relaxed = true).also { t ->
                        every { t.danger(capture(dangerMessages)) } returns Unit
                    }
                val service =
                    ApplyService(testTerminal) { token ->
                        if (token == "src") {
                            fullClient(srcOrgSvc, repoSvc = failRepoSvc)
                        } else {
                            fullClient(tgtOrgSvc)
                        }
                    }
                val team = Team(sourceId = 1, name = "backend", slug = "backend", members = emptyList(), memberships = emptyList())
                val plan = emptyPlan().copy(repositories = listOf(repo("broken-repo")), teams = listOf(team))

                service.apply(plan, "src", "tgt")

                dangerMessages.any { it.contains("broken-repo") } shouldBe true
                coVerify { tgtOrgSvc.createTeam(destOrg, "backend", any(), any(), any()) }
            }
        }
    })
