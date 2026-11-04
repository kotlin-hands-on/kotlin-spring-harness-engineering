# Spec Delta

## Purpose

Anonymous, link-based access to a group expense space: creating a group with a fixed ISO 4217 currency and reaching it afterwards by its secret token, which is the only credential and is never re-issued.

## ADDED Requirements

### Requirement: Group creation issues a secret token

A client SHALL create a group by supplying a non-blank name and an ISO 4217 currency code. The server SHALL generate a high-entropy secret access token, store the group, and return the token exactly once in the creation response. The currency is fixed at creation.

#### Scenario: Group is created with name, currency and token

- **WHEN** a create-group request supplies a non-blank name and a currency code the backend can resolve
- **THEN** the response is a successful creation and contains the group's id, its name, its currency code together with that currency's default fraction digits, and a newly generated access token

#### Scenario: Blank group name is rejected

- **WHEN** a create-group request supplies a missing or blank name
- **THEN** the response is status 400 with error code `validation_failed` and no group is created

#### Scenario: Unresolvable currency code is rejected

- **WHEN** a create-group request supplies a currency code the backend cannot resolve
- **THEN** the response is status 400 with error code `validation_failed` and no group is created

#### Scenario: Two creations issue distinct tokens

- **WHEN** two groups are created
- **THEN** the two creation responses carry different access tokens

### Requirement: The access token is the only credential

Group-scoped endpoints SHALL address the group by its access token in the path, per the api-conventions token-in-path rule. A request whose token matches no group SHALL be rejected as `not_found` without distinguishing a malformed token from an unknown one. No endpoint other than creation SHALL return the token.

#### Scenario: Group is read by its token

- **WHEN** a read request addresses an existing group by its token
- **THEN** the response is successful and contains the group's id, name, currency code and that currency's fraction digits, and it does not contain the token

#### Scenario: Unknown token is not found

- **WHEN** a request addresses a token that matches no group
- **THEN** the response is status 404 with error code `not_found`

#### Scenario: Malformed token is not found

- **WHEN** a request addresses a syntactically invalid token
- **THEN** the response is status 404 with error code `not_found`, indistinguishable from the unknown-token case

### Requirement: Group rename

A client SHALL be able to change the group's name by a request addressed with the token. Renaming SHALL NOT change the group's id.

#### Scenario: Group is renamed

- **WHEN** a rename request supplies a non-blank name
- **THEN** a subsequent read of the group returns the new name with the same id

#### Scenario: Blank name in rename is rejected

- **WHEN** a rename request supplies a missing or blank name
- **THEN** the response is status 400 with error code `validation_failed` and the group's name is unchanged

#### Scenario: Rename of an unknown token

- **WHEN** a rename request addresses a token that matches no group
- **THEN** the response is status 404 with error code `not_found`

### Requirement: Currency is fixed at creation

The group's currency SHALL NOT change after creation; no request SHALL alter it.

#### Scenario: Rename leaves the currency unchanged

- **WHEN** the group's name is updated
- **THEN** a subsequent read returns the currency code and fraction digits the group was created with

#### Scenario: A rename request carrying a currency field does not change it

- **WHEN** a rename request also carries a currency field with any value
- **THEN** the group's currency is unchanged after the update

### Requirement: Amounts are validated against the group's currency

Any request scoped to a group that carries a monetary amount SHALL have that amount validated against the group's fixed currency: a differing currency is rejected as `currency_mismatch`, an unresolvable currency code is rejected as `validation_failed`, per the api-conventions error model.

#### Scenario: Amount in the group's currency is accepted

- **WHEN** a group-scoped request carries an amount whose currency equals the group's currency
- **THEN** the request proceeds and the amount is accepted

#### Scenario: Amount in a different currency is rejected

- **WHEN** a group-scoped request carries an amount whose currency differs from the group's fixed currency
- **THEN** the response is status 422 with error code `currency_mismatch`

#### Scenario: Amount with an unresolvable currency is rejected

- **WHEN** a group-scoped request carries an amount with a currency code the backend cannot resolve
- **THEN** the response is status 400 with error code `validation_failed`
