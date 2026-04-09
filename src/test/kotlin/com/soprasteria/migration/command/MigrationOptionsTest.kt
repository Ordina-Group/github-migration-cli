package com.soprasteria.migration.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** Minimal command that exposes MigrationOptions token resolution for testing. */
private class TokenProbeCommand : CliktCommand() {
    val opts by MigrationOptions()

    override fun run() {
        // Token resolution is tested directly via opts.effectiveSourceToken() / effectiveTargetToken()
    }
}

class MigrationOptionsTest :
    DescribeSpec({

        fun probe(vararg args: String): TokenProbeCommand {
            val cmd = TokenProbeCommand()
            cmd.parse(listOf("--source", "src-org", "--destination", "dst-org") + args)
            return cmd
        }

        describe("single --token") {
            it("is used for both source and target") {
                val cmd = probe("--token", "tok123")
                cmd.opts.effectiveSourceToken() shouldBe "tok123"
                cmd.opts.effectiveTargetToken() shouldBe "tok123"
            }
        }

        describe("--source-token and --target-token") {
            it("each resolves to its own value") {
                val cmd = probe("--source-token", "src-tok", "--target-token", "tgt-tok")
                cmd.opts.effectiveSourceToken() shouldBe "src-tok"
                cmd.opts.effectiveTargetToken() shouldBe "tgt-tok"
            }
        }

        describe("invalid combinations") {
            it("raises UsageError when only --source-token is provided") {
                val cmd = probe("--source-token", "src-tok")
                val ex = shouldThrow<UsageError> { cmd.opts.effectiveTargetToken() }
                ex.message shouldContain "target-token"
            }

            it("raises UsageError when only --target-token is provided") {
                val cmd = probe("--target-token", "tgt-tok")
                val ex = shouldThrow<UsageError> { cmd.opts.effectiveSourceToken() }
                ex.message shouldContain "source-token"
            }

            it("raises UsageError when no token option is provided at all") {
                val cmd = probe()
                val ex = shouldThrow<UsageError> { cmd.opts.effectiveSourceToken() }
                ex.message shouldContain "token"
            }
        }
    })
