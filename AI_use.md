# AI Tool Documentation

## Overview

I used AI tools in **InvoiceMe** primarily as analytical and drafting assistants under my direction.  
They helped accelerate development by handling repetitive modeling and documentation tasks, while I made all design reasoning, validation, and final decisions manually.  
My process was iterative — the AI produced initial analyses, which I then reviewed, corrected, and refined through guided prompts and architectural feedback.

---

## Tools Used and Workflow

### 1. ChatGPT (GPT-5)

**Role:**  
I used ChatGPT as a structured brainstorming assistant during the early **Domain-Driven Design (DDD)** and architectural design phases.  
It generated first-pass analyses, which I then evaluated, corrected, or expanded through guided questioning.

**Process and Supervision:**  
- I began by asking the AI to *extract initial DDD design elements* — domains, subdomains, entities, value objects, and domain events — from the original PRD.  
- I reviewed those results, identified omissions (for example, missing “Reporting” and “Accounting Integration” subdomains), and prompted for further analysis.  
- I corrected assumptions that didn’t match the real requirements — such as clarifying that the system would **not** use Kafka or message queues, since the MVP scope didn’t warrant distributed messaging.  
- I also used the AI to generate **repository interface definitions** and boilerplate configuration for the **PostgreSQL 17** database, both for Docker-based local development and for AWS Aurora deployment.

**Example Prompts:**
```text
Read through and extract their initial DDD design elements: domain, sub-domains, entities, value objects, domain events, services, commands, queries, and so on.
```

```text
I feel like this PRD is lacking additional subdomains — at least reporting and accounting integration (to QuickBooks). Anything else?
```

```text
Also, for production, I don't intend to use Kafka or any queues later. This system won't grow beyond the MVP phase.
```

```text
Let's continue with defining the repository interfaces. Note: By requirement, we are to use Postgres...
````

**Outcome:**
This guided dialogue established a well-structured DDD foundation that aligned with the project’s business goals.
The AI accelerated exploration of alternatives while I maintained control over architectural boundaries and trade-offs.
It reduced mechanical drafting time (such as initial UML/ER modeling and repository scaffolding) but never replaced design judgment.

---

### 2. Cursor IDE (AI-Assisted Development Environment)

**Role:**
I used Cursor as an **AI-enhanced coding and testing assistant**, responsible for iterating through the generated task list from the PRD.
It executed one task at a time, generated boilerplate code, and ran automated test suites, reporting outcomes for my review.

**Process:**

* After finalizing the PRD and architecture, I created a detailed task list. Cursor worked through each task sequentially (for example, “Task 2.4 – Implement CustomerRepositoryAdapter”).
* For each iteration, I examined the output, verified test results, and adjusted the direction if the generated code diverged from the intended architecture.
* I also used Cursor to re-run JUnit + Testcontainers integration tests and produce concise reports summarizing passing and failing cases.
  Current test report summary (generated Nov 8, 2025):

  * Total tests: 167/167 passing (100% success rate, 6.3s execution time)
  * Invoice domain tests: 10/10 passing
  * Invoice repository tests: 4/4 passing
  * Invoice command handler tests: 20/20 passing (create, update, send, record payment, end-to-end flow)
  * Invoice API integration tests: 20/20 passing (REST endpoints, authentication, performance benchmarks)
  * Customer domain tests: 15/15 passing
  * Customer command handler tests: 4/4 passing
  * Customer API tests: 3/3 passing
  * Payment domain tests: 10/10 passing
  * Shared infrastructure tests: 81/81 passing (exception handling, validation, event publishing, persistence)

  **Note:** To reproduce these numbers, run `./backend/generate-test-report.sh` after executing `./gradlew test`.

**Example Prompts:**

```text
Re-run validation tests for Task 2.4 and summarize pass/fail counts.

Fix failing Money comparison tests using BigDecimal::isEqualByComparingTo().

Execute all API integration tests and report failing endpoints.

Generate curl verification tests for the API endpoints.
```

**Outcome:**
Cursor provided a structured, reproducible workflow similar to continuous integration.
It improved iteration speed and test coverage but operated strictly within my defined boundaries.

I mainly validated and questioned the initial design, but did not verify every AI-generated change before I accepted it into the codebase.

```bash
(main)$ cloc . \
  --include-lang=Java,Kotlin,TypeScript,SQL,YAML \
  --exclude-dir=build,test,.gradle,node_modules,target \
  --force-lang=TypeScript,ts \
  --force-lang=YAML,yml
     152 text files.
     151 unique files.                                          
      64 files ignored.

github.com/AlDanial/cloc v 1.90  T=0.04 s (2278.8 files/s, 147800.5 lines/s)
-------------------------------------------------------------------------------
Language                     files          blank        comment           code
-------------------------------------------------------------------------------
Java                            83            778            970           3534
TypeScript                       4             43            192            230
YAML                             5             24             29            151
SQL                              1             10             10             61
-------------------------------------------------------------------------------
SUM:                            93            855           1201           3976
-------------------------------------------------------------------------------
```