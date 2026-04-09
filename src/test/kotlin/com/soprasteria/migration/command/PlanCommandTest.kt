package com.soprasteria.migration.command

import arrow.core.left
import arrow.core.right
import com.github.ajalt.clikt.testing.test
import com.github.ajalt.mordant.terminal.Terminal
import com.soprasteria.migration.domain.MigrationError
import com.soprasteria.migration.domain.Plan
import com.soprasteria.migration.service.PlanOptions
import com.soprasteria.migration.service.PlanService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

class PlanCommandTest :
    DescribeSpec({

        val terminal = mockk<Terminal>(relaxed = true)

        fun emptyPlan() =
            mockk<Plan> {
                every { repositories } returns emptyList()
                every { members } returns emptyList()
                every { teams } returns emptyList()
                every { sourceOrganization } returns
                    mockk {
                        every { name } returns "Source"
                        every { login } returns "source"
                    }
                every { destinationOrganization } returns
                    mockk {
                        every { name } returns "Dest"
                        every { login } returns "dest"
                    }
                every { parentTeam } returns null
            }

        val requiredArgs = "--token tok --source src-org --destination dst-org"

        describe("argument validation") {
            it("fails when --source is missing") {
                val planService = mockk<PlanService>()
                val result = PlanCommand(terminal, planService).test("--token tok --destination dst-org")
                result.statusCode shouldBe 1
                result.output shouldContain "source"
            }

            it("fails when --destination is missing") {
                val planService = mockk<PlanService>()
                val result = PlanCommand(terminal, planService).test("--token tok --source src-org")
                result.statusCode shouldBe 1
                result.output shouldContain "destination"
            }
        }

        describe("empty plan") {
            it("prints a warning when nothing needs to be migrated") {
                val planService =
                    mockk<PlanService> {
                        coEvery { generatePlan(any<PlanOptions>()) } returns emptyPlan().right()
                    }
                val messages = mutableListOf<String>()
                val warnTerminal =
                    mockk<Terminal>(relaxed = true) {
                        every { warning(capture(messages)) } returns Unit
                    }

                PlanCommand(warnTerminal, planService).test(requiredArgs)

                messages.any { it.contains("Nothing to migrate") } shouldBe true
            }
        }

        describe("plan generation failure") {
            it("prints the error message when plan service returns a failure") {
                val planService =
                    mockk<PlanService> {
                        coEvery { generatePlan(any<PlanOptions>()) } returns
                            MigrationError.OrganizationNotFound("src-org").left()
                    }
                val dangerMessages = mutableListOf<String>()
                val dangerTerminal =
                    mockk<Terminal>(relaxed = true) {
                        every { danger(capture(dangerMessages)) } returns Unit
                    }

                PlanCommand(dangerTerminal, planService).test(requiredArgs)

                dangerMessages.any { it.contains("src-org") } shouldBe true
            }
        }
    })
