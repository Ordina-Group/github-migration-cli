package com.soprasteria.migration.command

import arrow.core.left
import arrow.core.right
import com.github.ajalt.clikt.testing.test
import com.github.ajalt.mordant.terminal.Terminal
import com.soprasteria.migration.domain.MigrationError
import com.soprasteria.migration.domain.Plan
import com.soprasteria.migration.service.ApplyService
import com.soprasteria.migration.service.PlanOptions
import com.soprasteria.migration.service.PlanService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

class ApplyCommandTest :
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
                val result =
                    ApplyCommand(terminal, mockk(), mockk()).test("--token tok --destination dst-org")
                result.statusCode shouldBe 1
                result.output shouldContain "source"
            }
        }

        describe("empty plan") {
            it("prints a warning and does not call apply when plan is empty") {
                val planService =
                    mockk<PlanService> {
                        coEvery { generatePlan(any<PlanOptions>()) } returns emptyPlan().right()
                    }
                val applyService = mockk<ApplyService>(relaxed = true)
                val messages = mutableListOf<String>()
                val warnTerminal =
                    mockk<Terminal>(relaxed = true) {
                        every { warning(capture(messages)) } returns Unit
                    }

                ApplyCommand(warnTerminal, planService, applyService).test(requiredArgs)

                messages.any { it.contains("Nothing to migrate") } shouldBe true
                coVerify(exactly = 0) { applyService.apply(any(), any(), any()) }
            }
        }

        describe("plan generation failure") {
            it("prints the error and does not proceed to apply") {
                val planService =
                    mockk<PlanService> {
                        coEvery { generatePlan(any<PlanOptions>()) } returns
                            MigrationError.OrganizationNotFound("src-org").left()
                    }
                val applyService = mockk<ApplyService>(relaxed = true)
                val dangerMessages = mutableListOf<String>()
                val dangerTerminal =
                    mockk<Terminal>(relaxed = true) {
                        every { danger(capture(dangerMessages)) } returns Unit
                    }

                ApplyCommand(dangerTerminal, planService, applyService).test(requiredArgs)

                dangerMessages.any { it.contains("src-org") } shouldBe true
                coVerify(exactly = 0) { applyService.apply(any(), any(), any()) }
            }
        }

        describe("user cancels confirmation") {
            it("does not call apply when user answers 'no'") {
                val plan =
                    mockk<Plan> {
                        every { repositories } returns listOf(mockk(relaxed = true))
                        every { members } returns emptyList()
                        every { teams } returns emptyList()
                        every { sourceOrganization } returns mockk { every { name } returns "S"; every { login } returns "s" }
                        every { destinationOrganization } returns mockk { every { name } returns "D"; every { login } returns "d" }
                        every { parentTeam } returns null
                    }
                val planService =
                    mockk<PlanService> {
                        coEvery { generatePlan(any<PlanOptions>()) } returns plan.right()
                    }
                val applyService = mockk<ApplyService>(relaxed = true)
                val promptTerminal =
                    mockk<Terminal>(relaxed = true) {
                        every { prompt(any(), choices = any()) } returns "no"
                    }

                ApplyCommand(promptTerminal, planService, applyService).test(requiredArgs)

                coVerify(exactly = 0) { applyService.apply(any(), any(), any()) }
            }
        }
    })
