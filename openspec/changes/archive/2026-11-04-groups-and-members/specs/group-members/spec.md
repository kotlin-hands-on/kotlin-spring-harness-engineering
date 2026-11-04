# Spec Delta

## Purpose

The people inside one group expense space: adding members, listing them, and managing each member's name and active state — all scoped to the group addressed by its token, with hard deletion guarded by ledger references.

## ADDED Requirements

### Requirement: Adding a member

A client SHALL add a member to the addressed group by supplying a non-blank name. The server SHALL assign the member an id and mark it active. Members carry no credentials; they exist only inside their group.

#### Scenario: Member is added

- **WHEN** an add-member request to an existing group supplies a non-blank name
- **THEN** the response is successful and contains the member's id, name and active state, and the member appears in the group's member list

#### Scenario: Blank member name is rejected

- **WHEN** an add-member request supplies a missing or blank name
- **THEN** the response is status 400 with error code `validation_failed` and no member is created

#### Scenario: Add with an unknown group token

- **WHEN** an add-member request addresses a token that matches no group
- **THEN** the response is status 404 with error code `not_found` and no member is created

### Requirement: Listing members

The member list SHALL contain every member of the addressed group — active and deactivated — each with its id, name and active state, in a deterministic order by name.

#### Scenario: List shows all members with their state

- **WHEN** the addressed group contains active and deactivated members
- **THEN** the list contains every one of them, each carrying its active state, ordered by name

#### Scenario: Empty group yields an empty list

- **WHEN** the addressed group contains no members
- **THEN** the list is empty

#### Scenario: List with an unknown group token

- **WHEN** a member-list request addresses a token that matches no group
- **THEN** the response is status 404 with error code `not_found`

### Requirement: Updating a member

A member's name and active state SHALL be updatable by its id within the addressed group. An absent field SHALL leave the corresponding property unchanged. Deactivation is the default removal path: the member stays in the list as inactive and can be reactivated later.

#### Scenario: Member is renamed

- **WHEN** an update-member request supplies a non-blank name
- **THEN** a subsequent member list returns the new name with the member's id and active state unchanged

#### Scenario: Member is deactivated

- **WHEN** an update-member request sets the member inactive
- **THEN** a subsequent member list returns the member with inactive state

#### Scenario: Member is reactivated

- **WHEN** an update-member request sets an inactive member active again
- **THEN** a subsequent member list returns the member with active state

#### Scenario: Absent fields leave the member unchanged

- **WHEN** an update-member request supplies no field to change
- **THEN** the member is returned unchanged

#### Scenario: Blank name in update is rejected

- **WHEN** an update-member request supplies a blank name
- **THEN** the response is status 400 with error code `validation_failed` and the member is unchanged

### Requirement: Members are addressable only within their own group

A member id SHALL be addressable only under the token of the group it belongs to. A request that addresses a member of another group under this group's token SHALL be rejected as `not_found`.

#### Scenario: Another group's member id is not found

- **WHEN** a member request addresses, under one group's token, a member id that belongs to a different group
- **THEN** the response is status 404 with error code `not_found`

#### Scenario: Other groups' members never appear in the list

- **WHEN** the addressed group's member list is read
- **THEN** it contains only members created in that group

### Requirement: Member deletion is guarded by references

A member SHALL be hard-deleted only while referenced by no ledger event. Deletion SHALL be rejected with `conflict` while any ledger event references the member; the `ON DELETE RESTRICT` reference from the ledger is the enforcement mechanism. A deleted member is gone from the list and is afterwards addressable only as `not_found`.

#### Scenario: Unreferenced member is deleted

- **WHEN** a delete request addresses a member referenced by no ledger event, whether active or deactivated
- **THEN** the member is removed, a subsequent member list no longer contains it, and addressing it afterwards returns status 404 with error code `not_found`

#### Scenario: Referenced member deletion is rejected

- **WHEN** a delete request addresses a member that is referenced by a ledger event
- **THEN** the response is status 409 with error code `conflict` and the member remains in the group

#### Scenario: Delete with an unknown member id

- **WHEN** a delete request addresses a member id that matches no member of the addressed group
- **THEN** the response is status 404 with error code `not_found`
