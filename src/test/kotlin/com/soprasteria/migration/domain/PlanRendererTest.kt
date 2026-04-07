package com.soprasteria.migration.domain

import com.soprasteria.github.organization.GitHubOrganization
import com.soprasteria.github.team.GitHubTeam
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk

class PlanRendererTest :
    DescribeSpec({

        val sourceOrg =
            mockk<GitHubOrganization> {
                every { name } returns "source-org"
            }
        val destOrg =
            mockk<GitHubOrganization> {
                every { name } returns "dest-org"
            }

        fun emptyPlan() = Plan(sourceOrg, destOrg, emptyList(), emptyList(), emptyList(), null)

        val renderer = PlanRenderer()

        describe("render header") {
            it("includes source and destination org names") {
                val output = renderer.render(emptyPlan())
                output shouldContain "source-org"
                output shouldContain "dest-org"
            }
        }

        describe("render members") {
            it("includes member login in output") {
                val plan = emptyPlan().copy(members = listOf(Member(1, "alice")))
                val output = renderer.render(plan)
                output shouldContain "alice"
                output shouldContain "invited"
            }

            it("omits members section when there are none") {
                val output = renderer.render(emptyPlan())
                output shouldNotContain "invited"
            }
        }

        describe("render repositories") {
            it("includes repository name and transfer keyword") {
                val ghRepo =
                    mockk<com.soprasteria.github.repository.GitHubRepository> {
                        every { id } returns 1
                        every { name } returns "my-repo"
                        every { fullName } returns "source-org/my-repo"
                    }
                val repo = Repository(ghRepo, emptyList())
                val plan = emptyPlan().copy(repositories = listOf(repo))
                val output = renderer.render(plan)
                output shouldContain "my-repo"
                output shouldContain "transferred"
            }

            it("includes collaborator details when present") {
                val ghRepo =
                    mockk<com.soprasteria.github.repository.GitHubRepository> {
                        every { id } returns 1
                        every { name } returns "my-repo"
                        every { fullName } returns "source-org/my-repo"
                    }
                val collaborator = RepositoryCollaborator(10, "charlie", "admin")
                val repo = Repository(ghRepo, listOf(collaborator))
                val plan = emptyPlan().copy(repositories = listOf(repo))
                val output = renderer.render(plan)
                output shouldContain "charlie"
                output shouldContain "admin"
            }

            it("omits repositories section when there are none") {
                val output = renderer.render(emptyPlan())
                output shouldNotContain "transferred"
            }
        }

        describe("render teams") {
            it("shows 'created' for a team with no destination") {
                val team = Team(1, null, "new-team", null, "new-team", null, emptyList(), emptyList())
                val plan = emptyPlan().copy(teams = listOf(team))
                val output = renderer.render(plan)
                output shouldContain "new-team"
                output shouldContain "created"
            }

            it("shows 'updated' for a team that already exists in destination") {
                val destTeam = mockk<GitHubTeam> { every { name } returns "existing-team" }
                val team = Team(1, destTeam, "existing-team", null, "existing-team", null, emptyList(), emptyList())
                val plan = emptyPlan().copy(teams = listOf(team))
                val output = renderer.render(plan)
                output shouldContain "existing-team"
                output shouldContain "updated"
            }

            it("lists team members") {
                val team = Team(1, null, "backend", null, "backend", null, emptyList(), listOf("alice", "bob"))
                val plan = emptyPlan().copy(teams = listOf(team))
                val output = renderer.render(plan)
                output shouldContain "alice"
                output shouldContain "bob"
            }

            it("lists team repository memberships with permissions") {
                val membership = Membership("my-repo", "push")
                val team = Team(1, null, "backend", null, "backend", null, listOf(membership), emptyList())
                val plan = emptyPlan().copy(teams = listOf(team))
                val output = renderer.render(plan)
                output shouldContain "my-repo"
                output shouldContain "push"
            }

            it("omits teams section when there are none") {
                val output = renderer.render(emptyPlan())
                output shouldNotContain "created"
                output shouldNotContain "updated"
            }
        }
    })
