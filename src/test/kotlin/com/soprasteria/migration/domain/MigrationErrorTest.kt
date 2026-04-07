package com.soprasteria.migration.domain

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain

class MigrationErrorTest :
    DescribeSpec({

        describe("MigrationError messages") {
            it("OrganizationNotFound includes the org name") {
                val error = MigrationError.OrganizationNotFound("my-org")
                error.message shouldContain "my-org"
            }

            it("RepositoryTransferFailed includes repo name and cause") {
                val error = MigrationError.RepositoryTransferFailed("my-repo", "403 Forbidden")
                error.message shouldContain "my-repo"
                error.message shouldContain "403 Forbidden"
            }

            it("MemberInviteFailed includes login and cause") {
                val error = MigrationError.MemberInviteFailed("alice", "User not found")
                error.message shouldContain "alice"
                error.message shouldContain "User not found"
            }

            it("TeamCreationFailed includes team name and cause") {
                val error = MigrationError.TeamCreationFailed("backend", "Name taken")
                error.message shouldContain "backend"
                error.message shouldContain "Name taken"
            }

            it("TeamMemberAddFailed includes member, team name and cause") {
                val error = MigrationError.TeamMemberAddFailed("alice", "backend", "Not a member of org")
                error.message shouldContain "alice"
                error.message shouldContain "backend"
                error.message shouldContain "Not a member of org"
            }

            it("TeamRepositoryAddFailed includes team name, repo name and cause") {
                val error = MigrationError.TeamRepositoryAddFailed("backend", "my-repo", "Repo not found")
                error.message shouldContain "backend"
                error.message shouldContain "my-repo"
                error.message shouldContain "Repo not found"
            }
        }
    })
